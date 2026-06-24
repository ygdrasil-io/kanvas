---
id: KGPU-M19-005
title: "Add gpu-renderer-scenes evidence: blur-radius-ladder, color-matrix-filter"
status: done
milestone: M19
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M19-001, KGPU-M19-002, KGPU-M19-003]
legacy_gate: null
---

# KGPU-M19-005 - Add gpu-renderer-scenes evidence: blur-radius-ladder, color-matrix-filter

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le Gaussian blur et le ColorMatrix filter fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M19 active GaussianBlur, ColorMatrix filter et filter DAG en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `blur-radius-ladder` (echelle de rayons de blur croissants), `color-matrix-filter` (image avec matrice de couleur appliquee)
- Etendre RectOnlyOffscreenRenderer pour supporter BlurRadiusLadder et ColorMatrixFilter
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de RuntimeShader ou arbitrary SkSL filters
- Pas de per-channel LUT ou color space conversion
- Pas de filter DAG au-dela de 2 nodes

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// BlurRadiusLadder command: multiple rects with increasing blur sigma values
data class BlurRadiusLadder(val rects: List<Pair<Rect, Float>>, val colors: List<Color>)
// ColorMatrixFilter command: image with 4x5 color matrix applied
data class ColorMatrixFilter(val image: Image, val matrix: FloatArray, val rect: Rect)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour BlurRadiusLadder et ColorMatrixFilter
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- blur-radius-ladder GPU rendering fixture dump (PNG)
- color-matrix-filter GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M19 (Picture, RuntimeShader, LUT filters) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m19.filter-dag-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=blur-radius-ladder
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=color-matrix-filter
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- GaussianBlurFilter, ColorMatrixFilter, FilterDAGExecutor
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M19`
- `area:scenes-evidence`
