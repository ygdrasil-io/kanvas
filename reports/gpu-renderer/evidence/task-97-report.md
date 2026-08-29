# W121 — gradient clamp + square stroke sous rotation de 90°

## Objectif

Prouver la combinaison du gradient linéaire `clamp` à deux stops et d’un stroke
mono-segment `square/miter` sous clip Winding `INTERSECT`, avec la rotation
affine exacte de 90° autour de `(16,16)`.

Aucune modification de production n’a été nécessaire : le contrat élargi par
W120 couvre déjà le cap `SQUARE`.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke local : `(8.25,8.25) -> (20.25,14.25)`, largeur `4`, cap `SQUARE`,
  join `MITER`, anti-aliasing désactivé.
- Rotation du draw : `90°` autour de `(16,16)`. Le segment device devient
  `(23.75,8.25) -> (17.75,20.25)` ; l’oracle étend chaque extrémité de `2 px`
  dans la direction tangentielle.
- Clip Winding device : `(27.75,4.25)`, `(27.75,27.25)`, `(4.75,4.25)`,
  `INTERSECT`, anti-aliasing désactivé.
- Gradient local `(0,0) -> (32,0)`, rouge → bleu, deux stops, `clamp`.
  La rotation mappe l’axe vers `(32,0) -> (32,32)` ; l’oracle évalue donc
  `t = clamp(y/32, 0, 1)`.
- L’oracle indépendant combine appartenance barycentrique au clip et distance
  au segment étendu, puis compare tout le buffer RGBA.

## Preuve native

Le test `clamp linear gradient right angle square miter stroke under winding
clip renders natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- transform facts de la rotation quart de tour et classe
  `right-angle-rotation` ;
- plan `StencilCoverage`, Winding non-inverse ;
- opérations producteur `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `NotEqual` ;
- préparation native `Recorded` ;
- résultat `Succeeded` après un submit et une readback copy ;
- égalité RGBA complète avec l’oracle CPU.

## Vérification

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient right angle square miter stroke under winding clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Classe complète à relancer avant publication :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Contrôle final : `git diff --check`.

