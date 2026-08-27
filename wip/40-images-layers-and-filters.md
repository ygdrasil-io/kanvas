# WIP 40 — images, layers et filtres

> Brief d'exécution de `W40` à `W48`. Les images déjà matérialisées sont dans le
> scope; un codec absent reste dependency-gated.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| API | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/SamplingOptions.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ImageFilter.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MaskFilter.kt` |
| Surface GPU | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` |
| Image routes | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPreparedImageClipAuthority.kt`, `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLowering.kt` |
| Evidence | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/KanvasSurfaceProgram.kt`, `../reports/gpu-renderer/evidence/` |

## W40 — sampling

- [ ] Prouver NEAREST et LINEAR aux centres, demi-pixels et bords.
- [ ] Implémenter le cubic borné avec coefficients et oracle explicites.
- [ ] Tester crop source/destination, scale up/down, rotation et clip.
- [ ] Définir mipmap policy et refus quand le mipmap requis n'existe pas.
- [ ] Vérifier alpha, premul et color space au readback.

## W41 — formats et uploads

- [ ] Prouver chaque format raw exposé et réellement matérialisable.
- [ ] Tester row stride, padding, sub-rect upload et layout invalides.
- [ ] Tester conversion color type/alpha type/color space supportée.
- [ ] Vérifier identité d'artefact, génération device et ownership.
- [ ] Garder les formats dépendant d'un codec absent en `DEPENDENCY_GATED`.

## W42 — image shaders

- [ ] Tester tile X/Y, sampling et local matrix.
- [ ] Tester image shader avec color filter, blend, clip et transform.
- [ ] Vérifier coordonnées hors texture et politique `DECAL`.
- [ ] Fixer profondeur de wrappers, texture binding et budget sampler.

## W43 — nine, lattice et atlas

- [ ] Tester `drawImageNine` avec centre étirable et dimensions limites.
- [ ] Tester lattice valide, cellules transparentes et divs invalides.
- [ ] Tester atlas avec transforms, rects, colors, blend et ordre des sprites.
- [ ] Vérifier clipping, batches, budgets vertices/indices et bounds.

## W44 — saveLayer

- [ ] Tester bounds explicites/implicites, alpha et paint.
- [ ] Tester SRC, SRC_OVER et modes déjà validés par `W33`.
- [ ] Tester deux layers imbriqués, init previous, restore et draw sentinelle.
- [ ] Définir backdrop/filter chain supportés et les refus correspondants.
- [ ] Vérifier textures intermédiaires, destination read et libération.

## W45 — filtres fondamentaux

- [ ] Prouver Crop, Blur, DropShadow, Offset, Tile et ColorFilter.
- [ ] Tester chacun sur image, primitive et saveLayer quand sémantiquement valide.
- [ ] Vérifier expansion/crop de bounds, sigma/radius et bords.
- [ ] Refuser paramètres non finis, négatifs et surfaces hors budget.

## W46 — graphes de filtres

- [ ] Prouver Compose, Blend et Merge avec deux puis trois enfants.
- [ ] Vérifier ordre, bounds, color space et alpha.
- [ ] Détecter cycle, enfant manquant, depth et intermediate budget.
- [ ] Vérifier réutilisation sûre des intermédiaires et aucune fuite de layer.

## W47 — filtres avancés

- [ ] Prouver Dilate, Erode, DisplacementMap et MatrixConvolution.
- [ ] Prouver Picture et Magnifier avec bounds et sampling déterministes.
- [ ] Prouver les variantes distant/point/spot diffuse et specular.
- [ ] Ajouter un oracle CPU par famille mathématique et un budget de kernel.
- [ ] Refuser toute variante qui dépasserait le plan mémoire validé.

## W48 — mask filters

- [ ] Étendre Blur à ses styles et qualités exposés.
- [ ] Prouver Shader et Table sur rect, RRect et path.
- [ ] Tester interaction avec stroke, clip, transform et saveLayer.
- [ ] Vérifier coverage, intermediate texture, sigma et cache key.

## Sortie

Chaque route image/filter doit distinguer absence de dépendance, entrée invalide,
budget dépassé et capacité GPU manquante. Un rendu noir ou transparent n'est
jamais considéré comme un fallback valide.

## Vérification

```bash
./gradlew :kanvas:test
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Image*' --tests '*Layer*' --tests '*Filter*' --tests '*Sampling*'
./gradlew :integration-tests:skia:test --tests '*Image*' --tests '*Blur*' --tests '*Composite*'
```
