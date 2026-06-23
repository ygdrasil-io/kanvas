---
id: KGPU-M16-002
title: "Add dash path effect: dash interval decomposition -> stroke sub-paths"
status: proposed
milestone: M16
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M16-001]
legacy_gate: null
---

# KGPU-M16-002 - Add dash path effect: dash interval decomposition -> stroke sub-paths

## PM Note

Les lignes pointillées (dash) sont omniprésentes en UI: sélection, focus, bordures. La décomposition en intervalles doit être exacte pour éviter les chevauchements.

## Problem

Dashed paths need interval decomposition into stroke sub-paths that respect dash array, phase, and path length. Without dash support, common UI patterns (selection rectangles, focus indicators) cannot render correctly.

## Scope

- Add dash interval decomposition from path + dash array + dash phase
- Add stroke sub-path generation for each dash interval
- Add dash phase offset handling
- Produce dashed path rendering fixture dumps

## Non-Goals

- No arbitrary path effects beyond dash
- No dash pattern animation support

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_TESSELLATE_STROKES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-strokes) - source src/core/SkDashPathEffect.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class DashPathEffect { fun apply(path: Path, intervals: FloatArray, phase: Float): List<Path> }
```

## Acceptance Criteria

- [ ] Dash decomposition produces correct interval sub-paths
- [ ] Dash phase offset correctly shifts the dash pattern
- [ ] Dashed stroke renders with correct gap/fill pattern on GPU

## Required Evidence

- Dashed path GPU rendering fixture dumps for various dash patterns
- Dash phase offset test dumps (phase=0, phase=interval/2, phase=interval)
- Dash boundary test: path shorter than dash interval

## Fallback / Refusal Behavior

Dash decomposition failure emits stable diagnostic; dashed paths render as solid strokes.

## Dashboard Impact

- Expected row: `gpu-renderer.m16.dash-path-effect`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Dash*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M16`
- `area:geometry-passes`
