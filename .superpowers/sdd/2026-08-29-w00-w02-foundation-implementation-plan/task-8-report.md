# Task 8 — writer `Picture` v8 stable, reader legacy v1–7

Date : 2026-09-01  
Base : `031834d22663488951e0e8fedfbf81ad5eac2f98`

## Résultat

Le writer `Picture` produit le format 8 uniquement. Les discriminants enum du
format v8 passent par des tables `when` explicites dans `PictureWireV8.kt`; ils
ne dépendent pas des `ordinal`. Le reader répartit explicitement les versions
1–7 vers le decoder legacy et la version 8 vers le decoder v8. Les versions
inconnues restent invalides (`null`).

## Provenance de la fixture v7

Avant toute modification du writer, une `Picture` 8×8 contenant un `DrawPath`
avec `Move`, `Line`, `Quad`, `Cubic`, `ArcTo`, `Close`, fill
`INVERSE_EVEN_ODD` et `PathEffect.Path1D` a été encodée par le writer v7 de la
base. La Base64 produite a été recopiée littéralement dans
`kanvas/src/test/resources/picture/format-7-path.base64`. Le test la charge et
la décode exclusivement via l'API publique `Picture.fromByteArray`, puis
compare les propriétés reconstruites.

## TDD

1. La fixture v7 a été capturée et son test de décodage a été écrit.
2. L'attente `writer emits version 8 pictures` a été ajoutée avant le writer
   v8. Le run RED a échoué exactement parce que le header contenait 7; la
   fixture v7 passait simultanément.
3. Après les tables stables et le dispatch de lecture, les tests Picture sont
   verts. Le test de round-trip des enums publics sérialisés a aussi été
   mutation-testé : changer temporairement l'id v8 de
   `INVERSE_EVEN_ODD` vers `EVEN_ODD` l'a fait échouer, puis la table correcte
   a été restaurée et le test est redevenu vert.

## Fichiers

- `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`
- `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureWireV8.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`
- `kanvas/src/test/resources/picture/format-7-path.base64`
- `refactor/waves/W01-immutable-geometry/status.md`
- `refactor/README.md`

## Claims de statut

- W0 est opérationnelle, mais sa gate stricte demeure **NON ATTEINTE** à cause
  de la quarantaine `jpg-color-cube` établie par W00.
- W1 est prouvée pour les conditions de cette tâche : les tests vérifient les
  mutations après enregistrement, les snapshots d'opérations, l'immuabilité de
  `Picture` et son round-trip, ainsi que la lecture de la fixture v7.

## Vérifications

- `rtk ./gradlew :kanvas:test --tests '*PictureTest*' --tests '*RecordedGeometrySnapshotTest*'` : succès.
- `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest` : succès.
- `rtk ./gradlew :kanvas:test` : 53 échecs GPU/Image hors scope observés.
  Ils sont laissés visibles; les tests Picture et d'immuabilité ciblés passent.

## Self-review et concerns

- Aucun fichier `font` ou codec, aucun GM, render, dashboard ou score n'a été
  modifié.
- La compatibilité v7 est établie par une fixture indépendante du nouveau
  writer; les fixtures v1/v2 et les cas legacy v1–6 existants restent couverts.
- La suite globale ne peut pas servir de gate verte tant que ses 53 échecs
  préexistants GPU/Image ne sont pas résolus; ils ne sont pas attribués à cette
  tâche sans une baseline plus fine.
