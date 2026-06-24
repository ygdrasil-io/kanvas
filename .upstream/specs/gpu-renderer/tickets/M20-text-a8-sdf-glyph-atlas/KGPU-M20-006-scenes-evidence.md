---
id: KGPU-M20-006
title: "Add gpu-renderer-scenes evidence: glyph-atlas-strip, sdf-glyph-scale"
status: done
milestone: M20
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M20-001, KGPU-M20-002, KGPU-M20-003, KGPU-M20-004]
legacy_gate: null
---

# KGPU-M20-006 - Add gpu-renderer-scenes evidence: glyph-atlas-strip, sdf-glyph-scale

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le glyph atlas A8 et le SDF glyph rendering fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M20 active l'A8 glyph atlas, le SDF glyph atlas, DrawTextRun et le text shaper en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `glyph-atlas-strip` (bande de glyphes A8 rendues depuis l'atlas), `sdf-glyph-scale` (glyphes SDF rendues a differentes echelles)
- Etendre RectOnlyOffscreenRenderer pour supporter GlyphAtlasStrip et SdfGlyphScale
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de bidi, complex scripts, color fonts, COLRv1 ou SVG glyphs
- Pas d'emoji
- Latin text only

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// GlyphAtlasStrip command: Latin text string rendered from A8 atlas
data class GlyphAtlasStrip(val text: String, val fontSize: Float, val position: Point)
// SdfGlyphScale command: single glyph rendered via SDF at multiple scale factors
data class SdfGlyphScale(val glyph: Char, val scales: List<Float>, val origin: Point)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour GlyphAtlasStrip et SdfGlyphScale
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- glyph-atlas-strip GPU rendering fixture dump (PNG)
- sdf-glyph-scale GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M20 (bidi, emoji, color fonts, complex scripts) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m20.text-atlas-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=glyph-atlas-strip
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=sdf-glyph-scale
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- TextA8AtlasExecutor, SDFGenerator, GPUDrawTextRunExecutor, WGSL snippets
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M20`
- `area:scenes-evidence`
