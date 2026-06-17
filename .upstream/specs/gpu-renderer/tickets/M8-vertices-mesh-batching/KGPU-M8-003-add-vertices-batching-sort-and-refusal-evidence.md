---
id: KGPU-M8-003
title: "Add vertices batching sort and refusal evidence"
status: done
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

- [x] Compatible batches have deterministic key dumps.
- [x] Incompatible cases split/refuse with stable reasons.
- [x] Ordering is preserved.

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

- `done`: KGPU-M8-001 and KGPU-M8-002 are `done`, and this ticket adds
  contract-only `GPUVerticesBatchingPlanner` evidence for deterministic batch
  keys, sort-window dumps, adjacent compatibility, split reasons, telemetry,
  and refusal rows. The evidence preserves `ImplementationCandidate`,
  `GPUNative`, `productActivation=false`, `materialized=false`, and stable
  stops for sort-window, topology, render-step, pipeline/layout, material,
  blend, clip, layer, destination-read, barrier, upload-generation, and
  unknown-overlap boundaries. It also refuses empty inputs, refused route
  decisions, refused buffer plans, and ambiguous paint-order regressions. The
  independent review P2 for cross-window batching is fixed by splitting on
  `sortWindowId` and exposing per-batch `sortWindow` axes. Independent final
  re-review `019ed5ec-2289-7d53-8778-7948635b5e06` found no remaining P0/P1/P2
  blockers. No performance readiness, executable batching, product
  `DrawVertices` support, adapter-backed execution, or CPU-rasterized mesh
  texture fallback is claimed.

## Linear Labels

- `gpu-renderer`
- `milestone:M8`
- `area:batching`
