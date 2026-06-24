---
id: KGPU-M24-002
title: "GPU-native bitmap shader + tile mode rendering"
status: proposed
milestone: M24
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M17-001, KGPU-M17-002, KGPU-M17-004]
legacy_gate: null
---

# KGPU-M24-002 - GPU-native bitmap shader + tile mode rendering

## PM Note

Les scenes `bitmap-sampler-matrix` et `tile-mode-strip` produisent encore des
PNGs solides via le fallback `RectOnlyOffscreenRenderer`. Le vrai shader bitmap
et les modes de tuilage (clamp/repeat/mirror/decal) ne sont jamais executes par
le chemin offscreen.

## Problem

M17 a livre les contrats bitmap shader (KGPU-M17-001), BitmapRect execution
(KGPU-M17-002) et tile modes (KGPU-M17-004), mais le renderer offscreen ne
dispatch pas ces commandes vers de vrais passes WGSL avec sampling de texture.

## Scope

- Etendre `GpuNativeOffscreenRenderer` (KGPU-M14-005) pour executer BitmapRect
  avec sampling nearest et linear
- Implementer les quatre modes de tuilage en WGSL : clamp, repeat, mirror, decal
- Assembler le module WGSL bitmap via `WgslModuleCatalog`, uploader la texture
  source, creer le pipeline, packer les uniforms (matrice, tile mode, filtre)
- Remplacer les PNGs solides de `bitmap-sampler-matrix` et `tile-mode-strip` par
  de vrais rendus de texture echantillonnee

## Non-Goals

- Pas de decodage de codec au-dela de ce que M17-003 couvre
- Pas de rendu filtre (KGPU-M24-001), texte (KGPU-M24-003), runtime effects
  (KGPU-M24-004), stroke (KGPU-M24-005), saveLayer (KGPU-M24-006)
- Pas d'activation de route produit

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- .upstream/specs/gpu-renderer/tickets/M17-image-shader-codec-upload/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.BitmapRect -> renderBitmap(command) // nearest/linear + tile mode
```

```wgsl
fn tile_coord(uv: vec2<f32>, mode: u32) -> vec2<f32> {
  switch (mode) {
    case 0u: { return clamp(uv, vec2(0.0), vec2(1.0)); } // clamp
    case 1u: { return fract(uv); }                        // repeat
    case 2u: { return abs(fract(uv * 0.5) * 2.0 - 1.0); } // mirror
    default: { return uv; }                               // decal (alpha 0 outside)
  }
}
```

## Acceptance Criteria

- [ ] `GpuNativeOffscreenRenderer` execute BitmapRect avec sampling nearest/linear
- [ ] Les quatre tile modes (clamp/repeat/mirror/decal) implementes en WGSL
- [ ] PNG `bitmap-sampler-matrix` montre une texture echantillonnee reelle
- [ ] PNG `tile-mode-strip` montre les quatre modes de tuilage distincts
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] `RectOnlyOffscreenRenderer` reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG `bitmap-sampler-matrix` avec texture echantillonnee visible
- PNG `tile-mode-strip` montrant clamp/repeat/mirror/decal distincts
- Logs d'assemblage WGSL + upload texture + creation pipeline

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic
`gpu-unavailable` et les scenes restent en not-yet-rendered. Pas de fallback
silencieux vers le rendu solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m24.gpu-native-bitmap`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=tile-mode-strip
# Verifier que les PNGs montrent une texture/tuilage reel (pas des carres unis)
```

## Status Notes

- `proposed`: Initial ticket. Debloque l'evidence visuelle reelle pour le bitmap
  shader et les tile modes. Necessite GPU pour l'execution.

## Linear Labels

- `gpu-renderer`
- `milestone:M24`
- `area:execution-renderer`
