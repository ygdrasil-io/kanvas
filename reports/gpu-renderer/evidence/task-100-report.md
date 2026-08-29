# W124 — SweepGradient clamp + stroke sous clip EvenOdd avec trou

## Objectif

Prouver côté GPU la combinaison d’un `SweepGradient` `clamp` à deux stops et
d’un stroke mono-segment `butt/miter`, sous un clip path `EvenOdd` avec un
contour extérieur et un trou intérieur.

Cette vague est test-only : aucune modification de production n’est requise
au-delà du support SweepGradient déjà livré en W123.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Clip `EvenOdd` : rectangle extérieur
  `(3.25,3.25)-(28.75,28.75)` et trou intérieur
  `(10.25,10.25)-(21.75,21.75)`, `ClipOp.INTERSECT`, anti-aliasing désactivé.
- Sweep gradient centré en `(16,16)`, angles `0..360`, deux stops rouge →
  bleu, interpolation sRGB linéaire, mode `clamp`.

L’oracle CPU indépendant parcourt les centres de pixels, conserve la coque
`EvenOdd` (`inOuter XOR inInner`), teste la distance au segment, puis évalue
la teinte avec `atan2` et une interpolation linear-light avant encodage sRGB.
La comparaison RGBA complète autorise au plus 1 LSB par canal pour la
différence numérique attendue entre `atan2` CPU et l’évaluation GPU en `f32`.

## Preuve native

Le test
`clamp sweep gradient butt miter stroke under even odd hole clip renders natively`
vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `EvenOdd`, non-inverse ;
- opérations producteur `Invert` / `Invert` ;
- comparaison consommateur `NotEqual`, correspondant à `INTERSECT` ;
- préparation `Recorded` ;
- résultat `Succeeded` ;
- exactement un submit et une copie de readback ;
- résultat RGBA conforme à l’oracle CPU à 1 LSB près.

Le refus hors contrat à trois stops reste couvert par le test W123
`clamp three stop sweep gradient stroke under winding path clip remains refused`.

## Vérification

Commande ciblée exécutée :

```text
./gradlew --no-daemon :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient butt miter stroke under even odd hole clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

La classe complète sera relancée séparément pour éviter les exécutions Gradle
concurrentes.

## Limites

Cette preuve couvre uniquement le lane exact déjà admis : deux stops, `clamp`,
interpolation sRGB, matrice locale identité, transformation de dessin identité,
stroke mono-segment direct `butt/miter`, anti-aliasing désactivé, clip `EvenOdd`
non-inverse et échantillonnage stencil 1x. Les caps `round`, les chemins
multi-segments, les matrices locales non identités et les gradients à plus de
deux stops restent hors contrat et doivent conserver une politique de refus
explicite.
