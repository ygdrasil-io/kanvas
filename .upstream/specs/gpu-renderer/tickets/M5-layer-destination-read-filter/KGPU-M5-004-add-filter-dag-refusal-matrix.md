---
id: KGPU-M5-004
title: "Add filter DAG refusal matrix"
status: proposed
milestone: M5
priority: P1
owner_area: filters-validation
claim_impact: RefuseRequired
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M5-003]
legacy_gate: null
---

# KGPU-M5-004 - Add filter DAG refusal matrix

## PM Note

Ce ticket rend les filtres hors périmètre visibles comme refus stables.

## Problem

Arbitrary filter DAGs, unbounded intermediates, and picture prepasses must not
be mistaken for supported routes.

## Scope

- Add filter DAG support/refusal matrix.
- Add stable diagnostics for unsupported nodes, recursion, bounds, and
  intermediates.

## Non-Goals

- Do not implement arbitrary DAG support.
- Do not weaken thresholds.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`

## Design Sketch

```kotlin
data class FilterDagRefusal(val nodeKind: String, val reason: String)
```

## Acceptance Criteria

- [ ] Unsupported DAG variants map to stable diagnostics.
- [ ] PM output separates supportable bounded rows from refusals.
- [ ] Missing bounds/intermediate ownership blocks promotion.

## Required Evidence

- Refusal matrix and dashboard/report entries.

## Fallback / Refusal Behavior

Unsupported DAGs refuse; no CPU-rendered filter/layer fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.filter-dag-refusal`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Refusal matrix ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:filters`
