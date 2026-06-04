# FOR-363 F16 Constrained Candidate Search

Linear: `FOR-363`

Decision: `F16_CONSTRAINED_CANDIDATE_SEARCH_MATRIX_READY`

FOR-363 restarts F16 candidate search after the rejected FOR-355 candidate was
closed. It is evidence-only: it does not reopen the rejected candidate, define a
selectable new candidate, select any candidate family for evaluation, raise
score, or change renderer behavior.

## Required Gates

- FOR-362 required closeout decision: `F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361`
- FOR-361 decision: `F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE`
- FOR-358 decision: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`
- FOR-355 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`

The FOR-355 candidate remains unselected and unavailable for reuse:
`nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`.

## Rejection Evidence

FOR-361 row `for361-bounded-independent-round-cap-arc-rec2020-f16` rejected the candidate at
`for361_arc_right_stroke_center`: current residual `0`,
candidate residual `37`, delta
`37`, worsened samples
`1`.

## Rejection Criteria Before Evaluation

- `rejected-candidate-reuse`: reject when the future proposal reuses `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`, or an equivalent FOR-355 policy, as a selectable candidate
- `for361-worsening`: reject when the future proposal worsens the bounded independent FOR-361 arc row versus current Kanvas
- `losing-for345-for358-guards`: reject when the future proposal loses either preserved non-arc guard from FOR-345 or FOR-358
- `scene-coordinate-selected-cell-full-gm-crop-branch`: reject when the future proposal depends on scene id, coordinate, selected-cell, fixture, or full-GM crop branching
- `missing-independent-comparable-arc-scene`: reject when the future proposal lacks an independent comparable F16 arc scene with reference/current samples

## Candidate Family Search Matrix

| family | requires future evidence | selected for evaluation | selectable candidate defined |
|---|---:|---:|---:|
| `guard-first-independent-arc-alpha-family` | True | False | False |
| `non-scene-cross-row-f16-composition-family` | True | False | False |
| `bounded-arc-non-arc-invariant-family` | True | False | False |

Every family is abstract, requires future evidence, and is not selected for
evaluation by this ticket.

## Required Evidence For Next Ticket

- `preserved-non-arc-guards`: FOR-345 reference/current comparable samples remain present; FOR-358 reference/current comparable samples remain present and distinct from FOR-345; future candidate-computed samples show no loss of either non-arc guard
- `for361-no-worsening-check`: FOR-361 bounded independent arc row remains comparable; future candidate-computed samples do not exceed current residual on FOR-361; the FOR-361 rejection sample remains explicitly audited
- `independent-comparable-arc-scene`: at least one independent F16 arc scene with isolated Skia reference samples; matching current Kanvas CPU samples; candidate-computed samples generated only as evidence, not renderer behavior; no selected-cell substitution and no full-GM crop branch
- `deterministic-next-ticket-artifacts`: deterministic JSON under reports/wgsl-pipeline/scenes/artifacts; human-readable report under reports/wgsl-pipeline; focused validator that fails before evaluation when any rejection criterion is hit

## Non-goals Preserved

- No renderer behavior change.
- No candidate implementation, selectable candidate, score increase, threshold
  change, GPU/WGSL change, geometry/coverage/fallback/promotion change, or
  Kadre change.
- No scene, coordinate, selected-cell, fixture, or full-GM crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-constrained-candidate-search-for363/f16-constrained-candidate-search-for363.json`
- Validator: `scripts/validate_for363_f16_constrained_candidate_search.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-363-f16-constrained-candidate-search.md`

## Validation

- `rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 -m py_compile scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
