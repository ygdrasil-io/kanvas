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
  et produit désormais `source-inventory.json` (615 entrées : 608 GMs
  instanciables et 7 providers non chargeables; 8 616 lignes JSON). Le parsing strict
  reste disponible pour les tests de contrat.
- Une tentative Surface unique distingue `attempted`, `renderSucceeded`,
  `terminalFailure` et `renderAvailable`; TEXT/BLOCKING portent des diagnostics
  de policy stables. L’initialisation runtime-effect et le `finally` de dispose
  sont alignés sur le runner.
- `referenceAvailable` reste basé sur le fichier; les statuts untrustable sont
  conservés par la source GM et signalés comme réserve de validation visuelle.

Vérifications du round :

- `./gradlew --no-daemon :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmInventoryTest --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest` — **SUCCESSFUL**.
- `./gradlew --no-daemon :integration-tests:skia:generateSkiaGmInventory -Pgm.inventoryOutput=/Users/chaos/.codex/worktrees/1540/kanvas/reports/gpu-renderer/evidence/gm-inventory/source-inventory.json` — **SUCCESSFUL**, artefact de 615 entrées (8 616 lignes JSON).

## Round 2 — corrections de re-review

L’artefact expose maintenant `scoreAudit` avec `orphanCount=136` et la liste
triée des lignes orphelines (dont `CubicStroke`), tout en conservant le
parsing strict qui échoue sur ces lignes hors mode d’audit. Les références ont
un statut explicite `trusted`, `missing` ou `untrustable`.

La capture d’inventaire effectue une seule construction/tentative `Surface`;
les compteurs `attempted`, `renderSucceeded`, `terminalFailure` et
`renderAvailable` sont distincts. Les exclusions sont non tentées et publient
leur diagnostic stable dans `firstDiagnostic`. Les providers non chargeables
restent des lignes `provider-unloadable`, sans être confondus avec des GMs.

Tests round 2 :

- `./gradlew --no-daemon :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmInventoryTest --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest` — **SUCCESSFUL**.
- Génération réelle Gradle — **SUCCESSFUL**, 615 entrées totales (608 GMs
  instanciables + 7 providers non chargeables), 8 616 lignes JSON et audit de
  136 scores orphelins.

## Round 3 — corrections de re-review

La capture borne désormais explicitement la phase de construction GM et la
phase `Surface.render()` : `terminalFailure` n’est vrai que pour la seconde,
tandis qu’un échec de setup est `route=setup-failure`. Les tests couvrent les
scores absents, l’audit trié, les statuts de référence et l’export déterministe
avec caractères de contrôle. Les WIP décrivent l’audit/rebaseline séparé des
scores orphelins sans suppression silencieuse.

Vérifications : tests ciblés **SUCCESSFUL**; génération Gradle réelle
**SUCCESSFUL**, artefact de 615 entrées (8 616 lignes JSON), 136 scores
orphelins audités.

## Round 4 — invariants terminal/setup et tests de frontière

`terminalFailure` est maintenant un invariant réservé au seul échec de
`Surface.render()`: il exige une setup phase réussie et une tentative de rendu
unique. Les providers non chargeables sont des échecs de setup explicites
(`setupState=FAILED` dans le modèle), `attempted=false`,
`terminalFailure=false`, avec `route=provider-unloadable` et le premier
diagnostic de chargement. Dans le JSON, la route et `firstDiagnostic` restent
le contrat machine-readable stable; l’artefact contient donc 615 entrées et
8 616 lignes JSON, sans ambiguïté avec un nombre de lignes.

La capture borne toute la setup phase — construction `Surface`, canvas,
callback `onOnceBeforeDraw` et `draw` — avant l’unique appel à
`Surface.render()`. Un échec setup conserve la ligne avec
`route=setup-failure`, le diagnostic setup et sans tentative de rendu; un
échec `Surface.render()` utilise `route=render-failure` et le seul état
terminal.

Les tests couvrent les défaillances constructeur et provider, le refus réel
`Surface.render()` avant hardware, l’absence de second appel render, les
références `trusted`/`missing`/`untrustable`, l’audit de plusieurs orphelins
triés et le cas strict, ainsi que l’export JSON byte-for-byte.

Vérifications round 4 :

- `./gradlew --no-daemon :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmInventoryTest --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest` — **SUCCESSFUL**, 11 tests d’inventaire et 5 tests de registry.
- La tâche Gradle réelle `generateSkiaGmInventory` a atteint l’écriture de
  l’artefact sans besoin de hardware; dans cette session, sa fermeture native
  est restée pendante après l’écriture et a été bornée. L’artefact régénéré,
  puis reformaté mécaniquement selon le sérialiseur stable, contient 8 616
  lignes et les 7 lignes `provider-unloadable` avec
  `terminalFailure=false`.
