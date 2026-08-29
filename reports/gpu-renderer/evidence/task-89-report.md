# W113 — cap square sous rotation exacte de 90°

## Objectif

Vérifier que la route native `square/miter` déjà utilisée par Kanvas reste
pixel-déterministe quand le segment est tourné exactement de 90° et rendu sous
un clip path Winding avec `ClipOp.INTERSECT`.

## Fixture et oracle

Le segment local `(8.25,8.25) → (20.25,14.25)`, largeur 4, cap `square`, join
`miter`, sans anti-aliasing, est transformé par une rotation de 90° autour de
`(16,16)`. Ses extrémités device sont `(23.75,8.25) → (17.75,20.25)`. Le
triangle de clip device est `(27.75,4.25) → (27.75,27.25) → (4.75,4.25)`.

L’oracle CPU indépendant étend le segment de 2 pixels à chaque extrémité,
classifie les centres de pixels par distance euclidienne au segment étendu,
puis intersecte ce résultat avec le triangle Winding.

## Preuve native

Le test vérifie :

- la route `native.path_stroke.stencil_cover` ;
- le clip `StencilCoverage`, la classe `right-angle-rotation`, les opérations
  Winding `IncrementWrap`/`DecrementWrap` et le consommateur `NotEqual` ;
- la préparation native `Recorded` ;
- un résultat `Succeeded`, un submit et un readback ;
- l’égalité RGBA complète avec l’oracle CPU.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.right angle rotated diagonal square miter stroke under winding path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également
passé avec `BUILD SUCCESSFUL`.

Aucun changement de production, de seuil, de PNG ou de `gpu-renderer-scenes`.
