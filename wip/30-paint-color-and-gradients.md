# WIP 30 — paint, gradients, blend et couleur

> Brief d'exécution de `W30` à `W35`. Une combinaison de paint n'hérite jamais
> automatiquement du support séparé de ses composants.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| API Paint | `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Paint.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt` |
| Surface mapping | `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUGradientColorFilter.kt` |
| Materials | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/` |
| WGSL paint | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/` |
| Oracle | `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SurfaceSrgbGradientCpuOracle.kt`, `../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SurfaceSrgbSrcOverCpuOracle.kt` |

## W30 — gradient stops et tile modes

- [ ] Tester 1, 2, 3, 4 et 8 stops, positions implicites/explicites et hard stops.
- [ ] Tester `CLAMP`, `REPEAT`, `MIRROR` et `DECAL` aux bords et hors domaine.
- [ ] Tester stops non finis, non monotones, dupliqués et dépassement de budget.
- [ ] Unifier l'oracle de tile/interpolation pour les familles de gradient.
- [ ] Conserver un refus stable au-delà du budget validé.

## W31 — familles de gradient

- [ ] Prouver linear, radial, sweep et conical à stops équivalents.
- [ ] Tester centres/rayons dégénérés, angles enveloppés et deux centres.
- [ ] Tester alpha, prémultiplication et interpolation sRGB/linear annoncée.
- [ ] Vérifier snippets WGSL, uniform layouts et cache keys par famille.

## W32 — gradients composés

- [ ] Tester CTM affine et local matrix séparément puis ensemble.
- [ ] Tester gradient sur rect, RRect, path, stroke et sous clip.
- [ ] Tester color space/interpolation options réellement exposées.
- [ ] Refuser les matrices singulières et les combinaisons sans route native.

## W33 — blend

- [ ] Prouver tous les modes Porter-Duff avec source/destination opaques puis
      translucides.
- [ ] Ajouter les modes avancés par familles mathématiques avec oracle CPU.
- [ ] Tester destination read, MSAA, layer et formats color compatibles.
- [ ] Tester `Blender.Mode` et `Blender.Arithmetic` avec coefficients valides et
      invalides.
- [ ] Refuser avant draw une combinaison dont l'exactness n'est pas garantie.

## W34 — color filters

- [ ] Prouver Matrix, Blend, Compose, Table, Lighting, SRGBToLinear,
      LinearToSRGB, HSLAMatrix, Lerp, HighContrast, Luma et Overdraw.
- [ ] Tester composition, ordre, alpha prémultiplié et espaces colorimétriques.
- [ ] Tester filtre sur couleur solide, gradient, image et layer.
- [ ] Vérifier layout 4x5, valeurs non finies et depth budget.

## W35 — composition de shaders

- [ ] Prouver `Shader.Blend`, `WithLocalMatrix`, `WithColorFilter`,
      `WithWorkingColorSpace` et `CoordClamp`.
- [ ] Ajouter PerlinNoise et FractalNoise avec seed, octaves, tile size et oracle
      déterministes.
- [ ] Tester profondeur de composition et cache key complète.
- [ ] Refuser cycles, child manquant et profondeur hors budget.

## Sortie

La fermeture exige une matrice positive et négative par variante publique,
pas seulement une preuve du shader nu. Chaque nouveau paint supporté est testé
sur au moins une primitive et une interaction clip/layer pertinente.

## Vérification

```bash
./gradlew :kanvas:test
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Gradient*' --tests '*Blend*' --tests '*ColorFilter*' --tests '*Shader*'
./gradlew :integration-tests:skia:test --tests '*Gradient*' --tests '*Color*' --tests '*Composite*'
```
