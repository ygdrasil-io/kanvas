# W137 — RadialGradient sous CTM bornée

## Périmètre

Cette vague étend la route native `CorePrimitive` d'un `RadialGradient` clamp à
deux stops sous une CTM affine bornée. Le cas prouvé est un stroke mono-segment
diagonal `SQUARE + MITER`, sans anti-aliasing, sous un clip triangle Winding.
La CTM utilisée est une échelle uniforme positive `1.25` suivie d'une
translation `(2, -1)`.

La matrice locale radiale bornée de W136 reste admise lorsque la CTM est
identité. Avec une CTM non identité, W137 exige une matrice locale identité :
le semantic builder ne rebasing alors que le centre et le rayon selon la CTM.

## Implémentation

- le semantic builder rebased le centre radial en coordonnées device ;
- le rayon est recalculé depuis le point centre + rayon, ce qui conserve
  exactement l'échelle uniforme et les rotations quart de tour admises ;
- la même frontière de transformation est utilisée pour l'analyse de route ;
- la CTM et la `localMatrix` restent séparées : cette vague exige une
  `localMatrix` identité, tandis que W136 couvre la matrice locale avec CTM
  identité ;
- les autres matériaux et la voie générique `FillRect` ne sont pas élargis.

## Preuve native

- route : `native.path_stroke.stencil_cover` ;
- préparation : `Recorded` ;
- clip Winding : producteur `IncrementWrap` / `DecrementWrap`, consumer
  `NotEqual` ;
- exécution WebGPU headless : `Succeeded` ;
- exactement un submit et une readback copy ;
- oracle CPU indépendant : centre des pixels, triangle de clip, stroke carré
  transformé (demi-largeur 2.5 px), distance radiale au centre device `(22,19)`
  avec rayon device `20`, interpolation linear-light puis encodage sRGB ;
- comparaison RGBA8 à une tolérance d'un LSB.

## Frontière contractuelle

Les transformations avec skew, perspective, singularité, rotation générale ou
échelle non uniforme restent refusées par la politique existante. La voie
radiale générique hors `CorePrimitive` conserve également sa limite actuelle.

## Vérification

Tests ciblés :

```text
./gradlew :kanvas:test --no-daemon --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest.translated radial draw reaches the hard path clip stroke stencil route' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.translated radial draw square miter stroke under winding clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, 2 tests, 0 échec.

La suite forcée des quatre classes impactées est également verte : 70 tests
de rendu natif, 141 tests de planification, 58 tests du mapper et 16 tests du
semantic builder, sans échec.

## Limites

Pas de `FillRect` radial générique, de skew/perspective, de rotation générale,
d'échelle non uniforme, de gradients à plus de deux stops, d'autres tile modes,
de chemins multi-segments, de caps round ou de MSAA.

## Correctif de revue W137

Le prédicat radial conserve désormais les `localMatrix` bornées de W136 lorsque
la CTM est identité. Pour une CTM non identité, seule la matrice locale identité
reste admise : le semantic builder ne rebasing actuellement que le centre et le
rayon selon la CTM. Aucun élargissement de `FillRect` ou des routes génériques
n'est introduit.

Vérifications ciblées après correction :

```text
./gradlew --no-daemon --no-parallel :kanvas:test \
  --tests '*translated local radial matrix square miter stroke under winding clip renders natively' \
  --tests '*translated radial draw square miter stroke under winding clip renders natively' \
  --tests '*general radial draw rotation remains refused before native preparation'
```

Résultat effectivement exécuté : `BUILD SUCCESSFUL`, 3 tests passés.
