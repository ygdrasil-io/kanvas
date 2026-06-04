# FOR-354 F16 Nonzero Arc Delta Generalization Constraints

Linear: `FOR-354`

Decision: `F16_NONZERO_ARC_DELTA_GENERALIZATION_CONSTRAINTS_READY`

FOR-354 turns the FOR-353 partial proof into the constraints required for a
future generalized F16 arc-delta candidate. No candidate is selected or
implemented here.

## FOR-353 Status

- Candidate: `nonzero_analytic_arc_delta_with_non_arc_identity_guard`
- Target-set shaped: `True`
- Scene shaped: `True`
- Renderer selectable: `False`
- Selected for implementation: `False`

## Generalization Blockers

- `target-set-dependence`: blocks-renderer-selection - candidate formula must evaluate from general draw/color/coverage state, not target sample ids
- `scene-dependence`: blocks-renderer-selection - candidate must apply across comparable arc evidence without scene identity
- `broader-non-arc-coverage-missing`: blocks-renderer-selection - future evidence must add or preserve broader non-arc rows before implementation
- `non-scene-analytic-rule-missing`: blocks-renderer-selection - candidate must define a renderer-independent rule with stable diagnostics

## Generalization Constraints

- `preserve-for345-non-arc-zero`: candidate residual on FOR-345 remains 0
- `preserve-for345-no-worsened-samples`: candidate worsened sample count on FOR-345 remains 0
- `positive-for341-arc-reduction`: candidate reduces FOR-341 arc residual versus current
- `no-target-set-or-scene-shaping`: candidate is not fixture, coordinate, selected-cell, full-GM-crop, scene, or target-set shaped
- `for348-rejection-criteria-preserved`: candidate passes every FOR-348 pre-evaluation rejection rule before selection
- `renderer-selection-deferred`: candidate remains evidence-only until a later implementation ticket

## Next Candidate Family

`nonzero_arc_delta_generalized_non_scene_guard_family`

Evaluate a generalized nonzero arc-delta formula that preserves FOR-345 residual 0 and produces positive FOR-341 reduction without target-set, scene, fixture, or coordinate shaping.

Status: `recommended-for-next-evaluation`

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

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-nonzero-arc-delta-generalization-constraints-for354/f16-nonzero-arc-delta-generalization-constraints-for354.json`
- Validator: `scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-354-f16-nonzero-arc-delta-generalization-constraints.md`

## Validation

- `rtk python3 scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py`
- `rtk python3 scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py`
- `rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- `rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
