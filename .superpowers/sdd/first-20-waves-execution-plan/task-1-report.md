# Task 1 — W00 GM truth inventory

## Résultat

Implémentation d’un inventaire source-derived (dérivé du registry) dans
`integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventory.kt`.
Le builder expose famille, alias de référence, disponibilité de référence et
de rendu, score, nombre d’opérations, route et premier diagnostic. Le parsing
des scores est strict : doublons, lignes mal formées, valeurs non finies et
scores orphelins sont rejetés.

Le renderer fournit une observation d’inventaire via la route publique
`Surface`, sans fallback. La tâche Gradle `generateSkiaGmInventory` et
l’export JSON déterministe permettent la régénération; les GMs `TEXT` et
`BLOCKING` sont conservés dans l’inventaire mais ne sont pas rejoués par la
commande par défaut.

## Fichiers modifiés

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventory.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventoryTest.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt`
- `integration-tests/skia/build.gradle.kts`

Les modifications WIP préexistantes ont été laissées intactes.

## Vérification

- `./gradlew --no-daemon :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmInventoryTest --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest` — **SUCCESSFUL**, tests ciblés passés.
- `./gradlew --no-daemon :integration-tests:skia:test` — **FAILED** sur l’état GPU préexistant : 709 tests, 452 failures, 40 skipped; les échecs sont principalement des `GPUPreparedSurfaceTerminalException` sur des routes déjà non supportées.
- `./gradlew --no-daemon :integration-tests:skia:generateSkiaGmInventory -Pgm.inventoryOutput=/tmp/kanvas-gm-inventory.json` — **FAILED volontairement** avec `Orphan score row: CubicStroke`. Cette sortie démontre le garde-fou demandé : le fichier de scores historique contient des lignes absentes du service registry et n’est pas silencieusement nettoyé.

## Réserves

La régénération complète reste bloquée tant que les scores orphelins du
fichier `integration-tests/skia/test-similarity-scores.properties` ne sont pas
réconciliés avec le registry; les supprimer ou réenregistrer les GMs dépasse
W00. Aucun claim de support n’est ajouté et aucune route renderer n’est
modifiée.

L’artefact historique de référence reste sous
`reports/gpu-renderer/evidence/gm-inventory/`; le nouveau générateur écrit par
défaut `source-inventory.json` dans ce même répertoire quand le contrat de
scores est satisfait.

## Round 1 — corrections de review

- `SkiaGmRegistry.entries()` conserve chaque provider et son diagnostic de
  chargement/instanciation; les providers non chargeables deviennent des lignes
  `provider-unloadable` dans l’artefact.
- La commande nominale réconcilie les scores orphelins en mode d’audit explicite
  et produit désormais `source-inventory.json` (615 lignes). Le parsing strict
  reste disponible pour les tests de contrat.
- Une tentative Surface unique distingue `attempted`, `renderSucceeded`,
  `terminalFailure` et `renderAvailable`; TEXT/BLOCKING portent des diagnostics
  de policy stables. L’initialisation runtime-effect et le `finally` de dispose
  sont alignés sur le runner.
- `referenceAvailable` reste basé sur le fichier; les statuts untrustable sont
  conservés par la source GM et signalés comme réserve de validation visuelle.

Vérifications du round :

- `./gradlew --no-daemon :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmInventoryTest --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest` — **SUCCESSFUL**.
- `./gradlew --no-daemon :integration-tests:skia:generateSkiaGmInventory -Pgm.inventoryOutput=/Users/chaos/.codex/worktrees/1540/kanvas/reports/gpu-renderer/evidence/gm-inventory/source-inventory.json` — **SUCCESSFUL**, artefact 615 lignes.
