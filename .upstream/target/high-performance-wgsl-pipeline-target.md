# Target: high-performance WGSL pipeline architecture

Date: 2026-05-26

This document describes the intended end state for Kanvas after the proof
of concept phase. It is a target architecture, not an epic breakdown. The
follow-up planning work should decompose this target into measurable epics
and milestones.

## Context

Kanvas has working CPU raster coverage, a WebGPU backend, handwritten WGSL
shader resources, and a compatibility facade for runtime effects. A WGSL
parser exists at `/Volumes/Cache/webgpu-ktypes/wgsl` and is expected to be
integrated soon.

The target keeps the current upstream-sync decisions:

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `SkRuntimeEffect` as a compatibility facade.
- Use registered Kotlin/WGSL implementations for runtime effects.
- Use the WGSL parser to reduce handwritten boilerplate and improve
  correctness, not to recreate SkSL.

The long-term goal is to move from backend-specific proof-of-concept paths
to a shared, high-performance rendering architecture inspired by Skia's CPU
pipeline model.

## Design Thesis

Skia's most relevant lesson for Kanvas is not the exact implementation of
`SkRasterPipeline`. The lesson is the separation of responsibilities:

1. Geometry produces coverage.
2. Paint objects lower into an ordered color pipeline.
3. Shaders, color filters, blenders, color-space transforms, coverage, and
   stores are composed consistently.
4. The backend is free to specialize the final pipeline aggressively.

Kanvas should adopt that model with a smaller, typed IR that can target both
CPU raster and WebGPU. The IR should be close enough to Skia's mental model
that upstream behavior maps naturally, but not so low-level that Kanvas must
inherit every Skia internal stage and legacy format path.

The target is:

```text
SkCanvas draw*
  -> SkDevice operation
  -> geometry extraction / rasterization
  -> paint lowering to KanvasPipelineIR
  -> backend specialization
       CPU: scalar or Java 25 Vector API kernels
       GPU: parser-validated/generated WGSL modules
  -> blend/store/present
```

## Target Properties

### Behavioral Fidelity

The CPU backend remains the primary reference for Skia-like behavior. GPU
results are validated against CPU and upstream GM references with explicit
similarity thresholds.

The pipeline must preserve:

- Skia-style local-matrix shader behavior.
- Paint color modulation.
- Color filters after shader evaluation.
- Blend-mode semantics.
- Coverage and clip interaction.
- Destination color-space conventions already established by current tests.
- Runtime-effect compatibility for registered effects.

### High Performance

The IR is descriptive. It is not executed as a naive per-pixel interpreter
in hot paths.

The backend flow is:

```text
PipelineIR
  -> normalization
  -> specialization key
  -> fused execution plan
  -> scalar/vector CPU kernel or WGSL pipeline
```

The CPU backend should specialize common pipelines such as:

- solid color + coverage + `SrcOver`;
- linear/radial/sweep gradient + coverage + blend;
- bitmap nearest/bilinear sampling;
- color matrix / blend color filter;
- common premul, unpremul, clamp, pack, and store paths.

The GPU backend should specialize by render-pipeline key and use WGSL
modules whose layouts are parser-reflected rather than manually duplicated
in Kotlin.

### Incremental Adoption

Existing `SkShader.setupForDraw()` / `shadeRow()` behavior remains valid
during migration. A shader can participate in the new architecture by
implementing append/lowering behavior. Shaders that are not ported yet
continue through compatibility stages.

This avoids a big-bang rewrite:

```text
legacy shader
  -> ShadeRowFallbackOp

ported shader
  -> appendStages(...)
  -> native pipeline stages
```

## Core Architecture

### Shared Pipeline IR

Add a backend-neutral pipeline module that owns the common lowering model:

```text
:render-pipeline
  PipelineIR
  PipelineOp
  PipelineStageRec
  MatrixRec
  PipelineKey
  UniformLayout
  ColorSpaceBlock
  CoverageModel
  PipelineNormalizer
```

This module should not depend on WebGPU or Java Vector API. It is the
contract between `kanvas-skia`, `cpu-raster`, and `gpu-raster`.

Representative API shape:

