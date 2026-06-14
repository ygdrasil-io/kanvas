---
id: KGPU-M2-004
title: "Add conservative batching and sort evidence"
status: proposed
milestone: M2
priority: P1
owner_area: analysis-recording
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M2-001, KGPU-M2-002, KGPU-M2-003]
legacy_gate: null
---

# KGPU-M2-004 - Add conservative batching and sort evidence

## PM Note

Ce ticket prouve que le regroupement de draws ne change pas l’ordre visuel.

## Problem

Batching can easily cross material, clip, layer, or destination-read boundaries
unless sort and compatibility facts are explicit.

## Scope

- Add conservative batching evidence for compatible rect/rrect draws.
- Add refusal/split reasons for incompatible material, clip, layer, and ordering
  cases.

## Non-Goals

- Do not optimize for broad performance gates.
- Do not batch across destination-dependent operations.

## Spec Sources

- `.upstream/specs/gpu-renderer/15-draw-layer-planner-and-sort-policy.md`
- `.upstream/specs/gpu-renderer/34-analysis-materialization-recording.md`

## Graphite Algorithm References

- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Study sorted key iteration, state-change detection, and per-pipeline draw-area telemetry.
- [`GFX-DRAW-WRITER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-writer) - source [DrawWriter.cpp:32](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawWriter.cpp:32); Reference coalesced vertex/instance emission and changed-buffer binding.
- [`GFX-DRAWLIST-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-layer) - source [DrawListLayer.cpp:48](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawListLayer.cpp:48); Use layered batching constraints for future overlap-aware batching decisions.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Keep painter-order/depth/stencil invariants explicit in batching evidence.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUBatchEvidence(val batchKey: String, val splitReasons: List<String>)
```

## Acceptance Criteria

- [ ] Compatible draws batch with deterministic key/preimage dumps.
- [ ] Incompatible draws split with stable reasons.
- [ ] Batching does not change payload or resource identity boundaries.

## Required Evidence

- Sort key and batch key dumps.
- Split/refusal fixtures.
- Telemetry counters for accepted/refused batches.

## Fallback / Refusal Behavior

If compatibility is ambiguous, the planner must split or refuse rather than
batching unsafely.

## Dashboard Impact

- Expected row: `gpu-renderer.rect-batching-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Follows M2 geometry/material/clip routes.

## Linear Labels

- `gpu-renderer`
- `milestone:M2`
- `area:batching`
