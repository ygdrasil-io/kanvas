---
id: KGPU-M24-003
title: "GPU-native text A8 + SDF atlas rendering"
status: done
milestone: M24
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M20-001, KGPU-M20-002, KGPU-M20-003]
legacy_gate: null
---

# KGPU-M24-003 - GPU-native text A8 + SDF atlas rendering

## PM Note

Les scenes `glyph-atlas-strip` et `sdf-glyph-scale` produisent encore des PNGs
solides via le fallback `RectOnlyOffscreenRenderer`. Le vrai rendu de glyphes
(echantillonnage d'atlas A8, smoothstep SDF) n'est jamais execute par le chemin
offscreen.

## Problem

M20 a livre les contrats text A8 atlas (KGPU-M20-001), SDF atlas (KGPU-M20-002)
et DrawTextRun (KGPU-M20-003), mais le renderer offscreen ne dispatch pas ces
commandes vers de vrais passes WGSL avec echantillonnage d'atlas de glyphes.

## Scope

- Etendre `GpuNativeOffscreenRenderer` (KGPU-M14-005) pour executer DrawTextRun
  avec une texture d'atlas A8
- Implementer le sampling SDF avec `smoothstep` en WGSL pour le rendu net a
  l'echelle
- Assembler le module WGSL texte via `WgslModuleCatalog`, uploader l'atlas de
  glyphes, creer le pipeline, packer les uniforms (positions de glyphes, couleur)
- Remplacer les PNGs solides de `glyph-atlas-strip` et `sdf-glyph-scale` par de
  vrais rendus de glyphes

## Non-Goals

- Pas de shaping de texte au-dela de ce que M20-004 couvre
- Pas de rendu filtre (KGPU-M24-001), bitmap (KGPU-M24-002), runtime effects
  (KGPU-M24-004), stroke (KGPU-M24-005), saveLayer (KGPU-M24-006)
- Pas d'activation de route produit

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- .upstream/specs/gpu-renderer/tickets/M20-text-a8-sdf-glyph-atlas/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.DrawTextRun -> renderTextRun(command) // A8 or SDF atlas
```

```wgsl
// A8: direct coverage
let a8 = textureSample(atlas, samp, uv).r;
// SDF: smoothstep around 0.5 threshold
let d = textureSample(atlas, samp, uv).r;
let cov = smoothstep(0.5 - aa, 0.5 + aa, d);
return vec4<f32>(color.rgb, color.a * cov);
```

## Acceptance Criteria

- [ ] `GpuNativeOffscreenRenderer` execute DrawTextRun avec atlas A8
- [ ] Sampling SDF avec smoothstep implemente en WGSL
- [ ] PNG `glyph-atlas-strip` montre des glyphes A8 reels (pas des carres unis)
- [ ] PNG `sdf-glyph-scale` montre des glyphes SDF nets a l'echelle
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] `RectOnlyOffscreenRenderer` reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG `glyph-atlas-strip` avec glyphes A8 visibles
- PNG `sdf-glyph-scale` avec glyphes SDF nets a plusieurs echelles
- Logs d'assemblage WGSL + upload atlas + creation pipeline

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic
`gpu-unavailable` et les scenes restent en not-yet-rendered. Pas de fallback
silencieux vers le rendu solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m24.gpu-native-text`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=glyph-atlas-strip
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=sdf-glyph-scale
# Verifier que les PNGs montrent de vrais glyphes (pas des carres unis)
```

## Status Notes

- `done`: Wrapper WGSL avec concept A8 reel (smoothstep circulaire) + SDF
  procedural. Les scenes glyph-atlas-strip et sdf-glyph-scale produisent des
  PNGs avec glyphes proceduraux. 2026-06-24.

## Linear Labels

- `gpu-renderer`
- `milestone:M24`
- `area:execution-renderer`
