# Runtime Effects

`SkRuntimeEffect` is the bridge that lets a Skia client author **shaders,
color filters, and blenders in SkSL at runtime**, then plug them into a
paint as a regular `SkShader`, `SkColorFilter`, or `SkBlender`. It is
the public API for SkSL — every other reference in this documentation
that mentions a "known runtime effect" or a "user-defined SkSL stage"
ultimately funnels through this header.

This doc covers the runtime layer: how SkSL source is compiled into a
reusable effect, how uniforms and child effects are bound, how the
resulting shader/filter/blender is dispatched to each backend, and how
the `SkImageFilters::RuntimeShader` factory exposes runtime effects to
the image-filter DAG. The compiler internals — lexer, IR, optimizer,
and the GLSL/Metal/SPIR-V/WGSL/HLSL/Raster-Pipeline code generators —
live in `src/sksl/` and are documented in
[SkSL Shading Language](sksl-shading-language.md).

## Pipeline at a glance

```
   SkString sksl ──► SkRuntimeEffect::MakeForShader / ForColorFilter / ForBlender
                         │
                         │ SkSL::Compiler::convertProgram
                         ▼
                  ┌──────────────┐    reflection collected:
                  │  SkSL::Program│   - uniforms (name, type, offset, count, flags)
                  │  + main fn    │   - children (shader/colorFilter/blender slots)
                  └──────┬────────┘   - sample-usage info (passthrough vs. variable)
                         │            - flags (alwaysOpaque, alphaUnchanged, ...)
                         ▼
              ┌─────────────────────┐
              │ sk_sp<SkRuntimeEffect>│  reusable, thread-safe handle
              └─────────┬───────────┘
                        │ effect->makeShader(uniforms, children, lm)
                        │ effect->makeColorFilter(uniforms, children)
                        │ effect->makeBlender(uniforms, children)
                        ▼
                ┌────────────────┐
                │ SkRuntimeShader│   wraps effect + sk_sp<SkData> uniforms
                │ SkRuntimeColor-│   + ChildPtr[] children;
                │   Filter       │   appendStages defers to a lazily-compiled
                │ SkRuntimeBlender│   SkSL::RP::Program (raster pipeline) or
                └────────┬───────┘   to backend-specific GLSL/MSL/etc.
                         │
                         ▼
              raster (CPU): SkSL::RP::Program → SkRasterPipeline ops
              GPU (Ganesh / Graphite): SkSL → backend FP / SkSL fragment
```

## Public API surface

| Header | Purpose |
|---|---|
| `include/effects/SkRuntimeEffect.h` | `SkRuntimeEffect`, `SkRuntimeEffect::Uniform`, `Child`, `ChildType`, `ChildPtr`, `Options`, `Result`, `TracedShader`, plus `SkRuntimeEffectBuilder` (a.k.a. the legacy `SkRuntimeShaderBuilder` / `SkRuntimeColorFilterBuilder` / `SkRuntimeBlendBuilder`). |
| `include/effects/SkBlenders.h` | `SkBlenders::Arithmetic` — a runtime-effect-backed arithmetic blender. |
| `include/effects/SkLumaColorFilter.h`, `SkOverdrawColorFilter.h`, `SkHighContrastFilter.h` | Built-in colour filters that are themselves implemented as runtime effects. |
| `include/effects/SkImageFilters.h` (`RuntimeShader` factories) | Pipe a runtime effect into an image-filter DAG. |
| `src/effects/SkRuntimeEffect.cpp` | Compilation, uniform reflection, raster-pipeline dispatch, child-effect plumbing, serialisation. |
| `src/shaders/SkRuntimeShader.{h,cpp}` | The `SkShader` subclass returned by `makeShader`. |
| `src/effects/colorfilters/SkRuntimeColorFilter.{h,cpp}` | The `SkColorFilter` subclass returned by `makeColorFilter`. |
| `src/core/SkRuntimeBlender.{h,cpp}` | The `SkBlender` subclass returned by `makeBlender`. |
| `src/core/SkKnownRuntimeEffects.{h,cpp}` | A registry of "stable-key" effects that Skia constructs internally (gainmap, noise, blend, lighting, ...) and serialises by stable key rather than by source. |
| `src/effects/imagefilters/SkRuntimeImageFilter.cpp` | The image-filter primitive returned by `SkImageFilters::RuntimeShader`. |
| `src/core/SkRuntimeEffectPriv.h` | Internal helpers: `TransformUniforms`, `ReadChildEffects`, `WriteChildEffects`, `CanDraw`, `RuntimeEffectRPCallbacks`. |

