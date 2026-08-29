# W123 — SweepGradient sur stroke exact sous clip Winding

## Objectif

Prouver le rendu natif d’un `SweepGradient` `clamp` à deux stops sur un
stroke mono-segment abaissé en `DirectTriangles`, cap `BUTT`, join `MITER`,
AA désactivé, sous un clip path Winding `INTERSECT` en single-sample.

## Fixture et oracle CPU

- cible RGBA 32 × 32, fond transparent ;
- clip triangle : `(7.25,6.25)`, `(30.25,6.25)`, `(7.25,29.25)` ;
- stroke : `(5.25,8.25)` → `(21.25,20.25)`, largeur `4` ;
- sweep : centre `(16,16)`, angles `0°..360°`, rouge vers bleu, deux stops.

L’oracle indépendant teste les centres de pixels avec un test barycentrique
du triangle et la distance au segment bornée par les extrémités butt. Pour
les pixels couverts, il reproduit `atan2`, la normalisation de l’angle sur un
tour complet et l’interpolation sRGB en espace linéaire. Le readback est
comparé à l’oracle sur tous les canaux avec une tolérance maximale d’un LSB,
nécessaire pour la différence de précision entre `atan2` CPU et WGSL/f32.

## Diagnostic initial

Le test ajouté avant l’extension du pipeline était préparé mais refusé à
l’exécution avec :

`unsupported.native-core-primitive.clip-stencil-pipeline: Prepared clip-stencil contains a structural pipeline outside the closed native programs.`

Le shader direct sweep, son composant d’identité et le mapping général
existaient déjà. Il manquait uniquement la variante consumer spécifique au
clip stencil, présente pour les gradients linéaire et radial mais pas pour le
sweep.

## Correction bornée

Les éléments suivants ont été ajoutés selon le pattern radial existant :

- programmes `ClipStencilConsumerSweepGradientRegular/Inverse` ;
- mapping de `DirectSweepGradient` vers ces programmes ;
- validation du pipeline, état stencil `NotEqual`/`Equal` et cache de
  composant ;
- admission du sweep dans la route clip-stencil single-sample ;
- prédicats semantic/prepared limités à `clamp`, sRGB, deux stops, matrice
  identité, angles finis avec span `(0,360]`, draw identity et stroke exact.

Aucune nouvelle architecture de shader ou route générique n’a été ajoutée.
`gpu-renderer-scenes` reste inchangé.

## Preuves

Le plan produit la route `native.path_stroke.stencil_cover` et
`StencilCoverage` avec :

- clip Winding non inverse ;
- producteur front `IncrementWrap`, back `DecrementWrap` ;
- consumer `NotEqual`.

La préparation est `Recorded`, l’exécution native est `Succeeded`, avec un
submit et une copie readback. Le test négatif d’un sweep à trois stops est
refusé dès l’analyse par `unsupported.material.sweep_gradient_stop_count`.

Commandes exécutées :

```text
./gradlew --no-daemon :kanvas:test --no-parallel --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient butt miter stroke under winding path clip renders natively'
./gradlew --no-daemon :kanvas:test --no-parallel --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient butt miter stroke under winding path clip renders natively' --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp three stop sweep gradient stroke under winding path clip remains refused'
```

Résultat : les deux commandes terminent par `BUILD SUCCESSFUL`. Le test
positif et le refus trois stops passent.

Vérifications complémentaires exécutées sur la branche :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest'
./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilderTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUCorePrimitiveNativeRouteTest'
```

Résultat : `51` tests Kanvas natifs et les deux suites unitaires renderer
terminent par `BUILD SUCCESSFUL`.

## Limites

Cette preuve couvre uniquement le lane exact identity-transform, deux stops,
Winding non inverse, clip single-sample et span de sweep borné. Les matrices
locales non identitaires, transformations du draw, spans invalides, plus de
deux stops, cap round et géométries edge-fan restent refusés par les gardes
existants.
