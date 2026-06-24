---
id: KGPU-M16-005
title: "Add gpu-renderer-scenes evidence: stroke-cap-join, dash-pattern-ladder"
status: done
milestone: M16
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M16-001, KGPU-M16-002, KGPU-M16-003]
legacy_gate: null
---

# KGPU-M16-005 - Add gpu-renderer-scenes evidence: stroke-cap-join, dash-pattern-ladder

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le stroke expansion avec cap/join et le dash pattern fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M16 active le stroke expansion, dash path effect et bounded clip en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `stroke-cap-join` (stroke avec configurations de cap et join visibles), `dash-pattern-ladder` (dash pattern avec intervalles progressifs)
- Etendre RectOnlyOffscreenRenderer pour supporter StrokeExpansion et DashPattern
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de combinaison stroke + gradient ou stroke + bitmap
- Pas de bounded clip stacks combines dans les scenes evidence

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// StrokeCapJoin command: path with explicit stroke width, cap, join params
data class StrokeCapJoin(val path: Path, val strokeWidth: Float, val cap: Cap, val join: Join, val color: Color)
// DashPatternLadder command: path with dash intervals and stroke
data class DashPatternLadder(val path: Path, val intervals: FloatArray, val phase: Float, val color: Color)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour StrokeCapJoin et DashPatternLadder
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- stroke-cap-join GPU rendering fixture dump (PNG)
- dash-pattern-ladder GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M16 (arbitrary path effects, unbounded clips) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m16.stroke-dash-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=stroke-cap-join
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dash-pattern-ladder
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
- `area:scenes-evidence`
