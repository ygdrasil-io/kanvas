# FOR-359 F16 Candidate After FOR-358 Guard

Linear: `FOR-359`

Decision: `F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-359 re-evaluates the exact FOR-355 candidate after FOR-358 added a real
additional non-arc comparable F16 row. This artifact is evidence-only: it does
not define a new candidate, select a renderer policy, raise score, or change
renderer behavior.

## Result

FOR-358 lifts the FOR-356 blockage only partially. It resolves the missing
additional non-arc comparable row recorded by FOR-357, but the FOR-356 arc
limitation remains open because no independent arc scene beyond
`circular_arcs_stroke_butt` has been added.

Next required proof: independent F16 arc scene outside circular_arcs_stroke_butt with reference/current/candidate comparable samples evaluated by the exact FOR-355 candidate while preserving both FOR-345 and FOR-358 non-arc guards.

## Prerequisites

- FOR-358 decision: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`
- FOR-357 decision: `F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE`
- FOR-356 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL`
- FOR-355 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`
- Source memory: `global/kanvas/ticket-drafts/draft-for-next-re-evaluate-f16-generalized-candidate-after-for-358-guard`

## Consolidated Non-Arc Guards

| source | row | role | current residual | candidate residual | candidate-current delta | worsened samples | passed |
|---|---|---|---:|---:|---:|---:|---|
| `FOR-345` | `non-arc-rec2020-f16-src-over-rect` | `original-non-arc-identity-guard` | 0 | 0 | 0 | 0 | yes |
| `FOR-358` | `for358-non-arc-rec2020-f16-src-over-green-rect` | `additional-non-arc-comparable-guard` | 3 | 3 | 0 | 0 | yes |

## Arc Evidence Carried Forward

| metric | value |
|---|---:|
| comparable arc artifacts | 2 |
| derivative evidence rows | 1 |
| independent arc scenes beyond `circular_arcs_stroke_butt` | 0 |
| current residual | 750 |
| candidate residual | 0 |
| residual reduction | 750 |

Existing positive arc evidence remains concentrated in the same CircularArcsStrokeButt adjacent groups; FOR-342 remains derivative evidence.

## Decision Boundary

- FOR-358 non-arc gap resolved: `True`
- Independent arc-scene gap resolved: `False`
- FOR-358 blockage lift: `partially`
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

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-candidate-after-for358-guard-for359/f16-candidate-after-for358-guard-for359.json`
- Validator: `scripts/validate_for359_f16_candidate_after_for358_guard.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-359-f16-candidate-after-for358-guard.md`

## Validation

- `rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 -m py_compile scripts/validate_for359_f16_candidate_after_for358_guard.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