## Authoring an effect

Three flavours, distinguished by the SkSL `main()` signature they require:

```cpp
// Shader: produces a colour for a given coordinate.
//     half4 main(float2 inCoords) { ... }
SkRuntimeEffect::Result rs = SkRuntimeEffect::MakeForShader(SkString(sksl));

// Color filter: transforms an input colour.
//     half4 main(half4 inColor) { ... }
SkRuntimeEffect::Result rc = SkRuntimeEffect::MakeForColorFilter(SkString(sksl));

// Blender: combines src and dst.
//     half4 main(half4 srcColor, half4 dstColor) { ... }
SkRuntimeEffect::Result rb = SkRuntimeEffect::MakeForBlender(SkString(sksl));
```

Each returns `Result { sk_sp<SkRuntimeEffect> effect; SkString errorText; }`.
A non-null `effect` indicates a successful compile; otherwise `errorText`
contains the SkSL compiler's diagnostic. The colour values are flexible:
`vec4`, `half4`, and `float4` are interchangeable in the entry point.
Returned colours from a shader's `main` are expected to be premultiplied;
returned colours from a colour filter or blender are expected to honor
the input alpha (the compiler enforces these rules through the
`kRuntimeShader` / `kRuntimeColorFilter` / `kRuntimeBlender` program
kinds).

`Options`:

| Field | Meaning |
|---|---|
| `forceUnoptimized` | Disable optimisation and inlining (testing only). |
| `fName` | A debug name attached to the compiled program. |
| `allowPrivateAccess` | (private) Permit references to internal Skia helpers like `sk_FragCoord` or `$rgb_to_hsl`. |
| `fStableKey` | (private) Reserved for `SkKnownRuntimeEffects` so internal effects can be flattened by 32-bit key instead of by source. |
| `maxVersionAllowed` | (private) Default is `SkSL::Version::k100`; tests can opt into `k300` for ES3 features (the CPU raster-pipeline still has limited ES3 support). |

The compilation pipeline (`MakeFromSource` → `MakeInternal` in
`src/core/SkRuntimeEffect.cpp`) does the following:

1. `SkSL::Compiler::convertProgram(kind, source, settings)` parses the
   SkSL into an `SkSL::Program`. On failure it returns null and the
   compiler's `errorText` is propagated up.
2. The `main` function is located and inspected for sample-coord usage.
   If the entry point is a colour filter or blender it is required not
   to depend on position (sample coords or `sk_FragCoord`).
3. `SkSL::Analysis` walks the program to set the rest of the flags:
   - `kSamplesOutsideMain_Flag` — does the effect call `child.eval` from
     a function other than `main`?
   - `kAlphaUnchanged_Flag` — for colour filters, does `main` return the
     input alpha unmodified?
   - `kUsesColorTransform_Flag` — does the effect use `toLinearSrgb` /
     `fromLinearSrgb` intrinsics?
   - `kAlwaysOpaque_Flag` — does `main` always return α=1?
4. The program's globals are walked to extract `uniform` declarations
   (becoming `Uniform` records) and child slots (becoming `Child`
   records). `SampleUsage` is recorded per child so the runtime can
   later decide whether the total local matrix is preserved through
   each `child.eval(coord)` call (passthrough) or destroyed (any other
   sample pattern).
5. The result is wrapped in a `sk_sp<SkRuntimeEffect>`; identical SkSL
   strings are deduplicated through `SkMakeCachedRuntimeEffect`
   (an `SkLRUCache` keyed by hash + kind, in `SkRuntimeEffect.cpp`).

The compiler does *not* generate the raster-pipeline byte-code at this
point. That is deferred to the first `appendStages` call via
`SkRuntimeEffect::getRPProgram`, which uses an `SkOnce` to amortise the
cost. If `forceUnoptimized` was *not* set but the parsed program was
left un-inlined (the public `convertProgram` skips the inliner because
the GPU compile would normally handle inlining), the program is
re-compiled with `fOptimize = true` and a default inline threshold so
the raster pipeline gets the inlining benefit.

## Uniforms

