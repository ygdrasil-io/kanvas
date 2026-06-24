---
id: KGPU-M14-005
title: "Add GPU-native offscreen renderer for executing WGSL material shaders"
status: done
milestone: M14
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-001, KGPU-M14-002, KGPU-M13-002]
legacy_gate: null
---

# KGPU-M14-005 - Add GPU-native offscreen renderer for WGSL material shaders

## PM Note

Le RectOnlyOffscreenRenderer actuel fait du rendu solide de diagnostic (toutes les
commandes deviennent des FillRect colores). Pour avoir de vrais degradés visuels
(radial, sweep, linear), il faut un renderer qui execute les vrais shaders WGSL
assembles par le pipeline gpu-renderer. Sans ca, les PNGs ne prouvent pas que
les shaders fonctionnent — ils prouvent juste que l'infrastructure de commandes
marche.

## Problem

Les scenes evidence (M13-005, M14-004) produisent des PNGs via
RectOnlyOffscreenRenderer qui remplace tous les types de commandes par des
FillRect unis. Les vrais shaders WGSL (LinearGradientSnippet,
RadialGradientSnippet, SweepGradientSnippet) ne sont jamais executes par le
chemin offscreen. L'evidence visuelle est donc trompeuse — on voit des carres
colores, pas des degradés.

## Scope

- Creer un GpuNativeOffscreenRenderer (ou etendre RectOnlyOffscreenRenderer)
  qui utilise le pipeline gpu-renderer complet : assemblage WGSL →
  creation pipeline → upload uniforms → submit → readback
- Pour chaque commande de type gradient, assembler le module WGSL correspondant
  via WgslModuleCatalog, creer le render pipeline, packer les uniforms, et
  executer le rendu
- Produire de vrais PNGs de degradés (pas des carres colores)
- Remplacer les PNGs des scenes M13 (rounded-rect-solids, linear-gradient-lanes,
  scissor-overlay) et M14 (radial-swatch, sweep-disk) par des rendus shader natifs

## Non-Goals

- Pas de rendu de texture/bitmap (M17)
- Pas de rendu de texte (M20)
- Pas de rendu de filtres (M19)
- Pas de rendu de vertices (M22)
- Pas de rendu de runtime effects (M21)

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer/src/main/kotlin/.../execution/ExecutionContracts.kt
- gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt
- gpu-renderer/src/main/kotlin/.../materials/LinearGradientMaterialLowering.kt
- gpu-renderer/src/main/kotlin/.../materials/RadialGradientMaterialLowering.kt
- gpu-renderer/src/main/kotlin/.../materials/SweepGradientMaterialLowering.kt
- gpu-raster/src/main/kotlin/.../GpuRendererFirstRouteWebGpuSubmitter.kt (reference pattern)

## Design Sketch

```kotlin
class GpuNativeOffscreenRenderer(
    private val backend: GPUExecutionContext,
    private val wgslCatalog: WgslModuleCatalog,
) {
    fun render(scene: GPURendererScene<SceneCommand>): GpuNativeRenderResult {
        for (command in scene.commands) {
            when (command) {
                is SceneCommand.LinearGradientRect -> renderLinearGradient(command)
                is SceneCommand.RadialGradientRect -> renderRadialGradient(command)
                is SceneCommand.SweepGradientRect -> renderSweepGradient(command)
                is SceneCommand.FillRect -> renderSolidRect(command)
                is SceneCommand.FillRRect -> renderSolidRRect(command)
                else -> return GpuNativeRenderResult.NotYetSupported(command)
            }
        }
    }
}
```

## Acceptance Criteria

- [ ] GpuNativeOffscreenRenderer cree et fonctionnel pour LinearGradient, RadialGradient, SweepGradient
- [ ] Les PNGs des scenes M13 (linear-gradient-lanes) et M14 (radial-swatch, sweep-disk)
      montrent de vrais degradés (pas des carres unis)
- [ ] Les nouveaux PNGs sont commites en remplacement des anciens
- [ ] Le RectOnlyOffscreenRenderer reste disponible pour le rendu solide de diagnostic

## Required Evidence

- PNG de linear-gradient-lanes avec degradation de couleurs visible
- PNG de radial-swatch avec degradation radial visible
- PNG de sweep-disk avec degradation angulaire visible
- WGSL module assembly + pipeline creation logs pour chaque type de gradient

## Fallback / Refusal Behavior

Si le GPU n'est pas disponible, le renderer produit un diagnostic "gpu-unavailable"
et les scenes restent en not-yet-rendered. Pas de fallback silencieux vers le
rendu solide.

## Dashboard Impact

- Expected row: `gpu-renderer.m14.gpu-native-offscreen-renderer`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=linear-gradient-lanes
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=radial-swatch
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=sweep-disk
# Verifier que les PNGs montrent de vrais degradés (pas des carres unis)
```

## Status Notes

- `proposed`: Initial ticket. Debloque l'evidence visuelle reelle pour les
  scenes gradient. Necessite GPU Apple Metal pour l'execution.
- `done`: Implemented. Added WgslComposer (WGSL helpers for linear/radial/sweep/solid), UniformPacker (byte packing), and GPUBackendRawUniformDraw + drawFullscreenRawUniformPass to backend recorder. Modified RectOnlyOffscreenRenderer.renderToPixels() to dispatch gradient draws with real WGSL shaders. Re-rendered linear-gradient-lanes (8.2K), radial-swatch (21.2K), sweep-disk (15.8K) with real gradients. 153/153 scenes tests pass.

## Linear Labels

- `gpu-renderer`
- `milestone:M14`
- `area:execution-renderer`
