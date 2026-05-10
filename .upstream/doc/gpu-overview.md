# GPU Overview

Skia ships two GPU backends — **Ganesh** (legacy, in `src/gpu/ganesh/`)
and **Graphite** (current, in `src/gpu/graphite/`) — but they share a
substantial layer of common infrastructure that lives directly under
`src/gpu/` in the `skgpu::` namespace. This document describes that
shared layer: the public type vocabulary, blend equations, swizzles,
resource keys, atlas primitives, dither / blur / tiling helpers, the
`SkSLToBackend` shim, and a handful of small utilities.

The backend-specific documents pick up where this one leaves off:

- [Ganesh Backend](ganesh-backend.md) — context, ops, render tasks, GLSL
- [Graphite Backend](graphite-backend.md) — recorder, tasks, draw passes
- [GPU Tessellation](gpu-tessellation.md) — shared path tessellator
- [SkSL Shading Language](sksl-shading-language.md) — front-end compiler
- [Surface & Output Targets](surface-and-output.md) — `SkSurface` factories

Per-API ports — [OpenGL / GLES](backend-opengl.md), [Vulkan](backend-vulkan.md),
[Metal](backend-metal.md), [Direct3D](backend-direct3d.md), [Dawn / WebGPU](backend-dawn.md)
— are documented separately.

## Layering at a glance

```
   ┌────────────────────────────────────────────────────────┐
   │  SkCanvas / SkSurface / SkImage / SkPaint  (core API)  │
   └─────────────────────────┬──────────────────────────────┘
                             │
   ┌─────────────────────────┴──────────────────────────────┐
   │             src/gpu/  (skgpu namespace)                │
   │  GpuTypes  Blend  BlendFormula  Swizzle  ResourceKey   │
   │  Rectanizer  GrAtlasTypes / DrawAtlas   BufferWriter   │
   │  BlurUtils  DitherUtils  TiledTextureUtils  Token      │
   │  SkSLToBackend  ShaderErrorHandler  MutableTextureSt   │
   └────────────┬────────────────────────────┬──────────────┘
                │                            │
   ┌────────────┴───────────┐  ┌─────────────┴─────────────┐
   │  src/gpu/ganesh/       │  │  src/gpu/graphite/        │
   │  (legacy)              │  │  (current)                │
   │  GrDirectContext, ops/ │  │  Context, Recorder,       │
   │  GrRenderTask, glsl/   │  │  DrawPass, RenderStep, …  │
   └────┬───────┬──────┬────┘  └──────┬──────────┬─────────┘
        │       │      │              │          │
        gl/    vk/    mtl/           Dawn       Metal
        d3d/                         Vulkan
```

The shared layer is dependency-light and knows nothing about contexts,
recorders, or drawing — it gives both backends common vocabulary so
GLSL effects, blend coalescing, glyph atlases, and resource caching
speak the same dialect.

The `src/gpu/tessellate/` subtree is shared too but has its own
document — see [GPU Tessellation](gpu-tessellation.md).

---

## Public type vocabulary — `include/gpu/GpuTypes.h`

The header defines the strongly-typed bool-like enums every client sees:

```cpp
enum class BackendApi : unsigned { kDawn, kMetal, kVulkan, kMock, kUnsupported };
enum class Budgeted   : bool { kNo = false, kYes = true };
enum class Mipmapped  : bool { kNo = false, kYes = true };
enum class Protected  : bool { kNo = false, kYes = true };
enum class Renderable : bool { kNo = false, kYes = true };
enum class Origin     : unsigned { kTopLeft, kBottomLeft };
```

`Budgeted::kYes` charges the allocation to the GPU resource cache;
`kNo` means the resource lives outside the budget (e.g. a wrapped
client texture). `Origin` reflects the texture's logical Y orientation
when it was created — important for GL render targets, which Skia
flips on read-back.

`MutableTextureState` is a ref-counted, type-erased wrapper holding a
small inlined backend-specific state (`SkAnySubclass` of size 16). The
Vulkan port stores its `(VkImageLayout, queueFamilyIndex)` here; other
backends ignore it. Clients use it to inform Skia of layout transitions
done outside Skia (and vice versa, to request transitions back).

`ShaderErrorHandler` is the abstract sink Skia hands compiler errors
to. The default implementation logs via `SkDebugf` and asserts.

