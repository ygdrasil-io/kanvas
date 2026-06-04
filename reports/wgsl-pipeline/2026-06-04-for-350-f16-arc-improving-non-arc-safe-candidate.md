# FOR-350 F16 Arc-Improving Non-Arc-Safe Candidate

Linear: `FOR-350`

Decision: `F16_ARC_IMPROVING_NON_ARC_SAFE_CANDIDATE_REJECTED`

Candidate: `halfway_to_retired_over_white_candidate`

FOR-350 evaluates a candidate that partially moves from current behavior toward
the retired over-white baseline. It improves the arc row, but it regresses the
non-arc FOR-345 row, so it is rejected.

## Evaluation Rows

| row | family | current residual | retired residual | candidate residual | residual reduction | worsened samples |
|---|---|---:|---:|---:|---:|---:|
| `arc-circular-arcs-stroke-butt-adjacent-for341` | arc | 375 | 0 | 188 | 187 | 0 |
| `non-arc-rec2020-f16-src-over-rect-for345` | non-arc | 0 | 111 | 56 | -56 | 3 |

## FOR-348 Rejection Criteria

- `retired-policy-reuse`: pass
- `missing-arc-or-non-arc-row`: pass
- `worsens-current-on-any-covered-reference-row`: fail
- `scene-shaped-branch`: pass
- `score-before-selection`: pass

## Minimum Selection Criteria

- `arc-and-non-arc-positive`: fail
- `no-covered-sample-regression`: fail
- `explicit-route-diagnostics`: pass
- `no-renderer-change-before-decision`: pass

## Result

The candidate improves the arc row but fails the non-arc safety requirement.
It is not selected for implementation and does not authorize a score increase.

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

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-arc-improving-non-arc-safe-candidate-for350/f16-arc-improving-non-arc-safe-candidate-for350.json`
- Validator: `scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-350-f16-arc-improving-non-arc-safe-candidate.md`

## Validation

- `rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py`
- `rtk python3 scripts/validate_for349_f16_replacement_candidate_evaluation.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
