# WIP 20 — Paths, strokes, coverage et clips

> Document temporaire. Ce lot ne remplace pas les limites de complexité
> décidées par le code ; il les rend observables et testées.

## Objectif du groupe

Élargir la couverture de géométrie au-delà du rectangle sans accepter de
rasterisation approximative. Chaque famille doit prouver son coverage CPU/GPU
ou garder un refus diagnostiqué, notamment pour AA, stroke et clips complexes.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| Path/coverage | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/PathTessellator.kt`, `GeometryContracts.kt` |
| Clips | `.../clips/ClipContracts.kt`, `GPUClipCoverageContracts.kt`, `GPUClipExecutionPlan.kt`, `.../execution/GPUCorePrimitivePathStencilNativeRoute.kt` |
| Stencil/mask | `.../passes/GPUCorePrimitiveCoverageMaskPreparedRoute.kt`, `.../execution/GPUCorePrimitiveCoverageMaskPreparedExecutionRoute.kt` |
| Strokes | `.../stroke/AdvancedStrokePlan.kt`, `PathEffectChain.kt`, `.../wgsl/StrokeSnippet.kt` |
| API | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt` (`drawPath`, `clipPath`, `clipRRect`) |

## Matrice de scénarios

| Sous-famille | Scènes et oracles | Limites/refus contractuels |
| --- | --- | --- |
| Fills path | Ligne, quadratique, cubique, contours multiples, fermeture implicite, winding/even-odd, convexité et auto-intersection. | Nombre de verbes/edges, NaN/Inf, fill type absent, perspective et auto-intersection non prise en charge. |
| Coverage AA | Arêtes horizontales/verticales/diagonales, positions à demi-pixel, petite primitive, primitive proche du bord et superposition. | Pas d'AA prétendu sans coverage mesurable ; la route sans AA reste distinguée de la route AA. |
| Strokes | Width aux bornes, butt/round/square caps, miter/round/bevel joins, miter limit, dash phase et hairline. | Path effect, dash trop long, largeur hors borne, transform ou material incompatible refusés avant soumission. |
| `clipPath` / `clipRRect` | Intersection, clips imbriqués, clip AA, clip transformé, clip vide, rrect avec rayons distincts. | Difference/inverse, perspective, profondeur, stencil/mask trop grand : code de refus et compteur de ressources nul ou borné. |
| Interactions | Path sous clip, stroke sous clip, path translucide, transform + clip + restore et sentinelle post-restore. | Aucun état de stencil/mask ne fuit dans la primitive suivante. |

## Preuves à exiger

Les tests unitaires vérifient tessellation, winding, coverage et calcul de
bounds. Les scènes `Surface` rendent chaque cas supporté, comparent une image
de référence adaptée à la famille, et exposent route stencil/mask, nombre de
passes, bytes d'intermédiaires et fallback. Une référence Skia est requise dès
qu'un cas est promu comme fidélité Skia ; l'oracle CPU Kanvas seul n'est qu'un
contrôle interne.

## Découpage d'intégration

Les tests de refus et les oracles peuvent être préparés en parallèle avec les
lots 30, 40, 50 et 60. Les captures de rendu attendent les garanties state/clip
rect du lot 10. Ne mélanger qu'une route stencil/mask à la fois pour garder les
diffs attribuables.

## Vérification

```bash
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Path*' --tests '*Clip*' --tests '*Stroke*'
./gradlew :integration-tests:gpu-evidence:test
```
