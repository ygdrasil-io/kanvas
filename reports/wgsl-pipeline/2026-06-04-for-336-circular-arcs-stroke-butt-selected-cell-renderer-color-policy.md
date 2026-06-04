# FOR-336 CircularArcsStrokeButt Selected-Cell Renderer Color Policy

Linear: `FOR-336`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_RENDERER_COLOR_POLICY_NEEDS_CROSS_SCENE_EVIDENCE`

FOR-336 turns the FOR-335 F16 blend-policy signal into an explicit renderer
color-policy decision. No renderer behavior is changed in this ticket.

## Decision

Kanvas should not adopt the straight-sRGB candidate or declare the current
Rec.2020 F16 working-space blend final from a single selected cell. The selected
policy is to require cross-scene evidence before any change to F16 premul,
SrcOver storage, or comparison-oracle behavior.

The straight-sRGB candidate is real evidence: it reduces the selected-cell
stroke residual from `132` to
`52` and reduces full-coverage stroke-center
residual from `57` to
`0`. It is still not safe as a
renderer patch because it worsens `2` edge
samples: `blue_left_aa_edge, blue_top_outer_edge`.

## Candidate Policies

| policy | known residual impact | regression risk | accepted in FOR-336 |
|---|---|---|---:|
| Keep Rec.2020 F16 working-space blend | Preserves the current selected-cell residual of 132; full-coverage stroke centers remain at 57 residual. | Lowest immediate regression risk because existing CPU/GPU comparison oracles stay stable, but it leaves the selected-cell mismatch unresolved. | no |
| Adopt straight-sRGB reference | Reduces selected-cell stroke residual from 132 to 52; full-coverage centers go from 57 to 0. | Not accepted in FOR-336: the candidate worsens 2 edge samples (blue_left_aa_edge, blue_top_outer_edge), so applying it locally could hide coverage or quantization regressions. | no |
| Require cross-scene evidence | Keeps FOR-334/FOR-335 runtime behavior unchanged for now; records the candidate improvement 132->52 and center improvement 57->0 as evidence, not as behavior. | Balanced risk: avoids committing a global F16 blend policy from one selected cell while preserving a clear path to prove or reject it. | yes |

## Required Gates For The Next Ticket

- FOR-336 validator passes and confirms FOR-335 prerequisite state.
- Next ticket samples at least selected cell, adjacent arc-stroke cells, and one non-arc F16/blend scene.
- CPU and GPU gates must pass before any change to colorToF16Premul or blendF16PremulMode.
- Any proposed comparison-policy migration must explicitly keep or replace SkBitmap.getPixel as oracle.

## Future Code-Change Criteria

- Do not change colorToF16Premul unless the artifact proves the policy across selected-cell and cross-scene evidence.
- Do not change blendF16PremulMode unless old/new CPU samples and GPU smoke checks prove no silent oracle movement.
- Do not change SkBitmap.getPixel unless a complete comparison-oracle migration is explicitly approved.
- Do not change SkBitmap.getPixelAsSrgb unless encoded export behavior and PNG evidence are covered.
- Do not change geometry, coverage, WGSL, thresholds, fallback policy, Kadre, promotion, or score in a color-policy ticket.

## Recommendation

Create the next ticket through memory as a cross-scene evidence artifact. It
should compare current Rec.2020 F16 working-space blend against the straight-sRGB
reference policy on the selected cell, adjacent `CircularArcsStrokeButt` cells,
and at least one non-arc F16/blend scene. It must not apply a renderer patch
until CPU and GPU evidence prove no hidden edge, threshold, fallback, or oracle
regression.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-335 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-renderer-color-policy-for336/circular-arcs-stroke-butt-selected-cell-renderer-color-policy-for336.json`
- Validator: `scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-336-circular-arcs-stroke-butt-selected-cell-renderer-color-policy.md`

## Validation

Required validation commands are listed in the JSON artifact. The handoff records
the observed pass/fail status for this run.
