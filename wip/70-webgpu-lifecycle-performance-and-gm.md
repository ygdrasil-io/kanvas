# WIP 70 — Cycle de vie WebGPU, performance et GMs

> Document temporaire. Le runtime cible est headless/offscreen. Les mesures et
> références de ce lot ne dépendent d'aucune fenêtre native ou sous-module de
> windowing non publié.

## Objectif du groupe

Vérifier que le backend reste correct et borné au fil des frames, des caches et
des incidents device. Mesurer ensuite les routes déjà prouvées ; la performance
ne remplace jamais une capture correctness ni une référence Skia traçable.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| Runtime/device | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`, `GPUBackendRuntimeNative.kt`, `GPURuntimeBaselineSnapshot.kt` |
| Caches | `.../execution/ExecutionCacheContracts.kt`, `GPUWgpu4kCorePrimitiveSessionCache.kt`, `GPUWgpu4kPreparedImageSessionCache.kt`, `GPUWgpu4kSeparableBlurRectSessionCache.kt` |
| Ressources | `.../resources/GPUScratchTexturePool.kt`, `GPUTextureFrameResourcePlan.kt`, `GPUVerticesFrameResourcePlan.kt` |
| Performance | `integration-tests/gpu-evidence/.../performance/GpuEvidencePerformanceRunner.kt`, `PerformanceContracts.kt`, `PerformanceBundleWriter.kt`, `PerformanceEligibility.kt` |
| Gates | `.../performance/PerformanceEligibilityTest.kt`, `PerformanceRunnerTest.kt`, `PerformanceStatisticsTest.kt`, `PerformanceBundleTest.kt` |

## Matrice de scénarios

| Sous-famille | Cas à couvrir | Résultat exigé |
| --- | --- | --- |
| Pipeline/cache | Deux frames identiques, changement d'uniform seulement, changement de shader/layout/blend/clip, eviction et limite de cache. | Pas de pipeline créé à chaud pour uniforms seuls ; nouvelle variante seulement pour les dimensions de clé prévues ; cache borné. |
| Ressources | Upload/image/vertices, scratch textures, readback, frame annulée, close/dispose et double close. | Comptage bytes/handles exact, libération sans fuite et diagnostic sur handle périmé. |
| Device lifecycle | Resize, perte/récupération device, adapter fallback, session interrompue puis réouverte. | Invalidation des caches, refus/résultat indisponible explicite et absence de resource réutilisée après perte. |
| Déterminisme | Même scène/seed/adapter, sérialisation/replay, ordre de passes et routes de fallback. | Pixels et diagnostics stables sur le même adapter ; les variations inter-adapter conservent leurs métadonnées. |
| Benchmarks | Rects solides, rect/rrect AA, gradients, images nearest/bilinear, blend alpha, `saveLayer`, blur/filter, texte/vertices seulement quand rendables. | 10 warmups, 90 mesures, échantillons bruts, p50/p95, adapter/commit/dimensions et compteurs de pipeline/draw/bytes. |
| GMs/références | Cas simple, bord et composition par famille supportée ; diff à cause classifiée. | Provenance Skia, référence, CPU/GPU/diff/stats/route/diagnostics liés au même scénario ; seuil local à la famille. |

## Règles de gate

Une scène n'entre dans la performance que si elle est déjà correcte et exécutée
sur adapter admissible. Un fallback adapter, une erreur de capture ou une route
non prouvée peut être rapporté comme diagnostic, jamais comme performance de
release. Les promotions GM sont sérialisées par adapter et par commit ; aucun
seuil global ne masque une différence de coverage, blend/premul, colorspace,
sampling, filter bounds ou glyph.

## Dépendances et sortie

Les tests unitaires de lifecycle/cache peuvent commencer après le lot 00. Les
scènes de bench et GMs sont ajoutées lorsque les lots 10/20/30/40/50/60 ont une
preuve correctness. Ce lot clôt la vague : ses résultats absorbés, le dossier
`wip/` peut être supprimé sans perdre de connaissance nécessaire à l'exécution.

## Vérification

```bash
./gradlew :integration-tests:gpu-evidence:test --tests '*Performance*' --tests '*Eligibility*'
./gradlew :integration-tests:gpu-evidence:gpuEvidencePerformance -PsourceCommit=<sha>
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