```cpp
struct Uniform {
    enum class Type { kFloat, kFloat2, kFloat3, kFloat4,
                      kFloat2x2, kFloat3x3, kFloat4x4,
                      kInt, kInt2, kInt3, kInt4 };
    enum Flags {
        kArray_Flag         = 0x01,
        kColor_Flag         = 0x02,  // layout(color)
        kVertex_Flag        = 0x04,  // SkMeshSpecification only
        kFragment_Flag      = 0x08,  // SkMeshSpecification only
        kHalfPrecision_Flag = 0x10,  // declared as `half`
    };
    std::string_view name;
    size_t   offset;
    Type     type;
    int      count;
    uint32_t flags;
};
```

Each uniform is given a packed offset within a single byte buffer; the
total byte size is reported by `SkRuntimeEffect::uniformSize`. The
caller is responsible for providing an `sk_sp<const SkData>` of exactly
that size. The packing rule mirrors std140 with the additional caveat
that `half` types are packed as 32-bit floats inside the buffer (the
compiler upcasts `half` declarations to float-precision uniforms).

`layout(color)` uniforms are special: the caller supplies an
unpremultiplied, *unclamped*, sRGB `SkColor4f`, and the runtime
automatically transforms it into the destination colour space at draw
time. `SkRuntimeEffectPriv::TransformUniforms` (`SkRuntimeEffect.cpp`)
walks the uniform vector and applies the appropriate
`SkColorSpaceXformSteps` to each tagged uniform. The transformed buffer
is allocated in the per-frame arena so it does not outlive the draw.

## Children — `ChildPtr` and child types

A child slot is one of three things, declared in SkSL as
`uniform shader|colorFilter|blender x;`. The reflection records the
declared type:

```cpp
enum class ChildType { kShader, kColorFilter, kBlender };

struct Child { std::string_view name; ChildType type; int index; };
```

The `ChildPtr` value type stores a `sk_sp<SkFlattenable>` that is one of
the three permitted types (or null). Implicit constructors accept
`sk_sp<SkShader>`, `sk_sp<SkColorFilter>`, or `sk_sp<SkBlender>`. The
caller supplies them in the order declared in SkSL; the array length
must match `effect->children().size()`. `verify_child_effects` (in
`SkRuntimeEffect.cpp`) cross-checks the runtime types against the SkSL
types and rejects mismatched calls.

In the SkSL itself, children are evaluated via
`color = childShader.eval(coord)`,
`color = childColorFilter.eval(inputColor)`, or
`color = childBlender.eval(srcColor, dstColor)`. Skia tracks how each
child is sampled so it can preserve the local matrix:

- *Passthrough* — the child is called with the same coord that came
  into `main`. The total matrix remains valid and is forwarded
  unchanged when the child is itself a shader.
- *Variable* — the child is called with a transformed coord. The total
  matrix is *invalidated* before the child is dispatched
  (`MatrixRec::markTotalMatrixInvalid`), preventing optimisations that
  rely on knowing the device-to-local mapping (e.g. mip selection).

The dispatch is performed by `RuntimeEffectRPCallbacks` (in
`SkRuntimeEffect.cpp`). When the SkSL byte-code reaches a `child.eval`
opcode it calls one of `appendShader`, `appendColorFilter`, or
`appendBlender` on the callbacks, which then `appendStages` the
appropriate `SkShaderBase`/`SkColorFilterBase`/`SkBlenderBase`. A null
child is replaced with a sensible default (transparent black for a
shader, identity for a colour filter, src-over for a blender).

## Building from a runtime effect

There are two factory styles. The flat one takes a uniform `SkData`
plus a child array:

```cpp
sk_sp<SkShader>     effect->makeShader     (uniforms, children, localMatrix);
sk_sp<SkColorFilter>effect->makeColorFilter(uniforms, children);
sk_sp<SkBlender>    effect->makeBlender    (uniforms, children);
```

The fluent one — `SkRuntimeEffectBuilder` — manages the byte buffer
and provides named accessors:

```cpp
SkRuntimeEffectBuilder b(effect);
b.uniform("uColor")       = SkColor4f{1, 0, 0, 1};   // checks size
b.uniform("uMatrix")      = SkMatrix::Translate(3, 4);  // transposes to col-major
b.child  ("uChildShader") = my_shader;
sk_sp<SkShader> shader    = b.makeShader();
```

