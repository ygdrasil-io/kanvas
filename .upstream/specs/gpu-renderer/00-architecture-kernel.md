# GPU Renderer Architecture Kernel

Status: Draft
Date: 2026-06-13

## Purpose

Define the small architecture kernel that every future GPU renderer spec and
implementation ticket must preserve. This is a target contract, not an
implementation patch.

The kernel documents the full technical scope first. Implementation slices are
planned after the specs are coherent and must not narrow the architectural
contract to their initial feature subset.

The direction is Graphite-inspired but inline: Kanvas borrows Graphite's
separation of recording, task preparation, draw passes, render steps, material
keys, and pipeline keys. Kanvas does not port Graphite code, Ganesh code, SkSL
IR, SkSL compilation, or backend abstraction layers.

## Module Boundary

The new renderer lives in `:gpu-renderer`. The module owns the Kanvas GPU
renderer core and is built on the `GPU` facade used with `wgpu4k`.

Core contracts live in `:gpu-renderer`. Legacy adapters may call into those
contracts, but they must not define the core command, analysis, pass, layer,
resource, material, pipeline, or diagnostic shapes outside this module.

The module owns:

- `NormalizedDrawCommand` contracts and normalized draw intake;
- GPU recording and immutable recordings;
- explicit draw analysis;
- task lists and draw passes;
- draw-layer planning;
- occlusion tracking;
- render-step selection;
- sort-key generation;
- material and pipeline keys;
- WGSL module assembly requests;
- WGSL layout and binding ABI contracts;
- blend, color, and target-state planning;
- resource-provider contracts;
- GPU execution context and submission contracts;
- telemetry, cache, and performance-gate contracts;
- route selection and diagnostics.

The module must not own:

- Skia-like public API compatibility;
- `SkCanvas` state stack interpretation;
- font shaping;
- codec loading;
- arbitrary SkSL parsing or compilation;
- native windowing and event loops;
- broad CPU raster rendering.

## Package Policy

Packages in `:gpu-renderer` use Kanvas responsibility names, not
Graphite-mirrored source names. All implementation packages must use
`org.graphiks.kanvas` as the base package. The expected renderer module root is
`org.graphiks.kanvas.gpu.renderer`, with subpackages named for Kanvas
responsibilities:

- `commands`
- `recording`
- `analysis`
- `passes`
- `layers`
- `filters`
- `materials`
- `pipelines`
- `resources`
- `execution`
- `state`
- `routing`
- `diagnostics`
- `telemetry`
- `wgsl`

Specs should document Graphite equivalents in an equivalence table for
orientation, but implementation packages must not mirror `skgpu::graphite`,
Graphite file names, or Graphite class ownership as a package taxonomy.

Exact class placement can evolve with implementation evidence, but new package
roots outside `org.graphiks.kanvas.gpu.renderer` are not allowed for renderer
core contracts without an explicit spec change.

## Naming Policy

Public concept names in the new renderer use uppercase acronyms:

- `GPURecorder`
- `GPURecording`
- `GPUTaskList`
- `GPUDrawAnalysis`
- `GPUOcclusionTracker`
- `GPULayerPlan`
- `GPUFilterPlan`
- `GPUDrawLayer`
- `GPUDrawLayerPlanner`
- `GPUDrawPass`
- `GPURenderStep`
- `GPUResourceProvider`
- `GPUCapabilities`
- `GPUExecutionContext`
- `GPUSharedScope`
- `GPURecorderScope`
- `GPUFrameScope`
- `GPUAtlasScope`
- `GPUCommandSubmission`
- `GPUSurfaceTarget`
- `GPUReadbackRequest`
- `WGSLBindingLayout`
- `WGSLUniformLayout`
- `WGSLPackingPlan`
- `GPUBlendPlan`
- `GPUColorPlan`
- `GPUTargetState`
- `GPUTelemetryLedger`
- `GPUPerformanceGate`
- `GPUNative`
- `CPUPreparedGPU`
- `CPUReferenceOnly`
- `WGSLFragment`
- `WGSLModule`

