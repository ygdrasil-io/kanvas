# W109 — cap square sous clip path Winding natif

## Objectif

Faire passer en rendu natif le cas borné d’un stroke mono-segment `square/miter`
sous un clip path `Winding + INTERSECT`, qui était auparavant refusé par la
composition du clip stencil.

## Correction

L’outline device déjà calculé pour un segment square est maintenant admis dans
la même forme `DirectTriangles` canonique que le butt : un contour, deux points
source, huit coordonnées et les indices `[0,1,2,0,2,3]`. Les contrats de
validation et de préparation reconnaissent `SingleSegmentSquareV1`; les autres
caps, les dashes, les contours multiples et les joins non-miter restent refusés.

## Preuve native

Fixture 32×32, AA désactivé, segment diagonal `(5.25,8.25)->(21.25,20.25)`,
largeur 4, cap square, join miter, sous un triangle Winding en `INTERSECT`.
Le test vérifie la route `native.path_stroke.stencil_cover`, le plan
`StencilCoverage`, les opérations `IncrementWrap/DecrementWrap`, le consumer
`NotEqual`, la préparation native, un submit et un readback. Le buffer RGBA
complet est comparé à un oracle CPU indépendant qui étend le segment de la
demi-largeur avant de tester la distance au segment.

## Vérification

```text
rtk ./gradlew --no-daemon :kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.diagonal square cap stroke under winding path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS. La classe complète
`GPUFramePathApiInventoryNativeSmokeTest` a également été relancée avec succès
et `rtk git diff --check` est propre.

## Limites

La correction reste limitée au single-segment square/miter non-AA, avec clip
stencil 1x. Les caps round, les dashes, l’anti-aliasing, les transformations
non bornées et les strokes multi-contours restent hors contrat. Aucun PNG,
seuil ou `gpu-renderer-scenes` n’a été modifié.
