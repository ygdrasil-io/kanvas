# Spec 04: GPU Generated WGSL Backend

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

## Purpose

Define how the WebGPU backend consumes `KanvasPipelineIR` and moves from
handwritten WGSL combinations to parser-validated generated WGSL modules.

The GPU backend specializes the shared semantic IR. It does not define paint
semantics by itself.

## Ownership

Current owner:

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/`

Important current surfaces:

- `SkWebGpuDevice`;
- `GeneratedSolidRectWgsl`;
- `GeneratedLinearGradientWgsl`;
- `WgslValidationReport`;
- `PipelineKeyClassification`;
- `GpuCacheTelemetrySnapshot`;
- `BlendPlan`.

## GPU Plan Flow

```mermaid
flowchart TD
    ir["KanvasPipelineIR"] --> normalize["GPU normalization"]
    normalize --> key["PipelineKey axes"]
    key --> module["WGSL module descriptor"]
    module --> validate["Parser validation"]
    validate --> reflect["Reflection"]
    reflect --> packer["Uniform packer"]
    reflect --> cache["Shader/pipeline/resource caches"]
    cache --> draw["Draw packet"]
    draw --> webgpu["WebGPU submit"]
```

Generated WGSL modules must be selected through a stable pipeline plan and key,
not by ad hoc draw-path branching.

## Supported Initial Families

Initial generated families:

- rect + solid color + `SrcOver`;
- rect + linear gradient + optional color-filter payload + `SrcOver`;
- runtime-effect descriptor pilot when a WGSL implementation id is registered.

Existing handwritten shader paths may remain as compatibility routes while
generated paths are validated.

## PipelineKey

`PipelineKey` axes are allowed only when they materially affect one of:

- bind-group layout, texture/sampler presence, uniform struct shape, vertex
  input shape, or attachment layout;
- generated WGSL code shape, helper set, entry-point structure, static branch
  removal, or shader graph shape;
- WebGPU render-pipeline state, such as blend state, format class, topology,
  multisampling, depth, or stencil.

Uniform-only values must not enter the key unless an accepted benchmark proves
that specialization is worth the cache pressure.

Axis classes:

| Axis class | Meaning |
|---|---|
| `Layout` | Changes bindings, uniform schema, texture/sampler set, vertex input, or attachments. |
| `Code` | Changes generated helper set, entry point, static branches, or shader graph shape. |
| `PipelineState` | Changes WebGPU fixed-function render-pipeline state. |
| `UniformOnly` | Changes only values consumed by already-generated code. |

`UniformOnly` axes are diagnostic facts, not cache-key axes for generated
pipelines.

Pipeline key serialization must be deterministic and independent of unordered
map/set traversal.

## Generated WGSL

Generated modules must:

- be deterministic;
- include only required helpers;
- parse successfully before shader module creation;
- expose reflected layout for packer verification;
- have golden source tests for promoted families;
- avoid one-module-per-uniform-value cache explosions.

Handwritten source templates may be used during transition, but promoted
families should move toward parser-aware assembly or WGSL IR construction.

## Blend And Composition

Blend decisions must go through `BlendPlan`:

- fixed-function WebGPU blending for allowlisted modes;
- shader/layer composite plan when destination color is required;
- explicit refusal when unsupported.

Generated WGSL must not silently approximate unsupported blend modes.

## Cache Policy

Required cache classes:

- shader module source/reflection registry;
- compiled WebGPU render pipeline cache;
- resource cache for textures, samplers, staging buffers, and reusable backend
  resources.

Telemetry must expose:

- shader module hits/misses;
- pipeline hits/misses;
- resource hits/misses;
- pipeline creations;
- resident entry counts.

Unbounded input domains require a budget or an explicit no-eviction
justification with finite key count evidence.

## Resource Lifecycle

GPU resources must document ownership and reset behavior:

- whether a handle survives device reset;
- which cache owns it;
- when temporary textures are closed;
- whether re-upload or recreation is automatic or explicit;
- what diagnostic is emitted on invalid use.

Generated pipelines must not hide resource leaks behind compatibility paths.

## GPU Gates

Promoted generated families require:

- CPU-vs-GPU visual comparison for selected fixtures;
- parser validation for generated modules;
- reflected uniform layout evidence;
- deterministic `PipelineKey` dump;
- no unexpected fallback reasons;
- zero pipeline creation in stable repeated frames after warmup for the demo
  scene, or an explicit accepted exception;
- bounded shader module count for the scene.

## Non-Goals

- Do not port Graphite or Ganesh.
- Do not create a general shader language compiler.
- Do not add uniform values to `PipelineKey` by default.
- Do not retire handwritten compatibility before evidence exists.
- Do not silently fall back to a visually weaker path.

## Acceptance Criteria

- Every generated family has generated-source golden tests.
- Every touched WGSL module parses.
- Every reflected layout has packer evidence.
- `PipelineKey` axes are classified before use.
- Cache telemetry is captured in tests or PM evidence.
- Handwritten fallback retirement follows `07-validation-performance-and-migration.md`.
