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
| Réalignement du catalogue | Capturer `repeat-gradient-refusal` après le correctif `REPEAT`, puis relire tous les IDs. | Le catalogue et les preuves promues convergent vers 13 rendus / 3 refus ; le nom historique de la scène ne modifie pas son verdict rendu. |
| Unicité/complétude | Un ID unique, une scène publique littérale par rendu, un oracle par rendu, aucun oracle de réussite pour un refus. | Échec de test sur ID doublon, scène implicite, oracle absent, raison de refus vide ou verdict contradictoire. |
| Intégrité de route | Rendu avec readback/draw/pipeline positifs ; refus sans submission, readback, draw ou pipeline. | Impossible de promouvoir un fallback CPU, une exécution partielle ou une preuve d'environnement différente. |
| Bundles | Round-trip, hash/PNG/JSON modifié, symlink, chemin de sortie, JSON ambigu et fichier manquant. | Le verifier rejette la corruption et l'écriture reste atomique. |
| Reproductibilité | Même scène, commit, seed, taille et adapter ; environnement manquant ou incohérent. | Bundle réutilisable et comparaison non ambiguë ; les métadonnées sont obligatoires. |
| CLI | Catalogue complet, filtre d'ID, échec d'initialisation, échec de close/dispose et tentative de promotion invalide. | Aucun artefact partiel ne survit et l'erreur expose une cause actionnable. |

## Artefacts requis

Un rendu contient CPU/reference, GPU, diff, stats, route, diagnostics,
environnement, manifest, verdict et promotion. Un refus contient route,
diagnostics, environnement, manifest, verdict et promotion ; il n'a ni PNG de
succès ni statistiques présentées comme performance valide.

## Dépendances et sortie

Ce lot est la seule dépendance dure de tous les autres briefs. Il est intégré
seul, car `GpuEvidenceCatalog.kt` et les programmes de scène sont des points de
conflit. Sa sortie est un harness capable de rejeter une fausse preuve, plus la
capture hardware qui réconcilie le catalogue courant avec les artefacts promus.

## Vérification

```bash
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
