# WIP 10 — état Canvas, transforms et primitives

> Brief d'exécution de `W10` à `W12`. Toute combinaison annoncée supportée doit
> traverser la route publique `Surface`.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| API et état | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/ClipStack.kt` |
| Surface GPU | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt` |
| Stroke rect | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedStrokeRectLowerer.kt` |
| Evidence | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/KanvasSurfaceProgram.kt`, `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SurfaceSrgbOracleMath.kt` |

## W10 — état Canvas

- [ ] Tester `save`, `restore`, `restoreToCount` et `saveCount` avec deux niveaux
      et un draw sentinelle après chaque restauration.
- [ ] Tester `matrix`, `localClipBounds`, `isClipEmpty`, `isClipRect` et
      `quickReject(RectF32|Path)` sans ajouter d'opération visuelle.
- [ ] Tester clip vide, clip hors surface et stack de clips rectangulaires.
- [ ] Refuser ou préserver l'état, selon le contrat du code, pour restore hors
      plage et profondeur de stack dépassée.
- [ ] Prouver qu'un état refusé ne fuit pas vers le draw suivant.

## W11 — transforms affines

- [ ] Ajouter les probes translation, scale uniforme/non uniforme, rotation
      avec/sans pivot, skew, concat, setMatrix et resetMatrix.
- [ ] Exercer chaque transform sur rect, RRect, path et clip avant de généraliser
      la route.
- [ ] Vérifier bounds, orientation, winding, stroke width et local coordinates.
- [ ] Refuser matrices non finies et singulières avec état inchangé.
- [ ] Conserver la perspective générale comme `OUT_OF_SCOPE` avec test stable.

## W12 — primitives de base

- [ ] Tester `drawColor`, `clear`, `drawPoint`, `drawPoints`, `drawRect`,
      `drawRRect` et `drawDRRect` avec couleurs opaques/translucides.
- [ ] Couvrir coordonnées entières/fractionnaires, bounds négatifs, primitives
      vides, primitives hors surface et chevauchements.
- [ ] Tester rayons uniformes, par coin, elliptiques et inner/outer DRRect.
- [ ] Tester `drawAnnotation` comme opération non visuelle.
- [ ] Tester `flushAndSnapshot` sans duplication, réordonnancement ou fuite de
      ressources.
- [ ] Ajouter un oracle CPU séparé pour chaque sémantique qui ne peut pas être
      exprimée proprement par l'oracle rect existant.

## Sortie

Les trois vagues sont fermées quand état, transform et primitive ont chacun un
cas rendu, leurs limites négatives, une preuve post-restore et aucune mutation
d'état après refus.

## Vérification

```bash
./gradlew :kanvas:test
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*CanvasState*' --tests '*Rect*' --tests '*Transform*'
./gradlew :integration-tests:gpu-evidence:test
```
