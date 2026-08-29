# W111 — cap round sous scissor intégral

## Objectif

Vérifier le cas round pixel-exact déjà supporté par Kanvas lorsqu’il est limité
par un rectangle device intégral (`ScissorOnly`), sans introduire de nouvelle
topologie de clip path.

## Fixture et oracle

Le test utilise un segment horizontal `(6,16) → (26,16)`, largeur 4, cap
`round`, anti-aliasing désactivé, sur une surface offscreen 32×32. Le scissor
intégral est `[5,14,18,19]`.

L’oracle CPU indépendant classe chaque centre de pixel comme rouge si celui-ci
appartient au corps du segment ou à l’un des deux disques de rayon 2, puis
applique le même rectangle de scissor. Le résultat RGBA complet est comparé au
readback GPU.

## Preuve native

La route d’analyse reste `native.path_stroke.stencil_cover`, le clip est
`ScissorOnly`, la préparation native réussit, et l’exécution effectue exactement
un submit et un readback. Le test ciblé passe.

Ce résultat montre que le cap round est utilisable par la route Kanvas lorsque
le clip est un scissor ; le refus W110 est donc spécifique à la composition avec
un clip path `StencilCoverage`, et non au cap round lui-même.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.horizontal round cap stroke under integral device scissor renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également passé
avec succès, avec tous les tests PASS. Aucun changement de production, seuil,
PNG ou `gpu-renderer-scenes` n’a été effectué.
