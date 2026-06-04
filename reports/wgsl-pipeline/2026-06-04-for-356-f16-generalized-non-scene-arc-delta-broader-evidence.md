# FOR-356 F16 Generalized Non-Scene Arc Delta Broader Evidence

Linear: `FOR-356`

Decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-356 reuses the FOR-355 candidate without defining or selecting a new
candidate. It evaluates broader available evidence, then keeps the result
partial because the positive arc evidence still comes from the same adjacent
CircularArcsStrokeButt groups and no additional comparable non-arc row is
available.

## FOR-345 Guard

- Candidate residual: `0`
- Candidate worsened samples: `0`
- Guard passed: `True`

## Comparable Arc Evidence

| source | cells | samples | applied samples | current residual | candidate residual | reduction |
|---|---:|---:|---:|---:|---:|---:|
| `FOR-340` | 2 | 12 | 10 | 375 | 0 | 375 |
| `FOR-341` | 2 | 12 | 10 | 375 | 0 | 375 |

Combined current residual: `750`

Combined candidate residual: `0`

Combined reduction: `750`

## Derivative Evidence

`FOR-342` is classified as `derivative-scoped-route-evidence-not-independent-scene`:
FOR-342 confirms the same adjacent groups and records no safe renderer route; it is useful corroboration but not a broader independent scene.

## Non-Arc Evidence Inventory

- `FOR-345`: `comparable-non-arc-guard`
- `additional-non-arc-rows`: `missing` - No additional comparable non-arc row with current/reference/candidate fields is available in the current artifact set.

## Result

The candidate improves the comparable FOR-340/FOR-341 adjacent arc evidence, but those artifacts cover the same two CircularArcsStrokeButt groups and FOR-342 is derivative. No independent arc scene or additional comparable non-arc row is available yet.

`familyZoneShaped` remains `True`
(`stroke-center`), so this ticket does not authorize an
implementation plan, renderer selection, promotion, or score increase.

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

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-generalized-non-scene-arc-delta-broader-evidence-for356/f16-generalized-non-scene-arc-delta-broader-evidence-for356.json`
- Validator: `scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-356-f16-generalized-non-scene-arc-delta-broader-evidence.md`

## Validation

- `rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py`
- `rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- `rtk python3 scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py`
- `rtk python3 -m py_compile scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
