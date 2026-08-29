# W139 — Sweep clamp 2-stop, square + miter stroke, right-angle winding clip

## Périmètre

- TDD inventory + native: cas `SweepGradient` 2 stops, `square/miter`, `antiAlias = false`, `hard` `Winding` avec `Clip` triangle,
  `CTM = Matrix3x3F32.rotation(90f, pivotX = 16f, pivotY = 16f)`.
- `isNativeHardPathClipSweepGradientTransform()` garde la frontière: identity/translation/scale/refus des projections non admissibles
  et n’autorise explicitement que la rotation cardinale droite (`+90°`) via `isExactQuarterTurnHardPathClipGradientRotation()`.
- `toCorePrimitiveMaterial()` mappe désormais `center` en espace device et rébase `startAngle`/`endAngle` avec `+90f`.
- Correction du cas `0f..360f` après rébase `+90°` pour éviter la normalisation conjointe à `start == end` (`sweepSpan` complet préservé).

## Résultat des preuves

- `GPUFramePathApiInventoryTest`:
  - `exact right angle sweep draw with square miter stroke under winding clip reaches the hard path clip route`
  - route `native.path_stroke.stencil_cover`
  - diagnostics `route:native.path_stroke.stencil_cover`
- `GPUFramePathApiInventoryNativeSmokeTest`:
  - `clamp sweep gradient right angle square miter stroke under winding clip renders natively`
  - route planner `native.path_stroke.stencil_cover`
  - `GPUClipExecutionPlan` `pathTransformClass = "right-angle-rotation"`
  - `fillRule = Winding`, `inverseFill = false`, `consumer.compare = NotEqual`
  - préparation `Recorded`, exécution `Succeeded`
  - `submit`/`readbackCopies` à `1`
  - image conforme via `assertRgbaWithinOneLsb` avec `deterministicRightAngleSquareMiterSweepWindingClipOracle()` (tolérance 1 LSB)
- `GPUFramePathApiInventoryTest`/`NativeSmokeTest` conservent le refus explicite des rotations non-cardinales (15°), avec le code observé `refused.unsupported.geometry.perspective_path`.

## Commandes de vérification cibles

```text
rtk ./gradlew :kanvas:test --no-daemon --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest.exact right angle sweep draw with square miter stroke under winding clip reaches the hard path clip route' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient right angle square miter stroke under winding clip renders natively' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest.sweep draw with non-right-angle rotation remains refused before hard path clip recording' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient non-right-angle stroke remains refused before native preparation'
```

### Résultat d'exécution réel

- Les 4 tests ciblés passent : route positive, preuve native avec oracle CPU, et les 2 refus non-cardinaux.
- Suite impactée complète passée sans échec :
  - `GPUFramePathApiInventoryNativeSmokeTest` : 72 tests, 0 échec ;
  - `GPUFramePathApiInventoryTest` : 144 tests, 0 échec ;
  - `GPUMaterialMapperTest` : 58 tests, 0 échec ;
  - `GPUCorePrimitiveSemanticBuilderTest` : 16 tests, 0 échec.
- Le run complet confirme notamment que les sweeps sans décalage conservent leurs angles source, tandis que la rotation +90° réencode le sweep dans l'espace device en conservant sa longueur.

## Notes

- La vague est prête pour revue ; aucun run de promotion n'est lancé dans cette session.