This intentionally matches the `GPU` vocabulary of the facade used with
`wgpu4k`. Kotlin import aliases are acceptable when names collide with lower
level facade types.

Concepts without an acronym keep standard PascalCase, including `SortKey`.

## Core Purity

The core renderer must not depend directly on Skia-like types such as
`SkPaint`, `SkShader`, `SkPath`, `SkCanvas`, or `SkRuntimeEffect`.

Compatibility adapters may depend on those types. They are responsible for
turning stateful legacy calls into normalized commands before calling the new
core.

The core may depend on backend-neutral Kanvas value types when those types are
stable and do not pull in Skia-like API ownership. If an existing type carries
legacy semantics that are too broad or ambiguous, the adapter must translate it
into a narrower GPU renderer value object.

## Graphite Equivalence Table

| Graphite idea | Kanvas target concept | Constraint |
|---|---|---|
| `Recorder` | `GPURecorder` | Records normalized commands, not `SkCanvas` operations. |
| `Recording` | `GPURecording` | Immutable product of recording; reusable only under explicit resource rules. |
| `TaskList` | `GPUTaskList` | Prepares resources and emits commands in dependency order. |
| Draw-list analysis and ordering | `GPUDrawAnalysis` | Explicit analysis product; not hidden in pass construction. |
| `SortKey` | `SortKey` | Deterministic Kanvas value for legal draw ordering; no Graphite bit-layout requirement. |
| Occlusion culling | `GPUOcclusionTracker` | Dedicated conservative culling capability; not an incidental pass-builder side effect. |
| SaveLayer and layer semantics | `GPULayerPlan` | Captured layer/saveLayer semantics, offscreen target needs, restore/composite behavior, and attached filters. |
| Image filter graph planning | `GPUFilterPlan` | Filter DAG, intermediate resources, render/compute routes, and filter refusals outside `MaterialKey`. |
| Layer/draw-context planning | `GPUDrawLayer` / `GPUDrawLayerPlanner` | Logical layer and composite scopes from captured state; not Graphite context classes. |
| `DrawPass` | `GPUDrawPass` | Immutable pass close to what the GPU facade will execute. |
| `Renderer` / `RenderStep` | `GPURenderStep` | Geometry/coverage technique with fixed shader and state contribution. |
| `PaintParamsKey` | `MaterialKey` | Paint/material identity; no SkSL. |
| `GraphicsPipelineDesc` | `GPURenderPipelineKey` | Render step, material, target state, fixed state, and capabilities. |
| `ResourceProvider` | `GPUResourceProvider` | Pipelines, buffers, textures, samplers, atlases, and cache ownership. |
| `SharedContext` / `Caps` | `GPUExecutionContext` / `GPUCapabilities` | Facade implementation, device generation, queue facts, and capability snapshot. |
| `CommandBuffer` / `QueueManager` | `GPUCommandSubmission` | Encoded command scopes, submission result, readback, and device-loss diagnostics. |
| `RenderPassDesc` | `GPUTargetState` | Attachment format, load/store, sample count, write state, and target assumptions. |
| `Uniform` / pipeline data gathering | `WGSLUniformLayout` / `WGSLPackingPlan` | WGSL reflection-backed ABI and Kotlin packing; no SkSL type ownership. |
| `GlobalCache` / recorder-local resources | `GPUSharedScope` / `GPURecorderScope` | Conceptual scope split for cache and transient resource lifetimes. |

The mapping is conceptual. Kanvas is not required to preserve Graphite class
names, inheritance, virtual dispatch shape, backend plugin model, or task
implementation.

## Data Flow

