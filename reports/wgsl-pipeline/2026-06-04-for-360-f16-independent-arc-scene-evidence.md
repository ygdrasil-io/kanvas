# FOR-360 F16 Independent Arc Scene Evidence

Linear: `FOR-360`

Decision: `F16_INDEPENDENT_ARC_SCENE_EVIDENCE_PARTIAL`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-360 searched for an independent comparable arc scene beyond the two
adjacent `CircularArcsStrokeButt` groups used by FOR-340/FOR-341, then kept
the exact FOR-355 candidate unchanged. This artifact is evidence-only: it does
not define a new candidate, select a renderer policy, raise score, or change
renderer behavior.

## Result

No inspected repository artifact supplies an independent arc scene outside the FOR-340/FOR-341 CircularArcsStrokeButt adjacent groups with accepted reference samples, current Kanvas F16 samples, and candidate RGBA values for the exact FOR-355 candidate. FOR-360 therefore records PARTIAL instead of accepting or rejecting the candidate from incomplete data.

The candidate was not evaluated on a new independent arc scene because no
accepted comparable scene exists in the inspected repository artifacts. No
sample RGBA values or residual totals are fabricated for the missing scene.

## Prerequisites

- FOR-359 decision: `F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE`
- FOR-358 decision: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`
- FOR-355 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`
- Source memory: `global/kanvas/ticket-drafts/draft-for-next-independent-arc-scene-evidence-for-f16-candidate`

## Inspected Paths

| source | path | independent from FOR-340/FOR-341 | comparable reference/current/candidate | eligible | exact reason |
|---|---|---:|---:|---:|---|
| `FOR-340` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/circular-arcs-stroke-butt-adjacent-f16-reference-for340.json` | `False` | `True` | `False` | `FOR-340 is the accepted Skia reference for the same two adjacent CircularArcsStrokeButt groups already used by FOR-341/FOR-355, so FOR-360 must not count it as independent proof.` |
| `FOR-341` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341/circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341.json` | `False` | `True` | `False` | `FOR-341 has the comparable current/reference/candidate arc rows, but they are exactly the two adjacent CircularArcsStrokeButt groups prohibited as new independent FOR-360 evidence.` |
| `FOR-342` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342/circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342.json` | `False` | `True` | `False` | `FOR-342 is derivative evidence for the same adjacent CircularArcsStrokeButt groups and records no independent scene beyond FOR-340/FOR-341.` |
| `FOR-337` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337/circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337.json` | `False` | `False` | `False` | `FOR-337 contains selected-cell evidence and only identifies adjacent targets without a comparable current/reference/candidate table. FOR-360 forbids selected-cell substitution.` |
| `FOR-338` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338/circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338.json` | `False` | `False` | `False` | `FOR-338 records adjacent CircularArcsStrokeButt instrumentation targets and explicitly lacks captured current/candidate comparable values; it is not an independent arc scene.` |
| `FOR-318` | `reports/wgsl-pipeline/scenes/artifacts/path-aa-arc-stroke-hairline-scout-for318/path-aa-arc-stroke-hairline-scout-for318.json` | `True` | `False` | `False` | `FOR-318 identifies independent arc GM rows such as AddArc and Crbug1472747, but they are scout/broad-GM expected-unsupported entries without accepted Rec.2020 F16 current/reference/candidate sample tables for the FOR-355 candidate.` |
| `FOR-356` | `reports/wgsl-pipeline/scenes/artifacts/f16-generalized-non-scene-arc-delta-broader-evidence-for356/f16-generalized-non-scene-arc-delta-broader-evidence-for356.json` | `False` | `False` | `False` | `FOR-356 already summarizes that the candidate has zero independent arc scenes beyond CircularArcsStrokeButt; FOR-360 treats that as prior evidence, not as a new scene.` |

## Candidate Evaluation

| metric | value |
|---|---:|
| evaluated on independent arc scene | `False` |
| sample count | `0` |
| current residual | `None` |
| candidate residual | `None` |
| candidate-current residual delta | `None` |
| worsened samples | `None` |

## Decision Boundary

- Independent comparable arc scenes found: `0`
- Selected-cell proof used: `False`
- Full-GM crop proof used: `False`
- FOR-340/FOR-341 adjacent groups reused as new proof: `False`
- Implementation plan authorized: `False`

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No implementation plan authorization.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution,
  full-GM crop, or threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-independent-arc-scene-evidence-for360/f16-independent-arc-scene-evidence-for360.json`
- Validator: `scripts/validate_for360_f16_independent_arc_scene_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-360-f16-independent-arc-scene-evidence.md`

## Validation

- `rtk python3 scripts/validate_for360_f16_independent_arc_scene_evidence.py`
- `rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 -m py_compile scripts/validate_for360_f16_independent_arc_scene_evidence.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
