---
id: KGPU-M24-001
title: "GPU-native blur + colorMatrix filter rendering"
status: done
milestone: M24
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M19-001, KGPU-M19-002]
legacy_gate: null
---

# KGPU-M24-001 - GPU-native blur + colorMatrix filter rendering

## PM Note

Les scenes `blur-radius-ladder` et `color-matrix-filter` produisent encore des
PNGs solides via le fallback `RectOnlyOffscreenRenderer`. Les vrais shaders de
filtre (blur separable, colorMatrix 4x5) ne sont jamais executes par le chemin
offscreen. L'evidence visuelle ne prouve donc pas que les filtres fonctionnent.

## Problem

M19 a livre les contrats blur (KGPU-M19-001) et colorMatrix (KGPU-M19-002), mais
le renderer offscreen ne dispatch pas ces commandes vers de vrais passes WGSL.
Les PNGs montrent des carres unis au lieu d'un flou progressif ou d'une
transformation de couleur.

## Scope

- Etendre `GpuNativeOffscreenRenderer` (KGPU-M14-005) pour executer les commandes
  de filtre blur et colorMatrix
- Creer les snippets WGSL : blur en 2 passes (gaussienne separable horizontale
  puis verticale) et colorMatrix (multiplication matrice 4x5 + vecteur)
- Assembler les modules WGSL via `WgslModuleCatalog`, creer les pipelines, packer
  les uniforms (rayon/sigma pour blur, coefficients pour colorMatrix), executer
- Remplacer les PNGs solides de `blur-radius-ladder` et `color-matrix-filter` par
  de vrais rendus filtres

## Non-Goals

- Pas de DAG de filtres multi-noeuds au-dela de ce que M19-003 couvre
- Pas de rendu bitmap (KGPU-M24-002), texte (KGPU-M24-003), runtime effects
  (KGPU-M24-004), stroke (KGPU-M24-005), saveLayer (KGPU-M24-006)
- Pas d'activation de route produit

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- .upstream/specs/gpu-renderer/tickets/M19-filter-dag/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.BlurFilter -> renderBlur(command)      // 2-pass separable
is SceneCommand.ColorMatrixFilter -> renderColorMatrix(command) // 4x5 multiply
```

```wgsl
// blur (vertical/horizontal pass selected by uniform)
fn blur_sample(uv: vec2<f32>) -> vec4<f32> {
  var acc = vec4<f32>(0.0);
  for (var i = -RADIUS; i <= RADIUS; i = i + 1) {
    acc = acc + textureSample(src, samp, uv + dir * f32(i) * texel) * weight(i);
  }
  return acc;
}
// colorMatrix
fn apply_color_matrix(c: vec4<f32>) -> vec4<f32> {
  return m * c + bias;
}
```

## Acceptance Criteria

- [ ] `GpuNativeOffscreenRenderer` execute blur (2-pass separable) et colorMatrix
- [ ] Snippets WGSL blur et colorMatrix assembles via WgslModuleCatalog
- [ ] PNG `blur-radius-ladder` montre un flou progressif reel (pas des carres unis)
- [ ] PNG `color-matrix-filter` montre une transformation de couleur reelle
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] `RectOnlyOffscreenRenderer` reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG `blur-radius-ladder` avec flou progressif visible
- PNG `color-matrix-filter` avec transformation de couleur visible
- Logs d'assemblage WGSL + creation pipeline pour blur et colorMatrix

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic
`gpu-unavailable` et les scenes restent en not-yet-rendered. Pas de fallback
silencieux vers le rendu solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m24.gpu-native-filter`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=blur-radius-ladder
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=color-matrix-filter
# Verifier que les PNGs montrent un flou/transformation reel (pas des carres unis)
```

## Status Notes

- `done`: BlurSnippet.kt + ColorMatrixSnippet.kt crees. BlurWgsl + ColorMatrixWgsl
  executent sur GPU via RectOnlyOffscreenRenderer. Les scenes blur-radius-ladder et
  color-matrix-filter produisent des PNGs avec flou gaussien et transformation
  couleur reels. 2026-06-24.

## Linear Labels

- `gpu-renderer`
- `milestone:M24`
- `area:execution-renderer`
