# FOR-377 Linear-sRGB plausibility audit M60 F16

Linear: `FOR-377`

Decision: `M60_F16_LINEAR_SRGB_PLAUSIBILITY_AUDIT_RECORDED`

Classification: `linear-srgb-mixed-needs-reference-color-evidence`

FOR-377 audits the FOR-376 diagnostic variant
`linear_srgb_source_over_effective_destination_nearest_255` without changing
renderer/runtime behavior. The audit preserves the ten FOR-376 samples exactly
and appends a per-sample `linearSrgbPlausibilityAudit` block.

## Result

- Current residual: `856`
- FOR-375 effective destination residual: `794`
- Linear-sRGB residual: `607`
- Delta versus current: `-249`
- Delta versus FOR-375: `-187`
- Destination clamp positive improvement: `118` / `249` (`0.473896`)
- Reason: The variant improves every sample against current and strongly improves round-round, but butt-bevel regresses against FOR-375 and nearly half of the positive gain comes from clamped inverse-destination channels.

## Band Coherence

| band | samples | current | linear-sRGB | FOR-375 | delta current | delta FOR-375 | coherence |
|---|---:|---:|---:|---:|---:|---:|---|
| `butt-bevel` | 2 | 108 | 79 | 54 | -29 | 25 | `improves-current-but-regresses-for375` |
| `round-round` | 8 | 748 | 528 | 740 | -220 | -212 | `improves-current-and-for375` |

## Producer

- Writer call: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:168`
- Writer FOR-377: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:837`
- JSON producer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1260`
- Sample audit: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:1785`
- Classification: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:2411`

## Samples

| # | x | y | band | current residual | linear-sRGB residual | FOR-375 residual | delta current | delta FOR-375 | clamp channels | clamp gain | coherence |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 92 | 75 | `round-round` | 105 | 73 | 109 | -32 | -36 | 2 | 17 | `improves-current-and-for375` |
| 2 | 91 | 76 | `round-round` | 105 | 73 | 109 | -32 | -36 | 2 | 17 | `improves-current-and-for375` |
| 3 | 90 | 77 | `round-round` | 105 | 73 | 109 | -32 | -36 | 2 | 17 | `improves-current-and-for375` |
| 4 | 89 | 78 | `round-round` | 105 | 73 | 109 | -32 | -36 | 2 | 17 | `improves-current-and-for375` |
| 5 | 88 | 79 | `round-round` | 105 | 73 | 109 | -32 | -36 | 2 | 17 | `improves-current-and-for375` |
| 6 | 87 | 80 | `round-round` | 105 | 73 | 109 | -32 | -36 | 2 | 17 | `improves-current-and-for375` |
| 7 | 21 | 81 | `butt-bevel` | 56 | 31 | 16 | -25 | 15 | 1 | 7 | `improves-current-but-regresses-for375` |
| 8 | 93 | 74 | `round-round` | 52 | 51 | 67 | -1 | -16 | 2 | 0 | `improves-current-and-for375` |
| 9 | 17 | 77 | `butt-bevel` | 52 | 48 | 38 | -4 | 10 | 1 | 0 | `improves-current-but-regresses-for375` |
| 10 | 69 | 81 | `round-round` | 66 | 39 | 19 | -27 | 20 | 1 | 9 | `improves-current-but-regresses-for375` |

## Validation Commands

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
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for377-pycache python3 -m py_compile scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
