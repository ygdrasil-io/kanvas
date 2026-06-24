---
id: KGPU-M14-004
title: "Add gpu-renderer-scenes evidence: radial-swatch, sweep-disk"
status: done
milestone: M14
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-001, KGPU-M14-002]
legacy_gate: null
---

# KGPU-M14-004 - Add gpu-renderer-scenes evidence: radial-swatch, sweep-disk

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que RadialGradient et SweepGradient fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M14 active RadialGradient et SweepGradient en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `radial-swatch` (degrade radial centre gauche), `sweep-disk` (degrade angulaire 360 degres)
- Etendre RectOnlyOffscreenRenderer pour supporter RadialGradientRect et SweepGradientRect
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de combinaison gradient + bitmap ou gradient + texte

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// RadialGradientRect command with center/radius + color stops
data class RadialGradientRect(val center: Point, val radius: Float, val stops: List<ColorStop>)
// SweepGradientRect command with center + startAngle/endAngle + color stops
data class SweepGradientRect(val center: Point, val angles: Pair<Float,Float>, val stops: List<ColorStop>)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour RadialGradientRect et SweepGradientRect
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- radial-swatch GPU rendering fixture dump (PNG)
- sweep-disk GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M14 (conical, etc.) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m14.radial-sweep-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=radial-swatch
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=sweep-disk
```

## Status Notes

- `proposed`: Initial ticket. Follows M13-005 pattern with offscreen render validation.
- `done`: Implemented in KGPU-M14. Added RadialGradientRect and SweepGradientRect to SceneCommands. Extended RectOnlyOffscreenRenderer for 2 new families. Created radial-swatch (3 radial rects) and sweep-disk (3 sweep rects) scenes in M14CandidatePromotionScenes. French docs in SceneHumanDocumentation. 153/153 scenes tests pass.

## Linear Labels

- `gpu-renderer`
- `milestone:M14`
- `area:scenes-evidence`
