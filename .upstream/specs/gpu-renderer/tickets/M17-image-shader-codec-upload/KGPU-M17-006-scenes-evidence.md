---
id: KGPU-M17-006
title: "Add gpu-renderer-scenes evidence: bitmap-sampler-matrix, tile-mode-strip"
status: proposed
milestone: M17
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M17-001, KGPU-M17-002, KGPU-M17-003, KGPU-M17-004]
legacy_gate: null
---

# KGPU-M17-006 - Add gpu-renderer-scenes evidence: bitmap-sampler-matrix, tile-mode-strip

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le BitmapShader avec sampler matrix et les tile modes fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M17 active BitmapShader, BitmapRect, image upload et tile mode expansion en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `bitmap-sampler-matrix` (image avec matrice de sampler transform), `tile-mode-strip` (bande montrant les 4 tile modes: Clamp, Repeat, Mirror, Decal)
- Etendre RectOnlyOffscreenRenderer pour supporter BitmapSamplerMatrix et TileModeStrip
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de mipmap ou anisotropic filter
- Pas de formats YUV ou color-managed decode

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// BitmapSamplerMatrix command: image with local matrix transforming UV coordinates
data class BitmapSamplerMatrix(val image: Image, val matrix: Matrix, val filter: FilterMode)
// TileModeStrip command: image strip showing Clamp | Repeat | Mirror | Decal side by side
data class TileModeStrip(val image: Image, val rect: Rect)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour BitmapSamplerMatrix et TileModeStrip
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- bitmap-sampler-matrix GPU rendering fixture dump (PNG)
- tile-mode-strip GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M17 (mipmap, YUV, anisotropic) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m17.bitmap-tile-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=tile-mode-strip
```

## Status Notes

- `proposed`: Initial ticket. Follows M14-004 pattern with offscreen render validation.

## Linear Labels

- `gpu-renderer`
- `milestone:M17`
- `area:scenes-evidence`
