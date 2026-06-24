---
id: KGPU-M21-005
title: "Add gpu-renderer-scenes evidence: runtime-effect-uniform, runtime-effect-child"
status: done
milestone: M21
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M21-001, KGPU-M21-002, KGPU-M21-003]
legacy_gate: null
---

# KGPU-M21-005 - Add gpu-renderer-scenes evidence: runtime-effect-uniform, runtime-effect-child

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que les runtime effects avec uniforms et child effects fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M21 active le runtime effect registry, l'execution lane et les effets enregistres (SimpleRT, LinearGradientRT, SpiralRT) en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `runtime-effect-uniform` (runtime effect avec uniforms variables), `runtime-effect-child` (runtime effect avec child shader input)
- Etendre RectOnlyOffscreenRenderer pour supporter RuntimeEffectUniform et RuntimeEffectChild
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de arbitrary SkSL compilation
- Pas de user-defined effect registration
- Existing hand-written WGSL only

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// RuntimeEffectUniform command: registered effect with uniform block parameters
data class RuntimeEffectUniform(val effectId: String, val uniforms: Map<String, Any>, val rect: Rect)
// RuntimeEffectChild command: registered effect with child shader input
data class RuntimeEffectChild(val effectId: String, val childShader: Shader, val rect: Rect)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour RuntimeEffectUniform et RuntimeEffectChild
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- runtime-effect-uniform GPU rendering fixture dump (PNG)
- runtime-effect-child GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes avec arbitrary SkSL ou effets non-enregistres restent refused avec diagnostic stable.

## Dashboard Impact

- Expected row: `gpu-renderer.m21.runtime-effect-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=runtime-effect-uniform
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=runtime-effect-child
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- SimpleRT/LinearGradientRT/SpiralRT descriptors, GPURuntimeEffectExecutor
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M21`
- `area:scenes-evidence`
