---
id: KGPU-M16-001
title: "Add stroke expansion: stroke path -> fillable contour with join/cap geometry"
status: done
milestone: M16
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M15-001]
legacy_gate: null
---

# KGPU-M16-001 - Add stroke expansion: stroke path -> fillable contour with join/cap geometry

## PM Note

L'expansion de contour (stroke) transforme un trait en une forme remplissable. La géométrie des joints et bouts de ligne doit être parfaite pour le rendu UI.

## Problem

Stroked paths need to be expanded into fillable contours with correct join (miter, round, bevel) and cap (butt, round, square) geometry. Without stroke expansion, only filled paths can be rendered.

## Scope

- Add stroke expansion from path + stroke params to fillable contour
- Add join geometry: miter (with limit), round, bevel
- Add cap geometry: butt, round, square
- Produce stroked path rendering fixture dumps

## Non-Goals

- No hairline stroke optimization
- 128-edge stroke budget
- No perspective or skew-aware stroke expansion

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_TESSELLATE_STROKES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-strokes) - source src/gpu/graphite/geom/Shape.cpp stroke; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class StrokeExpander { fun expand(path: Path, stroke: StrokeParams): Contour }\n// StrokeParams: width, join, cap, miterLimit
```

## Acceptance Criteria

- [ ] Stroke expansion produces valid fillable contours for all join/cap types
- [ ] Miter limit correctly truncates sharp angles
- [ ] Stroke width produces correct visual thickness on GPU

## Required Evidence

- Stroke rendering fixture dumps for miter/round/bevel joins
- Stroke rendering fixture dumps for butt/round/square caps
- Miter limit boundary test dumps

## Fallback / Refusal Behavior

Stroke expansion failure or budget exceeded emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m16.stroke-expansion`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Stroke*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- StrokeExpander, DashPathEffect, bounded clip expansion
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M16`
- `area:geometry-passes`
