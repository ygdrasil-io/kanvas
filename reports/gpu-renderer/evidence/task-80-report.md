# W104 — stroke diagonal sous clip path inverse Winding natif

## Objectif

Vérifier le consumer direct du stroke butt/miter sous un hard path clip
`INVERSE_WINDING + INTERSECT`, qui doit conserver la comparaison stencil
`Equal` et rendre la partie située à l’extérieur du clip.

## Scénario vérifié

La cible offscreen est 32×32, anti-aliasing désactivé, avec le segment
`(5.25,8.25)->(21.25,20.25)`, largeur `4`, et un triangle clip
`(7.25,6.25)-(30.25,6.25)-(7.25,29.25)` marqué `INVERSE_WINDING`.

Le test vérifie la route d’analyse `native.path_stroke.stencil_cover`, les
opérations producer Winding `IncrementWrap`/`DecrementWrap`, la comparaison
consumer `Equal`, ainsi que `fillRule=Winding` et `inverseFill=true` dans la
géométrie du clip. La préparation native est suivie d’un submit et d’un
readback. Le résultat RGBA complet est comparé à un oracle CPU indépendant
fondé sur l’appartenance barycentrique inversée au triangle et la distance au
segment.

Commande validée :

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.diagonal butt miter stroke under inverse winding path clip renders natively'
```

Résultat : `PASSED`, `BUILD SUCCESSFUL`, exactement un submit natif et un
readback. La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a
également été relancée avec succès.

## Conclusion et limites

La production supportait déjà ce cas : aucune modification de code n’a été
nécessaire. La preuve confirme que le consumer de stroke direct compose bien
avec le clip inverse via `Equal`. Les caps/join round ou square, les dash,
l’anti-aliasing, les transformations non couvertes et les strokes
multi-contours restent hors périmètre.
