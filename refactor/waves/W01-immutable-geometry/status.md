# W01 — Géométrie immuable et format `Picture` v8

Date de génération : 2026-09-02  
Commit source : `b575b5b74`

## État des gates

| Gate | État | Preuve |
| --- | --- | --- |
| W0 baseline opérationnelle | Atteinte | La baseline W00 publiée reste la référence opérationnelle. |
| W00 gate stricte | **NON ATTEINTE** | `jpg-color-cube` demeure en quarantaine `quarantined-resource-limit`; il ne relève ni de `font` ni de `codec`. |
| W1 immutabilité/Picture — périmètre fonctionnel | Implémenté et prouvé ciblé | Les snapshots copient en profondeur les images et effets mutables ; la copie itérative est résistante aux cycles et ses limites sont reportées à `SceneCaptureLimits`. Les mutations après enregistrement et après exposition d'un snapshot ne modifient ni `Surface.snapshotOps()`, ni une `Picture`, ni son round-trip ; la fixture littérale v7 est décodée par `Picture.fromByteArray`. Les `RuntimeEffect` lus depuis un `Picture` legacy sont construits détachés puis enregistrés transactionnellement dans un snapshot atomique du registry. |
| W1 gate stricte | **NON ATTEINTE / bloquée** | `:kanvas:test --rerun-tasks` reste rouge sur la baseline de 51 échecs GPU/Image hors scope (3 585 tests, 0 erreur) ; aucun nouveau test W1/W2 n'échoue. W1 ne peut pas être déclarée livrée globalement. |

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
| `rtk ./gradlew :kanvas:test --tests '*PictureTest*' --tests '*RuntimeEffectCompileTest*' --tests '*RecordedGeometrySnapshotTest*' --tests '*DisplayOpSceneAdapterTest*' --tests '*SceneRoundTripTest*' --tests '*SceneRecordingScopeTest*' --tests '*SurfaceSceneSnapshotTest*' --tests '*GPUFramePathApiInventoryTest*' --rerun-tasks` | Succès : tests ciblés couvrant le round-trip v8, les fixtures legacy, l'immuabilité profonde des images/effets, les graphes cycliques, l'enregistrement transactionnel des `RuntimeEffect` et les limites publiques de path. |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest` | Succès. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | Échec global hors scope : 3 585 tests, 51 échecs, 0 erreur ; les six classes de baseline GPU/Image sont exactement celles déjà connues. Les tests Picture, RuntimeEffect et d'immuabilité ciblés sont verts. |

Les 51 échecs globaux sont publiés tels quels; cette tâche ne les masque pas et
ne modifie ni GMs, ni renders, ni dashboard, ni scores.
