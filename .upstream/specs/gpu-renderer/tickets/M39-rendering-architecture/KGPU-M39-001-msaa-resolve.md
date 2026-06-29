---
id: KGPU-M39-001
title: "MSAA resolve"
status: done
milestone: M39
priority: P0
owner_area: state
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M39-001 - MSAA resolve

## PM Note

Le MSAA (multisample anti-aliasing) améliore la qualité des bords en
échantillonnant plusieurs positions par pixel et en résolvant vers une
texture single-sample, évitant le surcoût du supersampling complet.

## Problem

The GPU renderer currently has no multisample anti-aliasing support. Without
MSAA, fill-rect and path edges produce aliased (jagged) output that cannot
match the quality of CPU-supersampled reference renders. Adapter-backed routes
must offer MSAA as a TargetNative capability to close the visual quality gap.

## Scope

- `GPUMultisamplePlan` — configures sample count (1/4/8), sample mask, and
  alpha-to-coverage enablement.
- `GPUMultisampleResolvePlan` — selects resolve strategy: WGPU built-in
  resolve, custom WGSL resolve, or compute-shader resolve.
- `GPUMultisampleTargetDescriptor` — describes the multisample render target
  parameters bound to a render pass.
- `GPUTargetState` extended with `sampleCount` and `multisamplePlan` fields.
- `GPURenderPipelineKey` includes `sampleCount` as a `PipelineStateAffecting`
  field so that pipeline variants are correctly differentiated.

## Non-Goals

- Do not add temporal anti-aliasing (TAA) or post-process anti-aliasing
  (FXAA, SMAA).
- Do not claim MSAA support for non-renderable texture formats.
- Do not silently fall back to CPU supersampling when MSAA is unavailable.

## Spec Sources

- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-MSAA-PATH-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-msaa-path-heuristics) - source [Device.cpp:2040](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2040); Choose tessellated strokes for stroke/hairline, convex wedges for non-inverted convex fills, and switch between stencil wedges and curve+triangle tessellation.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128); Instantiate targets, prepare draw passes, recycle scratch resources, allocate MSAA/depth-stencil attachments, and replay the render pass.
- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Drain pending uploads, record compute path-atlas dispatches, derive pass bounds/MSAA/depth-stencil strategy, and convert pending draws into an immutable DrawPass.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUMultisamplePlan(
    val sampleCount: Int,          // 1, 4, or 8
    val sampleMask: UInt,
    val alphaToCoverageEnabled: Boolean,
)

data class GPUMultisampleResolvePlan(
    val strategy: GPUMultisampleResolveStrategy,
)

enum class GPUMultisampleResolveStrategy {
    WGPU_BUILTIN,
    CUSTOM_WGSL,
    COMPUTE_SHADER,
}

data class GPUMultisampleTargetDescriptor(
    val sampleCount: Int,
    val resolvePlan: GPUMultisampleResolvePlan,
)
```

## Acceptance Criteria

- [ ] 4x MSAA filled rect matches CPU 4x supersampled reference within PSNR
      threshold.
- [ ] 8x MSAA accepted or refused per adapter capability query.
- [ ] Alpha-to-coverage is tested with semitransparent geometry.
- [ ] Non-renderable formats produce stable refusal diagnostic.
- [ ] Resolve does not corrupt edges (no smearing, ghosting, or sub-pixel
      offset artifacts).

## Required Evidence

- PSNR comparison report: 4x MSAA vs CPU 4x supersampled for filled rects.
- Adapter capability dump showing 8x acceptance or refusal reason.
- Alpha-to-coverage visual evidence with semitransparent fill.
- Resolve-edge artifact report (accept or document any acceptable deviation).
- Refusal diagnostic dump for non-renderable format attempts.

## Fallback / Refusal Behavior

- Unsupported sample count → `unsupported.target.multisample_count`
  diagnostic.
- Incompatible format → `unsupported.target.multisample_resolve_format`
  diagnostic.
- Silent fallback to single-sample rendering or CPU supersampling is not
  allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.rendering.msaa`
- Expected classification: `TargetNative`
- Claim promotion allowed: only after Required Evidence is linked and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MSAA*'
```

## Status Notes

- `proposed`: Initial ticket.
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted — independent review accepted.

## Linear Labels

- `gpu-renderer`
- `milestone:M39`
- `area:state`
