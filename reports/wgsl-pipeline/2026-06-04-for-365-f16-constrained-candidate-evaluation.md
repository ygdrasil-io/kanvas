# FOR-365 F16 Constrained Candidate Evaluation

Linear: `FOR-365`

Decision: `F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS`

Candidate: `covered_source_alpha_src_over_white_without_non_arc_guard_probe`

FOR-365 evaluates exactly one new evidence-only F16 candidate after FOR-364. It
does not change renderer behavior, raise score, change thresholds, authorize
implementation, or alter GPU/WGSL, geometry, coverage, fallback, Kadre, F16
premul, blend, or `SkBitmap.getPixel` behavior.

## Candidate Formula

Evidence-only probe: for any covered sample with captured source/raw RGBA alpha in (0, 255), compute that source color alpha-composited over white; otherwise preserve current Kanvas RGBA. The computed samples are artifact values only and are not renderer behavior.

The candidate is distinct from closed FOR-355 policy
`nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard` and reuses none of its selection state.

## Required Inputs

- FOR-364: `F16_INDEPENDENT_COMPARABLE_ARC_EVIDENCE_CAPTURED`
- FOR-363: `F16_CONSTRAINED_CANDIDATE_SEARCH_MATRIX_READY`
- FOR-362: `F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361`
- FOR-361: `F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE`
- FOR-358: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`
- FOR-355: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`
- FOR-345: `F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE`

FOR-361 rejection evidence is preserved: current residual
`0`, rejected FOR-355 candidate residual
`37`, delta
`37`, worsened samples
`1`.

## Guard Evaluation

| source | row | current residual | candidate residual | delta | worsened samples | guard |
|---|---|---:|---:|---:|---:|---|
| `FOR-345` | `non-arc-rec2020-f16-src-over-rect` | 0 | 111 | 111 | 3 | `worsened` |
| `FOR-358` | `for358-non-arc-rec2020-f16-src-over-green-rect` | 3 | 150 | 147 | 3 | `worsened` |
| `FOR-361` | `for361-bounded-independent-round-cap-arc-rec2020-f16` | 0 | 74 | 74 | 2 | `worsened` |
| `FOR-364` | `for364-independent-butt-cap-arc-rec2020-f16` | 0 | 92 | 92 | 2 | `worsened` |

Result: the candidate is rejected because
`anyGuardWorsened=True`. This rejection is
evidence-only and does not authorize an implementation.

## Candidate Samples

Candidate samples are computed only in
`reports/wgsl-pipeline/scenes/artifacts/f16-constrained-candidate-evaluation-for365/f16-constrained-candidate-evaluation-for365.json`.

### FOR-345 Samples

| sample | zone | rule applied | current residual | candidate residual | delta | worsens |
|---|---|---:|---:|---:|---:|---|
| `background_top_left` | `background` | `False` | 0 | 0 | 0 | `False` |
| `rect_center` | `fill-center` | `True` | 0 | 37 | 37 | `True` |
| `rect_left_inside` | `fill-left-inside` | `True` | 0 | 37 | 37 | `True` |
| `rect_right_inside` | `fill-right-inside` | `True` | 0 | 37 | 37 | `True` |

### FOR-358 Samples

| sample | zone | rule applied | current residual | candidate residual | delta | worsens |
|---|---|---:|---:|---:|---:|---|
| `for358_background_top_left` | `background` | `False` | 0 | 0 | 0 | `False` |
| `for358_rect_center` | `fill-center` | `True` | 1 | 50 | 49 | `True` |
| `for358_rect_left_inside` | `fill-left-inside` | `True` | 1 | 50 | 49 | `True` |
| `for358_rect_bottom_inside` | `fill-bottom-inside` | `True` | 1 | 50 | 49 | `True` |

### FOR-361 Samples

| sample | zone | rule applied | current residual | candidate residual | delta | worsens |
|---|---|---:|---:|---:|---:|---|
| `for361_background_top_left` | `background` | `False` | 0 | 0 | 0 | `False` |
| `for361_arc_right_stroke_center` | `stroke-center` | `True` | 0 | 37 | 37 | `True` |
| `for361_arc_lower_right_stroke` | `stroke-edge` | `True` | 0 | 37 | 37 | `True` |
| `for361_arc_interior_clear` | `interior-clear` | `False` | 0 | 0 | 0 | `False` |

### FOR-364 Samples

| sample | zone | rule applied | current residual | candidate residual | delta | worsens |
|---|---|---:|---:|---:|---:|---|
| `for364_background_top_left` | `background` | `False` | 0 | 0 | 0 | `False` |
| `for364_arc_diagonal_stroke_center` | `stroke-center` | `True` | 0 | 46 | 46 | `True` |
| `for364_arc_top_stroke_center` | `stroke-center` | `True` | 0 | 46 | 46 | `True` |
| `for364_arc_interior_clear` | `interior-clear` | `False` | 0 | 0 | 0 | `False` |

## Disallowed Branch Guards

- `sceneIdShaped`: `False`
- `coordinateShaped`: `False`
- `selectedCellShaped`: `False`
- `fixtureOnlyShaped`: `False`
- `fullGmCropShaped`: `False`
- `rendererBranchRequired`: `False`

## Closed FOR-355 Boundary

- Closed policy: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`
- Reopened by FOR-365: `False`
- Selectable in FOR-365: `False`
- Selected for evaluation: `False`
- Selected for implementation: `False`

## Non-goals Preserved

- No renderer behavior change.
- No score increase, threshold change, candidate implementation, promotion, or
  selectable renderer policy.
- No GPU/WGSL, geometry, coverage, fallback, Kadre, `SkBitmap.getPixel`, F16
  premul, or blend change.
- No scene-id, coordinate, selected-cell, fixture-only, or full-GM-crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-constrained-candidate-evaluation-for365/f16-constrained-candidate-evaluation-for365.json`
- Validator: `scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-365-f16-constrained-candidate-evaluation.md`

## Validation

- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- `rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 -m py_compile scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
