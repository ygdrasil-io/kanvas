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
