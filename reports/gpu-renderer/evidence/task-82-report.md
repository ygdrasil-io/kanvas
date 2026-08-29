# W106 — stroke diagonal transformé sous clip Winding natif

## Objectif

Étendre la preuve du consumer direct de stroke à une transformation affine
bornée : scale uniforme positif puis translation, appliquée au draw et
reflétée dans la géométrie device-space du clip.

## Scénario vérifié

La cible offscreen est 32×32, anti-aliasing désactivé. Le stroke local
`(4.125,4.125)->(12.125,8.625)`, largeur `2`, est transformé par
`translation(2,1) * scaling(1.5,1.5)`, donnant en device-space
`(8.1875,7.1875)->(20.1875,13.9375)`, largeur `3`.

Le clip Winding `INTERSECT` est retenu en device-space avec le triangle
`(6.875,5.875)-(24.875,5.875)-(6.875,23.875)` et la classe de transformation
`uniform-positive-scale-translate`.

Le test vérifie la route `native.path_stroke.stencil_cover`, les sommets
transformés du clip, les opérations producer `IncrementWrap`/`DecrementWrap`
et le consumer `NotEqual`. Le readback RGBA complet est comparé à un oracle
CPU indépendant calculé dans l’espace device (appartenance barycentrique au
triangle et distance au segment transformé). La préparation native est
suivie d’un submit et d’un readback uniques.

Commande validée :

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.scaled translated diagonal butt miter stroke under winding path clip renders natively'
```

Résultat : `PASSED`, `BUILD SUCCESSFUL`, un submit natif et un readback.
La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également
été relancée avec succès.

## Conclusion et limites

La capacité existait déjà : aucune modification de production n’a été
nécessaire. La preuve confirme que la route conserve la géométrie device-space
transformée et compose correctement le stroke avec le clip Winding.
Les transformations non uniformes, rotations, perspectives, caps/join round
ou square, dash, anti-aliasing et strokes multi-contours restent hors
périmètre.
