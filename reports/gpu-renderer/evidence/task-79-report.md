# W103 — stroke diagonal sous clip path EvenOdd natif

## Objectif

Étendre la preuve W102 au cas d’un clip path `EvenOdd` composé de deux
rectangles (coque extérieure et trou intérieur), avec le même stroke
diagonal mono-segment butt/miter.

## Scénario vérifié

La cible offscreen est 32×32, anti-aliasing désactivé, avec le stroke
`(5.25,8.25)->(21.25,20.25)`, largeur `4`, et un clip `INTERSECT` composé de
la coque `(3.25,3.25)-(28.75,28.75)` et du trou
`(10.25,10.25)-(21.75,21.75)`. Le clip est explicitement `EvenOdd` et non
inverse.

Le test vérifie la route d’analyse `native.path_stroke.stencil_cover`, les
opérations producer `Invert`/`Invert`, la comparaison consumer `NotEqual`, le
fill rule `EvenOdd`, puis une préparation native, un submit et un readback.
Le readback RGBA complet est comparé à un oracle CPU indépendant : centres de
pixels dans la coque XOR le trou, puis distance au segment avec butt/miter.

Commande validée :

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.diagonal butt miter stroke under even odd path clip renders natively'
```

Résultat : `PASSED`, `BUILD SUCCESSFUL`, exactement un submit natif et un
readback.

## Conclusion et limites

La composition EvenOdd du clip fonctionne avec le consumer direct de stroke
introduit dans W102 ; aucune modification de production n’a été nécessaire.
Cette preuve ne couvre pas encore l’EvenOdd inverse (`Equal`), les caps/join
round ou square, les dash, l’anti-aliasing, les transformations non
couvertes, ni les strokes multi-contours. Aucun seuil, PNG ou
`gpu-renderer-scenes` n’a été modifié.
