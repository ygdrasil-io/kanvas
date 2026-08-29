# WIP 70 — lifecycle WebGPU, performance et fermeture GM

> Brief d'exécution de `W70` à `W75`. Les mesures performance ne commencent
> qu'après fermeture correctness de la route concernée.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| Frame lifecycle | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` |
| Resource routes | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/` |
| GM runner | `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt`, `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt`, `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistry.kt` |
| Evidence | `../integration-tests/gpu-evidence/`, `../reports/gpu-renderer/evidence/` |

## W70 — lifecycle des ressources

- [ ] Tester device/queue generation et invalidation après device loss.
- [ ] Tester upload, sampled texture, intermediate, readback et dispose/close.
- [ ] Tester échec de création adapter/device/pipeline sans artefact partiel.
- [ ] Vérifier absence de use-after-free et de ressource inter-device.
- [ ] Vérifier ownership sur success, refusal et exception.

## W71 — caches et déterminisme

- [ ] Vérifier les cache keys pour geometry, paint, layout, format, sample count,
      descriptor et device generation.
- [ ] Tester hit, miss, eviction, aggregate budget et reconstruction.
- [ ] Rejouer la même scène cold/warm et comparer pixels/routes/stats.
- [ ] Vérifier que parallélisme de préparation ne réordonne pas les draws.

## W72 — performance tiers

- [ ] Définir un corpus par famille correctness fermée.
- [ ] Mesurer cold/warm frame time, pipeline builds, allocations, uploads,
      readbacks et mémoire intermédiaire.
- [ ] Enregistrer adapter, driver, OS, SHA, warmups et nombre de mesures.
- [ ] Fixer des budgets par route et non un seuil global de similarité.
- [ ] Séparer strictement capture correctness et capture performance.

## W73 — régénération GM complète

- [ ] Repartir du SHA final de toutes les routes correctness intégrées.
- [ ] Régénérer les rendus des seuls GMs enregistrés.
- [ ] Régénérer les scores, publier l'audit des lignes orphelines et ne les
      nettoyer que dans un rebaseline séparé explicitement vérifié.
- [ ] Vérifier provenance et nombre des références/rendus.
- [ ] Régénérer le dashboard à partir de ces artefacts vérifiés.

## W74 — burn-down des résiduels

- [ ] Regrouper chaque échec par première cause native, jamais uniquement par
      score final.
- [ ] Séparer bug, feature non implémentée, stable refusal, dependency gate et
      référence invalide.
- [ ] Créer une micro-vague par route commune à plusieurs GMs.
- [ ] Réexécuter le cluster après chaque correction et le registre à la fin du
      lot.
- [ ] Continuer jusqu'à zéro `UNCLASSIFIED` non-font.

## W75 — fermeture

- [ ] Générer la matrice finale depuis l'API, le registre, les diagnostics et le
      catalogue.
- [ ] Vérifier chaque verdict `SUPPORTED`, `STABLE_REFUSAL`,
      `DEPENDENCY_GATED` ou `OUT_OF_SCOPE`.
- [ ] Vérifier le catalogue promoted complet et les rapports archivés.
- [ ] Supprimer les anciens rapports fourre-tout devenus redondants.
- [ ] Supprimer `wip/` après absorption des gates dans le code et les tests.

## Vérification

```bash
./gradlew :kanvas:test
./gradlew :gpu-renderer:test
./gradlew :integration-tests:skia:test
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
