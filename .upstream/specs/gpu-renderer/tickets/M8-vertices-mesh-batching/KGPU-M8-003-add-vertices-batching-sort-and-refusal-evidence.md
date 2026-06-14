---
id: KGPU-M8-003
title: "Add vertices batching sort and refusal evidence"
status: proposed
milestone: M8
priority: P2
owner_area: batching
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M8-001, KGPU-M8-002]
legacy_gate: null
---

# KGPU-M8-003 - Add vertices batching sort and refusal evidence

## PM Note

Ce ticket empêche le batching vertices de traverser des frontières visuelles.

## Problem

Vertices batching must respect topology, material, clip, layer, blend, and
destination-read constraints.

## Scope

- Add vertices batch key and split reason dumps.
- Add refusal evidence for incompatible batches.

## Non-Goals

- Do not claim performance readiness.
- Do not batch across destination-dependent operations.

## Spec Sources

- `.upstream/specs/gpu-renderer/15-draw-layer-planner-and-sort-policy.md`
- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`

## Graphite Algorithm References

- [`GFX-VERTICES-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-vertices-step) - source [VerticesRenderStep.cpp:71](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/VerticesRenderStep.cpp:71); Use vertices variants as route-specific batching inputs.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Reference sort keys and state-change minimization for vertices batching evidence.
- [`GFX-DRAWLIST-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-layer) - source [DrawListLayer.cpp:48](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawListLayer.cpp:48); Use overlap-aware layered batching constraints for future mesh-like batching.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Keep painter-order/depth constraints explicit in vertices refusal evidence.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class VerticesBatchEvidence(val batchKey: String, val splitReasons: List<String>)
```

## Acceptance Criteria

- [ ] Compatible batches have deterministic key dumps.
- [ ] Incompatible cases split/refuse with stable reasons.
- [ ] Ordering is preserved.

## Required Evidence

- Batch key, sort, telemetry, and split/refusal dumps.

## Fallback / Refusal Behavior

Ambiguous batching splits or refuses.

## Dashboard Impact

- Expected row: `gpu-renderer.vertices-batching`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Batching evidence only.

## Linear Labels

- `gpu-renderer`
- `milestone:M8`
- `area:batching`
