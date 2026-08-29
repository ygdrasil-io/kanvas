# W116 — gradient clamp + stroke sous clip avec scale uniforme et translation

## Objectif

Étendre la preuve W115 au cas réellement transformé par Kanvas : un stroke
mono-segment `butt/miter` avec gradient linéaire `clamp` à deux stops, sous un
clip path Winding `INTERSECT`, avec une transformation positive uniforme du
draw (`scale(1.5) + translation(2,1)`).

Aucune modification de production ni de `gpu-renderer-scenes` n’est nécessaire.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Géométrie locale du stroke : `(4.125,4.125) -> (12.125,8.625)`, largeur
  locale `2`, cap `BUTT`, join `MITER`, anti-aliasing désactivé.
- Transformation du draw : `Matrix3x3F32.translation(2,1) * scaling(1.5,1.5)`.
  Les extrémités device attendues sont donc `(8.1875,7.1875)` et
  `(20.1875,13.9375)`, avec une largeur device `3`.
- Clip Winding triangulaire device : `(6.875,5.875)`, `(24.875,5.875)`,
  `(6.875,23.875)`, `INTERSECT`, anti-aliasing désactivé.
- Gradient local `(0,0) -> (32,0)`, rouge → bleu, deux stops, `clamp`.
  L’autorité de conversion device mappe son axe vers `(2,1) -> (50,1)`.
- L’oracle indépendant teste les centres de pixels par barycentrique pour le
  triangle et par distance au segment pour le stroke, puis évalue le gradient
  dans l’espace device (`t = clamp((x-2)/48, 0, 1)`) en linear-light avant
  encodage sRGB. La comparaison porte sur tout le buffer RGBA.

## Preuve native

Le test `clamp linear gradient scaled translated butt miter stroke under
winding path clip renders natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- transform facts capturés (`translateX=2`, `translateY=1`, `scaleX=scaleY=1.5`) ;
- plan `StencilCoverage` avec classe `uniform-positive-scale-translate` ;
- opérations Winding `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `NotEqual` ;
- préparation native `Recorded` ;
- rendu `Succeeded`, un seul submit et une seule readback copy ;
- égalité RGBA complète avec l’oracle CPU.

## Vérifications

Test ciblé :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient scaled translated butt miter stroke under winding path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Classe complète à relancer avant publication :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Contrôle final : `git diff --check`.

