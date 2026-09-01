# W01 — Géométrie immuable et format `Picture` v8

Date de génération : 2026-09-01  
Commit source : `031834d22663488951e0e8fedfbf81ad5eac2f98`

## État des gates

| Gate | État | Preuve |
| --- | --- | --- |
| W0 baseline opérationnelle | Atteinte | La baseline W00 publiée reste la référence opérationnelle. |
| W00 gate stricte | **NON ATTEINTE** | `jpg-color-cube` demeure en quarantaine `quarantined-resource-limit`; il ne relève ni de `font` ni de `codec`. |
| W1 immutabilité/Picture | Atteinte | Les mutations après enregistrement et après exposition d'un snapshot ne modifient ni `Surface.snapshotOps()`, ni une `Picture`, ni son round-trip; la fixture littérale v7 est décodée par `Picture.fromByteArray`. |

## Format Picture

- Le writer émet exclusivement le format 8.
- Les ids v8 des enums sérialisés sont explicites et stables; ils ne dépendent
  pas de l'ordre de déclaration.
- Le reader choisit explicitement les formats 1–7 legacy ou le format 8.
  Les formats legacy gardent leurs ordinals et leurs dispositions historiques.
- La fixture `kanvas/src/test/resources/picture/format-7-path.base64` a été
  capturée avec le writer v7 avant la migration. Elle contient un `DrawPath`
  8×8 avec `Move`, `Line`, `Quad`, `Cubic`, `ArcTo`, `Close`, un fill
  `INVERSE_EVEN_ODD` et un `PathEffect.Path1D`.

## Vérifications exécutées

| Commande | Résultat |
| --- | --- |
| `rtk ./gradlew :kanvas:test --tests '*PictureTest*' --tests '*RecordedGeometrySnapshotTest*'` | Succès : round-trip v8, fixture v7, compatibilité v1–7 et immutabilité ciblée. |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest` | Succès. |
| `rtk ./gradlew :kanvas:test` | Échec global hors scope : 53 échecs GPU/Image observés, dont les cas `DrawPoint` d'advanced blends, des assertions GPU et `ImageTest.ColorType enum values`. Les tests Picture et d'immuabilité ciblés sont verts. |

Les 53 échecs globaux sont publiés tels quels; cette tâche ne les masque pas et
ne modifie ni GMs, ni renders, ni dashboard, ni scores.
