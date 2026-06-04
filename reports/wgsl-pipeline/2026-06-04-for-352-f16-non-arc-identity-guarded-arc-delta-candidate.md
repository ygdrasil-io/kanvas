# FOR-352 F16 Non-Arc Identity Guarded Arc Delta Candidate

Linear: `FOR-352`

Decision: `F16_NON_ARC_IDENTITY_GUARDED_ARC_DELTA_CANDIDATE_REJECTED`

Candidate: `non_arc_identity_guarded_arc_delta_zero_probe`

FOR-352 evaluates the safest first member of the FOR-351 candidate family. It
preserves the FOR-345 non-arc identity guard, then confirms that this zero-delta
probe does not reduce the FOR-341 arc residual. The candidate is rejected.

## Non-Arc Guard

- Required residual: `0`
- Candidate residual: `0`
- Required worsened samples: `0`
- Candidate worsened samples: `0`
- Guard passed: `True`

## Arc Evaluation

- Current residual: `375`
- Candidate residual: `375`
- Residual reduction: `0`
- Positive arc reduction: `False`

## Selection Criteria

- `non-arc-residual-zero`: pass
- `non-arc-worsened-sample-zero`: pass
- `positive-arc-residual-reduction`: fail
- `no-renderer-change-before-decision`: pass

## Result

The candidate is safe for the current non-arc guard but has no arc improvement.
It is not selected for implementation and does not authorize a score increase.

The next search step needs a nonzero analytic arc delta that still keeps the
FOR-345 residual at `0`.

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

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-non-arc-identity-guarded-arc-delta-candidate-for352/f16-non-arc-identity-guarded-arc-delta-candidate-for352.json`
- Validator: `scripts/validate_for352_f16_non_arc_identity_guarded_arc_delta_candidate.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-352-f16-non-arc-identity-guarded-arc-delta-candidate.md`

## Validation

- `rtk python3 scripts/validate_for352_f16_non_arc_identity_guarded_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- `rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
