# FOR-375 Effective destination candidate M60 F16

Linear: `FOR-375`

Decision: `M60_F16_EFFECTIVE_DESTINATION_CANDIDATE_RECORDED`

Classification: `effective-destination-candidate-reduces-residual`

FOR-375 teste uniquement l'axe destination effective identifie par FOR-374.
La candidate est calculee depuis `inverseDestinationEstimate.rgbClampedToSrgb`
et n'est pas appliquee au renderer/runtime.

## Formule

Ordre de calcul diagnostique:

1. `destinationRgb = inverseDestinationEstimate.rgbClampedToSrgb`.
2. `alpha = effectiveSourceAlphaByte / 255.0`.
3. Pour chaque canal source sRGB droit:
   `floor(((source / 255.0) * alpha + (destination / 255.0) * (1.0 - alpha)) * 256.0)`.
4. Chaque canal est borne dans `[0,255]`; alpha de sortie fixe a `255`.

La destination effective provient de l'inversion FOR-374, puis est bornee en
sRGB. Ce clamp explique pourquoi la candidate reduit le residuel sans expliquer
parfaitement la reference.

## Resultat

- Residuel courant preserve: `856`
- Residuel candidate FOR-373 preserve: `1033`
- Residuel candidate destination effective: `794`
- Delta versus courant: `-62`
- Delta versus candidate FOR-373: `-239`

## Producteur

- Appel writer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:166`
- Writer FOR-375: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:811`
- Producteur JSON: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1024`
- Sample diagnostique: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1305`
- Formule candidate: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1851`
- Classification: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1799`

## Samples

| # | x | y | bande | reference RGBA | current RGBA | candidate FOR-373 | destination effective | candidate destination | residuel courant | residuel FOR-373 | residuel destination | delta courant | delta FOR-373 |
|---|---:|---:|---|---|---|---|---|---|---:|---:|---:|---:|---:|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | `[255, 170, 255, 255]` | `[95, 150, 143, 255]` | 105 | 141 | 109 | 4 | -32 |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | `[255, 170, 255, 255]` | `[95, 150, 143, 255]` | 105 | 141 | 109 | 4 | -32 |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | `[255, 170, 255, 255]` | `[95, 150, 143, 255]` | 105 | 141 | 109 | 4 | -32 |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | `[255, 170, 255, 255]` | `[95, 150, 143, 255]` | 105 | 141 | 109 | 4 | -32 |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | `[255, 170, 255, 255]` | `[95, 150, 143, 255]` | 105 | 141 | 109 | 4 | -32 |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | `[255, 170, 255, 255]` | `[95, 150, 143, 255]` | 105 | 141 | 109 | 4 | -32 |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[191, 217, 243, 255]` | `[255, 250, 251, 255]` | `[191, 213, 240, 255]` | 56 | 23 | 16 | -40 | -7 |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[159, 211, 188, 255]` | `[255, 225, 255, 255]` | `[159, 193, 188, 255]` | 52 | 85 | 67 | 15 | -18 |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[95, 159, 223, 255]` | `[255, 231, 231, 255]` | `[95, 150, 214, 255]` | 52 | 56 | 38 | -14 | -18 |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[191, 226, 210, 255]` | `[255, 250, 254, 255]` | `[191, 222, 210, 255]` | 66 | 23 | 19 | -47 | -4 |

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend, score, seuil ou promotion.
- La destination effective et sa candidate restent des preuves diagnostiques calculees, jamais lues depuis le renderer ou appliquees.

## Validations

- `rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py`
- `rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py`
- `rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py`
- `rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py`
- `rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py`
- `rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for375-pycache python3 -m py_compile scripts/validate_for375_m60_f16_effective_destination_candidate.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