```kotlin
interface PipelineAppendable {
    fun appendStages(rec: PipelineStageRec, matrix: MatrixRec): Boolean
}

class PipelineStageRec(
    val pipeline: PipelineBuilder,
    val dstColorType: SkColorType,
    val dstColorSpace: SkColorSpace?,
    val paintColor: SkColor4f,
    val devBounds: SkRect,
)

sealed interface PipelineOp {
    data object SeedDeviceCoords : PipelineOp
    data class Transform2D(val matrix: SkMatrix) : PipelineOp
    data class ConstantColor(val color: SkColor4f) : PipelineOp
    data class LinearGradient(val payload: LinearGradientPayload) : PipelineOp
    data class RadialGradient(val payload: RadialGradientPayload) : PipelineOp
    data class SweepGradient(val payload: SweepGradientPayload) : PipelineOp
    data class BitmapSample(val payload: BitmapSamplePayload) : PipelineOp
    data class RuntimeEffect(val payload: RuntimeEffectPayload) : PipelineOp
    data class ColorSpaceXform(val payload: ColorSpacePayload) : PipelineOp
    data class ColorFilter(val payload: ColorFilterPayload) : PipelineOp
    data class BlendMode(val mode: SkBlendMode) : PipelineOp
    data class ApplyCoverage(val coverage: CoverageModel) : PipelineOp
    data object LoadDst : PipelineOp
    data object Store : PipelineOp
}
```

The actual op set should grow from measured needs, not from an attempt to
mirror every Skia internal opcode.

### Matrix Model

Adopt a Skia-like `MatrixRec`.

Responsibilities:

- Carry CTM and pending local matrices through shader wrappers.
- Apply inverse transforms at the point a shader needs local coordinates.
- Fold local-matrix wrappers instead of stacking per-pixel work.
- Surface singular matrix behavior explicitly.

This is important because it lets `SkLocalMatrixShader`,
`SkWorkingColorSpaceShader`, image shaders, gradients, and runtime effects
share coordinate handling instead of each reimplementing it.

### Paint Lowering

Paint lowering should produce a single logical pipeline:

```text
seed coords
-> shader stages or constant paint color
-> paint alpha modulation
-> color filter stages
-> color-space / working-space stages
-> load destination
-> blender stages
-> coverage / clip modulation
-> store
```

The ordering must be explicit and tested. Wrapper shaders such as local
matrix, color-filter shader, working-color-space shader, coord-clamp shader,
and blend shader become compositional appenders instead of backend-specific
special cases.

### Geometry And Coverage

Geometry remains separate from paint.

CPU:

- Rects, paths, glyph masks, vertices, and masks produce spans, masks, or
  typed coverage packets.
- Coverage is passed into the pipeline runner as span-local input.

GPU:

- Simple rect/rrect coverage can remain analytic in fragment WGSL.
- Complex paths can use existing stencil/cover strategy.
- The color pipeline should be shared between rect and cover paths where
  possible through generated WGSL helpers.

Coverage should be represented in the IR as an input source, not buried in
each shader implementation.

## WGSL Parser Role

The parser should become a build/runtime tooling layer for WGSL correctness
and layout generation.

### Build-Time Validation

Every WGSL resource in `gpu-raster/src/main/resources/shaders/` should be
parsed in CI. The build should fail on syntax or semantic errors that the
parser can catch.

The validation corpus should grow from:

- existing GPU shader resources;
- generated WGSL modules;
- small shader fragments used by the pipeline generator;
- upstream-derived runtime-effect fixtures that Kanvas explicitly supports.

### Reflection

Use parser output to reflect:

- entry points and stages;
- `@group` / `@binding`;
- uniform structs;
- member offsets, alignment, and size;
- texture and sampler declarations;
- vertex inputs and fragment outputs.

The goal is to remove manual duplication of WGSL layout in Kotlin packers.
Uniform packing should be generated or verified from reflected layout.

### WGSL Assembly

Use parser-aware assembly for reusable fragments:

```text
common/coords.wgsl
common/coverage.wgsl
common/blend.wgsl
common/colorspace.wgsl
common/color_filter.wgsl
shader/linear_gradient.wgsl
shader/bitmap_sample.wgsl
```

The assembly step should produce final WGSL modules with:

- one vertex entry point;
- one fragment entry point;
- only required helpers;
- deterministic binding layout;
- deterministic source output for golden tests.

This is not a general shader language compiler. It is a controlled module
builder for Kanvas pipeline fragments.

### WGSL IR Module Builder

If the Kotlin WGSL parser exposes a manipulable internal IR and a generator
from that IR back to WGSL source, Kanvas should use it as the GPU shader
module construction layer.

The intended layering is:

```text
KanvasPipelineIR
  -> GPU pipeline selection
  -> WGSL IR module builder
  -> deterministic WGSL source
  -> parser validation / reflection
  -> WebGPU shader module
```

The WGSL IR should not replace `KanvasPipelineIR`. They serve different
purposes:

- `KanvasPipelineIR` describes Skia-like rendering semantics: shader,
  color filter, blender, color space, matrix behavior, coverage, and store.
- WGSL IR describes a concrete GPU program: structs, bindings, functions,
  expressions, entry points, texture/sampler declarations, and helper calls.

