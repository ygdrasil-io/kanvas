# W108 — rotation à 180° d’un stroke sous clip path

## Objectif

Étendre la preuve de W107 à une rotation exacte de 180° autour d’un pivot
déterministe, sans élargir la route aux transformations affines générales.

## Fixture

Le test utilise une surface offscreen 32×32, un segment diagonal local
`(8.25,8.25) → (20.25,14.25)`, largeur 4, cap `butt`, join `miter` et
anti-aliasing désactivé. Une rotation de 180° autour de `(16,10)` produit les
extrémités device `(23.75,11.75) → (11.75,5.75)`. Le dessin est intersecté
avec un triangle Winding.

## Preuve

Le test vérifie :

- route `native.path_stroke.stencil_cover` ;
- classe de transformation `right-angle-rotation` ;
- géométrie du clip conservée en coordonnées device ;
- producer stencil Winding `IncrementWrap/DecrementWrap` ;
- consumer `NotEqual` ;
- préparation native réussie ;
- exactement une soumission et un readback ;
- résultat RGBA complet identique à un oracle CPU indépendant calculé au
  centre de chaque pixel (appartenance au triangle et distance au segment).

La route 180° réutilise donc uniquement la capacité bornée déjà introduite pour
les rotations exactes 90°/180°. Les rotations arbitraires restent refusées par
le contrat testé en W107.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.half turn rotated diagonal butt miter stroke under winding path clip renders natively' --rerun-tasks
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également passé
avec succès, avec tous les tests PASS.

## Limites

Aucun changement de production n’est nécessaire pour cette vague. Aucun seuil,
PNG de référence ou `gpu-renderer-scenes` n’a été modifié. Les transformations
affines générales, les rotations non multiples de 90° et les strokes multi-
segments restent hors contrat.
