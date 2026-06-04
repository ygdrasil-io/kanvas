# FOR-362 F16 Rejected Candidate Closeout

Linear: `FOR-362`

Decision: `F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-362 closes the exact FOR-355 candidate after FOR-361 rejected it on a
bounded independent arc scene. This artifact is evidence-only: it does not
define a new candidate, select or implement the candidate, raise score, or
change renderer behavior.

## Result

The candidate is rejected for selection. FOR-361 kept current Kanvas at residual
`0` on the independent arc row, while the candidate
produced residual `37` and worsened
`1` sample.

Rejected sample `for361_arc_right_stroke_center`:

| reference | current | candidate | candidate residual | worsens current |
|---|---|---|---:|---|
| [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 | yes |

## Prerequisites

- FOR-361 decision: `F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE`
- FOR-360 decision: `F16_INDEPENDENT_ARC_SCENE_EVIDENCE_PARTIAL`
- FOR-359 decision: `F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE`
- FOR-358 decision: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`
- FOR-355 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`

## Selection Closeout

- Candidate selectable: `False`
- Closed as rejected: `True`
- Rejection source: `FOR-361`
- New candidate defined by FOR-362: `False`
- Implementation plan authorized: `False`
- Score raised: `False`

## Next Candidate Search Constraints

- `preserve-for345`
- `preserve-for358`
- `do-not-worsen-for361`
- `require-independent-comparable-arc-scene`
- `refuse-scene-coordinate-selected-cell-full-gm-crop-branches`

The next search must preserve FOR-345 and FOR-358, must not worsen FOR-361,
must require at least one independent comparable F16 arc scene, and must refuse
scene, coordinate, selected-cell, and full-GM crop branches.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected or implemented.
- No implementation plan authorization.
- No score increase.
- No threshold, GPU/WGSL, geometry, coverage, fallback, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution, or
  full-GM crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-rejected-candidate-closeout-for362/f16-rejected-candidate-closeout-for362.json`
- Validator: `scripts/validate_for362_f16_rejected_candidate_closeout.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-362-f16-rejected-candidate-closeout.md`

## Validation

- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for360_f16_independent_arc_scene_evidence.py`
- `rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 -m py_compile scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