This separation keeps the CPU backend free to compile the same
`KanvasPipelineIR` into scalar or Java Vector API kernels instead of forcing
CPU execution through a GPU-shader-shaped model.

Primary uses for WGSL IR construction:

- Generate final WGSL modules from validated helper fragments.
- Generate rect, polygon, stencil-cover, image, and layer-composite variants
  from shared shader/color-filter/blend/color-space building blocks.
- Generate `struct Uniforms` declarations and matching binding layouts.
- Generate deterministic source for golden tests and debugging.
- Validate registered runtime-effect WGSL implementations.
- Remove copy-pasted helper code from handwritten WGSL files over time.

The builder should operate at pipeline creation time or build time, not per
draw. A draw should select a cached generated module through a `PipelineKey`,
pack uniforms, bind resources, and submit.

Guardrails:

- Do not accept arbitrary user WGSL as an implicit renderer extension.
- Do not build a CPU interpreter for arbitrary WGSL.
- Do not generate a unique shader for every uniform value.
- Do not let WGSL IR become the source of truth for Skia-like paint
  semantics.
- Keep generated WGSL deterministic so diffs and golden tests remain stable.

The first useful generated module should be deliberately small, for example:

```text
Rect + SolidColor + optional ColorMatrix + SrcOver
```

or:

```text
Rect + LinearGradient + optional ColorMatrix + SrcOver
```

That proves helper assembly, uniform reflection, source generation, WebGPU
pipeline creation, and cross-backend comparison without forcing a broad
rewrite of existing handwritten shaders.

### Runtime Effects

`SkRuntimeEffect` remains source-compatible with supported call sites.

Target behavior:

```text
SkRuntimeEffect.MakeForShader(source)
  -> canonical lookup
  -> registered effect descriptor
       CPU: Kotlin kernel implementation
       GPU: WGSL module or fragment descriptor
       reflection: uniforms and children
```

The parser can validate registered WGSL implementations and provide layout
reflection. It should not attempt to compile arbitrary SkSL.

If a runtime effect is not registered, failure should remain explicit and
diagnostic. Silent fallback is not acceptable.

## CPU Backend

### Execution Model

The CPU backend owns:

```text
:cpu-raster
  CpuPipelineCompiler
  CpuPipelinePlan
  CpuPipelineExecutor
  ScalarKernels
  VectorKernels
  PixelLoadStore
  SpanRunner
```

The CPU path should compile a `PipelineIR` into a `CpuPipelinePlan`:

```kotlin
interface CpuPipelineExecutor {
    fun runSpan(plan: CpuPipelinePlan, span: SpanInput, dst: PixelBuffer)
}

sealed interface CpuKernel {
    fun run(ctx: CpuPipelineContext, x: Int, y: Int, count: Int)
}
```

The plan can contain fused kernels rather than one kernel per IR op.

Examples:

```text
SolidSrcOver8888Kernel
SolidSrcOverF16Kernel
LinearGradientSrcOverF16Kernel
BitmapBilinearSrcOver8888Kernel
ColorMatrixThenSrcOverKernel
```

### Java 25 SIMD

Use Java 25's `jdk.incubator.vector` API behind an isolated JVM-specific
implementation. The rest of the project should not directly depend on
Vector API classes.

Rules:

- Scalar kernels are always available.
- Vector kernels are selected only when the module is present and a benchmark
  proves benefit.
- Vector code uses preferred species and scalar tails.
- Vector kernels operate on packed arrays or temporary planar lanes, not on
  object-heavy pixel abstractions.
- Vector support is a performance feature, not a correctness dependency.

The likely first vector targets are:

- solid color coverage modulation;
- `SrcOver` on float lanes;
- gradient `t` computation and color interpolation;
- color matrix;
- premul/unpremul;
- clamp and pack/unpack.

Avoid early vectorization of:

- branch-heavy path coverage;
- arbitrary runtime effects;
- scattered bitmap sampling with complex tile modes.

### Memory Model

Hot CPU paths should avoid per-pixel allocation. Preferred representations:

- primitive arrays;
- reusable span buffers;
- packed handles;
- frame-local arenas where useful;
- explicit temporary buffers for RGBA float lanes.

Kotlin data classes are acceptable for descriptors and immutable plans, not
for per-pixel loops.

## GPU Backend

### Execution Model

The GPU backend owns:

```text
:gpu-raster
  WebGpuPipelineCompiler
  WgslModuleRegistry
  WgslPipelineCache
  ReflectedUniformPacker
  DrawPacketEncoder
  WebGpuResourceCache
```

The GPU path lowers `PipelineIR` into a `GpuPipelinePlan`:

```text
PipelineIR
  -> PipelineKey
  -> WGSL module descriptor
  -> reflected bind-group layout
  -> uniform packer
  -> cached WebGPU render pipeline
  -> draw packet
```

