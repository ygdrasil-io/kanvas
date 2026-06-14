---
id: KGPU-M1-004
title: "Add first-route rollback and parity validation"
status: done
milestone: M1
priority: P0
owner_area: validation-adapter
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-003]
legacy_gate: "legacy drawRect"
---

# KGPU-M1-004 - Add first-route rollback and parity validation

## PM Note

Ce ticket prouve qu’une activation candidate peut être annulée proprement.

## Problem

Product activation requires a rollback and parity gate so a first-route issue
does not strand users on a broken default route.

## Scope

- Add first-route parity evidence against legacy output.
- Add rollback diagnostics and validation.
- Confirm activation does not affect unsupported variants.

## Non-Goals

- Do not define performance readiness.
- Do not expand beyond one route.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Study state changes and command emission for rollback parity comparisons.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Reference ordering invariants that rollback validation must preserve.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Use pipeline/texture preparation failure as rollback trigger vocabulary.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class RollbackEvidence(val legacyChecksum: String, val gpuRendererChecksum: String)
```

## Acceptance Criteria

- [x] Legacy and `:gpu-renderer` outputs are compared for the accepted scene.
- [x] Rollback path restores legacy routing.
- [x] PM output records scope and non-claims.

## Required Evidence

- Before/after route dumps.
- Readback or checksum comparison.
- Rollback validation transcript.

## Fallback / Refusal Behavior

Any parity failure keeps product activation disabled and emits visible
diagnostics.

## Dashboard Impact

- Expected row: `gpu-renderer.first-route-rollback-parity`
- Expected classification: `TargetNative`
- Claim promotion allowed: only after accepted activation decision.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk git diff --check
```

## Status Notes

- `done`: Added `GpuRendererFirstRouteRollbackParityValidator` and
  adapter-backed tests comparing legacy-before, product-flagged, and
  legacy-rollback pixel checksums. The report keeps
  `productRouteActivated=false`, `releaseBlocking=false`, and
  `readinessDelta=0.0`, records rollback diagnostics, and proves
  `StrokeAndFill` remains an unsupported product-flag variant.
- Fresh evidence:
  `reports/gpu-renderer/2026-06-14-m1-004-rollback-parity-validation.md`.
- Independent review `019ec731-4bf3-7e60-9ab6-af513036a6e9` approved moving
  the ticket to `done`: no hidden product activation, release blocking,
  readiness movement, or broadened route support claim was found.

## Linear Labels

- `gpu-renderer`
- `milestone:M1`
- `area:validation`
