# FOR-379 Effective source/color path M60 F16

Linear: `FOR-379`

Decision: `M60_F16_EFFECTIVE_SOURCE_COLOR_PATH_RECORDED`

Classification: `source-color-path-ready-for-correction`

FOR-379 preserves the exact ten FOR-378 samples and adds an
`effectiveSourceColorPath` block per sample. The added comparison is
`currentRgba` versus the transparent direct source recomposed on white; it does
not use the clamped inverse destination estimate as primary evidence.

## Result

- Current residual: `856`
- Direct source recomposed-on-white residual: `19`
- Gain versus current: `837`
- Current/direct RGBA distance total: `869`
- Ready samples: `10`
- Ambiguous samples: `0`
- Insufficient samples: `0`
- Current error totals: `{'r': 385, 'g': 327, 'b': 144, 'a': 0}`
- Direct recomposed error totals: `{'r': 9, 'g': 9, 'b': 1, 'a': 0}`
- Improvement totals: `{'r': 376, 'g': 318, 'b': 143, 'a': 0}`
- Reason: All ten FOR-378 samples have exact coverage/alpha agreement, valid premultiplied source channels, and direct source recomposition residuals at or below 4; the total residual falls from 856 to 19.

## Producer

- Writer call: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:170`
- Writer FOR-379: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:901`
- JSON producer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1580`
- Sample audit: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2401`
- Classification: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:3011`

## Samples

| # | x | y | band | alpha | coverage | direct residual | current residual | gain | current/direct distance | classification |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---|
| 1 | 92 | 75 | `round-round` | 160 | 160 | 2 | 105 | 103 | 107 | `source-color-ready-for-correction` |
| 2 | 91 | 76 | `round-round` | 160 | 160 | 2 | 105 | 103 | 107 | `source-color-ready-for-correction` |
| 3 | 90 | 77 | `round-round` | 160 | 160 | 2 | 105 | 103 | 107 | `source-color-ready-for-correction` |
| 4 | 89 | 78 | `round-round` | 160 | 160 | 2 | 105 | 103 | 107 | `source-color-ready-for-correction` |
| 5 | 88 | 79 | `round-round` | 160 | 160 | 2 | 105 | 103 | 107 | `source-color-ready-for-correction` |
| 6 | 87 | 80 | `round-round` | 160 | 160 | 2 | 105 | 103 | 107 | `source-color-ready-for-correction` |
| 7 | 21 | 81 | `butt-bevel` | 64 | 64 | 0 | 56 | 56 | 56 | `source-color-ready-for-correction` |
| 8 | 93 | 74 | `round-round` | 96 | 96 | 2 | 52 | 50 | 54 | `source-color-ready-for-correction` |
| 9 | 17 | 77 | `butt-bevel` | 160 | 160 | 2 | 52 | 50 | 54 | `source-color-ready-for-correction` |
| 10 | 69 | 81 | `round-round` | 64 | 64 | 3 | 66 | 63 | 63 | `source-color-ready-for-correction` |

## Validation Commands

- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py`
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
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for379-pycache python3 -m py_compile scripts/validate_for379_m60_f16_effective_source_color_path.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
