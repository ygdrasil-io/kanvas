# WIP 10 — État Canvas et géométrie de base

> Document temporaire. Les décisions de support proviennent du code et des
> résultats exécutés, jamais de ce brief.

## Objectif du groupe

Prouver que la route publique `Surface` conserve exactement l'état Canvas et
rend les primitives rectangulaires déjà abaissables sans fallback CPU. Chaque
combinaison hors périmètre est conservée comme refus stable.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| API publique | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt` |
| Préparation/exécution | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`, `.../execution/GPUWgpu4kSolidRectSessionCache.kt`, `GPUWgpu4kCorePrimitivePipelineDescriptor.kt` |
| Stroke rect | `.../geometry/GPUAxisAlignedStrokeRectLowerer.kt` |
| Oracles/catalogue | `integration-tests/gpu-evidence/.../catalog/GpuEvidenceCatalog.kt`, `.../oracle/SurfaceSrgbOracleMath.kt`, `SurfaceSrgbSrcOverCpuOracle.kt` |

## Matrice de scénarios

| Sous-famille | Scènes rendables à viser | Limites/refus à fixer |
| --- | --- | --- |
| `drawRect` | Rectangles opaques et alpha partiel, bords négatifs, hors surface, chevauchement de trois draws, coordonnées entières et fractionnaires. | Rectangle vide, NaN/Inf, transform non représentable ou budget dépassé ; aucune image partielle. |
| `drawRRect` / `drawDRRect` | Rayons uniformes et par coin, rayons égaux aux demi-dimensions, rrect inclus et trou DRRect. | Rayons/path AA/transform non supportés doivent exposer le refus du lowerer. |
| État/matrices | `save`/`restore` imbriqués, `restoreToCount`, translate, scale, rotate avec pivot, skew, concat, set/reset matrix. | `restoreToCount` hors plage, perspective ou matrice non finie : état inchangé ou refus explicite selon le code. |
| Clip rect | Intersections, clip vide, clip transformé, clip imbriqué, alpha AA activé/désactivé. | Clip impossible, AA/transform sans route native, profondeur dépassée ; zéro pixel et zéro submission de draw quand requis. |
| Clear/snapshot/annotation | `drawColor`, `clear`, `flushAndSnapshot`, annotation avant/après draw et replay. | Bounds vides/hors surface, annotation non visuelle, snapshot sans duplication ou réordonnancement. |
| Queries | `matrix`, `saveCount`, `localClipBounds`, `isClipEmpty`, `isClipRect`, `quickReject` rect/path. | Chaque query reflète l'état enregistré et n'ajoute pas de display op visuel. |

## Oracles et assertions

Créer des oracles isolés pour bounds, matrice affine, `SrcOver` prémultiplié,
coverage rect/rrect et clip. Les scénarios hardware vérifient pixels, diff,
draw/pipeline, route scissor/primitive, absence de fallback et restauration
d'état par un draw sentinelle placé après chaque `restore`.

## Fichiers de test attendus

Étendre les tests de catalogue et d'exécuteur existants ; ajouter des classes
nommées par responsabilité, par exemple `CanvasStateEvidenceTest`,
`RectGeometryEvidenceTest` et `CanvasStateCpuOracleTest`, plutôt qu'un test
fourre-tout. Les scènes publiques restent dans `KanvasScenePrograms.kt` avec
un ID unique et littéral.

## Dépendances et sortie

Commence après le lot 00. Ses contrats d'état et de clip rect sont une
prérequis pour les captures rendables de path/stroke/clip du lot 20. La sortie
est une preuve par scénario ou un refus stable, plus des artefacts promus pour
les scénarios déjà rendables.

## Vérification

```bash
./gradlew :integration-tests:gpu-evidence:test --tests '*CanvasState*' --tests '*Rect*'
./gradlew :integration-tests:gpu-evidence:test
```