Pipeline keys should encode only specialization axes that materially affect
compiled WGSL or WebGPU pipeline state:

- geometry path: rect, polygon, stencil-cover, image blit, layer composite;
- shader family;
- blend mode when implemented by WebGPU blend state;
- destination/intermediate format class;
- sampling mode when it changes shader or bind layout;
- fill/coverage mode when it changes pipeline state.

Tile modes, matrices, colors, gradient stops, color-space transforms, and
most clip data should remain uniforms unless specialization is proven faster.

### WGSL Generation

The end state is not one handwritten WGSL file per shader/path combination.
The end state is deterministic WGSL generation from reusable, validated
fragments.

Example:

```text
Rect + LinearGradient + ColorMatrix + SrcOver
  -> fullscreen-triangle vertex fragment
  -> analytic rect coverage helper
  -> linear gradient helper
  -> color matrix helper
  -> blend helper or WebGPU blend-state selection
```

Existing handwritten shaders can be treated as seed implementations. They
should be migrated only when the generated path has tests and equivalent
or better performance.

### Resource Lifetime

WebGPU resource ownership remains explicit:

- textures, buffers, bind groups, samplers, and pipelines are backend-owned;
- high-level paint/shader objects hold logical descriptors or handles;
- uploads happen through render/backend queues;
- cross-device resource sharing remains illegal unless explicitly supported.

## Color Management

The pipeline should preserve the current working-space behavior while making
the stages explicit.

Target model:

- source colors are represented as unpremul floats at pipeline boundaries;
- shader output convention is explicit per backend path;
- premul happens before blend when required;
- destination encoding is handled by explicit color-space stages;
- GPU intermediate conventions remain documented and tested.

Color-space transforms should be represented as payloads that both CPU and
WGSL can implement from the same descriptor.

HDR transfer functions can remain out of scope until a specific GM/API delta
requires them.

## Testing And Validation

The target architecture requires test layers that measure correctness and
performance separately.

### Correctness

- Golden screenshot tests for representative GMs.
- CPU old-path vs CPU pipeline equivalence tests during migration.
- CPU pipeline vs GPU pipeline cross-backend tests.
- WGSL parser validation tests for all resources and generated modules.
- Uniform layout reflection tests: reflected offsets vs packer output.
- Runtime-effect registry tests: missing effects fail with stable diagnostics.

### Performance

Benchmarks should track:

- empty draw overhead;
- solid rect spans;
- AA rect/rrect;
- gradient fill;
- bitmap shader nearest and bilinear;
- common color filters;
- saveLayer composite;
- path cover with shaded paint;
- allocations per frame;
- CPU scalar vs vector speedup;
- GPU pipeline cache hit rate;
- GPU uniform upload bytes and draw count.

Performance gates should compare to current baseline before each major
replacement, not to theoretical upstream Skia numbers.

### Diagnostics

Add developer-facing dumps:

- pipeline IR dump;
- normalized/fused CPU plan dump;
- generated WGSL dump;
- reflected uniform layout dump;
- pipeline key dump;
- backend fallback reason.

This is essential because the architecture introduces an optimization layer
between public paint objects and backend execution.

## Non-Goals

This target explicitly does not include:

- Ganesh or Graphite ports;
- arbitrary SkSL compilation;
- arbitrary WGSL CPU interpretation;
- a full clone of Skia's internal `SkRasterPipeline` op inventory;
- one universal shader that handles every paint combination;
- silent fallback for unsupported runtime effects.

## Success Criteria

The architecture target is reached when:

- New shader/color-filter/blender behavior is added by implementing shared
  lowering first, not by duplicating CPU and GPU code paths independently.
- The CPU backend can execute common pipelines through fused scalar/vector
  plans.
- The GPU backend can build parser-validated WGSL modules from reusable
  fragments and reflected layouts.
- Existing handwritten WGSL remains supported but is no longer the only path.
- Runtime effects have a single registry entry that describes CPU and GPU
  implementations plus uniform/child layout.
- Cross-backend tests prove parity for the selected GM set.
- Performance benchmarks show that the pipeline layer does not regress common
  CPU raster paths and improves at least one vectorized shader/composite path.

## Planning Notes For Epic Breakdown

The next document should not turn this target into one large migration. It
should split the work by independently verifiable capabilities:

- IR skeleton and dumps.
- CPU solid/gradient pilot.
- WGSL parser validation integration.
- WGSL IR module builder pilot.
- Uniform reflection and packer generation.
- GPU generated-shader pilot.
- Runtime-effect descriptor unification.
- Java 25 vector kernels.
- Legacy path retirement criteria.

Each epic should name the tests, benchmarks, affected GMs, and fallback
behavior it owns.
