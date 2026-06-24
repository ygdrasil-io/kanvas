---
id: KGPU-M24-005
title: "GPU-native stroke rendering"
status: proposed
milestone: M24
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M16-001, KGPU-M16-002]
legacy_gate: null
---

# KGPU-M24-005 - GPU-native stroke rendering

## PM Note

Les scenes `stroke-cap-join` et `dash-pattern-ladder` produisent encore des PNGs
solides via le fallback `RectOnlyOffscreenRenderer`. La vraie geometrie de stroke
tessellee (caps, joins, patterns de dash) n'est jamais executee par le chemin
offscreen.

## Problem

M16 a livre l'expansion de stroke (KGPU-M16-001) et le dash path effect
(KGPU-M16-002), mais le renderer offscreen ne dispatch pas ces commandes vers de
vrais passes WGSL avec geometrie tessellee. Les PNGs montrent des carres unis au
lieu de vrais traits avec caps/joins.

## Scope

- Etendre `GpuNativeOffscreenRenderer` (KGPU-M14-005) pour executer les chemins
  de stroke en utilisant la geometrie tessellee
- Creer un snippet WGSL pour le remplissage de stroke avec support des caps
  (butt/round/square) et des joins (miter/round/bevel)
- Assembler le module WGSL via `WgslModuleCatalog`, uploader les vertices
  tessellees, creer le pipeline, packer les uniforms (largeur, couleur, dash)
- Remplacer les PNGs solides de `stroke-cap-join` et `dash-pattern-ladder` par de
  vrais rendus de stroke

## Non-Goals

- Pas de clip expansion au-dela de ce que M16-003 couvre
- Pas de rendu filtre (KGPU-M24-001), bitmap (KGPU-M24-002), texte
  (KGPU-M24-003), runtime effects (KGPU-M24-004), saveLayer (KGPU-M24-006)
- Pas d'activation de route produit

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- .upstream/specs/gpu-renderer/tickets/M16-stroke-dash-clip-expansion/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.StrokePath -> renderStroke(command) // tessellated geometry
```

```wgsl
// stroke fill: tessellated triangles carry coverage in a varying
@vertex fn vs(in: StrokeVertex) -> VsOut { /* expand by half-width along normal */ }
@fragment fn fs(in: VsOut) -> @location(0) vec4<f32> {
  return vec4<f32>(color.rgb, color.a * in.coverage);
}
```

## Acceptance Criteria

- [ ] `GpuNativeOffscreenRenderer` execute les chemins de stroke via geometrie tessellee
- [ ] Snippet WGSL de stroke fill avec support cap/join
- [ ] PNG `stroke-cap-join` montre des caps et joins reels (pas des carres unis)
- [ ] PNG `dash-pattern-ladder` montre des patterns de dash reels
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] `RectOnlyOffscreenRenderer` reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG `stroke-cap-join` avec caps/joins visibles
- PNG `dash-pattern-ladder` avec patterns de dash visibles
- Logs de tessellation + assemblage WGSL + creation pipeline

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic
`gpu-unavailable` et les scenes restent en not-yet-rendered. Pas de fallback
silencieux vers le rendu solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m24.gpu-native-stroke`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=stroke-cap-join
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dash-pattern-ladder
# Verifier que les PNGs montrent de vrais strokes (pas des carres unis)
```

## Status Notes

- `proposed`: Initial ticket. Debloque l'evidence visuelle reelle pour le rendu
  de stroke (caps/joins/dash). Necessite GPU pour l'execution.

## Linear Labels

- `gpu-renderer`
- `milestone:M24`
- `area:execution-renderer`
