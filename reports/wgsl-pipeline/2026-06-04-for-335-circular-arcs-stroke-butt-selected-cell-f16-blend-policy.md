# FOR-335 CircularArcsStrokeButt Selected-Cell F16 Blend Policy

Linear: `FOR-335`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_BLEND_POLICY_REQUIRES_RENDERER_COLOR_POLICY_DECISION`

FOR-335 bounds the selected-cell residual that remains after FOR-334. No
renderer code is changed. The artifact compares the 8 stroke samples against
the current CPU F16 path and two explicit straight-sRGB SrcOver-over-white
candidate policies.

## Result

The remaining boundary is:

`paintColorXformAndPremul/source premul plus SkBitmapDevice.blendF16PremulMode.kSrcOver working-space blend policy before SkBitmap.getPixelAsSrgb export`

The best bounded candidate is `straight_srgb_quantized_alpha_src_over_white`. It reduces the 8-stroke sum
absolute delta from `132` to
`52`. It also worsens
`2` current FOR-334 samples,
so no local correction is applied in this ticket.

Full-coverage stroke centers are the strongest signal: the current CPU path has
sum absolute delta `57`,
while the best straight-sRGB policy has `0`.
That points at the color transform / premul / SrcOver working-space policy, not
PNG export.

## Samples

| sample | coverage | Skia over white | current CPU after FOR-334 | current abs | sRGB float coverage | float abs | sRGB quantized alpha | quant abs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| blue_left_aa_edge | 8/16 | [210, 210, 255, 255] | [214, 207, 255, 255] | 7 | [205, 205, 255, 255] | 10 | [205, 205, 255, 255] | 10 |
| blue_top_outer_edge | 8/16 | [209, 209, 255, 255] | [214, 207, 255, 255] | 7 | [205, 205, 255, 255] | 8 | [205, 205, 255, 255] | 8 |
| arc_rect_top_left | 6/16 | [215, 215, 255, 255] | [224, 219, 255, 255] | 13 | [218, 218, 255, 255] | 6 | [217, 217, 255, 255] | 4 |
| blue_top_stroke_center | 16/16 | [155, 155, 255, 255] | [171, 158, 255, 255] | 19 | [155, 155, 255, 255] | 0 | [155, 155, 255, 255] | 0 |
| red_right_stroke_center | 16/16 | [255, 155, 155, 255] | [255, 170, 159, 255] | 19 | [255, 155, 155, 255] | 0 | [255, 155, 155, 255] | 0 |
| red_bottom_stroke_center | 16/16 | [255, 155, 155, 255] | [255, 170, 159, 255] | 19 | [255, 155, 155, 255] | 0 | [255, 155, 155, 255] | 0 |
| red_outer_edge | 6/16 | [255, 210, 210, 255] | [255, 224, 219, 255] | 23 | [255, 218, 218, 255] | 16 | [255, 217, 217, 255] | 14 |
| red_bottom_outer_edge | 6/16 | [255, 209, 209, 255] | [255, 224, 219, 255] | 25 | [255, 218, 218, 255] | 18 | [255, 217, 217, 255] | 16 |

## Non-goals Preserved

- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No arc geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion,
  or score change.
- Historical artifacts FOR-329 through FOR-334 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-f16-blend-policy-for335/circular-arcs-stroke-butt-selected-cell-f16-blend-policy-for335.json`
- Validator: `scripts/validate_for335_circular_arcs_stroke_butt_selected_cell_f16_blend_policy.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-335-circular-arcs-stroke-butt-selected-cell-f16-blend-policy.md`

## Validation

Required validation commands are listed in the JSON artifact. The handoff records
the observed pass/fail status for this run.
