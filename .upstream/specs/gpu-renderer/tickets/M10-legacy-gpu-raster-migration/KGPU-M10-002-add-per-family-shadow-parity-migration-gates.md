---
id: KGPU-M10-002
title: "Add per-family shadow parity migration gates"
status: proposed
milestone: M10
priority: P0
owner_area: migration-validation
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M10-001]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M10-002 - Add per-family shadow parity migration gates

## PM Note

Ce ticket exige une preuve shadow par famille avant tout changement par défaut.

## Problem

Migration without per-family parity can overclaim broad replacement of
`gpu-raster`.

## Scope

- Add per-family shadow parity gate requirements.
- Define evidence needed before default route switch.

## Non-Goals

- Do not switch defaults.
- Do not combine unrelated families.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- [`GFX-DRAWGEOMETRY-ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source [Device.cpp:1512](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1512); Use route splitting and order updates to define per-family parity gates.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Reference batching/state-change evidence for shadow parity comparisons.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Keep painter-order/depth/stencil constraints as migration gates.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Use resource/pipeline validation before retiring legacy parity shadows.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class ShadowParityGate(val family: String, val evidenceRefs: List<String>)
```

## Acceptance Criteria

- [ ] Each family has its own parity evidence requirements.
- [ ] Missing parity keeps legacy default active.
- [ ] Rollback is named.

## Required Evidence

- Shadow route tests, before/after dumps, and PM rows per family.

## Fallback / Refusal Behavior

Families without accepted parity stay legacy-default or refused by policy.

## Dashboard Impact

- Expected row: `gpu-renderer.shadow-parity-gates`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted family evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk git diff --check
```

## Status Notes

- `proposed`: Migration gate definition.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:migration`
