# W131 — SweepGradient + stroke square/miter sous `INVERSE_WINDING` + `DIFFERENCE`

## Résultat

Le cas est maintenant supporté par le lane natif borné. Le planner admet une
clip path hard, non-AA, avec `ClipOp.DIFFERENCE` et `inverseFill=true` sous les
mêmes garde-fous que les autres clips stencil. La composition conserve
l’intérieur du triangle : le clip inverse couvre l’extérieur, puis
`DIFFERENCE` retire cet extérieur.

## Fixture et oracle CPU

- Cible offscreen : `32x32`.
- Stroke diagonal : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap
  `SQUARE`, join `MITER`, anti-aliasing désactivé.
- Clip triangulaire : `(7.25,6.25)`, `(30.25,6.25)`,
  `(7.25,29.25)`, fill `INVERSE_WINDING`, `ClipOp.DIFFERENCE`,
  anti-aliasing désactivé.
- SweepGradient : centre `(16,16)`, angles `0..360`, deux stops rouge →
  bleu, `clamp`.

L’oracle CPU indépendant conserve les centres de pixels à l’intérieur du
triangle, applique l’extension square de `2 px`, puis évalue le SweepGradient
avec `atan2` et une interpolation linear-light. La comparaison porte sur le
buffer RGBA complet avec une tolérance maximale de 1 LSB par canal pour la
différence CPU/GPU en `f32`.

## Cause et correctif

Avant correction, `GPUOpMapper.toMaskExecutionPlan()` ne sélectionnait le lane
`StencilCoverage` pour `DIFFERENCE` que lorsque `!inverseFill`. Le cas inverse
retombait donc sur `CoverageMask`.

Le prédicat `singleHardPathClip` admet désormais `DIFFERENCE` avec ou sans
inversion, en conservant les garde-fous existants : clip path unique, AA off,
transformations admissibles, stencil-cover et bounded-clip capabilities.
La formule existante
`effectiveConsumerInverseFill = geometry.inverseFill xor consumerInverseFill`
produit `true xor true = false`, donc `GPUClipStencilCompare.NotEqual`, ce qui
est l’opération correcte pour conserver l’intérieur du triangle.

## Preuve native

Le test
`clamp sweep gradient square miter stroke under inverse winding difference clip renders natively`
vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `Winding`, `inverseFill=true` ;
- opérations producteur `IncrementWrap` / `DecrementWrap` ;
- comparaison consommateur `NotEqual` ;
- préparation `Recorded` ;
- résultat `Succeeded` ;
- exactement un submit et une copie de readback ;
- résultat RGBA conforme à l’oracle CPU à 1 LSB près.

## Vérification

Commande ciblée exécutée après le correctif :

```text
./gradlew --no-daemon :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient square miter stroke under inverse winding difference clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé.

Le test planner dédié passe également :

```text
./gradlew --no-daemon :kanvas:test --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest.mapper admits inverse winding difference path clip to the single stencil route'
```

Les tests de contrat ciblés `GPUClipExecutionPlanTest` et
`GPUCorePrimitiveClipStencilNativeRouteTest` passent aussi dans
`gpu-renderer` (`BUILD SUCCESSFUL`).

Une relance forcée avec `--rerun-tasks` a été interrompue par l’arrêt externe
du daemon Gradle pendant la compilation ; elle n’a pas atteint le test.

## Limites

La preuve couvre uniquement le lane exact : deux stops, `clamp`, interpolation
sRGB, matrice locale identité, transformation de dessin identité, stroke
mono-segment direct `square/miter`, anti-aliasing désactivé, clip triangulaire
`INVERSE_WINDING` et stencil 1x. Les caps `round`, chemins multi-segments,
matrices locales non identités et gradients à plus de deux stops restent hors
contrat et doivent conserver une politique de refus explicite.
