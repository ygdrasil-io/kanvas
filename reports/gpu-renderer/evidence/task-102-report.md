# W126 — SweepGradient clamp + stroke square/miter sous clip Winding

## Objectif

Prouver côté GPU la combinaison d’un `SweepGradient` `clamp` à deux stops et
d’un stroke mono-segment `square/miter`, sous un clip path triangulaire
`Winding` avec `ClipOp.INTERSECT`.

Cette vague est test-only et réutilise le lane natif SweepGradient exact déjà
admis. Aucun changement de production n’est nécessaire.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `SQUARE`,
  join `MITER`, anti-aliasing désactivé.
- Le cap square est modélisé indépendamment par une extension de `2 px` aux
  deux extrémités du segment, puis un test de distance au segment étendu.
- Clip triangulaire : `(7.25,6.25)`, `(30.25,6.25)`,
  `(7.25,29.25)`, `ClipOp.INTERSECT`, anti-aliasing désactivé.
- Sweep gradient centré en `(16,16)`, angles `0..360`, deux stops rouge →
  bleu, interpolation sRGB linéaire, mode `clamp`.

L’oracle CPU indépendant parcourt les centres de pixels, vérifie leur présence
dans le triangle, leur distance au segment étendu, puis évalue le SweepGradient
avec `atan2` et une interpolation linear-light avant encodage sRGB. La
comparaison couvre l’intégralité du buffer RGBA avec une tolérance maximale de
1 LSB par canal pour la différence numérique CPU/GPU en `f32`.

## Preuve native

Le test
`clamp sweep gradient square miter stroke under winding path clip renders natively`
vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `Winding`, non-inverse ;
- opérations producteur `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `NotEqual`, correspondant à `INTERSECT` ;
- préparation `Recorded` ;
- résultat `Succeeded` ;
- exactement un submit et une copie de readback ;
- résultat RGBA conforme à l’oracle CPU à 1 LSB près.

## Vérification

Commande ciblée exécutée :

```text
./gradlew --no-daemon :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient square miter stroke under winding path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

## Limites

La preuve couvre uniquement le lane exact : deux stops, `clamp`, interpolation
sRGB, matrice locale identité, transformation de dessin identité, stroke
mono-segment direct `square/miter`, anti-aliasing désactivé, clip `Winding`
non-inverse et stencil 1x. Les caps `round`, chemins multi-segments, matrices
locales non identités et gradients à plus de deux stops restent hors contrat
et doivent conserver une politique de refus explicite.
