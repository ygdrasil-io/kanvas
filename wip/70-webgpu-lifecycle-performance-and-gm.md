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
| Benchmarks | Rects solides, rect/rrect AA, gradients, images nearest/bilinear, blend alpha, `saveLayer`, blur/filter, texte/vertices seulement quand rendables. | 1 cold, 10 warmups, 90 mesures, échantillons bruts, p50/p95, adapter/commit/dimensions et compteurs de pipeline/draw/bytes. |
| GMs/références | Cas simple, bord et composition par famille supportée ; diff à cause classifiée. | Provenance Skia, référence, CPU/GPU/diff/stats/route/diagnostics liés au même scénario ; seuil local à la famille. |

## Règles de gate

Une scène n'entre dans la performance que si elle est déjà correcte et exécutée
sur adapter admissible. Un fallback adapter, une erreur de capture ou une route
non prouvée peut être rapporté comme diagnostic, jamais comme performance de
release. Les promotions GM sont sérialisées par adapter et par commit ; aucun
seuil global ne masque une différence de coverage, blend/premul, colorspace,
sampling, filter bounds ou glyph.

## Baseline locale actuelle, non promue

Une baseline a été auditée localement au SHA
`a1143cee2425e3a818dabe076ac468c551fbae75` : Apple M2 Max natif non-fallback,
14 rendus, 1 cold + 10 warmups + 90 mesures par scène, 1260 échantillons et une
plage p95 de 4,30 à 8,40 ms. C'est un contexte reproductible de reporting,
pas une evidence correctness checked-in, pas une promotion et pas un gate de
release.

La capture performance nécessite son gate humain propre, distinct de celui de
la promotion correctness. Les rapports et preuves qui deviendraient partageables
vivent sous `reports/gpu-renderer/evidence/`; cette correction ne déplace ni ne
promote la baseline locale.

Stop point performance : avant toute capture, présenter à l'utilisateur le
catalogue rendu, le HEAD/SHA exact, le root de sortie exact et l'adapter exact,
puis obtenir son autorisation explicite. Ce consentement ne couvre ni la
promotion correctness transactionnelle `--all` d'un root generated exact, ni
une nouvelle vague, `gpu-renderer-scenes` ou une publication. Inversement,
l'autorisation de la promotion correctness ne couvre pas cette capture
performance.

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
