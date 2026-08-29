# W125 — SweepGradient clamp + stroke sous clip Winding inverse

## Objectif

Prouver côté GPU la combinaison d’un `SweepGradient` `clamp` à deux stops et
d’un stroke mono-segment `butt/miter`, sous un clip path triangulaire
`INVERSE_WINDING` avec `ClipOp.INTERSECT`.

Cette vague est test-only et réutilise le lane natif SweepGradient exact
introduit et borné par W123.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Clip triangulaire : `(7.25,6.25)`, `(30.25,6.25)`,
  `(7.25,29.25)`, `ClipOp.INTERSECT`, anti-aliasing désactivé.
- Fill rule : `INVERSE_WINDING` ; le rendu est donc conservé à l’extérieur
  du triangle.
- Sweep gradient centré en `(16,16)`, angles `0..360`, deux stops rouge →
  bleu, interpolation sRGB linéaire, mode `clamp`.

L’oracle CPU indépendant parcourt les centres de pixels, rejette ceux qui
sont dans le triangle, teste la distance au segment, puis évalue la teinte
avec `atan2` et une interpolation linear-light avant encodage sRGB. La
comparaison porte sur l’intégralité du buffer RGBA avec une tolérance maximale
de 1 LSB par canal pour la différence numérique CPU/GPU en `f32`.

## Preuve native

Le test
`clamp sweep gradient butt miter stroke under inverse winding clip renders natively`
vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `Winding`, `inverseFill=true` ;
- opérations producteur `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `Equal`, correspondant à `INTERSECT` avec clip
  inverse ;
- préparation `Recorded` ;
- résultat `Succeeded` ;
- exactement un submit et une copie de readback ;
- résultat RGBA conforme à l’oracle CPU à 1 LSB près.

## Vérification

Commande ciblée exécutée :

```text
./gradlew --no-daemon :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient butt miter stroke under inverse winding clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Vérification de régression de la classe complète :

```text
./gradlew :kanvas:test --no-daemon --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Résultat : `BUILD SUCCESSFUL`, 51 tests passés.

## Limites

La preuve couvre uniquement le lane exact : deux stops, `clamp`, interpolation
sRGB, matrice locale identité, transformation de dessin identité, stroke
mono-segment direct `butt/miter`, anti-aliasing désactivé, clip `Winding`
inverse et stencil 1x. Les caps `round`, chemins multi-segments, matrices
locales non identités et gradients à plus de deux stops restent hors contrat
et doivent conserver une politique de refus explicite.
