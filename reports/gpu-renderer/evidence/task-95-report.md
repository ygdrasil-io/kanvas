# W119 — gradient clamp + stroke sous clip Winding inverse

## Objectif

Prouver la combinaison d’un gradient linéaire `clamp` à deux stops et d’un
stroke mono-segment `butt/miter` sous un clip path `INVERSE_WINDING` avec
`ClipOp.INTERSECT`.

Aucune modification de production n’a été nécessaire : la route actuelle
supporte déjà le clip Winding inverse.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Clip triangulaire Winding inverse : `(7.25,6.25)`, `(30.25,6.25)`,
  `(7.25,29.25)`, `INTERSECT`, anti-aliasing désactivé.
- Gradient linéaire horizontal `(0,0) -> (32,0)`, deux stops rouge → bleu,
  interpolation linear-light puis encodage sRGB, mode `clamp`.
- L’oracle CPU indépendant conserve les centres de pixels hors du triangle
  (sémantique inverse Winding), vérifie ensuite la distance au segment et
  calcule la couleur du gradient. La comparaison porte sur tout le buffer
  RGBA.

## Preuve native

Le test `clamp linear gradient butt miter stroke under inverse winding clip
renders natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `Winding` avec `inverseFill=true` ;
- opérations producteur `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `Equal`, correspondant à l’intersection avec le
  complément du triangle ;
- préparation native `Recorded` ;
- résultat `Succeeded` après un submit et une readback copy ;
- égalité RGBA complète avec l’oracle CPU indépendant.

## Vérification

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient butt miter stroke under inverse winding clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Classe complète à relancer avant publication :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Contrôle final : `git diff --check`.

