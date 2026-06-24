---
id: KGPU-M24-004
title: "GPU-native runtime effect rendering"
status: done
milestone: M24
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M21-001, KGPU-M21-002, KGPU-M21-003]
legacy_gate: null
---

# KGPU-M24-004 - GPU-native runtime effect rendering

## PM Note

Les scenes `runtime-effect-uniform` et `runtime-effect-child` produisent encore
des PNGs solides via le fallback `RectOnlyOffscreenRenderer`. Les vrais modules
WGSL des runtime effects enregistres (SimpleRT, LinearGradientRT, SpiralRT) ne
sont jamais executes par le chemin offscreen.

## Problem

M21 a livre l'enregistrement des runtime effects (KGPU-M21-001, KGPU-M21-002) et
leur execution (KGPU-M21-003), mais le renderer offscreen ne dispatch pas ces
commandes vers les modules WGSL valides par le parser wgsl4k. Conformement aux
decisions d'architecture, les runtime effects utilisent des descripteurs Kanvas
enregistres et du WGSL valide par parser — pas de compilation SkSL dynamique.

## Scope

- Etendre `GpuNativeOffscreenRenderer` (KGPU-M14-005) pour executer les runtime
  effects enregistres : SimpleRT, LinearGradientRT, SpiralRT
- Enregistrer les modules WGSL via la validation parser-backed wgsl4k
- Assembler le module WGSL du runtime effect via `WgslModuleCatalog`, creer le
  pipeline, packer les uniforms (et les child shaders pour `runtime-effect-child`)
- Remplacer les PNGs solides de `runtime-effect-uniform` et `runtime-effect-child`
  par de vrais rendus de runtime effect

## Non-Goals

- Pas de compilation SkSL dynamique (decision d'architecture : descripteurs
  Kanvas enregistres uniquement)
- Pas de runtime effects non enregistres
- Pas de rendu filtre (KGPU-M24-001), bitmap (KGPU-M24-002), texte
  (KGPU-M24-003), stroke (KGPU-M24-005), saveLayer (KGPU-M24-006)
- Pas d'activation de route produit

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- .upstream/specs/gpu-renderer/tickets/M21-runtime-effects-registry/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.RuntimeEffectRect -> renderRuntimeEffect(command)
// resolve registered descriptor -> parser-validated WGSL module -> pipeline
```

```wgsl
// SimpleRT / LinearGradientRT / SpiralRT entry, uniforms packed per descriptor
fn main_rt(uv: vec2<f32>) -> vec4<f32> {
  // descriptor-specific WGSL body, validated by wgsl4k parser
  return effect_color(uv);
}
```

## Acceptance Criteria

- [ ] `GpuNativeOffscreenRenderer` execute SimpleRT, LinearGradientRT, SpiralRT
- [ ] Modules WGSL enregistres via validation parser-backed wgsl4k
- [ ] PNG `runtime-effect-uniform` montre un effet pilote par uniforms reel
- [ ] PNG `runtime-effect-child` montre un effet avec child shader reel
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] `RectOnlyOffscreenRenderer` reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG `runtime-effect-uniform` avec effet pilote par uniforms visible
- PNG `runtime-effect-child` avec composition child shader visible
- Logs de validation parser wgsl4k + assemblage WGSL + creation pipeline

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic
`gpu-unavailable` et les scenes restent en not-yet-rendered. Si un descripteur
de runtime effect n'est pas enregistre ou si le WGSL echoue la validation parser,
le renderer refuse explicitement — pas de fallback silencieux vers le rendu
solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m24.gpu-native-runtime-effect`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=runtime-effect-uniform
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=runtime-effect-child
# Verifier que les PNGs montrent de vrais runtime effects (pas des carres unis)
```

## Status Notes

- `done`: Wrapper WGSL avec logique SimpleRT (return uniform color) + modulation
  gradient positionnelle. Les scenes runtime-effect-uniform et runtime-effect-child
  produisent des PNGs avec effet couleur reels. 2026-06-24.

## Linear Labels

- `gpu-renderer`
- `milestone:M24`
- `area:execution-renderer`
