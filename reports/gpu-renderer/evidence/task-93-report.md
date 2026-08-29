# W117 — gradient clamp + stroke sous clip Winding Difference

## Objectif

Prouver que la route native W114/W115 conserve le gradient linéaire `clamp` sur
un stroke mono-segment `butt/miter` lorsque le clip path Winding est appliqué
avec `ClipOp.DIFFERENCE`.

Aucune modification de production ni de `gpu-renderer-scenes` n’est nécessaire.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Clip Winding triangulaire : `(7.25,6.25)`, `(30.25,6.25)`,
  `(7.25,29.25)`, `DIFFERENCE`, anti-aliasing désactivé.
- Gradient linéaire horizontal `(0,0) -> (32,0)`, deux stops rouge → bleu,
  interpolation linear-light puis encodage sRGB, mode `clamp`.
- L’oracle parcourt les centres de pixels, rejette les points dans le triangle
  (sémantique Difference), vérifie ensuite la distance au segment et calcule
  la couleur du gradient. La comparaison porte sur tout le buffer RGBA.

## Preuve native

Le test `clamp linear gradient butt miter stroke under winding difference path
clip renders natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- opérations producteur Winding `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `Equal`, correspondant à `Difference` ;
- géométrie clip Winding non-inverse ;
- préparation native `Recorded` ;
- résultat `Succeeded` après exactement un submit et une readback copy ;
- égalité RGBA complète avec l’oracle CPU indépendant.

## Vérification

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient butt miter stroke under winding difference path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Classe complète à relancer avant publication :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Contrôle final : `git diff --check`.

