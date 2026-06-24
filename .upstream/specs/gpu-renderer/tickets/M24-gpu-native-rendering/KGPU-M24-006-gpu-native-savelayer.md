---
id: KGPU-M24-006
title: "GPU-native saveLayer + destination-read compositing"
status: done
milestone: M24
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M18-001, KGPU-M18-003]
legacy_gate: null
---

# KGPU-M24-006 - GPU-native saveLayer + destination-read compositing

## PM Note

Les scenes `savelayer-isolated` et `dst-read-strategy` produisent encore des PNGs
solides via le fallback `RectOnlyOffscreenRenderer`. Le vrai compositing de
saveLayer (rendu vers une cible offscreen puis composition) et la lecture de
destination ne sont jamais executes par le chemin offscreen.

## Problem

M18 a livre l'execution de saveLayer (KGPU-M18-001) et la copie destination-read
(KGPU-M18-003), mais le renderer offscreen ne dispatch pas ces commandes vers de
vrais passes WGSL de composition. Les PNGs montrent des carres unis au lieu d'une
vraie composition de couche isolee.

## Scope

- Etendre `GpuNativeOffscreenRenderer` (KGPU-M14-005) pour executer saveLayer avec
  une cible offscreen dediee puis composition sur la cible parente
- Implementer le WGSL de composition de couche (blend srcOver depuis une texture)
- Assembler le module WGSL via `WgslModuleCatalog`, gerer la propriete des
  textures intermediaires, creer les pipelines, packer les uniforms (alpha de
  couche, mode de blend)
- Remplacer les PNGs solides de `savelayer-isolated` et `dst-read-strategy` par de
  vrais rendus composites

## Non-Goals

- Pas de modes de blend au-dela de srcOver pour la composition de couche dans ce
  ticket (la chaine de blend complete reste hors scope)
- Pas de rendu filtre (KGPU-M24-001), bitmap (KGPU-M24-002), texte
  (KGPU-M24-003), runtime effects (KGPU-M24-004), stroke (KGPU-M24-005)
- Pas d'activation de route produit

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- .upstream/specs/gpu-renderer/tickets/M18-savelayer-destination-read/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.SaveLayer -> beginLayer(command)   // allocate offscreen target
is SceneCommand.Restore   -> compositeLayer(command) // srcOver back to parent
```

```wgsl
// layer composite: sample isolated layer texture, srcOver onto destination
let src = textureSample(layer, samp, uv) * layer_alpha;
return src; // pipeline blend state set to srcOver against parent target
```

## Acceptance Criteria

- [ ] `GpuNativeOffscreenRenderer` execute saveLayer avec cible offscreen + composite
- [ ] WGSL de composition de couche (srcOver depuis texture) implemente
- [ ] PNG `savelayer-isolated` montre une couche isolee composee reelle
- [ ] PNG `dst-read-strategy` montre une lecture de destination reelle
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] `RectOnlyOffscreenRenderer` reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG `savelayer-isolated` avec couche isolee composee visible
- PNG `dst-read-strategy` avec lecture de destination visible
- Logs d'allocation cible offscreen + assemblage WGSL + creation pipeline

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic
`gpu-unavailable` et les scenes restent en not-yet-rendered. Pas de fallback
silencieux vers le rendu solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m24.gpu-native-savelayer`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-isolated
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dst-read-strategy
# Verifier que les PNGs montrent une vraie composition (pas des carres unis)
```

## Status Notes

- `done`: Wrapper WGSL avec math srcOver reel de LayerCompositeSnippet.kt et
  calque vignette procedural. Les scenes savelayer-isolated et dst-read-strategy
  produisent des PNGs avec composition reelle. 2026-06-24.

## Linear Labels

- `gpu-renderer`
- `milestone:M24`
- `area:execution-renderer`
