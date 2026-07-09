# GPU Phase 6 Effect Composition Families Evidence

## Summary

- Total COMPOSITE + BLUR rows: 156
- Families: {BLUR=45, COMPOSITE=111}
- Classifications: {expected-unsupported=47, instrumented-existing=83, no-score=26}
- Subfamilies: {blur-backdrop-gated=1, blur-clip-interaction-gated=2, blur-filter-graph-gated=3, blur-image-basic=6, blur-large-sigma-gated=2, blur-mask-basic=6, blur-matrix-convolution-gated=6, blur-rect-rrect-circle=9, blur-resource-budget-gated=1, blur-small-sigma=3, blur-text-dependent-gated=2, blur-transform-or-perspective-gated=4, composite-advanced-blend-gated=4, composite-atlas-or-vertices-gated=9, composite-backdrop-gated=2, composite-color-filter-gated=8, composite-destination-read-gated=1, composite-image-filter-gated=19, composite-layer-bounds-gated=1, composite-overdraw-diagnostic=1, composite-porter-duff=8, composite-save-layer-gated=5, composite-src-over-basic=53}
- Promoted rows: 0
- Unexpected fails: 0
- No score: 26

## Family Deltas

- Baseline source: `2026-07-09 local dashboard before effect-composition-family wave`
- Current dashboard: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json` (2026-07-09T11:04:08.12663)

| Family | Baseline | Current | Delta |
|---|---:|---:|---:|
| `BLUR` | 45 | 45 | +0 |
| `COMPOSITE` | 113 | 111 | -2 |

## Non-Claims

- No broad COMPOSITE or BLUR support is claimed from classification alone.
- saveLayer, destination-read, backdrop filters, image-filter DAGs, matrix convolution, and advanced blend chains remain outside this evidence wave unless row diagnostics prove a bounded route.
- Rows without route and effect/composition diagnostics remain instrumented rather than promoted.
- TEXT, IMAGE, PATH, CLIP, MATERIAL, and MESH dependencies are not absorbed into this wave.

## Reason Code Taxonomy

- Effect/composition `unsupported.composition.*` and `unsupported.blur.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.

## Rows

| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback | No Score Cause |
|---|---|---|---|---|---:|---|---|
| `animatedbackdropblur` | `animatedbackdropblur` | `BLUR` | `blur-backdrop-gated` | `no-score` | n/a | `unsupported.blur.backdrop` | `reference-missing` |
| `bigblurs` | `bigblurs` | `BLUR` | `blur-mask-basic` | `instrumented-existing` | 36.03 | `none` | `n/a` |
| `blur2rects` | `blur2rects` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 96.44 | `none` | `n/a` |
| `blur2rectsnonninepatch` | `blur2rectsnonninepatch` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 95.47 | `none` | `n/a` |
| `BlurBigSigma` | `BlurBigSigma` | `BLUR` | `blur-large-sigma-gated` | `expected-unsupported` | 0.00 | `unsupported.blur.large_sigma` | `n/a` |
| `blurcircles2` | `blurcircles2` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 54.17 | `none` | `n/a` |
| `blurcircles` | `blurcircles` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 86.57 | `none` | `n/a` |
| `BlurDrawImage` | `BlurDrawImage` | `BLUR` | `blur-image-basic` | `instrumented-existing` | 70.75 | `none` | `n/a` |
| `blur_ignore_xform_circle` | `blur_ignore_xform_circle` | `BLUR` | `blur-transform-or-perspective-gated` | `no-score` | n/a | `unsupported.blur.transform_or_perspective` | `generated-render-missing` |
| `blur_ignore_xform_rrect` | `blur_ignore_xform_rrect` | `BLUR` | `blur-transform-or-perspective-gated` | `no-score` | n/a | `unsupported.blur.transform_or_perspective` | `generated-render-missing` |
| `blur_ignore_xform_rect` | `blur_ignore_xform_rect` | `BLUR` | `blur-transform-or-perspective-gated` | `no-score` | n/a | `unsupported.blur.transform_or_perspective` | `generated-render-missing` |
| `blur_image` | `blur_image` | `BLUR` | `blur-image-basic` | `instrumented-existing` | 83.37 | `none` | `n/a` |
| `blur_matrix_rect` | `blur_matrix_rect` | `BLUR` | `blur-transform-or-perspective-gated` | `expected-unsupported` | 91.98 | `unsupported.blur.transform_or_perspective` | `n/a` |
| `check_small_sigma_offset` | `check_small_sigma_offset` | `BLUR` | `blur-small-sigma` | `instrumented-existing` | 96.68 | `none` | `n/a` |
| `blurquickreject` | `blurquickreject` | `BLUR` | `blur-mask-basic` | `instrumented-existing` | 81.75 | `none` | `n/a` |
| `blurrect_compare` | `blurrect_compare` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 80.54 | `none` | `n/a` |
| `blurrect_gallery` | `blurrect_gallery` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 73.71 | `none` | `n/a` |
| `blurrects` | `blurrects` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 44.62 | `none` | `n/a` |
| `BlurSmallSigma` | `BlurSmallSigma` | `BLUR` | `blur-small-sigma` | `instrumented-existing` | 87.89 | `none` | `n/a` |
| `blurredclippedcircle` | `blurredclippedcircle` | `BLUR` | `blur-clip-interaction-gated` | `expected-unsupported` | 71.02 | `unsupported.blur.clip_interaction` | `n/a` |
| `crbug_899512` | `crbug_899512` | `BLUR` | `blur-mask-basic` | `instrumented-existing` | 92.65 | `none` | `n/a` |
| `emboss` | `emboss` | `BLUR` | `blur-mask-basic` | `instrumented-existing` | 55.96 | `none` | `n/a` |
| `embossmaskfilter` | `embossmaskfilter` | `BLUR` | `blur-mask-basic` | `instrumented-existing` | 78.10 | `none` | `n/a` |
| `fast_slow_blurimagefilter` | `fast_slow_blurimagefilter` | `BLUR` | `blur-filter-graph-gated` | `expected-unsupported` | 42.28 | `unsupported.blur.image_filter_graph` | `n/a` |
| `hdr-pip-blur` | `hdr-pip-blur` | `BLUR` | `blur-resource-budget-gated` | `expected-unsupported` | 0.04 | `unsupported.blur.resource_budget` | `n/a` |
| `imageblur2` | `imageblur2` | `BLUR` | `blur-image-basic` | `instrumented-existing` | 9.98 | `none` | `n/a` |
| `imageblurclampmode` | `imageblurclampmode` | `BLUR` | `blur-image-basic` | `instrumented-existing` | 78.27 | `none` | `n/a` |
| `imageblur` | `imageblur` | `BLUR` | `blur-image-basic` | `instrumented-existing` | 32.33 | `none` | `n/a` |
| `imageblurrepeatmode` | `imageblurrepeatmode` | `BLUR` | `blur-image-basic` | `instrumented-existing` | 40.62 | `none` | `n/a` |
| `imageblurrepeatunclipped` | `imageblurrepeatunclipped` | `BLUR` | `blur-clip-interaction-gated` | `expected-unsupported` | 0.15 | `unsupported.blur.clip_interaction` | `n/a` |
| `imagefilterstext_cf` | `imagefilterstext_cf` | `BLUR` | `blur-text-dependent-gated` | `no-score` | n/a | `unsupported.blur.text_dependency` | `reference-missing` |
| `imagefilterstext_if` | `imagefilterstext_if` | `BLUR` | `blur-text-dependent-gated` | `no-score` | n/a | `unsupported.blur.text_dependency` | `reference-missing` |
| `inverse_fill_filters` | `inverse_fill_filters` | `BLUR` | `blur-filter-graph-gated` | `expected-unsupported` | 11.94 | `unsupported.blur.image_filter_graph` | `n/a` |
| `inverse_windingmode_filters` | `inverse_windingmode_filters` | `BLUR` | `blur-filter-graph-gated` | `expected-unsupported` | 19.78 | `unsupported.blur.image_filter_graph` | `n/a` |
| `matrixconvolution` | `matrixconvolution` | `BLUR` | `blur-matrix-convolution-gated` | `no-score` | n/a | `unsupported.blur.matrix_convolution` | `generated-render-missing` |
| `matrixconvolution_big_color` | `matrixconvolution_big_color` | `BLUR` | `blur-matrix-convolution-gated` | `no-score` | n/a | `unsupported.blur.matrix_convolution` | `generated-render-missing` |
| `matrixconvolution_big` | `matrixconvolution_big` | `BLUR` | `blur-matrix-convolution-gated` | `no-score` | n/a | `unsupported.blur.matrix_convolution` | `generated-render-missing` |
| `matrixconvolution_bigger` | `matrixconvolution_bigger` | `BLUR` | `blur-matrix-convolution-gated` | `no-score` | n/a | `unsupported.blur.matrix_convolution` | `generated-render-missing` |
| `matrixconvolution_biggest` | `matrixconvolution_biggest` | `BLUR` | `blur-matrix-convolution-gated` | `no-score` | n/a | `unsupported.blur.matrix_convolution` | `generated-render-missing` |
| `matrixconvolution_color` | `matrixconvolution_color` | `BLUR` | `blur-matrix-convolution-gated` | `no-score` | n/a | `unsupported.blur.matrix_convolution` | `generated-render-missing` |
| `rrect_blurs` | `rrect_blurs` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 52.28 | `none` | `n/a` |
| `simpleblurroundrect` | `simpleblurroundrect` | `BLUR` | `blur-rect-rrect-circle` | `instrumented-existing` | 69.33 | `none` | `n/a` |
| `smallemboss` | `smallemboss` | `BLUR` | `blur-small-sigma` | `instrumented-existing` | 84.00 | `none` | `n/a` |
| `tablemaskfilter` | `tablemaskfilter` | `BLUR` | `blur-mask-basic` | `instrumented-existing` | 83.44 | `none` | `n/a` |
| `TiledBlurBigSigma` | `TiledBlurBigSigma` | `BLUR` | `blur-large-sigma-gated` | `expected-unsupported` | 0.00 | `unsupported.blur.large_sigma` | `n/a` |
| `aaxfermodes` | `aaxfermodes` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 17.38 | `none` | `n/a` |
| `aarectmodes` | `aarectmodes` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 53.13 | `none` | `n/a` |
| `arithmode_blender` | `arithmode_blender` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 43.46 | `none` | `n/a` |
| `backdrop_hintrect_clipping` | `backdrop_hintrect_clipping` | `COMPOSITE` | `composite-backdrop-gated` | `expected-unsupported` | 42.76 | `unsupported.composition.backdrop_filter` | `n/a` |
| `backdrop_imagefilter_croprect` | `backdrop_imagefilter_croprect` | `COMPOSITE` | `composite-backdrop-gated` | `expected-unsupported` | 54.80 | `unsupported.composition.backdrop_filter` | `n/a` |
| `badpaint` | `badpaint` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 36.00 | `none` | `n/a` |
| `clip_shader_difference` | `clip_shader_difference` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `clipshadermatrix` | `clipshadermatrix` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 17.11 | `none` | `n/a` |
| `clip_shader_layer` | `clip_shader_layer` | `COMPOSITE` | `composite-save-layer-gated` | `expected-unsupported` | 58.08 | `unsupported.composition.save_layer` | `n/a` |
| `clip_shader_nested` | `clip_shader_nested` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 6.78 | `none` | `n/a` |
| `clip_shader_persp` | `clip_shader_persp` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 76.54 | `none` | `n/a` |
| `clip_shader` | `clip_shader` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 61.90 | `none` | `n/a` |
| `color4blendcf` | `color4blendcf` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 30.56 | `none` | `n/a` |
| `color4shader` | `color4shader` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 82.12 | `none` | `n/a` |
| `colorfilterimagefilter` | `colorfilterimagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 69.80 | `unsupported.composition.image_filter_dag` | `n/a` |
| `colorfilterimagefilter_layer` | `colorfilterimagefilter_layer` | `COMPOSITE` | `composite-save-layer-gated` | `expected-unsupported` | 0.00 | `unsupported.composition.save_layer` | `n/a` |
| `colorfiltershader` | `colorfiltershader` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 39.31 | `none` | `n/a` |
| `lightingcolorfilter` | `lightingcolorfilter` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 21.23 | `none` | `n/a` |
| `colormatrix` | `colormatrix` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 38.59 | `none` | `n/a` |
| `colorcomposefilter_alpha` | `colorcomposefilter_alpha` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `colorcomposefilter_wacky` | `colorcomposefilter_wacky` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `compare_atlas_vertices` | `compare_atlas_vertices` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 0.02 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `composeshader_alpha` | `composeshader_alpha` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 15.15 | `none` | `n/a` |
| `composeshader_bitmap` | `composeshader_bitmap` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 37.14 | `none` | `n/a` |
| `composeshader_bitmap_lm` | `composeshader_bitmap_lm` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `composeshader` | `composeshader` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 30.56 | `none` | `n/a` |
| `composeshader_grid` | `composeshader_grid` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 20.47 | `none` | `n/a` |
| `composeCFIF` | `composeCFIF` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `compositor_quads_color` | `compositor_quads_color` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 69.57 | `none` | `n/a` |
| `crbug_1162942` | `crbug_1162942` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 98.64 | `none` | `n/a` |
| `crbug_1167277` | `crbug_1167277` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 96.81 | `none` | `n/a` |
| `crbug_1174186` | `crbug_1174186` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 100.00 | `none` | `n/a` |
| `crbug_1177833` | `crbug_1177833` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 99.85 | `none` | `n/a` |
| `crbug_918512` | `crbug_918512` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `destcolor` | `destcolor` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `discard` | `discard` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `displacement` | `displacement` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 86.25 | `none` | `n/a` |
| `draw-atlas-colors` | `draw-atlas-colors` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 17.58 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `draw-atlas` | `draw-atlas` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 89.66 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `draw_image_set_alpha_only` | `draw_image_set_alpha_only` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `draw_image_set` | `draw_image_set` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 72.94 | `none` | `n/a` |
| `draw_image_set_rect_to_rect` | `draw_image_set_rect_to_rect` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.03 | `none` | `n/a` |
| `draw_quad_set` | `draw_quad_set` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 85.75 | `none` | `n/a` |
| `dropshadow_pseudopersp` | `dropshadow_pseudopersp` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 67.82 | `none` | `n/a` |
| `dstreadshuffle` | `dstreadshuffle` | `COMPOSITE` | `composite-destination-read-gated` | `expected-unsupported` | 0.00 | `unsupported.composition.destination_read` | `n/a` |
| `ducky_yuv_blend` | `ducky_yuv_blend` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 6.62 | `none` | `n/a` |
| `fadefilter` | `fadefilter` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 75.00 | `none` | `n/a` |
| `filterfastbounds` | `filterfastbounds` | `COMPOSITE` | `composite-layer-bounds-gated` | `expected-unsupported` | 0.02 | `unsupported.composition.layer_bounds` | `n/a` |
| `graphitestart` | `graphitestart` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `reference-missing` |
| `hslcolorfilter` | `hslcolorfilter` | `COMPOSITE` | `composite-advanced-blend-gated` | `expected-unsupported` | 32.89 | `unsupported.composition.advanced_blend` | `n/a` |
| `hairmodes` | `hairmodes` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 62.00 | `none` | `n/a` |
| `highcontrastfilter` | `highcontrastfilter` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 42.74 | `none` | `n/a` |
| `hsl` | `hsl` | `COMPOSITE` | `composite-advanced-blend-gated` | `expected-unsupported` | 78.68 | `unsupported.composition.advanced_blend` | `n/a` |
| `HSL_duck` | `HSL_duck` | `COMPOSITE` | `composite-advanced-blend-gated` | `expected-unsupported` | 23.26 | `unsupported.composition.advanced_blend` | `n/a` |
| `imagefilter_composed_transform` | `imagefilter_composed_transform` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 74.57 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefilter_convolve_subset` | `imagefilter_convolve_subset` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 32.18 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefiltersbase` | `imagefiltersbase` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 64.49 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefiltersclipped` | `imagefiltersclipped` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 73.80 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefilterscropped` | `imagefilterscropped` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 29.90 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefilters_effect_order` | `imagefilters_effect_order` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 1.02 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefiltersstroked` | `imagefiltersstroked` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 89.05 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefilter_matrix_localmatrix` | `imagefilter_matrix_localmatrix` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 74.77 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefilterstransformed` | `imagefilterstransformed` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 3.00 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefiltersunpremul` | `imagefiltersunpremul` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 0.00 | `unsupported.composition.image_filter_dag` | `n/a` |
| `imagefilters_xfermodes` | `imagefilters_xfermodes` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 56.77 | `unsupported.composition.image_filter_dag` | `n/a` |
| `internal_links` | `internal_links` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 99.27 | `none` | `n/a` |
| `lcdblendmodes` | `lcdblendmodes` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 41.98 | `none` | `n/a` |
| `lighting` | `lighting` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 35.48 | `none` | `n/a` |
| `localmatriximagefilter` | `localmatriximagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 63.90 | `unsupported.composition.image_filter_dag` | `n/a` |
| `luminosity_overflow` | `luminosity_overflow` | `COMPOSITE` | `composite-advanced-blend-gated` | `expected-unsupported` | 99.62 | `unsupported.composition.advanced_blend` | `n/a` |
| `matriximagefilter` | `matriximagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 0.03 | `unsupported.composition.image_filter_dag` | `n/a` |
| `mixerCF` | `mixerCF` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `modecolorfilters` | `modecolorfilters` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 14.70 | `none` | `n/a` |
| `modecolorfilters#2` | `modecolorfilters` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 14.70 | `none` | `n/a` |
| `offsetimagefilter` | `offsetimagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 62.68 | `unsupported.composition.image_filter_dag` | `n/a` |
| `overdraw_canvas` | `overdraw_canvas` | `COMPOSITE` | `composite-overdraw-diagnostic` | `instrumented-existing` | 84.98 | `none` | `n/a` |
| `overdrawcolorfilter` | `overdrawcolorfilter` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 12.50 | `none` | `n/a` |
| `patch_alpha` | `patch_alpha` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 71.76 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `patch_alpha_test` | `patch_alpha_test` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 41.79 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `patch_image` | `patch_image` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `no-score` | n/a | `unsupported.composition.atlas_or_vertices` | `generated-render-missing` |
| `patch_image_persp` | `patch_image_persp` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 71.80 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `patch_primitive` | `patch_primitive` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `expected-unsupported` | 72.24 | `unsupported.composition.atlas_or_vertices` | `n/a` |
| `perlinnoise` | `perlinnoise` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 48.72 | `none` | `n/a` |
| `perlinnoise_layered` | `perlinnoise_layered` | `COMPOSITE` | `composite-save-layer-gated` | `expected-unsupported` | 0.00 | `unsupported.composition.save_layer` | `n/a` |
| `perlinnoise_localmatrix` | `perlinnoise_localmatrix` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 62.50 | `none` | `n/a` |
| `perlinnoise_rotated` | `perlinnoise_rotated` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 16.90 | `none` | `n/a` |
| `PlusMergesAA` | `PlusMergesAA` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 69.48 | `none` | `n/a` |
| `rasterallocator` | `rasterallocator` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 27.28 | `none` | `n/a` |
| `recordopts` | `recordopts` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `rotate_imagefilter` | `rotate_imagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 62.07 | `unsupported.composition.image_filter_dag` | `n/a` |
| `runtimecolorfilter_vertices_atlas_and_patch` | `runtimecolorfilter_vertices_atlas_and_patch` | `COMPOSITE` | `composite-atlas-or-vertices-gated` | `no-score` | n/a | `unsupported.composition.atlas_or_vertices` | `generated-render-missing` |
| `gpusamplerstress` | `gpusamplerstress` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 99.24 | `none` | `n/a` |
| `save_behind` | `save_behind` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 7.93 | `none` | `n/a` |
| `savelayer_f16` | `savelayer_f16` | `COMPOSITE` | `composite-save-layer-gated` | `expected-unsupported` | 21.57 | `unsupported.composition.save_layer` | `n/a` |
| `savelayer_initfromprev` | `savelayer_initfromprev` | `COMPOSITE` | `composite-save-layer-gated` | `expected-unsupported` | 12.37 | `unsupported.composition.save_layer` | `n/a` |
| `shadermaskfilter_gradient` | `shadermaskfilter_gradient` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 81.62 | `none` | `n/a` |
| `shadow_utils_gray` | `shadow_utils_gray` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `shadow_utils_occl` | `shadow_utils_occl` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `simple-offsetimagefilter` | `simple-offsetimagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 84.66 | `unsupported.composition.image_filter_dag` | `n/a` |
| `sk3d_simple` | `sk3d_simple` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 52.29 | `none` | `n/a` |
| `skbug_14554` | `skbug_14554` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 41.76 | `none` | `n/a` |
| `srcmode` | `srcmode` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 74.03 | `none` | `n/a` |
| `srgb_colorfilter` | `srgb_colorfilter` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 0.05 | `none` | `n/a` |
| `tablecolorfilter` | `tablecolorfilter` | `COMPOSITE` | `composite-color-filter-gated` | `instrumented-existing` | 39.11 | `none` | `n/a` |
| `extractalpha` | `extractalpha` | `COMPOSITE` | `composite-src-over-basic` | `no-score` | n/a | `none` | `generated-render-missing` |
| `tileimagefilter` | `tileimagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `no-score` | n/a | `unsupported.composition.image_filter_dag` | `generated-render-missing` |
| `transparency_check` | `transparency_check` | `COMPOSITE` | `composite-src-over-basic` | `instrumented-existing` | 0.86 | `none` | `n/a` |
| `xfermodeimagefilter` | `xfermodeimagefilter` | `COMPOSITE` | `composite-image-filter-gated` | `expected-unsupported` | 52.43 | `unsupported.composition.image_filter_dag` | `n/a` |
| `xfermodes2` | `xfermodes2` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 38.81 | `none` | `n/a` |
| `xfermodes3` | `xfermodes3` | `COMPOSITE` | `composite-porter-duff` | `no-score` | n/a | `none` | `generated-render-missing` |
| `xfermodes` | `xfermodes` | `COMPOSITE` | `composite-porter-duff` | `instrumented-existing` | 45.77 | `none` | `n/a` |

## Regeneration Notes

- Run `:integration-tests:skia:generateSkiaDashboard` before generating effect/composition-family evidence.
- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
- Non-effect/composition families and cross-family dependencies are intentionally out of this evidence wave.

## Validation

- `rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks`
- `rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence --rerun-tasks`
- `rtk proxy git diff --check`
