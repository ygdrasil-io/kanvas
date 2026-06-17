---
id: KGPU-M8-002
title: "Add vertex index buffer payload and resource plans"
status: done
milestone: M8
priority: P1
owner_area: vertices-resources
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M8-001]
legacy_gate: null
---

# KGPU-M8-002 - Add vertex index buffer payload and resource plans

## PM Note

Ce ticket rend les buffers vertices/index vérifiables avant soumission GPU.

## Problem

Buffer packing and upload must be deterministic, resource-owned, and excluded
from material identity where appropriate.

## Scope

- Add vertex/index buffer payload, upload, and resource plan evidence.
- Add stale/missing/budget refusals.

## Non-Goals

- Do not optimize batching.
- Do not add broad mesh formats.

## Spec Sources

- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`

## Graphite Algorithm References

- [`GFX-VERTICES-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-vertices-step) - source [VerticesRenderStep.cpp:71](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/VerticesRenderStep.cpp:71); Reference expansion from indexed/strip/fan data into GPU append vertices.
- [`GFX-DRAW-WRITER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-writer) - source [DrawWriter.cpp:32](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawWriter.cpp:32); Study coalesced vertex/index/instance command emission.
- [`GFX-RESOURCE-KEYED-CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - source [ResourceProvider.cpp:113](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceProvider.cpp:113); Use buffer keying and shareability boundaries for vertex/index payload plans.
- [`GFX-DRAWLIST-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-record) - source [DrawList.cpp:21](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:21); Compare vertices payload capture to pipeline/uniform/texture key capture.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class VertexBufferEvidence(val layoutHash: String, val uploadPlan: String)
```

## Acceptance Criteria

- [x] Buffer layout and upload plan dumps are deterministic.
- [x] Resource facts stay out of material keys.
- [x] Invalid buffers refuse.

## Required Evidence

- Buffer layout, payload, upload, resource, and refusal dumps.

## Fallback / Refusal Behavior

Invalid or stale buffer facts refuse before submission.

## Dashboard Impact

- Expected row: `gpu-renderer.vertices.buffers`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: KGPU-M8-001 is `done`, and this ticket adds contract-only
  `GPUVerticesBufferPlanPlanner` evidence for deterministic vertex/index
  payload, upload, and resource plans. The evidence preserves
  `CPUPreparedGPU`, `TargetPrepared`, `productActivation=false`,
  `materialized=false`, and stable refusals for missing route decisions,
  invalid indices, missing upload-before-draw, budget pressure, stale
  generation facts, missing usage flags, and live-handle leakage. Independent
  re-review accepted the evidence with no remaining P0/P1/P2 blockers. No
  adapter-backed upload, product `DrawVertices` support, batching, mesh
  support, or CPU-rasterized mesh texture fallback is allowed.

## Linear Labels

- `gpu-renderer`
- `milestone:M8`
- `area:vertices`
