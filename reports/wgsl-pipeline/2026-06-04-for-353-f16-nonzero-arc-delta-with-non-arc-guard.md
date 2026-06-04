# FOR-353 F16 Nonzero Arc Delta With Non-Arc Guard

Linear: `FOR-353`

Decision: `F16_NONZERO_ARC_DELTA_WITH_NON_ARC_GUARD_PARTIAL`

Candidate: `nonzero_analytic_arc_delta_with_non_arc_identity_guard`

FOR-353 evaluates one nonzero arc-delta candidate after the FOR-345 non-arc
guard passes. The candidate proves the target-set arc delta can reduce the
captured FOR-341 residual, but remains partial because it is explicitly shaped
to the FOR-351 target set and is not renderer-selectable.

## Non-Arc Guard

- Required residual: `0`
- Candidate residual: `0`
- Required worsened samples: `0`
- Candidate worsened samples: `0`
- Guard passed: `True`

## Arc Evaluation

- Current residual: `375`
- Candidate residual: `0`
- Residual reduction: `375`
- Positive arc reduction: `True`
- Target-set shaped: `True`
- Renderer selectable: `False`

| sample | group | current residual | candidate residual | reduction |
|---|---|---:|---:|---:|
| `sweep45_blue_top_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | 37 | 0 | 37 |
| `sweep45_blue_left_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | 37 | 0 | 37 |
| `sweep45_red_right_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | 38 | 0 | 38 |
| `sweep45_red_inner_sweep_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | 38 | 0 | 38 |
| `sweep45_blue_bottom_stroke_center` | `adjacent_arc_stroke_start0_sweep45_target` | 37 | 0 | 37 |
| `sweep130_blue_top_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | 37 | 0 | 37 |
| `sweep130_red_right_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | 38 | 0 | 38 |
| `sweep130_red_bottom_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | 38 | 0 | 38 |
| `sweep130_red_inner_sweep_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | 38 | 0 | 38 |
| `sweep130_blue_left_stroke_center` | `adjacent_arc_stroke_start0_sweep130_target` | 37 | 0 | 37 |

## Selection Criteria

- `non-arc-residual-zero`: pass
- `non-arc-worsened-sample-zero`: pass
- `positive-arc-residual-reduction`: pass
- `not-retired-policy`: pass
- `not-previous-rejected-candidate`: pass
- `not-target-set-shaped`: fail
- `no-renderer-change-before-decision`: pass

## Result

The candidate keeps FOR-345 at residual `0` and reduces the FOR-341 target-set
residual from `375` to `0`, but it is still not a renderer candidate. It is
dependent on the measured target set and cannot be selected, promoted, or used
to raise score in this ticket.

## Next Constraint

Broaden or generalize the nonzero arc delta so it is no longer fixture, scene, coordinate, selected-cell, full-GM-crop, or target-set shaped while preserving the FOR-345 residual 0 guard.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution,
  full-GM crop, or threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-nonzero-arc-delta-with-non-arc-guard-for353/f16-nonzero-arc-delta-with-non-arc-guard-for353.json`
- Validator: `scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-353-f16-nonzero-arc-delta-with-non-arc-guard.md`

## Validation

- `rtk python3 scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py`
- `rtk python3 scripts/validate_for352_f16_non_arc_identity_guarded_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- `rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
