---
id: KGPU-M10-001
title: "Inventory legacy `gpu-raster` route ownership"
status: done
milestone: M10
priority: P0
owner_area: legacy-adapter
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
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

## Graphite Algorithm References

- [`GFX-DRAWGEOMETRY-ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source [Device.cpp:1512](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1512); Use draw-family routing as comparison vocabulary for legacy route ownership.
- [`GFX-SHAPE-ROUTING-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-shape-routing-heuristics) - source [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900); Map legacy families to supported, gated, or refused Kanvas routes.
- [`GFX-RENDERSTEP-MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83); Compare legacy route ownership to explicit technique/step decomposition.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

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

- `done`: `reports/gpu-renderer/2026-06-15-m10-legacy-inventory-hygiene.md`
  inventories legacy `gpu-raster` ownership per route family and links each
  family to the current `:gpu-renderer` replacement ticket, refusal, blocker,
  or remaining gate. The report keeps all legacy routes non-retired and does
  not change default behavior. Independent review
  `019ec878-7c64-7e42-ab70-bb80043e53d1` accepted the remediated inventory
  after explicit material/paint, rect/rrect stroke, and clear/discard rows were
  added.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:legacy`
