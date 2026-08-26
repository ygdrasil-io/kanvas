# WIP 00 — Evidence, catalogue et refus

> Document temporaire à supprimer après intégration des tests et promotion des
> artefacts. Le code Kotlin et les bundles vérifiés restent la source de vérité.

## Objectif du groupe

Faire du catalogue une frontière fiable entre trois résultats exclusifs : rendu
GPU prouvé, refus explicite, ou observation indisponible. Ce groupe traite le
harness commun ; les familles graphiques sont détaillées dans les autres briefs.

## Code et tests à lire avant modification

| Zone | Fichiers principaux |
| --- | --- |
| Catalogue/scènes | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt`, `EvidenceCase.kt`, `EvidenceSceneContracts.kt` |
| Programmes publics | `.../programs/KanvasScenePrograms.kt`, `KanvasSurfaceProgram.kt`, `RendererRefusalPrograms.kt` |
| Exécution | `.../runner/KanvasSurfaceEvidenceExecutor.kt`, `GPUPreparedEvidenceExecutor.kt` |
| Artefacts | `.../artifacts/EvidenceBundleWriter.kt`, `EvidenceBundleVerifier.kt`, `PromoteEvidenceCli.kt`, `VerifyEvidenceCli.kt` |
| Tests existants | `EvidenceSceneContractsTest.kt`, `CatalogExpectationInvariantTest.kt`, `GpuEvidenceCatalogTest.kt`, `KanvasSurfaceEvidenceExecutorTest.kt`, tests sous `artifacts/` |

## Tests à ajouter ou compléter

| Sujet | Scénarios précis | Résultat exigé |
| --- | --- | --- |
| Snapshot du catalogue | Lire le catalogue de la branche avant toute capture, puis relire tous les IDs après chaque changement de code. | État courant : 31 rendus / 2 refus. Les IDs historiques `repeat-gradient-refusal` et `gradient-stroke-refusal` sont désormais des rendus publics `Surface`; Wave 2 ajoute `clip-rrect-solid`, `clip-rrect-ellipse` et `clip-rrect-two-bands`. |
| Route réelle | Vérifier le type de programme de chaque cas : `KanvasSurfaceProgram` ou `RoutedSceneProgram` interne. | Les rendus sont des preuves `Surface`; les deux refus internes ne sont jamais présentés comme couverture de cette route publique. |
| Unicité/complétude | Un ID unique, une scène publique littérale par rendu, un oracle par rendu, aucun oracle de réussite pour un refus. | Échec de test sur ID doublon, scène implicite, oracle absent, raison de refus vide ou verdict contradictoire. |
| Intégrité de route | Rendu avec readback/draw/pipeline positifs ; refus sans submission, readback, draw ou pipeline. | Impossible de promouvoir un fallback CPU, une exécution partielle ou une preuve d'environnement différente. |
| Bundles | Round-trip, hash/PNG/JSON modifié, symlink, chemin de sortie, JSON ambigu et fichier manquant. | Le verifier rejette la corruption et l'écriture reste atomique. |
| Reproductibilité | Même scène, commit, seed, taille et adapter ; environnement manquant ou incohérent. | Bundle réutilisable et comparaison non ambiguë ; les métadonnées sont obligatoires. |
| CLI | Catalogue complet, filtre d'ID, échec d'initialisation, échec de close/dispose et tentative de promotion invalide. | Aucun artefact partiel ne survit et l'erreur expose une cause actionnable. |

## Artefacts requis et promotion

Le root v2 de correctness vit sous
`reports/gpu-renderer/evidence/correctness/<generated|promoted>/` et porte le
catalogue et les métadonnées partagées : `catalog.json`, `environment.json` et,
pour le root promoted checked-in, `promotion.json`. Les bundles de scène ne
dupliquent plus `environment.json` ni `promotion.json`.

Un bundle de scène généré de rendu contient CPU/reference, GPU, diff, stats,
route, diagnostics, manifest et verdict. Un bundle généré de refus contient
route, diagnostics, stats, manifest et verdict ; il n'a ni PNG de succès ni
statistiques présentées comme performance valide. La promotion checked-in
ajoute les métadonnées de revue au root promoted v2, sans réécrire les bytes
des PNG déjà vérifiés.

Une capture de diagnostic peut être faite scène par scène avec une sélection
explicite, par exemple `-Pscene=solid-card-stack` ou
`-PscenesFile=scenes.txt`. En revanche, la promotion checked-in reste une
transaction de catalogue complet via `promoteGpuEvidence -Pall` (la CLI reçoit
alors `--all`), après vérification du catalogue entier. Les rapports et preuves
associés vivent sous `reports/gpu-renderer/evidence/`.

Les formulations de ce WIP sont dérivées du code, des tests et des artefacts
générés/promus vérifiés ; ces éléments font autorité, pas le Markdown.

Une capture doit être faite après rebase/cherry-pick seulement si le SHA exact
à capturer est fixé. Après capture ou promotion, toute réécriture du SHA exige
une nouvelle capture et un nouvel audit ; elle ne peut pas hériter de la preuve
du SHA précédent.

### Rebaseline du harness

La tâche Gradle `promoteGpuEvidence` expose le rebaseline par les propriétés
officielles `promotionRebaseline`, `promotionPriorComparison` et
`promotionNewComparison`. `promotionRebaseline` accepte exactement `true` ou
`false` (absent équivaut à `false`) ; `true` exige les deux comparaisons non
vides et les transmet à la CLI. Sans rebaseline, toute comparaison est rejetée
plutôt qu'ignorée. Aucun init script caché n'est un workflow valide.

## Dépendances et sortie

Ce lot est la seule dépendance dure de tous les autres briefs. Il est intégré
seul, car `GpuEvidenceCatalog.kt` et les programmes de scène sont des points de
conflit. Sa sortie est un harness capable de rejeter une fausse preuve, plus un
catalogue et des artefacts promus cohérents avec le code de la branche.

## Vérification

```bash
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha> -Pscene=solid-card-stack
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha> -PscenesFile=scenes.txt
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=<sha> -Pscene=solid-card-stack
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=<sha> -PscenesFile=scenes.txt
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha> -Pall
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=<sha> -Pall
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
./gradlew :integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=<sha> -PpromotionReviewer=<reviewer> -PpromotionReason=<reason> -Pall
```
