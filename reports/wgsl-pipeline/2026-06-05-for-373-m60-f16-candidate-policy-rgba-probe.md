# FOR-373 Candidate policy RGBA M60 F16

Linear: `FOR-373`

Decision: `M60_F16_CANDIDATE_POLICY_RGBA_PROBE_RECORDED`

Classification: `candidate-policy-regresses`

FOR-373 calcule une candidate diagnostique par sample M60 depuis les champs
FOR-372. La candidate n'est pas appliquee au renderer et n'est pas lue depuis
une image GPU.

## Formule

Politique: `straight_srgb_quantized_alpha_src_over_white`.

Ordre de calcul:

1. `alphaByte = round((paintAlpha / 255.0) * (sourceCoverageByte / 255.0) * 255)`.
2. `alpha = alphaByte / 255.0`.
3. Pour chaque canal source sRGB droit: `floor(((source / 255.0) * alpha + (1.0 - alpha)) * 256.0)`.
4. Chaque canal est borne dans `[0,255]`; alpha de sortie fixe a `255`.

## Resultat

- Residuel courant: `856`
- Residuel candidate: `1033`
- Delta candidate versus courant: `177`

## Producteur

- Appel writer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:164`
- Writer FOR-373: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:785`
- Producteur JSON: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:797`
- Formule candidate: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1251`
- Garde lecture renderer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:839`
- Garde application renderer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:841`

## Samples

| # | x | y | bande | reference RGBA | current/gpu RGBA | source paint RGBA | coverage byte | coverage | candidatePolicyRgba | residuel courant | residuel candidate | delta | ameliore |
|---|---:|---:|---|---|---|---|---:|---:|---|---:|---:|---:|---|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | `[95, 182, 143, 255]` | 105 | 141 | 36 | False |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | `[95, 182, 143, 255]` | 105 | 141 | 36 | False |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | `[95, 182, 143, 255]` | 105 | 141 | 36 | False |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | `[95, 182, 143, 255]` | 105 | 141 | 36 | False |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | `[95, 182, 143, 255]` | 105 | 141 | 36 | False |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | `[95, 182, 143, 255]` | 105 | 141 | 36 | False |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[0, 102, 204, 255]` | 64 | 0.250980 | `[191, 217, 243, 255]` | 56 | 23 | -33 | True |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[0, 138, 76, 255]` | 96 | 0.376471 | `[159, 211, 188, 255]` | 52 | 85 | 33 | False |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[0, 102, 204, 255]` | 160 | 0.627451 | `[95, 159, 223, 255]` | 52 | 56 | 4 | False |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[0, 138, 76, 255]` | 64 | 0.250980 | `[191, 226, 210, 255]` | 66 | 23 | -43 | True |

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend, score, seuil ou promotion.
- La candidate reste une preuve diagnostique et n'est pas appliquee.

## Validations

- `rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py`
- `rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py`
- `rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py`
- `rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for373-pycache python3 -m py_compile scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
