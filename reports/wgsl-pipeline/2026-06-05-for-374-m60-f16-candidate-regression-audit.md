# FOR-374 Candidate regression audit M60 F16

Linear: `FOR-374`

Decision: `M60_F16_CANDIDATE_REGRESSION_AUDIT_RECORDED`

Classification: `candidate-regression-likely-destination-model`

FOR-374 audite pourquoi la candidate `straight_srgb_quantized_alpha_src_over_white` issue de
FOR-373 regresse M60 F16. L'audit ne produit pas de correction et n'applique
aucun changement renderer/runtime.

## Resultat

- Residuel courant preserve: `856`
- Residuel candidate preserve: `1033`
- Delta preserve: `177`
- Samples qui regressent: `8`
- Samples ameliores: `2`
- Conflits avec destination blanche: `10`
- Direction source trop forte: `8`
- Parametre probablement manquant: `effective-destination-color-or-background-model-before-stroke-composition`

La destination inverse simple demande souvent une destination non blanche ou
hors sRGB pour reproduire la reference avec la source et la couverture FOR-373.
Le prochain axe experimental doit donc isoler le modele de destination effective
avant d'essayer une correction de rendu.

## Producteur

- Appel writer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:165`
- Writer FOR-374: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:798`
- Producteur JSON: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:890`
- Estimation destination inverse: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1556`
- Classification: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1599`
- Garde renderer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:958`

## Samples

| # | x | y | bande | reference RGBA | current RGBA | candidate RGBA | residuel courant | residuel candidate | delta | erreur candidate | erreur current | delta erreur | canal | direction | destination inverse RGB |
|---|---:|---:|---|---|---|---|---:|---:|---:|---|---|---|---|---|---|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | 105 | 141 | 36 | `{'r': 38, 'g': 32, 'b': 71, 'a': 0}` | `{'r': 48, 'g': 41, 'b': 16, 'a': 0}` | `{'r': -10, 'g': -9, 'b': 55, 'a': 0}` | `blue` | `source-tint-too-strong` | `[357, 170, 446]` |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | 105 | 141 | 36 | `{'r': 38, 'g': 32, 'b': 71, 'a': 0}` | `{'r': 48, 'g': 41, 'b': 16, 'a': 0}` | `{'r': -10, 'g': -9, 'b': 55, 'a': 0}` | `blue` | `source-tint-too-strong` | `[357, 170, 446]` |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | 105 | 141 | 36 | `{'r': 38, 'g': 32, 'b': 71, 'a': 0}` | `{'r': 48, 'g': 41, 'b': 16, 'a': 0}` | `{'r': -10, 'g': -9, 'b': 55, 'a': 0}` | `blue` | `source-tint-too-strong` | `[357, 170, 446]` |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | 105 | 141 | 36 | `{'r': 38, 'g': 32, 'b': 71, 'a': 0}` | `{'r': 48, 'g': 41, 'b': 16, 'a': 0}` | `{'r': -10, 'g': -9, 'b': 55, 'a': 0}` | `blue` | `source-tint-too-strong` | `[357, 170, 446]` |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | 105 | 141 | 36 | `{'r': 38, 'g': 32, 'b': 71, 'a': 0}` | `{'r': 48, 'g': 41, 'b': 16, 'a': 0}` | `{'r': -10, 'g': -9, 'b': 55, 'a': 0}` | `blue` | `source-tint-too-strong` | `[357, 170, 446]` |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[95, 182, 143, 255]` | 105 | 141 | 36 | `{'r': 38, 'g': 32, 'b': 71, 'a': 0}` | `{'r': 48, 'g': 41, 'b': 16, 'a': 0}` | `{'r': -10, 'g': -9, 'b': 55, 'a': 0}` | `blue` | `source-tint-too-strong` | `[357, 170, 446]` |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[191, 217, 243, 255]` | 56 | 23 | -33 | `{'r': 15, 'g': 4, 'b': 4, 'a': 0}` | `{'r': 25, 'g': 22, 'b': 9, 'a': 0}` | `{'r': -10, 'g': -18, 'b': -5, 'a': 0}` | `none` | `candidate-improves-or-neutral` | `[275, 250, 251]` |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[159, 211, 188, 255]` | 52 | 85 | 33 | `{'r': 23, 'g': 19, 'b': 43, 'a': 0}` | `{'r': 24, 'g': 21, 'b': 7, 'a': 0}` | `{'r': -1, 'g': -2, 'b': 36, 'a': 0}` | `blue` | `source-tint-too-strong` | `[292, 225, 325]` |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[95, 159, 223, 255]` | 52 | 56 | 4 | `{'r': 38, 'g': 9, 'b': 9, 'a': 0}` | `{'r': 24, 'g': 20, 'b': 8, 'a': 0}` | `{'r': 14, 'g': -11, 'b': 1, 'a': 0}` | `red` | `source-tint-too-strong` | `[357, 231, 231]` |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[191, 226, 210, 255]` | 66 | 23 | -43 | `{'r': 18, 'g': 4, 'b': 1, 'a': 0}` | `{'r': 24, 'g': 18, 'b': 24, 'a': 0}` | `{'r': -6, 'g': -14, 'b': -23, 'a': 0}` | `none` | `candidate-improves-or-neutral` | `[279, 250, 254]` |

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend, score, seuil ou promotion.
- L'estimation de destination inverse reste une preuve diagnostique et n'est pas appliquee.

## Validations

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
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for374-pycache python3 -m py_compile scripts/validate_for374_m60_f16_candidate_regression_audit.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
