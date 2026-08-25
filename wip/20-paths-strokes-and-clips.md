# WIP 20 — Paths, strokes, coverage et clips

> Document temporaire. Ce lot ne remplace pas les limites de complexité
> décidées par le code ; il les rend observables et testées.

## Objectif du groupe

Élargir la couverture de géométrie au-delà du rectangle sans accepter de
rasterisation approximative. Chaque famille doit prouver son coverage CPU/GPU
ou garder un refus diagnostiqué, notamment pour AA, stroke et clips complexes.

## Preuve bornée actuelle

Le code, les tests et les artefacts générés/promus vérifiés font autorité pour
les affirmations ci-dessous ; ce WIP n'est qu'une vue dérivée. Trois fills path
solides non-AA sont actuellement prouvés par la route publique native
`kanvas.surface.render` et le stencil-cover WebGPU : `solid-triangle-path`
(1128 pixels), `solid-concave-path` (1920 pixels) et `even-odd-path-hole`
(1776 pixels). Les trois scènes sont 64×64, exactes à `100.0` de similarité,
avec zéro pixel différent et zéro écart de canal.

Ne sont pas revendiqués par cette preuve : l'AA, les strokes, les contours
curves (quadratiques ou cubiques), les inverse fills, les oval/circle et tous
les clips (`clipPath`, `clipRRect` ou interactions de clip).

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
| Fills path | `solid-triangle-path`, `solid-concave-path` et `even-odd-path-hole` : fills solides non-AA, `WINDING`/`EVEN_ODD`, fermeture implicite et oracle pixel-center. | Aucun autre contour ou fill path n'est revendiqué ; notamment lignes, courbes quadratiques/cubiques, auto-intersection et inverse fills. |
| Coverage AA | Aucun cas revendiqué. | Arêtes et positions AA, petites primitives et superpositions restent non revendiquées sans coverage mesurable. |
| Strokes | Aucun cas revendiqué. | Caps, joins, miter, dash, hairline et path effects restent non revendiqués. |
| `clipPath` / `clipRRect` | Aucun cas revendiqué. | Intersections, clips imbriqués/AA/transformés, clips vides et clips avec rayons distincts restent non revendiqués. |
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
