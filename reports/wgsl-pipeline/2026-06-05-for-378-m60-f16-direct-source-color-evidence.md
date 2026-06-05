# FOR-378 Direct source color evidence M60 F16

Linear: `FOR-378`

Decision: `M60_F16_DIRECT_SOURCE_COLOR_EVIDENCE_RECORDED`

Classification: `direct-source-color-confirms-linear-axis`

FOR-378 captures a transparent CPU diagnostic source for the same ten FOR-377
samples. It preserves each FOR-377 sample exactly and appends
`directSourceColorEvidence`; the inverse destination estimate is retained only
as historical FOR-377 context, not as primary proof.

## Result

- Current residual: `856`
- FOR-375 effective destination residual: `794`
- FOR-377 linear-sRGB residual: `607`
- Direct source recomposed-on-white residual: `19`
- Delta versus current: `-837`
- Delta versus FOR-375: `-775`
- Delta versus FOR-377 linear-sRGB: `-588`
- Source alpha versus FOR-372 coverage: total `0`, max `0`
- Paint source unpremultiplied RGB max delta: `114`
- Reason: The transparent-source recomposition improves current, FOR-375, and the FOR-377 linear-sRGB residual without using inverse-destination clamp channels as primary evidence.

## Producer

- Writer call: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:169`
- Writer FOR-378: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:888`
- Transparent source GM: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:604`
- JSON producer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1444`
- Sample audit: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2156`
- Classification: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2739`

## Samples

| # | x | y | band | direct alpha | coverage | alpha delta | direct residual | current | FOR-375 | FOR-377 linear | delta current | delta linear |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 92 | 75 | `round-round` | 160 | 160 | 0 | 2 | 105 | 109 | 73 | -103 | -71 |
| 2 | 91 | 76 | `round-round` | 160 | 160 | 0 | 2 | 105 | 109 | 73 | -103 | -71 |
| 3 | 90 | 77 | `round-round` | 160 | 160 | 0 | 2 | 105 | 109 | 73 | -103 | -71 |
| 4 | 89 | 78 | `round-round` | 160 | 160 | 0 | 2 | 105 | 109 | 73 | -103 | -71 |
| 5 | 88 | 79 | `round-round` | 160 | 160 | 0 | 2 | 105 | 109 | 73 | -103 | -71 |
| 6 | 87 | 80 | `round-round` | 160 | 160 | 0 | 2 | 105 | 109 | 73 | -103 | -71 |
| 7 | 21 | 81 | `butt-bevel` | 64 | 64 | 0 | 0 | 56 | 16 | 31 | -56 | -31 |
| 8 | 93 | 74 | `round-round` | 96 | 96 | 0 | 2 | 52 | 67 | 51 | -50 | -49 |
| 9 | 17 | 77 | `butt-bevel` | 160 | 160 | 0 | 2 | 52 | 38 | 48 | -50 | -46 |
| 10 | 69 | 81 | `round-round` | 64 | 64 | 0 | 3 | 66 | 19 | 39 | -63 | -36 |

## Validation Commands

- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py`
- `rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py`
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
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for378-pycache python3 -m py_compile scripts/validate_for378_m60_f16_direct_source_color_evidence.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
