# WIP 00 — vérité terrain et evidence

> Brief d'exécution de `W00` et `W01`. Le code, les tests exécutés et les
> bundles vérifiés font autorité; les nombres de ce document ne font pas partie
> des gates.

## Objectif

Réconcilier le registre GM, les rendus, les scores, le catalogue GPU evidence
v2 et les preuves standalone avant toute nouvelle affirmation de support.

Le snapshot observé lors de la rédaction contenait 615 entrées enregistrées,
73 scènes de catalogue et 689 lignes de scores. Cet écart est uniquement le
signal d'entrée de `W00`; il doit être recalculé depuis le code à l'exécution.

## Fichiers propriétaires

| Responsabilité | Fichiers |
| --- | --- |
| Registre/runner GM | `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistry.kt`, `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt`, `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt` |
| Scores | `../integration-tests/skia/src/test/resources/test-similarity-scores.properties` |
| Catalogue | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt` |
| Programmes publics | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/KanvasSurfaceProgram.kt` |
| Exécution | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/KanvasSurfaceEvidenceExecutor.kt` |
| Artefacts | `../reports/gpu-renderer/evidence/` |

## W00 — inventaire source-derived

- [ ] Ajouter un test qui échoue si un nom enregistré apparaît zéro ou plusieurs
      fois dans la table de scores courante.
- [ ] Ajouter un test qui énumère pour chaque GM : famille, référence disponible,
      rendu disponible, score, nombre d'opérations, première route ou premier
      diagnostic terminal.
- [ ] Rejouer le registre complet au SHA de la branche.
- [ ] Auditer explicitement les lignes de scores sans GM enregistré et les
      refuser en mode strict, sans suppression silencieuse.
- [ ] Refuser une référence ou un rendu dont le nom ne peut pas être relié à un
      GM enregistré.
- [ ] Écrire le rapport machine-readable et son résumé humain sous
      `reports/gpu-renderer/evidence/gm-inventory/`.
- [ ] Vérifier que l'inventaire peut être régénéré sans modifier ses résultats.

## W01 — convergence du catalogue de preuve

- [ ] Énumérer les scènes rendues, les refus, les oracles et les bundles
      standalone depuis le code et les répertoires vérifiés.
- [ ] Faire échouer le test de catalogue sur ID dupliqué, scène implicite,
      oracle manquant, refus sans diagnostic ou verdict contradictoire.
- [ ] Associer chaque preuve standalone encore pertinente à une scène du
      catalogue ou la déclarer explicitement comme diagnostic non promouvable.
- [ ] Vérifier qu'un rendu possède draw, pipeline, submission et readback
      positifs et qu'un refus n'en possède aucun.
- [ ] Vérifier les hash, manifests, PNG/JSON, chemins, symlinks et écritures
      atomiques des bundles.
- [ ] Vérifier que génération et promotion conservent le SHA, l'adapter, la
      taille, la seed et la version d'oracle.
- [ ] Promouvoir uniquement les scènes réconciliées.

## Sortie

`W00` et `W01` sont terminées quand les comptages sont dérivés automatiquement,
que les scores orphelins sont explicitement audités (le mode strict les refuse), que chaque scène a un verdict unique et que le
catalogue promoted complet est vérifiable headless.

## Vérification

```bash
./gradlew :integration-tests:skia:test
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
