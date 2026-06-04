# FOR-351 F16 Non-Arc-Preserving Arc Constraints

Linear: `FOR-351`

Decision: `F16_NON_ARC_PRESERVING_ARC_CONSTRAINTS_READY`

FOR-351 derives the measurable constraints for the next F16 candidate search.
The current non-arc FOR-345 row is already exact, so future candidates must keep
that residual at `0` before any arc gain can count.

## Non-Arc Preserving Constraints

Required residual: `0`

Required worsened samples: `0`

Current residual: `0`

Retired over-white residual: `111`

- `preserve-current-reference-equality`: future candidate residual must stay exactly 0 on the FOR-345 row (current Kanvas samples equal the isolated Skia reference on every sampled point)
- `forbid-over-white-shift-on-covered-non-arc-samples`: covered non-arc samples must not move toward the retired over-white policy (retired policy residual is 111 with 3 worsened covered samples)
- `non-arc-first-selection-gate`: arc improvement cannot count unless non-arc residual remains 0 and worsened samples remain 0 (FOR-350 improved arc but was rejected after producing non-arc residual 56)

## Arc Residual Targets

FOR-341 current arc residual: `375`

Perfect arc-fit reduction target: `375`

| sample | group | current RGBA | target RGBA | current residual |
|---|---|---:|---:|---:|
| `sweep45_blue_top_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 |
| `sweep45_blue_left_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 |
| `sweep45_red_right_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 |
| `sweep45_red_inner_sweep_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 |
| `sweep45_blue_bottom_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 |
| `sweep130_blue_top_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 |
| `sweep130_red_right_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 |
| `sweep130_red_bottom_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 |
| `sweep130_red_inner_sweep_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 |
| `sweep130_blue_left_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 |

## FOR-350 Rejection Carried Forward

`halfway_to_retired_over_white_candidate` reduced the arc residual by
`187`, but introduced non-arc residual
`56` with `3`
worsened samples. That proves linear movement toward the retired over-white
policy is insufficient.

## Next Candidate Families

- `non_arc_identity_guarded_arc_delta_candidate_family`: Evaluate formulas whose first invariant is FOR-345 non-arc residual 0, then measure whether they reduce FOR-341 arc residuals. Status: `not-selected`.
- `additional_non_arc_rows_before_selection`: Add more non-arc rows before any renderer implementation decision if a future candidate passes the current FOR-345 guard. Status: `not-selected`.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-non-arc-preserving-arc-constraints-for351/f16-non-arc-preserving-arc-constraints-for351.json`
- Validator: `scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-351-f16-non-arc-preserving-arc-constraints.md`

## Validation

- `rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- `rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py`
- `rtk python3 scripts/validate_for349_f16_replacement_candidate_evaluation.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
