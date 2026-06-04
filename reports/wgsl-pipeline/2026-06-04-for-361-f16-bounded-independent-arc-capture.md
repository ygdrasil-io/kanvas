# FOR-361 F16 Bounded Independent Arc Capture

Linear: `FOR-361`

Decision: `F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-361 creates a bounded independent Rec.2020 `kRGBA_F16Norm` `drawArc`
fixture outside the FOR-340/FOR-341 adjacent `CircularArcsStrokeButt` groups.
It reuses the FOR-355 candidate exactly and keeps the work evidence-only.

## Result

The current Kanvas capture matches the Skia over-white reference on this
bounded arc row. The FOR-355 candidate applies only to the `stroke-center`
sample with raw alpha `100`; there it computes `[155, 155, 255, 255]`, while
the captured Skia over-white reference is `[180, 167, 255, 255]`. This creates
a candidate residual of `37`, so the independent arc capture rejects the
candidate.

## Samples

| sample | x,y | zone | raw reference | reference | current | candidate | FOR-355 rule applies | current residual | candidate residual | worsened |
|---|---|---|---|---|---|---|---:|---:|---:|---|
| for361_background_top_left | 0,0 | background | [0, 0, 0, 0] | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | no | 0 | 0 | no |
| for361_arc_right_stroke_center | 52,32 | stroke-center | [0, 0, 255, 100] | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | yes | 0 | 37 | yes |
| for361_arc_lower_right_stroke | 44,46 | stroke-edge | [0, 0, 255, 100] | [180, 167, 255, 255] | [180, 167, 255, 255] | [180, 167, 255, 255] | no | 0 | 0 | no |
| for361_arc_interior_clear | 32,32 | interior-clear | [0, 0, 0, 0] | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | no | 0 | 0 | no |

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | 4 |
| covered samples | 2 |
| current residual | 0 |
| candidate residual | 37 |
| residual delta | 37 |
| worsened samples | 1 |
| FOR-355 rule-applied samples | 1 |

## Independence Boundary

- Bounded micro-fixture: `drawArc` oval `[12, 12, 40, 40]`, start `0`, sweep
  `120`, `kRound_Cap`.
- Selected-cell substitution used: `False`.
- Full-GM crop used: `False`.
- FOR-340/FOR-341 adjacent groups reused as proof: `False`.
- Renderer behavior changed: `False`.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No implementation plan authorization.
- No score increase.
- No threshold, GPU/WGSL, geometry, coverage, fallback, promotion, score, or
  Kadre change.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-bounded-independent-arc-capture-for361/f16-bounded-independent-arc-capture-for361.json`
- Skia samples: `reports/wgsl-pipeline/scenes/artifacts/f16-bounded-independent-arc-capture-for361/skia-reference-samples.json`
- Kanvas samples: `reports/wgsl-pipeline/scenes/artifacts/f16-bounded-independent-arc-capture-for361/current-kanvas-samples.json`
- Skia PNG: `reports/wgsl-pipeline/scenes/artifacts/f16-bounded-independent-arc-capture-for361/skia-reference.png`
- Validator: `scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-361-f16-bounded-independent-arc-capture.md`

## Validation

- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 tools/skia-reference/build_for361_f16_bounded_independent_arc_capture.py`
- `KANVAS_FOR361_CURRENT_CAPTURE_OUTPUT=reports/wgsl-pipeline/scenes/artifacts/f16-bounded-independent-arc-capture-for361/current-kanvas-samples.json rtk ./gradlew --no-daemon --rerun-tasks :skia-integration-tests:test --tests org.skia.tests.For361BoundedIndependentArcF16CurrentCaptureTest`
- `rtk python3 scripts/validate_for360_f16_independent_arc_scene_evidence.py`
- `rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 -m py_compile scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
