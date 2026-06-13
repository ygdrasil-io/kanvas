# GPU Renderer Architecture Kernel

Status: Draft
Date: 2026-06-13

## Purpose

Define the small architecture kernel that every future GPU renderer spec and
implementation ticket must preserve. This is a target contract, not an
implementation patch.

The direction is Graphite-inspired but inline: Kanvas borrows Graphite's
separation of recording, task preparation, draw passes, render steps, material
keys, and pipeline keys. Kanvas does not port Graphite code, Ganesh code, SkSL
IR, SkSL compilation, or backend abstraction layers.

## Module Boundary

The new renderer should live in a dedicated module. The final Gradle name is
not accepted by this kernel, but candidate names must communicate that the
module owns the Kanvas GPU renderer and is built on the `GPU` facade used with
`wgpu4k`.

The module owns:

- normalized draw intake;
- GPU recording and immutable recordings;
- task lists and draw passes;
- render-step selection;
- material and pipeline keys;
- WGSL module assembly requests;
- resource-provider contracts;
- route selection and diagnostics.

The module must not own:

- Skia-like public API compatibility;
- `SkCanvas` state stack interpretation;
- font shaping;
- codec loading;
- arbitrary SkSL parsing or compilation;
- native windowing and event loops;
- broad CPU raster rendering.

## Naming Policy

Public concept names in the new renderer use uppercase acronyms:

- `GPURecorder`
- `GPURecording`
- `GPUTaskList`
- `GPUDrawPass`
- `GPURenderStep`
- `GPUResourceProvider`
- `GPUCapabilities`
- `GPUNative`
- `CPUPreparedGPU`
- `CPUReferenceOnly`
- `WGSLFragment`
- `WGSLModule`

This intentionally matches the `GPU` vocabulary of the facade used with
`wgpu4k`. Kotlin import aliases are acceptable when names collide with lower
level facade types.

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

## Graphite-Inspired Mapping

| Graphite idea | Kanvas target concept | Constraint |
|---|---|---|
| `Recorder` | `GPURecorder` | Records normalized commands, not `SkCanvas` operations. |
| `Recording` | `GPURecording` | Immutable product of recording; reusable only under explicit resource rules. |
| `TaskList` | `GPUTaskList` | Prepares resources and emits commands in dependency order. |
| `DrawPass` | `GPUDrawPass` | Immutable pass close to what the GPU facade will execute. |
| `Renderer` / `RenderStep` | `GPURenderStep` | Geometry/coverage technique with fixed shader and state contribution. |
| `PaintParamsKey` | `MaterialKey` | Paint/material identity; no SkSL. |
| `GraphicsPipelineDesc` | `PipelineKey` | Render step, material, target state, fixed state, and capabilities. |
| `ResourceProvider` | `GPUResourceProvider` | Pipelines, buffers, textures, samplers, atlases, and cache ownership. |

The mapping is conceptual. Kanvas is not required to preserve Graphite class
names, inheritance, virtual dispatch shape, backend plugin model, or task
implementation.

## Data Flow

```text
legacy stateful API
  -> adapter captures transform/clip/layer/material/bounds
  -> NormalizedDrawCommand
  -> GPURecorder
  -> GPURecording
  -> GPUTaskList
  -> GPUDrawPass
  -> GPURenderStep + MaterialKey
  -> PipelineKey
  -> GPUResourceProvider
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
geometry buffer, or uniform payload. It must not become silent full CPU
rendering.

## WGSL-Only Shader Implementation

WGSL is the only shader implementation target for the new renderer.

Graphite's SkSL paint-key machinery maps to Kanvas `MaterialKey`,
`PipelineKey`, `WGSLFragment`, and `WGSLModule` concepts. SkSL may appear only
as compatibility vocabulary around Skia-facing APIs. It must not appear as a
runtime shader language for new GPU renderer implementation.

## `KanvasPipelineIR` Position

`KanvasPipelineIR` remains relevant historical and compatibility context. It
does not become the durable semantic center of the new GPU renderer.

New specs and tickets may reuse proven `KanvasPipelineIR` facts when they are
useful, but the core contract is `NormalizedDrawCommand` plus
`MaterialKey`/`PipelineKey`, not `KanvasPipelineIR` execution.

## Non-Goals

- No Ganesh port.
- No Graphite port.
- No arbitrary SkSL compiler.
- No broad CPU fallback.
- No new browser-only assumption.
- No hidden workaround for `wgsl4k` parser or reflection behavior.
- No render behavior change during cleanup-only phases.

## Acceptance Rules

The architecture kernel can be treated as accepted only when:

- the target direction is approved by project owners;
- the module boundary is referenced by implementation tickets;
- cleanup tickets prove no render changes;
- the first promoted route reports `GPUNative`, `CPUPreparedGPU`, or
  `RefuseDiagnostic` deterministically;
- the old `KanvasPipelineIR` center is not silently reintroduced through
  adapter code.
