# W133 — SweepGradient transformé sous clip inverse Winding

## Périmètre

Cette vague ajoute une preuve native d’un stroke mono-segment diagonal `SQUARE + MITER`,
anti-aliasing désactivé, avec un `SweepGradient` clamp à deux stops. Le dessin utilise la
transformation uniforme positive `scale(1.5) + translate(2, 1)` et le clip est un triangle
`INVERSE_WINDING + INTERSECT` dans le même espace device.

## Correction runtime

La préparation refusait auparavant ce cas avec
`unsupported.core_primitive.material.path_stencil`, car le prédicat SweepGradient n’admettait
que la transformation identité. Le correctif borné :

- admet le SweepGradient uniquement pour le lane exact déjà éprouvé (2 stops, clamp, sRGB,
  matrice locale identité, stroke mono-segment butt/square + miter, AA off, stencil 1x) ;
- convertit le centre du sweep dans l’espace device pour une translation et une échelle
  uniforme positive ;
- refuse toujours les rotations, perspectives, matrices locales, caps round, géométries
  multi-segments et autres variantes non authentifiées.

Le clip inverse Winding + Intersect conserve l’extérieur du triangle. Son consumer stencil est
`Equal` (`inverseFill=true` et aucune inversion additionnelle de `Difference`), conformément à
la formule runtime `geometry.inverseFill xor consumerInverseFill`. `NotEqual` est réservé ici au
cas inverse + `Difference`.

## Preuves

- route : `native.path_stroke.stencil_cover` ;
- transform : `uniform-positive-scale-translate` ;
- producteur Winding : `IncrementWrap` / `DecrementWrap` ;
- consumer : `Equal` ;
- préparation : `Recorded` ;
- exécution native : `Succeeded` ;
- un submit et une readback copy ;
- oracle CPU indépendant en coordonnées device, incluant l’extension square de 1,5 px et le
  sweep `atan2` évalué autour du centre transformé, tolérance de 1 LSB.
- refus contractuel : une rotation exacte `right-angle-rotation` du SweepGradient reste refusée
  par `unsupported.core_primitive.material.path_stencil`, car le lane ne transforme pas encore
  les angles du sweep.

## Test exécuté

```text
./gradlew --no-daemon --no-parallel :kanvas:test \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient square miter stroke under scaled translated inverse winding clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test ciblé PASS.

Le test de frontière positif + refus a également passé avec :

```text
./gradlew --no-daemon --no-parallel :kanvas:test \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient square miter stroke under scaled translated inverse winding clip renders natively' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient transformed right angle stroke remains refused'
```

Résultat : `BUILD SUCCESSFUL`, les deux tests PASS.

## Limites

Cette preuve ne couvre pas les transformations affine avec rotation, les matrices locales du
shader, les caps round, les chemins multi-segments, ni les gradients de plus de deux stops.