```text
legacy stateful API
  -> adapter captures transform/clip/layer/material/bounds
  -> NormalizedDrawCommand
  -> GPULayerPlan / GPUFilterPlan
  -> GPURecorder
  -> GPUDrawAnalysis
  -> GPUOcclusionTracker + GPUDrawLayerPlanner
  -> GPURecording
  -> GPUTaskList
  -> GPUDrawPass
  -> GPURenderStep + MaterialKey
  -> GPUBlendPlan + GPUColorPlan + GPUTargetState
  -> WGSLBindingLayout + WGSLPackingPlan
  -> GPURenderPipelineKey
  -> GPUResourceProvider
  -> GPUExecutionContext + GPUCommandSubmission
  -> GPU facade command submission
```

The new core receives fully captured draw state. It does not replay save,
restore, clip, or matrix operations.

## GPU-First Route Order

The renderer prefers route selection in this order:

1. `GPUNative`
2. `CPUPreparedGPU`
3. `RefuseDiagnostic`

`CPUReferenceOnly` is not a production route. It exists to produce oracles,
diffs, diagnostics, and conformance evidence.

`CPUPreparedGPU` is allowed only when CPU work prepares an artifact that the
GPU consumes, such as a coverage mask, path atlas entry, uploaded texture,
geometry buffer, or another registered typed artifact. It must not become
silent full CPU rendering.

## WGSL-Only Shader Implementation

WGSL is the only shader implementation target for the new renderer.

Graphite's SkSL paint-key machinery maps to Kanvas `MaterialKey`,
`GPURenderPipelineKey`, `WGSLFragment`, and `WGSLModule` concepts. Compute work
uses `GPUComputeProgramKey`, `WGSLComputeModule`, and `GPUComputePipelineKey`
instead of `MaterialKey`. SkSL may appear only as compatibility vocabulary
around Skia-facing APIs. It must not appear as a runtime shader language for
new GPU renderer implementation.

Before GPU submission, the complete assembled WGSL module for a route must be
validated and reflected through `wgsl4k`. Validating individual fragments is
useful evidence, but it is not enough to claim that a GPU-submitted shader is
supported.

## `KanvasPipelineIR` Position

`KanvasPipelineIR` remains relevant historical and compatibility context. It
does not become the durable semantic center of the new GPU renderer.

New specs and tickets may reuse proven `KanvasPipelineIR` facts when they are
useful, but the core contract is `NormalizedDrawCommand` plus
`MaterialKey` and render/compute pipeline-key families, not
`KanvasPipelineIR` execution.

## Implementation Slicing Policy

This kernel does not choose the first implementation slice. Implementation
plans come later and must cite the full target contracts instead of narrowing
the renderer architecture to an initial feature subset.

Future slices must keep `:gpu-renderer` contract tests isolated from
`gpu-raster` integration until the touched contracts have deterministic dumps,
key preimages, route diagnostics, resource-planning behavior, and WGSL
validation evidence. Integration must not silently change the default legacy
route or pixels; route activation needs explicit evidence.

## Non-Goals

- No Ganesh port.
- No Graphite port.
- No SkSL implementation, including arbitrary SkSL compiler behavior.
- No broad CPU fallback.
- No new browser-only assumption.
- No hidden workaround for `wgsl4k` parser or reflection behavior.
- No render behavior change during cleanup-only phases.

## Acceptance Rules

The architecture kernel can be treated as accepted only when:

- the target direction is approved by project owners;
- the module boundary is referenced by implementation tickets;
- cleanup tickets prove no render changes;
- isolated `:gpu-renderer` tests pass before `gpu-raster` integration;
- complete GPU-submitted WGSL modules validate through `wgsl4k`;
- WGSL binding layouts, reflection, and Kotlin packing plans match;
- blend/color/target-state plans are explicit for promoted routes;
- execution-context and device-generation assumptions are tested;
- telemetry distinguishes correctness support from performance readiness;
- the first promoted route reports `GPUNative`, `CPUPreparedGPU`, or
  `RefuseDiagnostic` deterministically;
- the old `KanvasPipelineIR` center is not silently reintroduced through
  adapter code.
