# W115 — gradient linéaire clamp + stroke sous clip Winding

## Objectif

Prouver que la capacité autorisée par W114 est effectivement utilisable par la
route Kanvas native : un gradient linéaire `clamp` à deux stops sur un stroke
mono-segment `butt/miter`, sous un clip path `Winding` avec `ClipOp.INTERSECT`.

Cette vague ne modifie pas la production et ne touche pas
`gpu-renderer-scenes`.

## Fixture et oracle CPU

- Cible offscreen : `32x32`, format/couleur issus de `RenderConfig.DEFAULT`.
- Segment device : `(5.25, 8.25) -> (21.25, 20.25)`, largeur `4`, cap `BUTT`,
  join `MITER`, anti-aliasing désactivé.
- Clip Winding triangulaire : `(7.25, 6.25)`, `(30.25, 6.25)`,
  `(7.25, 29.25)`, `INTERSECT`, anti-aliasing désactivé.
- Shader : gradient linéaire horizontal de `(0,0)` à `(32,0)`, deux stops
  rouge → bleu, interpolation linéaire-light puis encodage sRGB.
- L’oracle indépendant parcourt tous les centres de pixels, teste séparément
  l’appartenance au triangle et la distance au segment, puis calcule la couleur
  du gradient. La comparaison porte sur la totalité du buffer RGBA.

## Preuve native

Le test `clamp linear gradient butt miter stroke under winding path clip renders
natively` vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- opérations producteur Winding `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `NotEqual` ;
- préparation native `Recorded` ;
- résultat `Succeeded` après un submit et une readback copy ;
- égalité RGBA complète avec l’oracle CPU.

La frontière reste verrouillée par le cas négatif W114
`clamp linear gradient round stroke under winding path clip remains refused`,
qui conserve le refus stable `unsupported.core_primitive.material.path_stencil`.
Le contrat ne devient donc pas une prise en charge générique des strokes : les
caps round et les topologies non admises restent refusés explicitement.

## Vérifications

Commande ciblée positive et négative :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient butt miter stroke under winding path clip renders natively' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient round stroke under winding path clip remains refused'
```

Résultat : `BUILD SUCCESSFUL`, les deux tests passent.

Classe complète :

```text
./gradlew :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
```

Résultat : `BUILD SUCCESSFUL`, tous les tests de la classe passent, dont les
preuves W115 positive et négative.

Contrôle de patch : `git diff --check`.
