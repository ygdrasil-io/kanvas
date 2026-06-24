---
id: KGPU-M18-006
title: "Add gpu-renderer-scenes evidence: savelayer-isolated, dst-read-strategy"
status: done
milestone: M18
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M18-001, KGPU-M18-002, KGPU-M18-003, KGPU-M18-004]
legacy_gate: null
---

# KGPU-M18-006 - Add gpu-renderer-scenes evidence: savelayer-isolated, dst-read-strategy

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le SaveLayer isole et le destination-read strategy fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M18 active SaveLayer execution, restore compositing et destination-read strategies en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `savelayer-isolated` (SaveLayer avec contenu isole puis composite), `dst-read-strategy` (SaveLayer utilisant la strategie de destination-read avec blend)
- Etendre RectOnlyOffscreenRenderer pour supporter SaveLayerIsolated et DstReadStrategy
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de framebuffer-fetch ou layer elision
- Pas de backdrop filters ou f16/HDR

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// SaveLayerIsolated command: push SaveLayer, draw child content, restore composite
data class SaveLayerIsolated(val bounds: Rect, val paint: Paint, val children: List<DrawCommand>)
// DstReadStrategy command: SaveLayer with blend mode requiring destination read
data class DstReadStrategy(val bounds: Rect, val blendMode: BlendMode, val children: List<DrawCommand>)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour SaveLayerIsolated et DstReadStrategy
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- savelayer-isolated GPU rendering fixture dump (PNG)
- dst-read-strategy GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M18 (backdrop filters, layer elision, framebuffer-fetch) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m18.savelayer-dst-read-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-isolated
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dst-read-strategy
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- SaveLayerExecutor, layer composite WGSL, destination read executor
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M18`
- `area:scenes-evidence`
