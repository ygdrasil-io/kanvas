# W72 — Performance tiers (task 48)

Status: `reporting-only`

Cette preuve réutilise le harness headless `integration-tests:gpu-evidence` et
sa route publique `KanvasSurfaceProgram`. La capture performance est séparée
de la génération/promotion correctness : un run ouvre une session réutilisable,
valide le cold frame contre l’oracle CPU, exécute 10 warmups puis 90 frames
mesurées, et écrit un bundle immutable par scène.

## Capture reproductible

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:gpuEvidencePerformance \
  -PsourceCommit=4e3d9d17c641d6ff9fa48b6cdd21b6d2dc46b475
```

Résultat sur cet hôte : `103` scènes `ShouldRender`, chacune avec
`EligibleMeasurement`, `90` samples mesurés et les deltas de soumissions
`cold/warmup/measured = 1/10/90`. Les bundles sont sous
`reports/gpu-renderer/evidence/performance/generated/<SHA>/<scene>/`.

Le payload conserve `sceneId`, SHA, OS/architecture, JDK, adapter, device
generation, p50/p95, samples bruts, et les compteurs runtime. Les compteurs
désormais sérialisés incluent `intermediateTexturesCreated`,
`destinationReadbackSnapshots`, `uniformSlabBytesAllocated` et `samplersCreated`.

## Tiers et budgets

`PerformanceTiering` applique des budgets par famille (`P0`, `P1`,
`Inventory`) : p50/p95 frame time, allocations, pipeline builds, upload bytes
et readback bytes. Le statut reste `reporting-only` et
`countsAsReleaseGate=false`; aucune valeur ne modifie un seuil de similarité
correctness.

| Métrique | Source dans le harness | Politique W72 |
|---|---|---|
| frame p50/p95 | `Observed` (`MonotonicClock`) | mesurée, budget familial |
| allocations | `Derived` (somme des resource counters) | proxy explicite, non native |
| pipeline builds | `Derived` (`cache.execution` / pass plans) | proxy explicite, non build count |
| upload bytes | `Observed` quand `uniformSlabBytesAllocated` est exposé | sinon `Unavailable` |
| readback bytes | `Unavailable` (le backend n’expose pas les bytes) | jamais remplacé par zéro |
| intermediate bytes | `Unavailable` (seul le count de textures est exposé) | jamais estimé |

Les compteurs absents restent `Unavailable` avec un reason code stable ; ils ne
sont pas convertis silencieusement en `0`. Les allocations et pipeline builds
présents sont explicitement `Derived`, donc non éligibles à une promotion
release-blocking.

## Vérification

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests org.graphiks.kanvas.gpu.evidence.performance.PerformanceRunnerTest \
  --tests org.graphiks.kanvas.gpu.evidence.performance.PerformanceCliTest \
  --tests org.graphiks.kanvas.gpu.evidence.performance.PerformanceStatisticsTest
```

Résultat : `15/15` tests passés (8 runner, 2 CLI, 5 tier/statistics), dont le
refus d’un programme non-Surface à la frontière `EvidenceCase`, les fixtures
négatives pour les compteurs manquants et le maintien du statut
`reporting-only`.

## Non-claims

- aucune release gate blocking n’est activée ;
- aucune performance native GPU timestamp-query n’est revendiquée ;
- le statut de tier est visible via la sortie CLI (`tier=P1
  status=reporting-only`) et les entrées `tier:*` de `diagnostics.json`, sans
  champ de release gate structuré dans le bundle ;
- les proxies `Derived` ne sont pas des mesures de pipeline build/allocation
  native ;
- readback/intermediate bytes restent dependency/instrumentation-gated ;
- aucun `gpu-renderer-scenes`, font ou codec n’est ajouté à cette vague.