`BuilderUniform::operator=` and `BuilderChild::operator=` validate that
the named slot exists and that the type sizes match; both
`SkDEBUGFAIL` on mismatch and become silent no-ops in release. There is
also a `set(const T*, count)` for array uniforms, and a special
overload for `SkMatrix` that transposes the input into the column-major
layout SkSL expects.

`SkRuntimeShaderBuilder`, `SkRuntimeColorFilterBuilder`, and
`SkRuntimeBlendBuilder` are aliases preserved for compatibility — the
unified base class provides all three `makeShader` / `makeColorFilter`
/ `makeBlender` build methods, and validates the SkSL `main` signature
matches the requested output kind on each call.

## Backends

### CPU — raster pipeline

`SkRuntimeShader::appendStages` (`src/shaders/SkRuntimeShader.cpp`)
checks `SkRuntimeEffectPriv::CanDraw` first to verify the effect's
required SkSL version is supported by the raster backend (currently
`#version 100` only). If supported, it fetches the lazily-built
`SkSL::RP::Program` via `SkRuntimeEffect::getRPProgram` and runs
`program->appendStages(rec.fPipeline, rec.fAlloc, callbacks, uniforms)`.
The `RuntimeEffectRPCallbacks` carry the in-flight `SkShaders::MatrixRec`
and the `ChildPtr` array, and they implement the `child.eval` /
`toLinearSrgb` / `fromLinearSrgb` intrinsics.

Runtime colour filters (`SkRuntimeColorFilter`) and blenders
(`SkRuntimeBlender`) follow the same pattern: lazily compile the
raster-pipeline program once, hand it the uniforms (transformed into
the dst colour space), and walk through the bytecode emitting
`SkRasterPipelineOp::*` stages. The bytecode lives in
`src/sksl/codegen/SkSLRasterPipelineBuilder.h` and the per-instruction
emitter is in `SkSLRasterPipelineCodeGenerator`.

The `SkRP` byte-code is fairly capable but does not yet implement all
of `#version 300 es`; effects that require it succeed at compile time
(when `maxVersionAllowed = k300`) but fall back to "draws nothing" on
the CPU until the missing ops are filled in.

### GPU — Ganesh and Graphite

For GPU dispatch the SkSL is re-emitted into the appropriate backend
language by the compiler in `src/sksl/codegen/`:
- `SkSLGLSLCodeGenerator` — GLSL for desktop GL / WebGL / GLES.
- `SkSLMetalCodeGenerator` — MSL for the Metal backend.
- `SkSLSPIRVCodeGenerator` — SPIR-V for Vulkan.
- `SkSLWGSLCodeGenerator` — WGSL for WebGPU / Dawn.
- `SkSLHLSLCodeGenerator` — HLSL for Direct3D 12 (via SPIR-V-Cross).

Ganesh wraps the resulting program in a `GrSkSLFP` (a
`GrFragmentProcessor`) that is composed into the GP/FP tree the
op-flushing layer builds; uniforms are uploaded to a `GrShaderCaps`-
provided uniform buffer per draw. Graphite emits the SkSL into the
fragment of the precompiled `Recorder`-side pipeline; uniforms are
written into the per-instance buffer slice. See
[Ganesh Backend](ganesh-backend.md) and
[Graphite Backend](graphite-backend.md) for the broader context.

`SkCapabilities` (`include/core/SkCapabilities.h`) exposes the maximum
SkSL version the runtime supports for the current device, which is
how `SkRuntimeEffectPriv::CanDraw` decides whether the effect is
usable.

### Image-filter integration — `SkImageFilters::RuntimeShader`

`include/effects/SkImageFilters.h` exposes two overload families that
let a runtime shader appear in an image-filter DAG:

```cpp
SkImageFilters::RuntimeShader(builder, childShaderName, input);
SkImageFilters::RuntimeShader(builder, sampleRadius, childShaderName, input);
SkImageFilters::RuntimeShader(builder, childShaderNames[], inputs[], inputCount);
SkImageFilters::RuntimeShader(builder, maxSampleRadius, childShaderNames[], inputs[], inputCount);
```

The implementation in `src/effects/imagefilters/SkRuntimeImageFilter.cpp`
binds each named child shader of the runtime effect to the
corresponding image-filter input (using the layer source for null
inputs). The `sampleRadius` (or `maxSampleRadius`) tells the bounds
machinery how far the SkSL might offset coords from the output position
when sampling each child — this lets Skia size the child layer's
render target to include any neighbouring pixels the shader might
read. `SkRuntimeImageFilter::onAffectsTransparentBlack` returns true,
because the SkSL is free to produce non-zero output for transparent
input.

