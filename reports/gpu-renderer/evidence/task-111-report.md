# W135 — SweepGradient avec matrice locale bornée

## Périmètre

Cette vague atteste la route native d'un `SweepGradient` clamp à deux stops,
appliqué à un stroke mono-segment diagonal `SQUARE + MITER`, AA désactivé,
sous un clip triangle Winding. Le shader porte une matrice locale de translation
`translate(1.25, -0.75)`.

## Preuve native

- route : `native.path_stroke.stencil_cover` ;
- clip Winding : producteur `IncrementWrap` / `DecrementWrap`, consumer
  `NotEqual` ;
- préparation : `Recorded` ;
- exécution native : `Succeeded` ;
- un submit et une readback copy ;
- oracle CPU indépendant : coordonnées au centre des pixels, extension square
  de 2 px, inclusion barycentrique du triangle, puis sweep `atan2` dans
  l'espace de shader après la translation locale ; interpolation linear-light,
  encodage sRGB et tolérance d'un LSB.

## Frontière contractuelle

Les matrices locales avec rotation (`rotation(90)`) ou échelle non uniforme
(`scaling(2, 1)`) restent refusées avant préparation native avec
`refused.unsupported.material.mapping.local_matrix`. La route ne promet donc
que identité ou échelle uniforme positive suivie d'une translation, sans skew,
perspective ni singularité.

## Vérification

Commande ciblée exécutée :

```text
./gradlew :kanvas:test --no-daemon --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.translated local sweep matrix square miter stroke under winding clip renders natively' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.rotated and nonuniform local sweep matrices remain refused before native preparation' \
  --rerun-tasks
```

Résultat : `BUILD SUCCESSFUL`, 2 tests exécutés, 0 échec.

La suite forcée des inventaires W131–W135 est également verte : 67 tests
de rendu natif, 137 tests de planification, 57 tests du mapper et 16 tests
du semantic builder, tous sans échec.

## Limites

Cette preuve ne couvre pas les rotations ou matrices affines générales du
shader, les perspectives, les gradients à plus de deux stops, les autres tile
modes, les caps round, les chemins multi-segments, ni l'AA multi-sample.