---

## Blend equations — `src/gpu/Blend.h`, `BlendFormula.h`

`BlendEquation` enumerates 18 equations:

| Range | Members |
|---|---|
| Basic (HW supported) | `kAdd`, `kSubtract`, `kReverseSubtract` |
| Advanced (KHR_blend_equation_advanced / SVG / PDF) | `kScreen`, `kOverlay`, `kDarken`, `kLighten`, `kColorDodge`, `kColorBurn`, `kHardLight`, `kSoftLight`, `kDifference`, `kExclusion`, `kMultiply`, `kHSL{Hue,Saturation,Color,Luminosity}` |
| Sentinel | `kIllegal` |

`BlendCoeff` lists 16 coefficients (`kZero`, `kOne`, `kSC`, `kISC`,
`kDC`, `kIDC`, `kSA`, `kISA`, `kDA`, `kIDA`, `kConstC`, `kIConstC`,
plus `kS2C`, `kIS2C`, `kS2A`, `kIS2A` for dual-source). A `BlendInfo`
POD bundles `(equation, srcCoeff, dstCoeff, blendConstant, writesColor)`.

`constexpr` predicates give the GPU code its algebraic vocabulary:
`BlendCoeffRefsSrc` / `RefsDst` / `RefsSrc2` ask which shader output
a coefficient consumes; `BlendEquationIsAdvanced` flags equations that
must lower to a programmable blend in SkSL when HW doesn't support them;
`BlendShouldDisable` lets the framework drop a blend altogether for
`(kAdd, kOne, kZero)`; `BlendAllowsCoverageAsAlpha` proves it is safe
to fold pixel coverage into the alpha channel rather than emitting it
as a secondary output. `BlendFuncName(SkBlendMode)` returns the SkSL
built-in implementing that mode (e.g. `"blend_src_over"`).

`BlendFormula` is the mapping `(SkBlendMode, has-coverage,
src-is-opaque)` → `(primaryOutputType, secondaryOutputType, equation,
srcCoeff, dstCoeff)`. `OutputType` enumerates what the fragment shader
should write: nothing, coverage, modulated color, modulated alpha,
inverse-source-alpha modulated, etc. Both backends consult this table
to decide between single-source HW blending, dual-source, or full
programmable blending.

## Swizzles — `src/gpu/Swizzle.h`

`Swizzle` is a 4-channel reordering encoded in a single `uint16_t` (4
bits per channel: `r=0, g=1, b=2, a=3, '0'=4, '1'=5`). Constants
`Swizzle::RGBA()`, `BGRA()`, `RRRA()`, `RGB1()` cover common cases.
Operations: `apply(SkRasterPipeline*)` (CPU fallback),
`applyTo(SkRGBA4f<>)` (constant-fold into a color literal),
`Concat(a, b)` (function composition), `invert()` (best-effort inverse),
`selectChannelInR(i)` (extract single channel into R, zero rest — used
for SDF channels), and `asKey()` (the raw 16-bit code, suitable for
hashing into a `ResourceKey`).

Texture views and render targets each carry a read-swizzle and a
write-swizzle so a backend can map odd format choices (e.g. an
`R8G8B8A8` GL texture used as an `RRRA` alpha-only mask) to the
shader's expected semantics with no data copies.

## Resource keys — `src/gpu/ResourceKey.h`

Every cached GPU resource is keyed by either a `ScratchKey` or a
`UniqueKey`, both subclasses of `ResourceKey`. The key is a packed
`uint32_t` array beginning with two metadata words (hash and
domain-or-size), built through a `Builder` that hashes on destruction.

- **Scratch keys** describe an interchangeable resource type — multiple
  resources can share one. The block-comment in `ResourceKey.h` walks
  through a separable-blur example: two scratch textures get checked
  out simultaneously by the same key, each gets a different physical
  resource, and they recycle independently as refs drop.
- **Unique keys** describe a single owning use case (a "domain"). Skia
  guarantees at most one resource has a given unique key at a time;
  cross-domain collisions are prevented by generating fresh domains
  via `UniqueKey::GenerateDomain()`. While a unique key is set, the
  resource is invisible to scratch lookups.

The cache (`GrResourceCache` in Ganesh, `ResourceCache` in Graphite)
indexes both spaces, so a resource may be reusable through its scratch
key, claimed via a unique key, and re-released back to scratch when
the unique key is removed.

