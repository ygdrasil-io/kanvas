# W112 — cap round translaté sous scissor intégral

## Objectif

Vérifier que la capacité round pixel-exact déjà démontrée en W111 conserve son
rendu lorsqu’une translation entière est appliquée au stroke, avec un scissor
device intégral.

## Fixture et preuve

Le segment local `(6,16) → (26,16)`, largeur 4, cap `round`, AA désactivé, est
translaté de `(3,2)`, donnant un segment device `(9,18) → (29,18)`. Le scissor
est `[8,16,21,21]`.

Le test vérifie la route `native.path_stroke.stencil_cover`, le clip
`ScissorOnly`, la préparation native, un submit et un readback uniques. Le
readback RGBA complet est comparé à un oracle CPU indépendant qui combine le
corps du segment et les deux disques de rayon 2 dans l’espace device, puis
applique le scissor.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.translated horizontal round cap stroke under integral device scissor renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également passé
avec `BUILD SUCCESSFUL`. Aucun changement de production, seuil, PNG ou
`gpu-renderer-scenes` n’a été effectué.
