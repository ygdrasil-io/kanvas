# WIP 20 — paths, coverage, clips et strokes

> Brief d'exécution de `W20` à `W26`. Les routes bornées déjà présentes dans le
> code servent de point de départ; leur simple présence ne généralise pas le
> support à de nouvelles géométries.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| Geometry | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/` |
| Clip contracts | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/ClipContracts.kt`, `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/GPUClipCoverageContracts.kt`, `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/GPUClipExecutionPlan.kt` |
| Clip mapping | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipMapper.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoveragePlanner.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt` |
| Strokes | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUStroke.kt`, `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokePlan.kt`, `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/PathEffectChain.kt` |
| Evidence | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SurfaceSrgbPathFillCpuOracle.kt`, `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SurfaceSrgbClipPathCpuOracle.kt` |

## W20 — path curves

- [ ] Ajouter des cas quadratique, cubique, conique abaissable, oval et circle.
- [ ] Tester fermeture explicite/implicite, segments dégénérés et bounds serrés.
- [ ] Définir des budgets déterministes de segments, fan et mémoire.
- [ ] Comparer un oracle CPU indépendant aux pixels GPU.
- [ ] Refuser avant submission toute courbe dépassant les budgets.

## W21 — path topology

- [ ] Tester plusieurs contours avec orientations identiques et opposées.
- [ ] Tester winding, even-odd et leurs variantes inverse.
- [ ] Tester auto-intersections bornées et sommets partagés.
- [ ] Vérifier que transform négative/réflexion conserve la sémantique de fill.
- [ ] Fixer le diagnostic des topologies non déterministes ou hors budget.

## W22 — coverage anti-aliased

- [ ] Écrire un oracle de coverage pour arêtes entières, demi-pixel et
      fractionnaires.
- [ ] Tester rect, RRect, triangle, courbe, petite primitive et chevauchement.
- [ ] Vérifier alpha prémultiplié et ordre de composition.
- [ ] Prouver le comportement sous clip et transform affine.
- [ ] Refuser les combinaisons dont le format ou le sample count ne garantit pas
      l'exactness annoncée.

## W23 — clip shapes

- [ ] Tester `clipRRect` et `clipPath` avec consommateurs rect, RRect et path.
- [ ] Tester clips polygonaux et courbes sous transform affine.
- [ ] Vérifier le choix scissor, analytic, stencil ou intermediate dans les
      diagnostics de route.
- [ ] Vérifier que les bounds du clip ne sont ni élargis ni réutilisés après
      restore.

## W24 — clip composition

- [ ] Tester `INTERSECT`, `DIFFERENCE`, inverse fill et clip vide.
- [ ] Tester deux puis trois clips imbriqués avec save/restore.
- [ ] Tester clip path + RRect + transform dans les deux ordres utiles.
- [ ] Vérifier depth, edge-fan et intermediate budgets.
- [ ] Ajouter un refus stable au premier dépassement sans draw partiel.

## W25 — stroke geometry

- [ ] Tester stroke de rect, RRect et path pour butt/round/square caps.
- [ ] Tester miter/round/bevel joins et limites de miter.
- [ ] Tester largeurs entières, fractionnaires, hairline et zéro selon contrat.
- [ ] Tester transform affine, clip, gradient déjà supporté et AA.
- [ ] Fixer un budget d'expansion et refuser avant allocation excessive.

## W26 — path effects

- [ ] Implémenter et prouver `Dash`, puis `Corner` et `Trim` sur une géométrie
      bornée.
- [ ] Implémenter ou refuser explicitement `Discrete`, `Path1D` et `Path2D` après
      un probe de leurs coûts et besoins de sampling.
- [ ] Tester la chaîne d'effets, l'ordre, les phases, les paramètres invalides et
      les transforms.
- [ ] Vérifier que le résultat abaissé rejoint la même route stroke/coverage.

## Sortie

Chaque vague sort séparément avec un cas rendu, un cas hors limite, un oracle,
une capture native et une promotion. Les refus GM historiques ne sont retirés
qu'après preuve de la route qui les remplace.

## Vérification

```bash
./gradlew :gpu-renderer:test
./gradlew :kanvas:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Path*' --tests '*Clip*' --tests '*Stroke*' --tests '*Coverage*'
./gradlew :integration-tests:skia:test --tests '*SurfaceRefusalEvidenceTest'
```
