# W122 — radial gradient sur stroke exact sous clip Winding

## Objectif

Prouver que le pipeline natif Kanvas peut rendre un gradient radial `clamp` à
deux stops sur un stroke mono-segment exact (`DirectTriangles`, cap `BUTT`,
join `MITER`, sans antialiasing), lorsque le stroke est limité par un clip
path Winding `INTERSECT` en single-sample.

## Fixture et oracle

- cible RGBA : 32 × 32, fond transparent ;
- clip : triangle `(7.25,6.25)`, `(30.25,6.25)`, `(7.25,29.25)` ;
- stroke : segment `(5.25,8.25)` → `(21.25,20.25)`, largeur `4`, cap
  `BUTT`, join `MITER`, AA désactivé ;
- radial : centre `(16,16)`, rayon `16`, stops rouge `(0)` et bleu `(1)`,
  tile mode `clamp`.

L’oracle CPU indépendant évalue chaque centre de pixel : inclusion
barycentrique dans le triangle, distance au segment avec bornes butt, puis
le rayon radial clampé. Les composantes RGB sont interpolées en espace
linéaire avant réencodage sRGB ; l’alpha est opaque uniquement dans
l’intersection clip/stroke.

## Diagnostic et correction

Avant la correction, la préparation native refusait la scène avec :

`unsupported.recording.core_primitive_material.non_solid: The legacy native CorePrimitive task builder accepts only solid color, or the exact single-sample clamp-linear-gradient direct-triangle hard-path-clip material ABI.`

Le shader radial natif et le mapping `DirectRadialGradient` existaient déjà.
Le manque était dans les gardes du task builder, qui n’autorisaient le
contrat gradient sous clip que pour `LinearGradient`. La correction est
bornée au même ABI déjà prouvé :

- reconnaître `RadialGradient` `clamp` dans le prédicat de consumer exact ;
- autoriser `DirectRadialGradient` uniquement pour
  `hasExactDirectStrokePathConsumerGeometry()` et le payload radial exact
  (2 stops/8 composantes de couleur, sRGB, matrice identité) ;
- produire `targetBounds` comme scissor exact pour ce cas radial.

Le semantic builder (constructeur de sémantique) applique la même frontière
avec, en plus, une transformation du draw en identité et un clip
`StencilCoverage` 1x. Un radial à trois stops est refusé dès l’analyse avec
`unsupported.material.radial_gradient_stop_count`, plutôt que d’être envoyé
vers une route partiellement prouvée.

Aucune route générique de stroke, aucun cap round, multi-segment ou
`StrokeStencilEdgeFan` n’est admis par cette correction. `gpu-renderer-scenes`
n’a pas été modifié.

## Preuves

La préparation produit `Recorded`, avec la route
`native.path_stroke.stencil_cover`. Le plan clip est `StencilCoverage` :

- géométrie `Winding`, non inverse ;
- producteur front `IncrementWrap`, back `DecrementWrap` ;
- consumer `NotEqual`.

L’exécution native produit `Succeeded`, avec exactement un submit et une
copie readback. Le readback RGBA correspond entièrement à l’oracle CPU.

Commandes exécutées :

```text
./gradlew :kanvas:test --rerun-tasks --no-parallel --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp radial gradient butt miter stroke under winding path clip renders natively'
./gradlew :kanvas:test --rerun-tasks --no-parallel --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
./gradlew :kanvas:test --no-parallel --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp radial gradient butt miter stroke under winding path clip renders natively' --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp three stop radial gradient stroke under winding path clip remains refused'
```

Résultat : les trois commandes terminent par `BUILD SUCCESSFUL`; le test
radial positif, le refus 3 stops et les 51 tests de la classe passent.

## Limites

Cette vague ne prouve pas les gradients radiaux sur géométrie générale :
elle couvre uniquement le stroke mono-segment abaissé en `DirectTriangles`,
Winding non inverse, clip path single-sample et configuration `clamp` exacte.
Les variantes hors contrat restent refusées explicitement par les gardes
existants.
