# FOR-355 F16 Generalized Non-Scene Arc Delta Candidate

Linear: `FOR-355`

Decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

Family: `nonzero_arc_delta_generalized_non_scene_guard_family`

FOR-355 evaluates one evidence-only candidate from
`nonzero_arc_delta_generalized_non_scene_guard_family`. The candidate is not
selected for implementation and does not authorize renderer, score, threshold,
GPU/WGSL, geometry, coverage, fallback, promotion, or Kadre changes.

## Candidate Formula

Evidence-only generalized non-scene probe: evaluate the FOR-345 non-arc guard first; if it stays exact, preserve current RGBA except for samples with zone `stroke-center` and a captured raw reference alpha in (0, 255), where the candidate computes the raw source RGBA alpha-composited over white. This is artifact evaluation, not renderer logic.

## Distinctness

- Retired policy `straight_srgb_quantized_alpha_src_over_white`: `False`
- FOR-350 candidate `halfway_to_retired_over_white_candidate`: `False`
- FOR-352 candidate `non_arc_identity_guarded_arc_delta_zero_probe`: `False`
- FOR-353 candidate `nonzero_analytic_arc_delta_with_non_arc_identity_guard`: `False`

## Shaping Diagnostics

- Target-set shaped: `False`
- Scene shaped: `False`
- Fixture shaped: `False`
- Coordinate shaped: `False`
- Selected-cell shaped: `False`
- Full-GM-crop shaped: `False`
- Family-zone shaped: `True` (`stroke-center`)

The candidate uses the cross-artifact sample zone and partial raw-alpha evidence; it does not use target sample ids, scene ids, fixture ids, coordinates, selected cells, or full-GM crops.

## Evaluation Order

1. FOR-345 non-arc guard.
2. FOR-341 arc residual evaluation only after the guard passes.

## FOR-345 Non-Arc Guard

- Required residual: `0`
- Candidate residual: `0`
- Required worsened samples: `0`
- Candidate worsened samples: `0`
- Guard passed: `True`

## FOR-341 Arc Evaluation

- Current residual: `375`
- Candidate residual: `0`
- Residual reduction: `375`
- Positive arc reduction: `True`
- Rule-applied sample count: `10`

| sample | group | rule applied | current residual | candidate residual | reduction |
|---|---|---:|---:|---:|---:|
| `sweep45_blue_top_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | `True` | 37 | 0 | 37 |
| `sweep45_blue_left_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | `True` | 37 | 0 | 37 |
| `sweep45_red_right_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | `True` | 38 | 0 | 38 |
| `sweep45_red_inner_sweep_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | `True` | 38 | 0 | 38 |
| `sweep45_blue_bottom_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | `True` | 37 | 0 | 37 |
| `sweep130_blue_top_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | `True` | 37 | 0 | 37 |
| `sweep130_red_right_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | `True` | 38 | 0 | 38 |
| `sweep130_red_bottom_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | `True` | 38 | 0 | 38 |
| `sweep130_red_inner_sweep_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | `True` | 38 | 0 | 38 |
| `sweep130_blue_left_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | `True` | 37 | 0 | 37 |

## Criteria

- `non-arc-residual-zero`: pass
- `non-arc-worsened-sample-zero`: pass
- `positive-arc-residual-reduction`: pass
- `not-retired-policy`: pass
- `not-for350-candidate`: pass
- `not-for352-candidate`: pass
- `not-for353-candidate`: pass
- `not-target-set-shaped`: pass
- `not-scene-shaped`: pass
- `not-fixture-shaped`: pass
- `not-coordinate-shaped`: pass
- `not-selected-cell-shaped`: pass
- `not-full-gm-crop-shaped`: pass
- `no-renderer-change-before-decision`: pass
- `score-not-raised`: pass

## Result

The candidate is ready for broader evidence because it preserves the FOR-345 non-arc guard, then reduces the FOR-341 arc residual from 375 to 0. It remains evidence-only and cannot be selected, implemented, promoted, or used for score in FOR-355.

## Non-goals Preserved

- No renderer behavior change.
- No renderer branch by fixture, coordinate, scene, selected cell, or full-GM crop.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-generalized-non-scene-arc-delta-candidate-for355/f16-generalized-non-scene-arc-delta-candidate-for355.json`
- Validator: `scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-355-f16-generalized-non-scene-arc-delta-candidate.md`

## Validation

- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py`
- `rtk python3 scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py`
- `rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 -m py_compile scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
