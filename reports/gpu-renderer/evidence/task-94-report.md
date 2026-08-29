# W118 — gradient clamp + stroke sous clip EvenOdd avec trou

## Objectif

Prouver la combinaison d’un gradient linéaire `clamp` à deux stops et d’un
stroke mono-segment `butt/miter` sous un clip path EvenOdd `INTERSECT` composé
d’un contour extérieur et d’un trou intérieur.

Aucune correction de production n’a été nécessaire : le contrat existant
admet déjà cette combinaison.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Clip EvenOdd : rectangle extérieur `(3.25,3.25)-(28.75,28.75)` et trou
  intérieur `(10.25,10.25)-(21.75,21.75)`, `ClipOp.INTERSECT`, anti-aliasing
  désactivé.
- Gradient linéaire horizontal `(0,0) -> (32,0)`, deux stops rouge → bleu,
  interpolation linear-light puis encodage sRGB, mode `clamp`.
- L’oracle CPU indépendant accepte les centres de pixels dans la coque
  EvenOdd (`inOuter XOR inInner`), vérifie leur distance au segment, puis
  évalue le gradient. La comparaison porte sur l’intégralité du buffer RGBA.

## Preuve native

Le test `clamp linear gradient butt miter stroke under even odd hole clip
renders natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `EvenOdd`, non-inverse ;
- opérations producteur `Invert` / `Invert` ;
- comparaison consommateur `NotEqual`, correspondant à `INTERSECT` ;
- préparation native `Recorded` ;
- résultat `Succeeded` après un submit et une readback copy ;
- égalité RGBA complète avec l’oracle CPU.

## Vérification

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient butt miter stroke under even odd hole clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Classe complète à relancer avant publication :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Contrôle final : `git diff --check`.