A current limitation: the runtime image filter forces
`MatrixCapability::kTranslate`, because there is no way for an effect
to declare which uniforms are geometric (and so should respond to the
canvas matrix). Tracked as skbug.com/40044507.

## Tracing

`SkRuntimeEffect::MakeTraced(shader, traceCoord)` returns a
`TracedShader { sk_sp<SkShader>, sk_sp<SkSL::DebugTrace> }` pair — the
traced shader behaves identically to the original but, when painted
onto a raster canvas, writes a full SkSL execution trace at the
requested device-space coordinate into the debug trace object. The
trace can be dumped (`writeTrace`) into the format SkSL Debugger
consumes, or `dump`'d as human-readable text. The implementation forks
the runtime effect through `makeUnoptimizedClone()` so the SkSL source
and IR survive for stepping; this is why `kDisableOptimization_Flag`
exists on the effect's flag set.

Tracing currently works only on the CPU raster backend and only for
shaders; colour-filter / blender tracing is in progress.

## Stable keys and known runtime effects

Skia builds many of its internal effects (Perlin noise, gainmap apply,
displacement, morphology, lighting, the `SkBlenders::Arithmetic`
blender, the gainmap and HDR pipelines, etc.) on top of
`SkRuntimeEffect`. To avoid serialising the full SkSL source for these
into every SKP, the effects register a 32-bit *stable key* via
`SkKnownRuntimeEffects` (`src/core/SkKnownRuntimeEffects.{h,cpp}`).

When `SkRuntimeShader::flatten` (or its colour-filter / blender
counterparts) sees a non-zero `fStableKey` it writes the key instead of
the source; on deserialise the key is matched against
`SkKnownRuntimeEffects::MaybeGetKnownRuntimeEffect` which returns the
re-built `SkRuntimeEffect`. User-defined effects always serialise by
source and re-compile on the receiving end.

## Serialisation summary

The flatten format for a runtime shader / color-filter / blender is:

```
u32   stableKey         // 0 for user-defined effects
str   sksl              // present only when stableKey == 0
bytes uniformData       // flat byte buffer matching effect->uniformSize()
u32   childCount
[childCount] flattenable_record  // each is an SkShader / SkColorFilter / SkBlender
```

Older SKPs may include a legacy local matrix immediately after the
uniform data (read into an `std::optional<SkMatrix>` and re-applied via
`SkLocalMatrixShader`). Lenient deserialisation
(`kLenientSkSLDeserialization`, true under `SK_BUILD_FOR_DEBUGGER`)
will fall back to returning the first child shader if the SkSL fails to
re-compile; in normal builds a re-compile failure poisons the buffer.

## Practical example

```cpp
const char* kCheckerboard = R"(
    uniform half2  iTileSize;
    uniform half3  iColorA;
    uniform half3  iColorB;
    half4 main(float2 p) {
        int2  c = int2(floor(p / iTileSize));
        bool  pick = ((c.x ^ c.y) & 1) == 0;
        half3 rgb = pick ? iColorA : iColorB;
        return half4(rgb, 1);
    }
)";

auto [effect, error] = SkRuntimeEffect::MakeForShader(SkString(kCheckerboard));
if (!effect) { /* report error */ return; }

SkRuntimeShaderBuilder b(effect);
b.uniform("iTileSize") = SkV2{16, 16};
b.uniform("iColorA")   = SkV3{1, 1, 1};
b.uniform("iColorB")   = SkV3{0, 0, 0};

SkPaint paint;
paint.setShader(b.makeShader());
canvas->drawPaint(paint);
```

The compiled `effect` is reusable — keep it alive across draws to
avoid re-compiling. The `SkRuntimeShaderBuilder` is cheap to
re-construct per draw, and the resulting `SkShader` is immutable, so a
stateless effect can also be built once and reused if the uniforms do
not change.

For the SkSL language reference (operators, intrinsics, sampling
syntax, ES2 vs ES3 differences) and the compiler architecture, see
[SkSL Shading Language](sksl-shading-language.md). For paint-side
configuration see [Paint, Color & Blending](paint-color-and-blending.md);
for how runtime shaders interact with image-filter graphs see
[Image Filters & Mask Filters](image-filters-and-mask-filters.md); for
working-colour-space behaviour see [Color Management](color-management.md).