## Atlases and rectanizers

`Rectanizer` (`src/gpu/Rectanizer.h`) is the abstract 2-D bin-packer
interface (`addRect(w, h, &out)`, `percentFull()`). Two implementations:
`RectanizerPow2` (quick, fragments harder) and `RectanizerSkyline`
(better packing, used by every production atlas). `addPaddedRect`
inflates the request by per-side padding so glyphs leave room for
bilinear filtering at edges.

Atlas pages are organised in plots; each plot keeps a `Token` (see
`src/gpu/Token.h`) recording the most recent flush that consumed it,
so LRU eviction picks plots that no in-flight GPU work needs.

`MaskFormat` (`src/gpu/MaskFormat.h`) enumerates the three glyph-cache
mask flavours: `kA8` (monochrome / SDF coverage), `kA565` (3-channel
LCD coverage stored in RGB565), and `kARGB` (full-color glyphs — emoji,
COLR/SVG outlines). `MaskFormatToColorType` maps each to the
`SkColorType` used to allocate the backing texture.

Both backends layer their own atlas managers on top:
`GrAtlasTypes.h` + `GrDrawOpAtlas` + `GrDynamicAtlas` for Ganesh, and
`graphite/DrawAtlas.h` + `AtlasProvider` + `PathAtlas` /
`ClipAtlasManager` for Graphite. Both ultimately call into a
`Rectanizer` and key plots with `Token`s.

## SkSL → backend — `src/gpu/SkSLToBackend.h`

Both backends pull SkSL through the same shim — `SkSLToBackend` takes
a `ShaderCaps`, an `sksl` string, a `ProgramKind`, a callback pointer
(`ToGLSL`, `ToSPIRV`, `ToHLSL`, `ToMSL`, or `ToWGSL`), and a
`ShaderErrorHandler`. It compiles to an `SkSL::Program`, dispatches
to the callback, populates `NativeShader::fText` or `fBinary`, fills
the program interface (uniforms, samplers, varyings) needed to bind
the program, and routes diagnostics to the error handler. Each
per-API backend lives next to a thin `<api>Utils.cpp` that supplies
its own callback. See [SkSL Shading Language](sksl-shading-language.md)
for what happens upstream of this call.

## Smaller helpers

- **`BufferWriter`** (`src/gpu/BufferWriter.h`) — non-owning pointer
  with a `Mark` for sub-region fences. Subclasses `VertexWriter`,
  `IndexWriter`, `UniformWriter`, and `TextureUploadWriter` add typed
  `<<` operators (`vw << SkPoint{x,y} << uv;`) and centralize alignment.
- **Blur** (`src/gpu/BlurUtils.h`) — thin re-exports of
  `SkBlurEngine` / `SkShaderBlurAlgorithm`. Picks between a 2-D sampled
  kernel and separable 1-D linear-sampling kernels, returning a cached
  `SkRuntimeEffect`.
- **Dither** (`src/gpu/DitherUtils.h`) — `DitherRangeForConfig` returns
  the per-format noise amplitude (e.g. `1/63` for `kRGB_565`);
  `MakeDitherLUT()` builds the 8x8 Bayer texture once and caches it.
- **Tiled textures** (`src/gpu/TiledTextureUtils.h`) —
  `ShouldTileImage` decides at draw time whether to break a too-big
  `SkImage` into tiles based on clip bounds, max texture size, and
  resource budget; `OptimizeSampleArea` trims `(src, dst)` to image
  bounds.
- **Backing fit** (`src/gpu/SkBackingFit.h`) —
  `SkBackingFit::{kApprox, kExact}` distinguishes "any size at least
  this big" (so the cache can recycle a larger plot) from "exactly
  this size". `GetApproxSize` rounds up to the next power of two (or
  midpoint), bucketing scratch allocations.
- **Async-readback** (`src/gpu/AsyncReadTypes.h`) —
  `TClientMappedBufferManager` keeps mapped buffers alive across
  threads until the client signals completion via `SkMessageBus`.

---

Once you have these primitives, the two backends fork: continue with
[Ganesh Backend](ganesh-backend.md) for the legacy retained-mode
op-list architecture, or [Graphite Backend](graphite-backend.md) for
the new recorder-and-task model.
