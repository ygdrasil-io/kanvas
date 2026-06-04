# FOR-348 F16 New-Candidate Search Matrix

Linear: `FOR-348`

Decision: `F16_NEW_CANDIDATE_SEARCH_MATRIX_READY`

FOR-348 starts the `new-candidate-search` route selected by FOR-347. It builds
an arc plus non-arc matrix for future candidate evaluation. It does not select
a new candidate, change renderer behavior, or raise score.

## Result

The search matrix is ready with `1` arc row and
`1` non-arc row.

`straight_srgb_quantized_alpha_src_over_white` remains forbidden as a global candidate.

## Matrix Rows

| row | family | reference | current | candidate | current residual | candidate residual | worsened samples |
|---|---|---|---|---|---:|---:|---:|
| `arc-circular-arcs-stroke-butt-adjacent-for341` | arc | available-isolated-skia-adjacent-cell-render | available-current-for339-export-rgba | historical-retired-candidate-baseline-only | 375 | 0 | 0 |
| `non-arc-rec2020-f16-src-over-rect-for345` | non-arc | available-isolated-skia-non-arc-rec2020-f16-src-over-reference | available-current-kanvas-kotlin-cpu-rec2020-f16-src-over-samples | historical-retired-candidate-rejected | 0 | 111 | 3 |

## Rejection Criteria Before Evaluation

- `retired-policy-reuse`: reject when candidate policy id is `straight_srgb_quantized_alpha_src_over_white` or algebraically identical without new evidence
- `missing-arc-or-non-arc-row`: reject when candidate lacks at least one comparable arc row and one comparable non-arc row
- `worsens-current-on-any-covered-reference-row`: reject when candidate residual exceeds current residual on any covered comparable sample group
- `scene-shaped-branch`: reject when candidate requires fixture, coordinate, selected-cell, or full-GM-crop branching
- `score-before-selection`: reject when candidate is used to raise F16 score before selection evidence is complete

## Minimum Selection Criteria

- `arc-and-non-arc-positive`: positive residual evidence on both arc and non-arc comparable rows
- `no-covered-sample-regression`: zero covered samples worsened versus current across the required matrix
- `explicit-route-diagnostics`: stable diagnostics for unsupported or out-of-domain routes
- `no-renderer-change-before-decision`: candidate remains evidence-only until a later implementation ticket

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No new candidate selected.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-new-candidate-search-matrix-for348/f16-new-candidate-search-matrix-for348.json`
- Validator: `scripts/validate_for348_f16_new_candidate_search_matrix.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-348-f16-new-candidate-search-matrix.md`

## Validation

- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
