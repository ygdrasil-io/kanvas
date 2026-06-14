---
id: KGPU-M0-004
title: "Review R3 material payload and pipeline key evidence"
status: review
milestone: M0
priority: P0
owner_area: payloads-pipelines
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M0-003]
legacy_gate: null
---

# KGPU-M0-004 - Review R3 material payload and pipeline key evidence

## PM Note

Ce ticket vérifie que les valeurs de draw ne contaminent pas les clés durables.

## Problem

R3 is reported as integrated, but material identity, payload values, pipeline
keys, and cache facts need review before they can be treated as accepted.

## Scope

- Review `MaterialKey`, material dictionary, payload gatherer, and pipeline key
  evidence.
- Confirm RGBA values, bounds, concrete resources, handles, and surface leases
  stay out of durable keys.
- Review cache telemetry facts for material/module/pipeline hits and misses.

## Non-Goals

- Do not review real backend submission.
- Do not expand beyond solid `FillRect`.

## Spec Sources

- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - source [PaintParamsKey.cpp:88](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParamsKey.cpp:88); Study material key trees, embedded data blocks, and serializability checks.
- [`GFX-DRAWLIST-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-record) - source [DrawList.cpp:21](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:21); Reference how render-step ID plus paint ID becomes the graphics pipeline key input.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Use state-change detection and draw-area telemetry as batching evidence vocabulary.
- [`GFX-PIPELINE-MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source [PipelineManager.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PipelineManager.cpp:38); Compare cache-miss and in-flight pipeline creation handling against Kanvas pipeline keys.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class KeyBoundaryReview(val materialKeyDump: String, val pipelineKeyDump: String)
```

## Acceptance Criteria

- [ ] Material and pipeline key preimages are deterministic.
- [ ] Payload mutation tests prove values do not change durable keys.
- [ ] Cache ledger facts are linked and scoped.

## Required Evidence

- `SolidMaterialKeyTest`, payload gatherer, and pipeline key test output.
- Material/key/payload dumps.
- R3 progress row.

## Fallback / Refusal Behavior

If a resource handle or payload value enters a durable key, the route remains in
`review` or `blocked`.

## Dashboard Impact

- Expected row: `gpu-renderer.r3-key-payload-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.materials.SolidMaterialKeyTest --tests org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGathererTest --tests org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyDerivationTest
rtk git diff --check
```

## Status Notes

- `review`: R3 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:pipeline-key`
