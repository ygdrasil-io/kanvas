---
id: KGPU-M10-001
title: "Inventory legacy `gpu-raster` route ownership"
status: proposed
milestone: M10
priority: P0
owner_area: legacy-adapter
claim_impact: ImplementationCandidate
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M10-001 - Inventory legacy `gpu-raster` route ownership

## PM Note

Ce ticket cartographie les routes legacy avant toute migration ou suppression.

## Problem

Legacy `gpu-raster` routes must be migrated per family with ownership,
evidence, and rollback visibility.

## Scope

- Inventory legacy route owners and replacement status by family.
- Link each route to `:gpu-renderer` target ticket or refusal.

## Non-Goals

- Do not delete routes.
- Do not change default behavior.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`

## Design Sketch

```kotlin
data class LegacyRouteInventoryRow(val legacyRoute: String, val replacementTicket: String?)
```

## Acceptance Criteria

- [ ] Every inventoried family has owner, status, and evidence/refusal link.
- [ ] Archived evidence is labeled historical.
- [ ] No route is removed.

## Required Evidence

- Inventory report and route ownership matrix.

## Fallback / Refusal Behavior

Unknown ownership keeps the route legacy-owned and non-retired.

## Dashboard Impact

- Expected row: `gpu-renderer.legacy-route-inventory`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Inventory before migration.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:legacy`
