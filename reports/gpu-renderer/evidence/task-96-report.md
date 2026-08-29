# W120 — gradient clamp + stroke sous rotation exacte de 90°

## Objectif

Étendre la preuve du gradient linéaire `clamp` à deux stops sur un stroke
mono-segment `butt/miter` sous clip Winding `INTERSECT`, avec la rotation
affine exacte de 90° déjà admise par la route des strokes.

## Diagnostic et correction bornée

La première préparation native a refusé le cas avec
`unsupported.core_primitive.material.path_stencil`. La normalisation des
transformations autorisait déjà la rotation quart de tour, mais la whitelist
des classes de clip utilisées par le lane gradient ne contenait pas
`right-angle-rotation`.

Le correctif minimal ajoute uniquement cette classe à
`HARD_PATH_CLIP_GRADIENT_TRANSFORM_CLASSES`. Les rotations arbitraires restent
refusées par le contrat existant ; aucune route générique n’est introduite.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format de `RenderConfig.DEFAULT`.
- Stroke local : `(8.25,8.25) -> (20.25,14.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Transformation du draw : rotation `90°` autour de `(16,16)`. Les extrémités
  device attendues sont `(23.75,8.25) -> (17.75,20.25)`.
- Clip Winding device : `(27.75,4.25)`, `(27.75,27.25)`, `(4.75,4.25)`,
  `INTERSECT`, anti-aliasing désactivé.
- Gradient local `(0,0) -> (32,0)`, rouge → bleu, deux stops, `clamp`.
  La transformation mappe son axe vers `(32,0) -> (32,32)` ; l’oracle évalue
  donc `t = clamp(y/32, 0, 1)`.
- L’oracle indépendant teste l’appartenance barycentrique au triangle et la
  distance au segment dans l’espace device, puis compare tout le buffer RGBA.

## Preuve native

Le test `clamp linear gradient right angle butt miter stroke under winding clip
renders natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- transform facts capturés (`scaleX=scaleY=0`, `skewX=-1`, `skewY=1`,
  translation `(32,0)`) et classe `right-angle-rotation` ;
- plan `StencilCoverage`, Winding non-inverse ;
- opérations producteur `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `NotEqual` ;
- préparation native `Recorded` ;
- résultat `Succeeded` après un submit et une readback copy ;
- égalité RGBA complète avec l’oracle CPU.

## Vérifications

Test ciblé :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient right angle butt miter stroke under winding clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé après le correctif borné.

Classe complète :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Contrôle final : `git diff --check`.

