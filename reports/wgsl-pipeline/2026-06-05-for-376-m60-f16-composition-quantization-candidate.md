# FOR-376 Composition/quantization candidate M60 F16

Linear: `FOR-376`

Decision: `M60_F16_COMPOSITION_QUANTIZATION_CANDIDATE_RECORDED`

Classification: `composition-quantization-candidate-reduces-residual`

FOR-376 teste uniquement l'axe espace de composition et quantification autour
de la destination effective FOR-375. Les variantes sont calculees comme preuves
diagnostiques depuis les samples FOR-375 preserves; elles ne sont pas appliquees
au renderer/runtime.

## Resultat

- Residuel courant preserve: `856`
- Residuel candidate FOR-373 preserve: `1033`
- Residuel candidate FOR-375 preserve: `794`
- Meilleure variante: `linear_srgb_source_over_effective_destination_nearest_255`
- Residuel meilleure variante: `607`
- Delta versus courant: `-249`
- Delta versus FOR-375: `-187`
- Delta versus FOR-373: `-426`

## Classement

| rang | variante | residuel total | delta courant | delta FOR-375 | delta FOR-373 |
|---:|---|---:|---:|---:|---:|
| 1 | `linear_srgb_source_over_effective_destination_nearest_255` | 607 | -249 | -187 | -426 |
| 2 | `straight_srgb_source_over_effective_destination_nearest_255` | 791 | -65 | -3 | -242 |
| 3 | `source_over_effective_destination_floor_256` | 794 | -62 | 0 | -239 |
| 4 | `premultiplied_srgb_terms_floor_256_source_over_effective_destination` | 806 | -50 | 12 | -227 |

## Producteur

- Appel writer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:167`
- Writer FOR-376: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:824`
- Producteur JSON: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1130`
- Sample diagnostique: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1505`
- Definitions variantes: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2151`
- Formule linear-sRGB: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2253`
- Classification: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2083`

## Samples

| # | x | y | bande | reference RGBA | current RGBA | candidate FOR-375 | meilleure variante | candidate meilleure variante | residuel courant | residuel FOR-375 | residuel meilleure | delta courant | delta FOR-375 |
|---|---:|---:|---|---|---|---|---|---|---:|---:|---:|---:|---:|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 150, 143, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[164, 151, 173, 255]` | 105 | 109 | 73 | -32 | -36 |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 150, 143, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[164, 151, 173, 255]` | 105 | 109 | 73 | -32 | -36 |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 150, 143, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[164, 151, 173, 255]` | 105 | 109 | 73 | -32 | -36 |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 150, 143, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[164, 151, 173, 255]` | 105 | 109 | 73 | -32 | -36 |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 150, 143, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[164, 151, 173, 255]` | 105 | 109 | 73 | -32 | -36 |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 150, 143, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[164, 151, 173, 255]` | 105 | 109 | 73 | -32 | -36 |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[191, 213, 240, 255]` | `straight_srgb_source_over_effective_destination_nearest_255` | `[191, 213, 239, 255]` | 56 | 16 | 15 | -41 | -1 |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[159, 193, 188, 255]` | `linear_srgb_source_over_effective_destination_nearest_255` | `[207, 198, 211, 255]` | 52 | 67 | 51 | -1 | -16 |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[95, 150, 214, 255]` | `premultiplied_srgb_terms_floor_256_source_over_effective_destination` | `[95, 150, 214, 255]` | 52 | 38 | 38 | -14 | 0 |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[191, 222, 210, 255]` | `straight_srgb_source_over_effective_destination_nearest_255` | `[191, 222, 209, 255]` | 66 | 19 | 18 | -48 | -1 |

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend runtime, score, seuil ou promotion.
- Les variantes restent des preuves diagnostiques calculees, jamais lues depuis le renderer ou appliquees.

## Validations

- `rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py`
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
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for376-pycache python3 -m py_compile scripts/validate_for376_m60_f16_composition_quantization_candidate.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
