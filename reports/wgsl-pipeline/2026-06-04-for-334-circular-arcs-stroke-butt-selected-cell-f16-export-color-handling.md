# FOR-334 CircularArcsStrokeButt Selected-Cell F16 Export Color Handling

Linear: `FOR-334`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_EXPORT_COLOR_HANDLING_CORRECTED`

FOR-334 corrects the exported CPU F16 readback boundary for the selected
`CircularArcsStrokeButt` cell. `SkBitmap.getPixelAsSrgb` converts non-sRGB F16
premultiplied bitmap values to sRGB unpremultiplied `SkColor` values, while
`SkBitmap.getPixel` remains the historical internal byte oracle used by
renderer comparisons. `SkPngEncoder` remains coherent because PNG rows are
materialized through `SkBitmap.getPixelAsSrgb`.

## Scope

- Changed boundary: explicit `SkBitmap.getPixelAsSrgb` for `kRGBA_F16Norm` non-sRGB exports.
- Preserved boundary: `SkBitmap.getPixel` for internal renderer/test byte oracles.
- PNG coherence: `SkPngEncoder` uses the same explicit sRGB result for RGBA rows.
- Not changed: arc geometry, coverage, internal F16 store, GPU, WGSL, thresholds,
  fallback policy, Kadre, promotion, or score.

## Impact

- 13 FOR-333 samples compared before and after.
- Sum absolute delta before: `231`.
- Sum absolute delta after: `132`.
- Reduction: `99` (42.857143%).
- Improved samples: `8`.
- Unchanged samples: `5`.
- Worsened samples: `0`.

Promotion remains explicitly forbidden because FOR-334 only corrects the export
boundary and does not change score or promotion policy.

## Samples

| sample | Skia over white | CPU before | CPU after | abs before | abs after | reduction |
|---|---:|---:|---:|---:|---:|---:|
| top_left_background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |
| top_edge_background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |
| left_edge_background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |
| blue_left_aa_edge | [210, 210, 255, 255] | [214, 208, 253, 255] | [214, 207, 255, 255] | 8 | 7 | 1 |
| blue_top_outer_edge | [209, 209, 255, 255] | [214, 208, 253, 255] | [214, 207, 255, 255] | 8 | 7 | 1 |
| arc_rect_top_left | [215, 215, 255, 255] | [224, 220, 253, 255] | [224, 219, 255, 255] | 16 | 13 | 3 |
| blue_top_stroke_center | [155, 155, 255, 255] | [172, 160, 250, 255] | [171, 158, 255, 255] | 27 | 19 | 8 |
| red_right_stroke_center | [255, 155, 155, 255] | [235, 178, 162, 255] | [255, 170, 159, 255] | 50 | 19 | 31 |
| red_bottom_stroke_center | [255, 155, 155, 255] | [235, 178, 162, 255] | [255, 170, 159, 255] | 50 | 19 | 31 |
| red_outer_edge | [255, 210, 210, 255] | [248, 227, 221, 255] | [255, 224, 219, 255] | 35 | 23 | 12 |
| red_bottom_outer_edge | [255, 209, 209, 255] | [248, 227, 221, 255] | [255, 224, 219, 255] | 37 | 25 | 12 |
| cell_center_hole | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |
| bottom_right_background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-f16-export-color-handling-for334/circular-arcs-stroke-butt-selected-cell-f16-export-color-handling-for334.json`
- Validator: `scripts/validate_for334_circular_arcs_stroke_butt_selected_cell_f16_export_color_handling.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-334-circular-arcs-stroke-butt-selected-cell-f16-export-color-handling.md`

## Validation

Required validation commands are listed in the JSON artifact. The implementation
handoff records the observed pass/fail status for this run.
