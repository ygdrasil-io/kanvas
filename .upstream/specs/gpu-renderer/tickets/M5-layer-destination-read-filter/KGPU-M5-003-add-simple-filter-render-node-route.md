---
id: KGPU-M5-003
title: "Add simple filter render node route"
status: proposed
milestone: M5
priority: P0
owner_area: filters
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-001]
legacy_gate: "filter legacy"
---

# KGPU-M5-003 - Add simple filter render node route

## PM Note

Ce ticket ajoute un premier nœud de filtre GPU borné avec ses intermédiaires.

## Problem

Filters require graph, bounds, crop, intermediate, resource, and route evidence
before any support claim.

## Scope

- Add one simple filter node route.
- Add intermediate texture ownership and bounds diagnostics.

## Non-Goals

- Do not add arbitrary filter DAGs.
- Do not add runtime-effect filters unless descriptor route is accepted.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`

## Design Sketch

```kotlin
data class FilterNodeEvidence(val nodePlan: String, val intermediatePlan: String)
```

## Acceptance Criteria

- [ ] Filter graph and node plan dumps are linked.
- [ ] Intermediate ownership is explicit.
- [ ] Unsupported nodes refuse.

## Required Evidence

- Filter graph, node, bounds, intermediate, route, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported filters refuse; no CPU-rendered filter texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.simple-node`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: First simple filter node only.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:filters`
