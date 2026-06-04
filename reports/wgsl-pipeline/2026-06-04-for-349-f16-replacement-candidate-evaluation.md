# FOR-349 F16 Replacement Candidate Evaluation

Linear: `FOR-349`

Decision: `F16_REPLACEMENT_CANDIDATE_EVALUATED_REJECTED`

Candidate: `current_color_managed_rec2020_f16_src_over_then_srgb_export_control`

FOR-349 evaluates a first replacement candidate against the FOR-348 arc plus
non-arc matrix. The candidate is a current-control baseline, distinct from
`straight_srgb_quantized_alpha_src_over_white`, and is rejected because it produces no positive residual
reduction.

## Result

The candidate does not worsen current behavior, but it also does not improve
any matrix row. It therefore fails the minimum selection criterion requiring
positive residual evidence on both arc and non-arc rows.

## Evaluation Rows

| row | family | current residual | candidate residual | residual reduction | worsened samples |
|---|---|---:|---:|---:|---:|
| `arc-circular-arcs-stroke-butt-adjacent-for341` | arc | 375 | 375 | 0 | 0 |
| `non-arc-rec2020-f16-src-over-rect-for345` | non-arc | 0 | 0 | 0 | 0 |

## FOR-348 Rejection Criteria

- `retired-policy-reuse`: pass
- `missing-arc-or-non-arc-row`: pass
- `worsens-current-on-any-covered-reference-row`: pass
- `scene-shaped-branch`: pass
- `score-before-selection`: pass

## Minimum Selection Criteria

- `arc-and-non-arc-positive`: fail
- `no-covered-sample-regression`: pass
- `explicit-route-diagnostics`: pass
- `no-renderer-change-before-decision`: pass

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

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-replacement-candidate-evaluation-for349/f16-replacement-candidate-evaluation-for349.json`
- Validator: `scripts/validate_for349_f16_replacement_candidate_evaluation.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-349-f16-replacement-candidate-evaluation.md`

## Validation

- `rtk python3 scripts/validate_for349_f16_replacement_candidate_evaluation.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
