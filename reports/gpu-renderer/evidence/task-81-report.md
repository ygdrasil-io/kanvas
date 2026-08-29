# W105 — stroke diagonal sous clip path Winding Difference natif

## Objectif

Vérifier le consumer direct du stroke butt/miter sous un hard path clip
`WINDING + DIFFERENCE`, c’est-à-dire en conservant les pixels du stroke
situés hors du triangle.

## Scénario vérifié

La cible offscreen est 32×32, anti-aliasing désactivé, avec le segment
`(5.25,8.25)->(21.25,20.25)`, largeur `4`, et un triangle clip
`(7.25,6.25)-(30.25,6.25)-(7.25,29.25)` en fill rule Winding, non inverse.
L’opération de clip est `ClipOp.DIFFERENCE`.

Le test vérifie la route d’analyse `native.path_stroke.stencil_cover`, les
opérations producer `IncrementWrap`/`DecrementWrap`, la comparaison consumer
`Equal`, ainsi que `fillRule=Winding` et `inverseFill=false`. La préparation
native est suivie d’un submit et d’un readback. Le résultat RGBA complet est
comparé à un oracle CPU indépendant : centres de pixels hors du triangle et
distance au segment.

Commande validée :

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.diagonal butt miter stroke under winding difference path clip renders natively'
```

Résultat : `PASSED`, `BUILD SUCCESSFUL`, exactement un submit natif et un
readback. La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a
également été relancée avec succès.

## Conclusion et limites

La capacité existait déjà après la correction W102 : aucune modification de
production n’a été nécessaire. La preuve confirme que `DIFFERENCE` sélectionne
le consumer `Equal` tout en conservant la route native du stroke. Les caps/join
round ou square, les dash, l’anti-aliasing, les transformations non couvertes
et les strokes multi-contours restent hors périmètre.
