# Skia Fidelity Wave 1 Reconciliation

- schemaVersion: `1`
- kind: `skia-fidelity-wave-1`
- generatedBy: `reconcile_skia_fidelity_wave1.py`
- generatedAt: `2026-08-16T21:19:00.031384Z`
- sourceCommit: `ec61f49da68c6e7e00ee8369364d0f35566a6821`
- status: `classification`

## Policy

- globalThresholdWeakened: `False`
- assertionsWeakened: `False`
- referencesModified: `False`
- scoresDirectlyEdited: `False`
- memoryBudgetChanged: `False`
- readinessDelta: `0.0`

## Population Shift

- includeBlocking: `True`
- runnerProperty: `-Dkanvas.gm.includeBlocking=true`
- dashboardProperty: `-Pgm.includeBlocking=true`
- wave0Population: `615`
- wave0DirectlyComparable: `False`
- comparisonNote: `population-shifted`

## Current Counters

- observedComparableRows: `78`
- candidateUnlockedRows: `0`
- supportedRowsAfter: `0`
- routeOnlyRows: `0`
- routeOnlyRowsPromoted: `False`

| Lane | Rows | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| `skia-runner` | 610 | 532 | 0 | 1 |
| `dashboard` | 610 | 0 | 0 | 0 |
| `svg` | 17 | 1 | 0 | 12 |
| `testOracle` | 0 | 0 | 0 | 0 |
| `cpuOracle` | 0 | 0 | 0 | 0 |

## Row Classifications

| Lane | Name | Outcome | Classification | Reference |
| --- | --- | --- | --- | --- |
| `skia-dashboard` | `animated-backdrop-blur` | failure | failure | skia-upstream |
| `skia-dashboard` | `bigblurs` | failure | failure | skia-upstream |
| `skia-dashboard` | `blur2rects` | passed | pass | skia-upstream |
| `skia-dashboard` | `blur2rectsnonninepatch` | failure | failure | skia-upstream |
| `skia-dashboard` | `BlurBigSigma` | passed | pass | skia-upstream |
| `skia-dashboard` | `blurcircles2` | failure | failure | skia-upstream |
| `skia-dashboard` | `blurcircles` | failure | failure | skia-upstream |
| `skia-dashboard` | `BlurDrawImage` | failure | failure | skia-upstream |
| `skia-dashboard` | `blur_ignore_xform_circle` | failure | failure | skia-upstream |
| `skia-dashboard` | `blur_ignore_xform_rrect` | failure | failure | skia-upstream |
| `skia-dashboard` | `blur_ignore_xform_rect` | failure | failure | skia-upstream |
| `skia-dashboard` | `blur_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `blur_matrix_rect` | failure | failure | skia-upstream |
| `skia-dashboard` | `check_small_sigma_offset` | failure | failure | skia-upstream |
| `skia-dashboard` | `blurquickreject` | failure | failure | skia-upstream |
| `skia-dashboard` | `blurrect_compare` | failure | failure | skia-upstream |
| `skia-dashboard` | `blurrect_gallery` | failure | failure | skia-upstream |
| `skia-dashboard` | `blurrects` | failure | failure | skia-upstream |
| `skia-dashboard` | `BlurSmallSigma` | passed | pass | skia-upstream |
| `skia-dashboard` | `blurredclippedcircle` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_899512` | passed | pass | skia-upstream |
| `skia-dashboard` | `emboss` | failure | failure | skia-upstream |
| `skia-dashboard` | `embossmaskfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `fast_slow_blurimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `hdr-pip-blur` | failure | failure | skia-upstream |
| `skia-dashboard` | `imageblur2` | passed | pass | skia-upstream |
| `skia-dashboard` | `imageblurclampmode` | failure | failure | skia-upstream |
| `skia-dashboard` | `imageblur` | passed | pass | skia-upstream |
| `skia-dashboard` | `imageblurrepeatmode` | failure | failure | skia-upstream |
| `skia-dashboard` | `imageblurrepeatunclipped` | failure | failure | skia-upstream |
| `skia-dashboard` | `inverse_fill_filters` | failure | failure | skia-upstream |
| `skia-dashboard` | `inverse_windingmode_filters` | failure | failure | skia-upstream |
| `skia-dashboard` | `matrixconvolution` | passed | pass | skia-upstream |
| `skia-dashboard` | `matrixconvolution_big_color` | passed | pass | skia-upstream |
| `skia-dashboard` | `matrixconvolution_big` | passed | pass | skia-upstream |
| `skia-dashboard` | `matrixconvolution_bigger` | passed | pass | skia-upstream |
| `skia-dashboard` | `matrixconvolution_biggest` | passed | pass | skia-upstream |
| `skia-dashboard` | `matrixconvolution_color` | failure | failure | skia-upstream |
| `skia-dashboard` | `rrect_blurs` | failure | failure | skia-upstream |
| `skia-dashboard` | `simpleblurroundrect` | failure | failure | skia-upstream |
| `skia-dashboard` | `smallemboss` | passed | pass | skia-upstream |
| `skia-dashboard` | `tablemaskfilter` | passed | pass | skia-upstream |
| `skia-dashboard` | `TiledBlurBigSigma` | failure | failure | skia-upstream |
| `skia-dashboard` | `aaclip` | failure | failure | skia-upstream |
| `skia-dashboard` | `bug339297_as_clip` | failure | failure | skia-upstream |
| `skia-dashboard` | `circular-clips` | failure | failure | skia-upstream |
| `skia-dashboard` | `clipcubic` | failure | failure | skia-upstream |
| `skia-dashboard` | `clipdrawdraw` | passed | pass | skia-upstream |
| `skia-dashboard` | `cliplargerect` | failure | failure | skia-upstream |
| `skia-dashboard` | `clip_region` | failure | failure | skia-upstream |
| `skia-dashboard` | `clip_sierpinski_region` | failure | failure | skia-upstream |
| `skia-dashboard` | `clip_strokerect` | failure | failure | skia-upstream |
| `skia-dashboard` | `clipsuperrrect` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `complexclip3_complex` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip3_simple` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip4_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip4_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_aa_invert` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_aa_layer` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_aa_layer_invert` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_blur_tiled` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_bw_invert` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_bw_layer` | failure | failure | skia-upstream |
| `skia-dashboard` | `complexclip_bw_layer_invert` | failure | failure | skia-upstream |
| `skia-dashboard` | `convex_poly_clip` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_892988` | failure | failure | skia-upstream |
| `skia-dashboard` | `croppedrects` | failure | failure | skia-upstream |
| `skia-dashboard` | `distantclip` | failure | failure | skia-upstream |
| `skia-dashboard` | `fast_constraint_red_is_allowed` | failure | failure | skia-upstream |
| `skia-dashboard` | `fast_constraint_red_is_allowed_manual` | failure | failure | skia-upstream |
| `skia-dashboard` | `inverseclip` | failure | failure | skia-upstream |
| `skia-dashboard` | `manypathatlases_128` | failure | failure | skia-upstream |
| `skia-dashboard` | `manypathatlases_2048` | failure | failure | skia-upstream |
| `skia-dashboard` | `pdf_crbug_772685` | failure | failure | skia-upstream |
| `skia-dashboard` | `perspective_clip` | failure | failure | skia-upstream |
| `skia-dashboard` | `rrect_clip_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `rrect_clip_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `rrect_clip_draw_paint` | failure | failure | skia-upstream |
| `skia-dashboard` | `simpleaaclip_path` | failure | failure | skia-upstream |
| `skia-dashboard` | `simpleaaclip_rect` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug1719` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug_9319` | failure | failure | skia-upstream |
| `skia-dashboard` | `strict_constraint_batch_no_red_allowed` | failure | failure | skia-upstream |
| `skia-dashboard` | `strict_constraint_batch_no_red_allowed_manual` | failure | failure | skia-upstream |
| `skia-dashboard` | `strict_constraint_no_red_allowed` | failure | failure | skia-upstream |
| `skia-dashboard` | `strict_constraint_no_red_allowed_manual` | failure | failure | skia-upstream |
| `skia-dashboard` | `windowrectangles` | failure | failure | skia-upstream |
| `skia-dashboard` | `color` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `filter` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `orientation` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `p3_ovals` | failure | failure | skia-upstream |
| `skia-dashboard` | `paint_alpha_normals_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `rect` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `shader` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `aaxfermodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `aarectmodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `arithmode_blender` | failure | failure | skia-upstream |
| `skia-dashboard` | `backdrop_hintrect_clipping` | failure | failure | skia-upstream |
| `skia-dashboard` | `backdrop_imagefilter_croprect` | failure | failure | skia-upstream |
| `skia-dashboard` | `badpaint` | failure | failure | skia-upstream |
| `skia-dashboard` | `clip_shader_difference` | passed | pass | skia-upstream |
| `skia-dashboard` | `clipshadermatrix` | failure | failure | skia-upstream |
| `skia-dashboard` | `clip_shader_layer` | passed | pass | skia-upstream |
| `skia-dashboard` | `clip_shader_nested` | failure | failure | skia-upstream |
| `skia-dashboard` | `clip_shader_persp` | passed | pass | skia-upstream |
| `skia-dashboard` | `clip_shader` | passed | pass | skia-upstream |
| `skia-dashboard` | `color4blendcf` | passed | pass | skia-upstream |
| `skia-dashboard` | `color4shader` | passed | pass | skia-upstream |
| `skia-dashboard` | `colorfilterimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorfilterimagefilter_layer` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorfiltershader` | failure | failure | skia-upstream |
| `skia-dashboard` | `lightingcolorfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `colormatrix` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorcomposefilter_alpha` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorcomposefilter_wacky` | failure | failure | skia-upstream |
| `skia-dashboard` | `compare_atlas_vertices` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeshader_alpha` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeshader_bitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeshader_bitmap_lm` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeshader` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeshader_grid` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeCFIF` | failure | failure | skia-upstream |
| `skia-dashboard` | `compositor_quads_color` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_1162942` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_1167277` | passed | pass | skia-upstream |
| `skia-dashboard` | `crbug_1174186` | passed | pass | skia-upstream |
| `skia-dashboard` | `crbug_1177833` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_918512` | failure | failure | skia-upstream |
| `skia-dashboard` | `destcolor` | failure | failure | skia-upstream |
| `skia-dashboard` | `discard` | failure | failure | skia-upstream |
| `skia-dashboard` | `displacement` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw-atlas-colors` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw-atlas` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw_image_set_alpha_only` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw_image_set` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw_image_set_rect_to_rect` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw_quad_set` | failure | failure | skia-upstream |
| `skia-dashboard` | `dropshadow_pseudopersp` | failure | failure | skia-upstream |
| `skia-dashboard` | `dstreadshuffle` | failure | failure | skia-upstream |
| `skia-dashboard` | `ducky_yuv_blend` | passed | pass | skia-upstream |
| `skia-dashboard` | `encode` | failure | failure | skia-upstream |
| `skia-dashboard` | `fadefilter` | passed | pass | skia-upstream |
| `skia-dashboard` | `filterfastbounds` | failure | failure | skia-upstream |
| `skia-dashboard` | `hslcolorfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `hairmodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `highcontrastfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `hsl` | failure | failure | skia-upstream |
| `skia-dashboard` | `HSL_duck` | passed | pass | skia-upstream |
| `skia-dashboard` | `imagefilter_composed_transform` | passed | pass | skia-upstream |
| `skia-dashboard` | `imagefilter_convolve_subset` | passed | pass | skia-upstream |
| `skia-dashboard` | `imagefiltersbase` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefiltersclipped` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefilterscropped` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefilters_effect_order` | passed | pass | skia-upstream |
| `skia-dashboard` | `imagefiltersstroked` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefilter_matrix_localmatrix` | passed | pass | skia-upstream |
| `skia-dashboard` | `imagefilterstransformed` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefiltersunpremul` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefilters_xfermodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `internal_links` | failure | failure | skia-upstream |
| `skia-dashboard` | `lcdblendmodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `lighting` | failure | failure | skia-upstream |
| `skia-dashboard` | `localmatriximagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `luminosity_overflow` | failure | failure | skia-upstream |
| `skia-dashboard` | `matriximagefilter` | passed | pass | skia-upstream |
| `skia-dashboard` | `mixerCF` | failure | failure | skia-upstream |
| `skia-dashboard` | `modecolorfilters` | passed | pass | skia-upstream |
| `skia-dashboard` | `offsetimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `overdraw_canvas` | failure | failure | skia-upstream |
| `skia-dashboard` | `overdrawcolorfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `patch_alpha` | failure | failure | skia-upstream |
| `skia-dashboard` | `patch_alpha_test` | failure | failure | skia-upstream |
| `skia-dashboard` | `patch_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `patch_image_persp` | failure | failure | skia-upstream |
| `skia-dashboard` | `patch_primitive` | failure | failure | skia-upstream |
| `skia-dashboard` | `perlinnoise` | failure | failure | skia-upstream |
| `skia-dashboard` | `perlinnoise_layered` | failure | failure | skia-upstream |
| `skia-dashboard` | `perlinnoise_localmatrix` | passed | pass | skia-upstream |
| `skia-dashboard` | `perlinnoise_rotated` | failure | failure | skia-upstream |
| `skia-dashboard` | `PlusMergesAA` | failure | failure | skia-upstream |
| `skia-dashboard` | `rasterallocator` | failure | failure | skia-upstream |
| `skia-dashboard` | `recordopts` | failure | failure | skia-upstream |
| `skia-dashboard` | `rotate_imagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtimecolorfilter_vertices_atlas_and_patch` | failure | failure | skia-upstream |
| `skia-dashboard` | `gpusamplerstress` | failure | failure | skia-upstream |
| `skia-dashboard` | `save_behind` | passed | pass | skia-upstream |
| `skia-dashboard` | `savelayer_f16` | failure | failure | skia-upstream |
| `skia-dashboard` | `savelayer_initfromprev` | passed | pass | skia-upstream |
| `skia-dashboard` | `shadermaskfilter_gradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `shadow_utils_gray` | failure | failure | skia-upstream |
| `skia-dashboard` | `shadow_utils_occl` | failure | failure | skia-upstream |
| `skia-dashboard` | `simple-offsetimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `sk3d_simple` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug_14554` | passed | pass | skia-upstream |
| `skia-dashboard` | `srcmode` | failure | failure | skia-upstream |
| `skia-dashboard` | `srgb_colorfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `tablecolorfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `extractalpha` | failure | failure | skia-upstream |
| `skia-dashboard` | `tileimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `transparency_check` | failure | failure | skia-upstream |
| `skia-dashboard` | `xfermodeimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `xfermodes2` | failure | failure | skia-upstream |
| `skia-dashboard` | `xfermodes3` | failure | failure | skia-upstream |
| `skia-dashboard` | `xfermodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `alphagradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `analytic_gradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `bug6643` | failure | failure | skia-upstream |
| `skia-dashboard` | `clamped_gradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_2pt_conical_inside_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_2pt_conical_outside` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_938592` | failure | failure | skia-upstream |
| `skia-dashboard` | `degenerate_gradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `emptyshader` | failure | failure | skia-upstream |
| `skia-dashboard` | `fillrect_gradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradient_dirty_laundry` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_many` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradient_many_hard_stops` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradient_many_stops` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradient_matrix` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_alpha_many_stops` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_color_space` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_color_space_many_stops` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_color_space_tilemode` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_degenerate_2pt` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_dup_color_stops` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_hue_method` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_interesting` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_local_perspective` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_no_texture` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradients_powerless_hue` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `gradients_view_perspective` | failure | failure | skia-upstream |
| `skia-dashboard` | `hardstop_gradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `hardstop_gradients_many` | failure | failure | skia-upstream |
| `skia-dashboard` | `linear_gradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `linear_gradient_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `linear_gradient_tiny` | failure | failure | skia-upstream |
| `skia-dashboard` | `persp_shaders_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `persp_shaders_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient2` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient3` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient3_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient4` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient4_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `radial_gradient_precision` | failure | failure | skia-upstream |
| `skia-dashboard` | `scaled_tilemode_gradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_conical` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_conical_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_linear` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_linear_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_radial` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_radial_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_sweep` | failure | failure | skia-upstream |
| `skia-dashboard` | `shallow_gradient_sweep_nodither` | failure | failure | skia-upstream |
| `skia-dashboard` | `small_color_stop` | failure | failure | skia-upstream |
| `skia-dashboard` | `sweep_tiling` | failure | failure | skia-upstream |
| `skia-dashboard` | `testgradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `all_bitmap_configs` | failure | failure | skia-upstream |
| `skia-dashboard` | `all_variants_8888` | failure | failure | skia-upstream |
| `skia-dashboard` | `alpha_image_alpha_tint` | failure | failure | skia-upstream |
| `skia-dashboard` | `alpha_image_shader_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `animatedGif` | passed | pass | skia-upstream |
| `skia-dashboard` | `anisomips` | failure | failure | skia-upstream |
| `skia-dashboard` | `anisotropic_image_scale_aniso` | failure | failure | skia-upstream |
| `skia-dashboard` | `anisotropic_image_scale_linear` | failure | failure | skia-upstream |
| `skia-dashboard` | `anisotropic_image_scale_mip` | failure | failure | skia-upstream |
| `skia-dashboard` | `async_rescale_and_read_alpha_type` | passed | pass | skia-upstream |
| `skia-dashboard` | `async_rescale_and_read_rose` | passed | pass | skia-upstream |
| `skia-dashboard` | `async_rescale_and_read_no_bleed` | passed | pass | skia-upstream |
| `skia-dashboard` | `async_yuv_no_scale` | passed | pass | skia-upstream |
| `skia-dashboard` | `bc1_transparency` | failure | failure | skia-upstream |
| `skia-dashboard` | `bicubic` | failure | failure | skia-upstream |
| `skia-dashboard` | `bigmatrix` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmapcopy` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmapfilters` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmap-image-srgb-legacy` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmap_premul` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmaprect_rounding` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmapshaders` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmap_subset_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `bleed_downscale` | passed | pass | skia-upstream |
| `skia-dashboard` | `bmp_filter_quality_repeat` | failure | failure | skia-upstream |
| `skia-dashboard` | `bug6783` | failure | failure | skia-upstream |
| `skia-dashboard` | `cgimage` | passed | pass | skia-upstream |
| `skia-dashboard` | `child_sampling_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `clippedbitmapshaders` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `color_cube_cf_rt` | passed | pass | skia-upstream |
| `skia-dashboard` | `colorwheel_alphatypes` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorwheel` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorspace2` | failure | failure | skia-upstream |
| `skia-dashboard` | `colorspace` | failure | failure | skia-upstream |
| `skia-dashboard` | `compositor_quads_image` | passed | pass | skia-upstream |
| `skia-dashboard` | `compressed_textures` | failure | failure | skia-upstream |
| `skia-dashboard` | `coordclampshader` | passed | pass | skia-upstream |
| `skia-dashboard` | `copyTo4444` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_224618` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_404394639` | failure | failure | skia-upstream |
| `skia-dashboard` | `deferred_shader_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmaprect_s` | failure | failure | skia-upstream |
| `skia-dashboard` | `bitmaprect_i` | failure | failure | skia-upstream |
| `skia-dashboard` | `3x3bitmaprect` | failure | failure | skia-upstream |
| `skia-dashboard` | `draw_bitmap_rect_skbug4734` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawminibitmaprect` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawimage_sampling` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawimagerect_filter` | failure | failure | skia-upstream |
| `skia-dashboard` | `encode-alpha-jpeg` | passed | pass | skia-upstream |
| `skia-dashboard` | `encode-color-types-webp-lossless` | failure | failure | skia-upstream |
| `skia-dashboard` | `encode-platform` | passed | pass | skia-upstream |
| `skia-dashboard` | `encode-srgb-png` | failure | failure | skia-upstream |
| `skia-dashboard` | `exoticformats` | failure | failure | skia-upstream |
| `skia-dashboard` | `filterbug` | failure | failure | skia-upstream |
| `skia-dashboard` | `filterindiabox` | passed | pass | skia-upstream |
| `skia-dashboard` | `flight_animated_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `flippity` | failure | failure | skia-upstream |
| `skia-dashboard` | `format4444` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_clamp_bilerp_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_clamp_bilerp_scale` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_clamp_point_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_clamp_point_scale` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_mirror_bilerp_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_mirror_bilerp_scale` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_mirror_point_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_mirror_point_scale` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_repeat_bilerp_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_repeat_bilerp_scale` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_repeat_point_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `giantbitmap_repeat_point_scale` | failure | failure | skia-upstream |
| `skia-dashboard` | `grayscalejpg` | passed | pass | skia-upstream |
| `skia-dashboard` | `hugebitmapshader` | failure | failure | skia-upstream |
| `skia-dashboard` | `image-cacherator-from-picture` | failure | failure | skia-upstream |
| `skia-dashboard` | `image_dither` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefilter_transformed_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefilterscropexpand` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagefiltersgraph` | failure | failure | skia-upstream |
| `skia-dashboard` | `image-surface` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagemagnifier_bounds` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagemagnifier_cropped` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagemagnifier` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagemakewithfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagemasksubset` | failure | failure | skia-upstream |
| `skia-dashboard` | `image_out_of_gamut` | failure | failure | skia-upstream |
| `skia-dashboard` | `image-picture` | failure | failure | skia-upstream |
| `skia-dashboard` | `imageresizetiled` | failure | failure | skia-upstream |
| `skia-dashboard` | `image-shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `imagesource` | failure | failure | skia-upstream |
| `skia-dashboard` | `image_subset` | failure | failure | skia-upstream |
| `skia-dashboard` | `imageshader_tinyscale` | passed | pass | skia-upstream |
| `skia-dashboard` | `jpg-color-cube` | failure | failure | skia-upstream |
| `skia-dashboard` | `lit_shader_linear_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `localmatriximageshader_filtering` | failure | failure | skia-upstream |
| `skia-dashboard` | `localmatriximageshader` | failure | failure | skia-upstream |
| `skia-dashboard` | `localmatrixshader_nested` | failure | failure | skia-upstream |
| `skia-dashboard` | `localmatrixshader_persp` | failure | failure | skia-upstream |
| `skia-dashboard` | `local_matrix_shader_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `localmatrix_order` | failure | failure | skia-upstream |
| `skia-dashboard` | `makecolorspace` | failure | failure | skia-upstream |
| `skia-dashboard` | `makecolortypeandspace` | passed | pass | skia-upstream |
| `skia-dashboard` | `makeRasterImage` | failure | failure | skia-upstream |
| `skia-dashboard` | `mipmap_gray8_srgb` | failure | failure | skia-upstream |
| `skia-dashboard` | `mipmap_srgb` | failure | failure | skia-upstream |
| `skia-dashboard` | `mirror_tile` | failure | failure | skia-upstream |
| `skia-dashboard` | `nearest_half_pixel_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `new_texture_image` | passed | pass | skia-upstream |
| `skia-dashboard` | `ninepatch-stretch` | failure | failure | skia-upstream |
| `skia-dashboard` | `not_native32_bitmap_config` | failure | failure | skia-upstream |
| `skia-dashboard` | `null_child_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `p3` | failure | failure | skia-upstream |
| `skia-dashboard` | `persp_images` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureimagefilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureimagegenerator` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureshader_alpha` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureshadercache` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureshader` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureshader_localwrapper` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureshader_persp` | failure | failure | skia-upstream |
| `skia-dashboard` | `pictureshadertile` | failure | failure | skia-upstream |
| `skia-dashboard` | `poster_circle` | passed | pass | skia-upstream |
| `skia-dashboard` | `raw_image_shader_normals_rt` | passed | pass | skia-upstream |
| `skia-dashboard` | `readpixelscodec` | failure | failure | skia-upstream |
| `skia-dashboard` | `readpixelspicture` | failure | failure | skia-upstream |
| `skia-dashboard` | `reinterpretcolorspace` | failure | failure | skia-upstream |
| `skia-dashboard` | `repeated_bitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `repeated_bitmap_jpg` | failure | failure | skia-upstream |
| `skia-dashboard` | `scale-pixels` | failure | size-mismatch | skia-upstream |
| `skia-dashboard` | `scaled_tilemode_bitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `scaled_tilemodes` | passed | pass | skia-upstream |
| `skia-dashboard` | `scaled_tilemodes_npot` | passed | pass | skia-upstream |
| `skia-dashboard` | `scalepixels_unpremul` | failure | failure | skia-upstream |
| `skia-dashboard` | `shaderpath` | failure | failure | skia-upstream |
| `skia-dashboard` | `showmiplevels_explicit` | passed | pass | skia-upstream |
| `skia-dashboard` | `skbug_8664` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug_9819` | failure | failure | skia-upstream |
| `skia-dashboard` | `stoplight_animated_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `surface_underdraw` | failure | failure | skia-upstream |
| `skia-dashboard` | `texture` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `textureimage_and_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `tiled_picture_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `tiledscaledbitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `tilemode_decal` | failure | failure | skia-upstream |
| `skia-dashboard` | `tilemodes_alpha` | failure | failure | skia-upstream |
| `skia-dashboard` | `tilemodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `tilemode_bitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `tilemode_gradient` | failure | failure | skia-upstream |
| `skia-dashboard` | `tinybitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `unpremul` | failure | failure | skia-upstream |
| `skia-dashboard` | `verylargebitmap` | failure | failure | skia-upstream |
| `skia-dashboard` | `verylarge_picture_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `wacky_yuv_formats` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `yuv420_odd_dim` | passed | pass | skia-upstream |
| `skia-dashboard` | `yuv420_odd_dim_repeat` | passed | pass | skia-upstream |
| `skia-dashboard` | `custommesh_cs` | failure | failure | skia-upstream |
| `skia-dashboard` | `custommesh_cs_uniforms` | failure | failure | skia-upstream |
| `skia-dashboard` | `custommesh` | failure | failure | skia-upstream |
| `skia-dashboard` | `custommesh_uniforms` | skipped | expected-unsupported | skia-upstream |
| `skia-dashboard` | `mesh_updates` | failure | failure | skia-upstream |
| `skia-dashboard` | `mesh_with_effects` | failure | failure | skia-upstream |
| `skia-dashboard` | `mesh_with_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `mesh_with_paint_color` | passed | pass | skia-upstream |
| `skia-dashboard` | `mesh_with_paint_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `mesh_zero_init` | failure | failure | skia-upstream |
| `skia-dashboard` | `picture_mesh` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug_13047` | failure | failure | skia-upstream |
| `skia-dashboard` | `vertices_batching` | failure | failure | skia-upstream |
| `skia-dashboard` | `vertices_collapsed` | failure | failure | skia-upstream |
| `skia-dashboard` | `vertices` | failure | failure | skia-upstream |
| `skia-dashboard` | `vertices_perspective` | failure | failure | skia-upstream |
| `skia-dashboard` | `bezier_conic_effects` | failure | failure | skia-upstream |
| `skia-dashboard` | `bezier_quad_effects` | failure | failure | skia-upstream |
| `skia-dashboard` | `bug41422450` | failure | failure | skia-upstream |
| `skia-dashboard` | `ctmpatheffect` | failure | failure | skia-upstream |
| `skia-dashboard` | `circle_sizes` | failure | failure | skia-upstream |
| `skia-dashboard` | `clockwise` | failure | failure | skia-upstream |
| `skia-dashboard` | `concavepaths` | failure | failure | skia-upstream |
| `skia-dashboard` | `conicpaths` | failure | failure | skia-upstream |
| `skia-dashboard` | `convex_lineonly_paths` | failure | failure | skia-upstream |
| `skia-dashboard` | `convex_lineonly_paths_stroke_and_fill` | failure | failure | skia-upstream |
| `skia-dashboard` | `convexpaths` | failure | failure | skia-upstream |
| `skia-dashboard` | `convex-polygon-inset` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_640176` | passed | pass | skia-upstream |
| `skia-dashboard` | `crbug_691386` | failure | failure | skia-upstream |
| `skia-dashboard` | `cubicclosepath` | failure | failure | skia-upstream |
| `skia-dashboard` | `cubicpath` | failure | failure | skia-upstream |
| `skia-dashboard` | `cubicpath_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `dashcircle` | failure | failure | skia-upstream |
| `skia-dashboard` | `dashtextcaps` | failure | failure | skia-upstream |
| `skia-dashboard` | `dashing5_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `dashing` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawlines_with_local_matrix` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawregion` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawregionmodes` | failure | failure | skia-upstream |
| `skia-dashboard` | `fancy_gradients` | failure | failure | skia-upstream |
| `skia-dashboard` | `fatpathfill` | failure | failure | skia-upstream |
| `skia-dashboard` | `filltypespersp` | failure | failure | skia-upstream |
| `skia-dashboard` | `inner_join_geometry` | failure | failure | skia-upstream |
| `skia-dashboard` | `lattice2` | failure | failure | skia-upstream |
| `skia-dashboard` | `lineclosepath` | failure | failure | skia-upstream |
| `skia-dashboard` | `linepath` | failure | failure | skia-upstream |
| `skia-dashboard` | `macaatest` | failure | failure | skia-upstream |
| `skia-dashboard` | `mandoline` | failure | failure | skia-upstream |
| `skia-dashboard` | `manycircles` | failure | failure | skia-upstream |
| `skia-dashboard` | `manypathatlases` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `nested` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `nonclosedpaths` | failure | failure | skia-upstream |
| `skia-dashboard` | `OverStroke` | failure | failure | skia-upstream |
| `skia-dashboard` | `parsedpaths` | failure | failure | skia-upstream |
| `skia-dashboard` | `path_huge_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `path_huge_aa_manual` | failure | failure | skia-upstream |
| `skia-dashboard` | `path_mask_cache` | failure | failure | skia-upstream |
| `skia-dashboard` | `pathops_blend` | failure | failure | skia-upstream |
| `skia-dashboard` | `pathopsinverse` | failure | failure | skia-upstream |
| `skia-dashboard` | `pathops_skbug_10155` | failure | failure | skia-upstream |
| `skia-dashboard` | `path-reverse` | failure | failure | skia-upstream |
| `skia-dashboard` | `path_stroke_clip_crbug1070835` | failure | failure | skia-upstream |
| `skia-dashboard` | `points` | failure | failure | skia-upstream |
| `skia-dashboard` | `poly2poly` | failure | failure | skia-upstream |
| `skia-dashboard` | `polygons` | failure | failure | skia-upstream |
| `skia-dashboard` | `preservefillrule_big` | failure | failure | skia-upstream |
| `skia-dashboard` | `preservefillrule_little` | failure | failure | skia-upstream |
| `skia-dashboard` | `quadclosepath` | failure | failure | skia-upstream |
| `skia-dashboard` | `quadpath` | failure | failure | skia-upstream |
| `skia-dashboard` | `roundrects` | failure | failure | skia-upstream |
| `skia-dashboard` | `shadow_utils_directional` | passed | pass | skia-upstream |
| `skia-dashboard` | `sharedcorners` | failure | failure | skia-upstream |
| `skia-dashboard` | `simpleshapes_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `simpleshapes` | failure | failure | skia-upstream |
| `skia-dashboard` | `stlouisarch` | failure | failure | skia-upstream |
| `skia-dashboard` | `stroke_rect_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `strokedline_caps` | failure | failure | skia-upstream |
| `skia-dashboard` | `strokes3` | failure | failure | skia-upstream |
| `skia-dashboard` | `strokes_round` | failure | failure | skia-upstream |
| `skia-dashboard` | `teenyStrokes` | failure | failure | skia-upstream |
| `skia-dashboard` | `thin_aa_dash_lines` | failure | failure | skia-upstream |
| `skia-dashboard` | `thinconcavepaths` | passed | pass | skia-upstream |
| `skia-dashboard` | `thinrects` | failure | failure | skia-upstream |
| `skia-dashboard` | `thinroundrects` | failure | failure | skia-upstream |
| `skia-dashboard` | `thinstrokedrects` | failure | failure | skia-upstream |
| `skia-dashboard` | `tinyanglearcs` | failure | failure | skia-upstream |
| `skia-dashboard` | `trickycubicstrokes_largeradius` | failure | failure | skia-upstream |
| `skia-dashboard` | `trimpatheffect` | failure | failure | skia-upstream |
| `skia-dashboard` | `widebuttcaps` | failure | failure | skia-upstream |
| `skia-dashboard` | `zero_control_stroke` | failure | failure | skia-upstream |
| `skia-dashboard` | `zeroPath` | failure | failure | skia-upstream |
| `skia-dashboard` | `zero_length_paths_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `zero_length_paths_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `zero_length_paths_dbl_aa` | failure | failure | skia-upstream |
| `skia-dashboard` | `zero_length_paths_dbl_bw` | failure | failure | skia-upstream |
| `skia-dashboard` | `zerolinedash` | failure | failure | skia-upstream |
| `skia-dashboard` | `zerolinestroke` | failure | failure | skia-upstream |
| `skia-dashboard` | `AlternateLuma` | failure | failure | skia-upstream |
| `skia-dashboard` | `arithmode` | failure | failure | skia-upstream |
| `skia-dashboard` | `color_cube_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `composeCF` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_intrinsics_common` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_intrinsics_exponential` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_intrinsics_geometric` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_intrinsics_matrix` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_intrinsics_relational` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_intrinsics_trig` | failure | failure | skia-upstream |
| `skia-dashboard` | `kawase_blur_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `lineargradientrt` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `lumafilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `rippleshader` | failure | failure | skia-upstream |
| `skia-dashboard` | `rtif_distort` | failure | failure | skia-upstream |
| `skia-dashboard` | `rtif_unsharp` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtimecolorfilter` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtimefunctions` | failure | failure | skia-upstream |
| `skia-dashboard` | `runtime_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `spiral_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `threshold_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `unsharp_rt` | failure | failure | skia-upstream |
| `skia-dashboard` | `workingspace` | passed | pass | skia-upstream |
| `skia-dashboard` | `annotated_text` | failure | failure | skia-upstream |
| `skia-dashboard` | `bigtext_crbug_1370488` | failure | failure | skia-upstream |
| `skia-dashboard` | `bigtext` | failure | failure | skia-upstream |
| `skia-dashboard` | `blob_rsxform_distortable` | failure | failure | skia-upstream |
| `skia-dashboard` | `blob_rsxform` | failure | failure | skia-upstream |
| `skia-dashboard` | `chrome_gradtext2` | failure | failure | skia-upstream |
| `skia-dashboard` | `cliperror` | failure | failure | skia-upstream |
| `skia-dashboard` | `coloremoji_colrv0` | failure | failure | skia-upstream |
| `skia-dashboard` | `coloremoji` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `colorwheelnative` | failure | failure | skia-upstream |
| `skia-dashboard` | `coloremoji_blendmodes_colrv0` | failure | failure | skia-upstream |
| `skia-dashboard` | `coloremoji_blendmodes` | failure | missing-reference | skia-upstream |
| `skia-dashboard` | `colrv1_gradient_stops_repeat` | failure | failure | skia-upstream |
| `skia-dashboard` | `crbug_1073670` | failure | failure | skia-upstream |
| `skia-dashboard` | `dftext_blob_persp` | failure | failure | skia-upstream |
| `skia-dashboard` | `dftext` | failure | failure | skia-upstream |
| `skia-dashboard` | `drawTextRSXform` | failure | failure | skia-upstream |
| `skia-dashboard` | `fontcache` | passed | pass | skia-upstream |
| `skia-dashboard` | `fontmgr_bounds` | failure | failure | skia-upstream |
| `skia-dashboard` | `fontmgr_iter` | passed | pass | skia-upstream |
| `skia-dashboard` | `fontmgr_match` | passed | pass | skia-upstream |
| `skia-dashboard` | `font_palette_default` | failure | failure | skia-upstream |
| `skia-dashboard` | `fontregen` | passed | pass | skia-upstream |
| `skia-dashboard` | `fontscalerdistortable` | failure | failure | skia-upstream |
| `skia-dashboard` | `fontscaler` | failure | failure | skia-upstream |
| `skia-dashboard` | `gammagradienttext` | passed | pass | skia-upstream |
| `skia-dashboard` | `gammatext_color_shader` | failure | failure | skia-upstream |
| `skia-dashboard` | `gammatext` | failure | failure | skia-upstream |
| `skia-dashboard` | `getpostextpath` | failure | failure | skia-upstream |
| `skia-dashboard` | `gradtext` | failure | failure | skia-upstream |
| `skia-dashboard` | `largeglyphblur` | failure | failure | skia-upstream |
| `skia-dashboard` | `lcdoverlap` | failure | failure | skia-upstream |
| `skia-dashboard` | `macaa_colors` | failure | failure | skia-upstream |
| `skia-dashboard` | `mixedtextblobs` | failure | failure | skia-upstream |
| `skia-dashboard` | `overdraw_text_xform` | failure | failure | skia-upstream |
| `skia-dashboard` | `palette` | passed | pass | skia-upstream |
| `skia-dashboard` | `pdf_never_embed` | failure | failure | skia-upstream |
| `skia-dashboard` | `pdf_table_based_subset` | passed | pass | skia-upstream |
| `skia-dashboard` | `persptext` | failure | failure | skia-upstream |
| `skia-dashboard` | `persptext_minimal` | failure | failure | skia-upstream |
| `skia-dashboard` | `rsx_blob_shader` | passed | pass | skia-upstream |
| `skia-dashboard` | `scaledemojiperspective_colrv0` | failure | failure | skia-upstream |
| `skia-dashboard` | `scaledemojipos_colrv0` | failure | failure | skia-upstream |
| `skia-dashboard` | `scaledemoji_colrv0` | failure | failure | skia-upstream |
| `skia-dashboard` | `scaledemoji_rendering` | failure | failure | skia-upstream |
| `skia-dashboard` | `shadertext3` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug_12212` | passed | pass | skia-upstream |
| `skia-dashboard` | `skbug_257` | failure | failure | skia-upstream |
| `skia-dashboard` | `skbug_5321` | passed | pass | skia-upstream |
| `skia-dashboard` | `skbug_8955` | failure | failure | skia-upstream |
| `skia-dashboard` | `slug` | failure | failure | skia-upstream |
| `skia-dashboard` | `stroketext` | failure | failure | skia-upstream |
| `skia-dashboard` | `stroketext_native` | failure | failure | skia-upstream |
| `skia-dashboard` | `surfaceprops` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobblockreordering` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobcolortrans` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobgeometrychange` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblob` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblob_intercepts` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobmixedsizes` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobrandomfont` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobshader` | passed | pass | skia-upstream |
| `skia-dashboard` | `textblobtransforms` | failure | failure | skia-upstream |
| `skia-dashboard` | `textblobuseaftergpufree` | failure | failure | skia-upstream |
| `skia-dashboard` | `fancyblobunderline` | failure | failure | skia-upstream |
| `skia-dashboard` | `textfilter_color` | failure | failure | skia-upstream |
| `skia-dashboard` | `textfilter_image` | failure | failure | skia-upstream |
| `skia-dashboard` | `text_scale_skew` | failure | similarity-failure | skia-upstream |
| `skia-dashboard` | `text_scale_skew_rotate` | failure | failure | skia-upstream |
| `skia-dashboard` | `typefacerendering` | failure | failure | skia-upstream |
| `skia-dashboard` | `typefacerendering_pfa` | passed | pass | skia-upstream |
| `skia-dashboard` | `typefacerendering_pfb` | passed | pass | skia-upstream |
| `skia-dashboard` | `typefacestyles` | failure | failure | skia-upstream |
| `skia-dashboard` | `typefacestyles_kerning` | failure | failure | skia-upstream |
| `skia-dashboard` | `typeface_styling` | failure | failure | skia-upstream |
| `skia-dashboard` | `user_typeface` | failure | failure | skia-upstream |
| `skia` | `[1] animated-backdrop-blur` | failure | terminal-refusal | skia-upstream |
| `skia` | `[2] bigblurs` | failure | terminal-refusal | skia-upstream |
| `skia` | `[3] blur2rects` | passed | pass | skia-upstream |
| `skia` | `[4] blur2rectsnonninepatch` | failure | terminal-refusal | skia-upstream |
| `skia` | `[5] BlurBigSigma` | passed | pass | skia-upstream |
| `skia` | `[6] blurcircles2` | failure | terminal-refusal | skia-upstream |
| `skia` | `[7] blurcircles` | failure | terminal-refusal | skia-upstream |
| `skia` | `[8] BlurDrawImage` | failure | terminal-refusal | skia-upstream |
| `skia` | `[9] blur_ignore_xform_circle` | failure | terminal-refusal | skia-upstream |
| `skia` | `[10] blur_ignore_xform_rrect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[11] blur_ignore_xform_rect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[12] blur_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[13] blur_matrix_rect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[14] check_small_sigma_offset` | failure | terminal-refusal | skia-upstream |
| `skia` | `[15] blurquickreject` | failure | terminal-refusal | skia-upstream |
| `skia` | `[16] blurrect_compare` | failure | terminal-refusal | skia-upstream |
| `skia` | `[17] blurrect_gallery` | failure | terminal-refusal | skia-upstream |
| `skia` | `[18] blurrects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[19] BlurSmallSigma` | passed | pass | skia-upstream |
| `skia` | `[20] blurredclippedcircle` | failure | terminal-refusal | skia-upstream |
| `skia` | `[21] crbug_899512` | passed | pass | skia-upstream |
| `skia` | `[22] emboss` | failure | terminal-refusal | skia-upstream |
| `skia` | `[23] embossmaskfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[24] fast_slow_blurimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[25] hdr-pip-blur` | failure | terminal-refusal | skia-upstream |
| `skia` | `[26] imageblur2` | passed | pass | skia-upstream |
| `skia` | `[27] imageblurclampmode` | failure | terminal-refusal | skia-upstream |
| `skia` | `[28] imageblur` | passed | pass | skia-upstream |
| `skia` | `[29] imageblurrepeatmode` | failure | terminal-refusal | skia-upstream |
| `skia` | `[30] imageblurrepeatunclipped` | failure | terminal-refusal | skia-upstream |
| `skia` | `[31] inverse_fill_filters` | failure | terminal-refusal | skia-upstream |
| `skia` | `[32] inverse_windingmode_filters` | failure | terminal-refusal | skia-upstream |
| `skia` | `[33] matrixconvolution` | passed | pass | skia-upstream |
| `skia` | `[34] matrixconvolution_big_color` | passed | pass | skia-upstream |
| `skia` | `[35] matrixconvolution_big` | passed | pass | skia-upstream |
| `skia` | `[36] matrixconvolution_bigger` | passed | pass | skia-upstream |
| `skia` | `[37] matrixconvolution_biggest` | passed | pass | skia-upstream |
| `skia` | `[38] matrixconvolution_color` | failure | terminal-refusal | skia-upstream |
| `skia` | `[39] rrect_blurs` | failure | terminal-refusal | skia-upstream |
| `skia` | `[40] simpleblurroundrect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[41] smallemboss` | passed | pass | skia-upstream |
| `skia` | `[42] tablemaskfilter` | passed | pass | skia-upstream |
| `skia` | `[43] TiledBlurBigSigma` | failure | terminal-refusal | skia-upstream |
| `skia` | `[44] aaclip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[45] bug339297_as_clip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[46] circular-clips` | failure | terminal-refusal | skia-upstream |
| `skia` | `[47] clipcubic` | failure | terminal-refusal | skia-upstream |
| `skia` | `[48] clipdrawdraw` | passed | pass | skia-upstream |
| `skia` | `[49] cliplargerect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[50] clip_region` | failure | terminal-refusal | skia-upstream |
| `skia` | `[51] clip_sierpinski_region` | failure | terminal-refusal | skia-upstream |
| `skia` | `[52] clip_strokerect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[53] clipsuperrrect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[54] complexclip3_complex` | failure | terminal-refusal | skia-upstream |
| `skia` | `[55] complexclip3_simple` | failure | terminal-refusal | skia-upstream |
| `skia` | `[56] complexclip4_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[57] complexclip4_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[58] complexclip_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[59] complexclip_aa_invert` | failure | terminal-refusal | skia-upstream |
| `skia` | `[60] complexclip_aa_layer` | failure | terminal-refusal | skia-upstream |
| `skia` | `[61] complexclip_aa_layer_invert` | failure | terminal-refusal | skia-upstream |
| `skia` | `[62] complexclip_blur_tiled` | failure | terminal-refusal | skia-upstream |
| `skia` | `[63] complexclip_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[64] complexclip_bw_invert` | failure | terminal-refusal | skia-upstream |
| `skia` | `[65] complexclip_bw_layer` | failure | terminal-refusal | skia-upstream |
| `skia` | `[66] complexclip_bw_layer_invert` | failure | terminal-refusal | skia-upstream |
| `skia` | `[67] convex_poly_clip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[68] crbug_892988` | failure | terminal-refusal | skia-upstream |
| `skia` | `[69] croppedrects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[70] distantclip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[71] fast_constraint_red_is_allowed` | failure | terminal-refusal | skia-upstream |
| `skia` | `[72] fast_constraint_red_is_allowed_manual` | failure | terminal-refusal | skia-upstream |
| `skia` | `[73] inverseclip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[74] manypathatlases_128` | failure | terminal-refusal | skia-upstream |
| `skia` | `[75] manypathatlases_2048` | failure | terminal-refusal | skia-upstream |
| `skia` | `[76] pdf_crbug_772685` | failure | terminal-refusal | skia-upstream |
| `skia` | `[77] perspective_clip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[78] rrect_clip_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[79] rrect_clip_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[80] rrect_clip_draw_paint` | failure | terminal-refusal | skia-upstream |
| `skia` | `[81] simpleaaclip_path` | failure | terminal-refusal | skia-upstream |
| `skia` | `[82] simpleaaclip_rect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[83] skbug1719` | failure | terminal-refusal | skia-upstream |
| `skia` | `[84] skbug_9319` | failure | terminal-refusal | skia-upstream |
| `skia` | `[85] strict_constraint_batch_no_red_allowed` | failure | terminal-refusal | skia-upstream |
| `skia` | `[86] strict_constraint_batch_no_red_allowed_manual` | failure | terminal-refusal | skia-upstream |
| `skia` | `[87] strict_constraint_no_red_allowed` | failure | terminal-refusal | skia-upstream |
| `skia` | `[88] strict_constraint_no_red_allowed_manual` | failure | terminal-refusal | skia-upstream |
| `skia` | `[89] windowrectangles` | failure | terminal-refusal | skia-upstream |
| `skia` | `[90] color` | failure | missing-reference | skia-upstream |
| `skia` | `[91] filter` | failure | missing-reference | skia-upstream |
| `skia` | `[92] orientation` | failure | missing-reference | skia-upstream |
| `skia` | `[93] p3_ovals` | failure | terminal-refusal | skia-upstream |
| `skia` | `[94] paint_alpha_normals_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[95] rect` | failure | missing-reference | skia-upstream |
| `skia` | `[96] shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[97] aaxfermodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[98] aarectmodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[99] arithmode_blender` | failure | terminal-refusal | skia-upstream |
| `skia` | `[100] backdrop_hintrect_clipping` | failure | terminal-refusal | skia-upstream |
| `skia` | `[101] backdrop_imagefilter_croprect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[102] badpaint` | failure | terminal-refusal | skia-upstream |
| `skia` | `[103] clip_shader_difference` | passed | pass | skia-upstream |
| `skia` | `[104] clipshadermatrix` | failure | terminal-refusal | skia-upstream |
| `skia` | `[105] clip_shader_layer` | passed | pass | skia-upstream |
| `skia` | `[106] clip_shader_nested` | failure | terminal-refusal | skia-upstream |
| `skia` | `[107] clip_shader_persp` | passed | pass | skia-upstream |
| `skia` | `[108] clip_shader` | passed | pass | skia-upstream |
| `skia` | `[109] color4blendcf` | passed | pass | skia-upstream |
| `skia` | `[110] color4shader` | passed | pass | skia-upstream |
| `skia` | `[111] colorfilterimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[112] colorfilterimagefilter_layer` | failure | terminal-refusal | skia-upstream |
| `skia` | `[113] colorfiltershader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[114] lightingcolorfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[115] colormatrix` | failure | terminal-refusal | skia-upstream |
| `skia` | `[116] colorcomposefilter_alpha` | failure | terminal-refusal | skia-upstream |
| `skia` | `[117] colorcomposefilter_wacky` | failure | terminal-refusal | skia-upstream |
| `skia` | `[118] compare_atlas_vertices` | failure | terminal-refusal | skia-upstream |
| `skia` | `[119] composeshader_alpha` | failure | terminal-refusal | skia-upstream |
| `skia` | `[120] composeshader_bitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[121] composeshader_bitmap_lm` | failure | terminal-refusal | skia-upstream |
| `skia` | `[122] composeshader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[123] composeshader_grid` | failure | terminal-refusal | skia-upstream |
| `skia` | `[124] composeCFIF` | failure | terminal-refusal | skia-upstream |
| `skia` | `[125] compositor_quads_color` | failure | terminal-refusal | skia-upstream |
| `skia` | `[126] crbug_1162942` | failure | terminal-refusal | skia-upstream |
| `skia` | `[127] crbug_1167277` | passed | pass | skia-upstream |
| `skia` | `[128] crbug_1174186` | passed | pass | skia-upstream |
| `skia` | `[129] crbug_1177833` | failure | terminal-refusal | skia-upstream |
| `skia` | `[130] crbug_918512` | failure | terminal-refusal | skia-upstream |
| `skia` | `[131] destcolor` | failure | terminal-refusal | skia-upstream |
| `skia` | `[132] discard` | failure | terminal-refusal | skia-upstream |
| `skia` | `[133] displacement` | failure | terminal-refusal | skia-upstream |
| `skia` | `[134] draw-atlas-colors` | failure | terminal-refusal | skia-upstream |
| `skia` | `[135] draw-atlas` | failure | terminal-refusal | skia-upstream |
| `skia` | `[136] draw_image_set_alpha_only` | failure | terminal-refusal | skia-upstream |
| `skia` | `[137] draw_image_set` | failure | terminal-refusal | skia-upstream |
| `skia` | `[138] draw_image_set_rect_to_rect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[139] draw_quad_set` | failure | terminal-refusal | skia-upstream |
| `skia` | `[140] dropshadow_pseudopersp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[141] dstreadshuffle` | failure | terminal-refusal | skia-upstream |
| `skia` | `[142] ducky_yuv_blend` | passed | pass | skia-upstream |
| `skia` | `[143] encode` | failure | terminal-refusal | skia-upstream |
| `skia` | `[144] fadefilter` | passed | pass | skia-upstream |
| `skia` | `[145] filterfastbounds` | failure | terminal-refusal | skia-upstream |
| `skia` | `[146] hslcolorfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[147] hairmodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[148] highcontrastfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[149] hsl` | failure | terminal-refusal | skia-upstream |
| `skia` | `[150] HSL_duck` | passed | pass | skia-upstream |
| `skia` | `[151] imagefilter_composed_transform` | passed | pass | skia-upstream |
| `skia` | `[152] imagefilter_convolve_subset` | passed | pass | skia-upstream |
| `skia` | `[153] imagefiltersbase` | failure | terminal-refusal | skia-upstream |
| `skia` | `[154] imagefiltersclipped` | failure | terminal-refusal | skia-upstream |
| `skia` | `[155] imagefilterscropped` | failure | terminal-refusal | skia-upstream |
| `skia` | `[156] imagefilters_effect_order` | passed | pass | skia-upstream |
| `skia` | `[157] imagefiltersstroked` | failure | terminal-refusal | skia-upstream |
| `skia` | `[158] imagefilter_matrix_localmatrix` | passed | pass | skia-upstream |
| `skia` | `[159] imagefilterstransformed` | failure | terminal-refusal | skia-upstream |
| `skia` | `[160] imagefiltersunpremul` | failure | terminal-refusal | skia-upstream |
| `skia` | `[161] imagefilters_xfermodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[162] internal_links` | failure | terminal-refusal | skia-upstream |
| `skia` | `[163] lcdblendmodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[164] lighting` | failure | terminal-refusal | skia-upstream |
| `skia` | `[165] localmatriximagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[166] luminosity_overflow` | failure | terminal-refusal | skia-upstream |
| `skia` | `[167] matriximagefilter` | passed | pass | skia-upstream |
| `skia` | `[168] mixerCF` | failure | terminal-refusal | skia-upstream |
| `skia` | `[169] modecolorfilters` | passed | pass | skia-upstream |
| `skia` | `[170] offsetimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[171] overdraw_canvas` | failure | terminal-refusal | skia-upstream |
| `skia` | `[172] overdrawcolorfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[173] patch_alpha` | failure | terminal-refusal | skia-upstream |
| `skia` | `[174] patch_alpha_test` | failure | terminal-refusal | skia-upstream |
| `skia` | `[175] patch_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[176] patch_image_persp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[177] patch_primitive` | failure | terminal-refusal | skia-upstream |
| `skia` | `[178] perlinnoise` | failure | terminal-refusal | skia-upstream |
| `skia` | `[179] perlinnoise_layered` | failure | terminal-refusal | skia-upstream |
| `skia` | `[180] perlinnoise_localmatrix` | passed | pass | skia-upstream |
| `skia` | `[181] perlinnoise_rotated` | failure | terminal-refusal | skia-upstream |
| `skia` | `[182] PlusMergesAA` | failure | terminal-refusal | skia-upstream |
| `skia` | `[183] rasterallocator` | failure | terminal-refusal | skia-upstream |
| `skia` | `[184] recordopts` | failure | terminal-refusal | skia-upstream |
| `skia` | `[185] rotate_imagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[186] runtimecolorfilter_vertices_atlas_and_patch` | failure | terminal-refusal | skia-upstream |
| `skia` | `[187] gpusamplerstress` | failure | terminal-refusal | skia-upstream |
| `skia` | `[188] save_behind` | passed | pass | skia-upstream |
| `skia` | `[189] savelayer_f16` | failure | terminal-refusal | skia-upstream |
| `skia` | `[190] savelayer_initfromprev` | passed | pass | skia-upstream |
| `skia` | `[191] shadermaskfilter_gradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[192] shadow_utils_gray` | failure | terminal-refusal | skia-upstream |
| `skia` | `[193] shadow_utils_occl` | failure | terminal-refusal | skia-upstream |
| `skia` | `[194] simple-offsetimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[195] sk3d_simple` | failure | terminal-refusal | skia-upstream |
| `skia` | `[196] skbug_14554` | passed | pass | skia-upstream |
| `skia` | `[197] srcmode` | failure | terminal-refusal | skia-upstream |
| `skia` | `[198] srgb_colorfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[199] tablecolorfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[200] extractalpha` | failure | terminal-refusal | skia-upstream |
| `skia` | `[201] tileimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[202] transparency_check` | failure | terminal-refusal | skia-upstream |
| `skia` | `[203] xfermodeimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[204] xfermodes2` | failure | terminal-refusal | skia-upstream |
| `skia` | `[205] xfermodes3` | failure | terminal-refusal | skia-upstream |
| `skia` | `[206] xfermodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[207] alphagradients` | failure | terminal-refusal | skia-upstream |
| `skia` | `[208] analytic_gradients` | failure | terminal-refusal | skia-upstream |
| `skia` | `[209] bug6643` | failure | terminal-refusal | skia-upstream |
| `skia` | `[210] clamped_gradients` | failure | terminal-refusal | skia-upstream |
| `skia` | `[211] gradients_2pt_conical_inside_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[212] gradients_2pt_conical_outside` | failure | terminal-refusal | skia-upstream |
| `skia` | `[213] crbug_938592` | failure | terminal-refusal | skia-upstream |
| `skia` | `[214] degenerate_gradients` | failure | terminal-refusal | skia-upstream |
| `skia` | `[215] emptyshader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[216] fillrect_gradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[217] gradient_dirty_laundry` | failure | terminal-refusal | skia-upstream |
| `skia` | `[218] gradients_many` | failure | terminal-refusal | skia-upstream |
| `skia` | `[219] gradient_many_hard_stops` | failure | terminal-refusal | skia-upstream |
| `skia` | `[220] gradient_many_stops` | failure | terminal-refusal | skia-upstream |
| `skia` | `[221] gradient_matrix` | failure | terminal-refusal | skia-upstream |
| `skia` | `[222] gradients_alpha_many_stops` | failure | terminal-refusal | skia-upstream |
| `skia` | `[223] gradients_color_space` | failure | terminal-refusal | skia-upstream |
| `skia` | `[224] gradients_color_space_many_stops` | failure | terminal-refusal | skia-upstream |
| `skia` | `[225] gradients_color_space_tilemode` | failure | terminal-refusal | skia-upstream |
| `skia` | `[226] gradients_degenerate_2pt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[227] gradients_dup_color_stops` | failure | terminal-refusal | skia-upstream |
| `skia` | `[228] gradients` | failure | terminal-refusal | skia-upstream |
| `skia` | `[229] gradients_hue_method` | failure | terminal-refusal | skia-upstream |
| `skia` | `[230] gradients_interesting` | failure | terminal-refusal | skia-upstream |
| `skia` | `[231] gradients_local_perspective` | failure | terminal-refusal | skia-upstream |
| `skia` | `[232] gradients_no_texture` | failure | terminal-refusal | skia-upstream |
| `skia` | `[233] gradients_powerless_hue` | failure | terminal-refusal | skia-upstream |
| `skia` | `[234] gradients_view_perspective` | failure | terminal-refusal | skia-upstream |
| `skia` | `[235] hardstop_gradients` | failure | terminal-refusal | skia-upstream |
| `skia` | `[236] hardstop_gradients_many` | failure | terminal-refusal | skia-upstream |
| `skia` | `[237] linear_gradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[238] linear_gradient_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[239] linear_gradient_tiny` | failure | terminal-refusal | skia-upstream |
| `skia` | `[240] persp_shaders_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[241] persp_shaders_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[242] radial_gradient2` | failure | terminal-refusal | skia-upstream |
| `skia` | `[243] radial_gradient3` | failure | terminal-refusal | skia-upstream |
| `skia` | `[244] radial_gradient3_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[245] radial_gradient4` | failure | terminal-refusal | skia-upstream |
| `skia` | `[246] radial_gradient4_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[247] radial_gradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[248] radial_gradient_precision` | failure | terminal-refusal | skia-upstream |
| `skia` | `[249] scaled_tilemode_gradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[250] shallow_gradient_conical` | failure | terminal-refusal | skia-upstream |
| `skia` | `[251] shallow_gradient_conical_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[252] shallow_gradient_linear` | failure | terminal-refusal | skia-upstream |
| `skia` | `[253] shallow_gradient_linear_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[254] shallow_gradient_radial` | failure | terminal-refusal | skia-upstream |
| `skia` | `[255] shallow_gradient_radial_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[256] shallow_gradient_sweep` | failure | terminal-refusal | skia-upstream |
| `skia` | `[257] shallow_gradient_sweep_nodither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[258] small_color_stop` | failure | terminal-refusal | skia-upstream |
| `skia` | `[259] sweep_tiling` | failure | terminal-refusal | skia-upstream |
| `skia` | `[260] testgradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[261] all_bitmap_configs` | failure | terminal-refusal | skia-upstream |
| `skia` | `[262] all_variants_8888` | failure | terminal-refusal | skia-upstream |
| `skia` | `[263] alpha_image_alpha_tint` | failure | terminal-refusal | skia-upstream |
| `skia` | `[264] alpha_image_shader_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[265] animatedGif` | passed | pass | skia-upstream |
| `skia` | `[266] anisomips` | failure | terminal-refusal | skia-upstream |
| `skia` | `[267] anisotropic_image_scale_aniso` | failure | terminal-refusal | skia-upstream |
| `skia` | `[268] anisotropic_image_scale_linear` | failure | terminal-refusal | skia-upstream |
| `skia` | `[269] anisotropic_image_scale_mip` | failure | terminal-refusal | skia-upstream |
| `skia` | `[270] async_rescale_and_read_alpha_type` | passed | pass | skia-upstream |
| `skia` | `[271] async_rescale_and_read_rose` | passed | pass | skia-upstream |
| `skia` | `[272] async_rescale_and_read_no_bleed` | passed | pass | skia-upstream |
| `skia` | `[273] async_yuv_no_scale` | passed | pass | skia-upstream |
| `skia` | `[274] bc1_transparency` | failure | terminal-refusal | skia-upstream |
| `skia` | `[275] bicubic` | failure | terminal-refusal | skia-upstream |
| `skia` | `[276] bigmatrix` | failure | terminal-refusal | skia-upstream |
| `skia` | `[277] bitmapcopy` | failure | terminal-refusal | skia-upstream |
| `skia` | `[278] bitmapfilters` | failure | terminal-refusal | skia-upstream |
| `skia` | `[279] bitmap-image-srgb-legacy` | failure | terminal-refusal | skia-upstream |
| `skia` | `[280] bitmap_premul` | failure | terminal-refusal | skia-upstream |
| `skia` | `[281] bitmaprect_rounding` | failure | terminal-refusal | skia-upstream |
| `skia` | `[282] bitmapshaders` | failure | terminal-refusal | skia-upstream |
| `skia` | `[283] bitmap_subset_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[284] bleed_downscale` | passed | pass | skia-upstream |
| `skia` | `[285] bmp_filter_quality_repeat` | failure | terminal-refusal | skia-upstream |
| `skia` | `[286] bug6783` | failure | terminal-refusal | skia-upstream |
| `skia` | `[287] cgimage` | passed | pass | skia-upstream |
| `skia` | `[288] child_sampling_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[289] clippedbitmapshaders` | failure | missing-reference | skia-upstream |
| `skia` | `[290] color_cube_cf_rt` | passed | pass | skia-upstream |
| `skia` | `[291] colorwheel_alphatypes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[292] colorwheel` | failure | terminal-refusal | skia-upstream |
| `skia` | `[293] colorspace2` | failure | terminal-refusal | skia-upstream |
| `skia` | `[294] colorspace` | failure | terminal-refusal | skia-upstream |
| `skia` | `[295] compositor_quads_image` | passed | pass | skia-upstream |
| `skia` | `[296] compressed_textures` | failure | terminal-refusal | skia-upstream |
| `skia` | `[297] coordclampshader` | passed | pass | skia-upstream |
| `skia` | `[298] copyTo4444` | failure | terminal-refusal | skia-upstream |
| `skia` | `[299] crbug_224618` | failure | terminal-refusal | skia-upstream |
| `skia` | `[300] crbug_404394639` | failure | terminal-refusal | skia-upstream |
| `skia` | `[301] deferred_shader_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[302] bitmaprect_s` | failure | terminal-refusal | skia-upstream |
| `skia` | `[303] bitmaprect_i` | failure | terminal-refusal | skia-upstream |
| `skia` | `[304] 3x3bitmaprect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[305] draw_bitmap_rect_skbug4734` | failure | terminal-refusal | skia-upstream |
| `skia` | `[306] drawminibitmaprect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[307] drawimage_sampling` | failure | terminal-refusal | skia-upstream |
| `skia` | `[308] drawimagerect_filter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[309] encode-alpha-jpeg` | passed | pass | skia-upstream |
| `skia` | `[310] encode-color-types-webp-lossless` | failure | terminal-refusal | skia-upstream |
| `skia` | `[311] encode-platform` | passed | pass | skia-upstream |
| `skia` | `[312] encode-srgb-png` | failure | terminal-refusal | skia-upstream |
| `skia` | `[313] exoticformats` | failure | terminal-refusal | skia-upstream |
| `skia` | `[314] filterbug` | failure | terminal-refusal | skia-upstream |
| `skia` | `[315] filterindiabox` | passed | pass | skia-upstream |
| `skia` | `[316] flight_animated_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[317] flippity` | failure | terminal-refusal | skia-upstream |
| `skia` | `[318] format4444` | failure | terminal-refusal | skia-upstream |
| `skia` | `[319] giantbitmap_clamp_bilerp_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[320] giantbitmap_clamp_bilerp_scale` | failure | terminal-refusal | skia-upstream |
| `skia` | `[321] giantbitmap_clamp_point_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[322] giantbitmap_clamp_point_scale` | failure | terminal-refusal | skia-upstream |
| `skia` | `[323] giantbitmap_mirror_bilerp_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[324] giantbitmap_mirror_bilerp_scale` | failure | terminal-refusal | skia-upstream |
| `skia` | `[325] giantbitmap_mirror_point_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[326] giantbitmap_mirror_point_scale` | failure | terminal-refusal | skia-upstream |
| `skia` | `[327] giantbitmap_repeat_bilerp_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[328] giantbitmap_repeat_bilerp_scale` | failure | terminal-refusal | skia-upstream |
| `skia` | `[329] giantbitmap_repeat_point_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[330] giantbitmap_repeat_point_scale` | failure | terminal-refusal | skia-upstream |
| `skia` | `[331] grayscalejpg` | passed | pass | skia-upstream |
| `skia` | `[332] hugebitmapshader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[333] image-cacherator-from-picture` | failure | terminal-refusal | skia-upstream |
| `skia` | `[334] image_dither` | failure | terminal-refusal | skia-upstream |
| `skia` | `[335] imagefilter_transformed_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[336] imagefilterscropexpand` | failure | terminal-refusal | skia-upstream |
| `skia` | `[337] imagefiltersgraph` | failure | terminal-refusal | skia-upstream |
| `skia` | `[338] image-surface` | failure | terminal-refusal | skia-upstream |
| `skia` | `[339] imagemagnifier_bounds` | failure | terminal-refusal | skia-upstream |
| `skia` | `[340] imagemagnifier_cropped` | failure | terminal-refusal | skia-upstream |
| `skia` | `[341] imagemagnifier` | failure | terminal-refusal | skia-upstream |
| `skia` | `[342] imagemakewithfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[343] imagemasksubset` | failure | terminal-refusal | skia-upstream |
| `skia` | `[344] image_out_of_gamut` | failure | terminal-refusal | skia-upstream |
| `skia` | `[345] image-picture` | failure | terminal-refusal | skia-upstream |
| `skia` | `[346] imageresizetiled` | failure | terminal-refusal | skia-upstream |
| `skia` | `[347] image-shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[348] imagesource` | failure | terminal-refusal | skia-upstream |
| `skia` | `[349] image_subset` | failure | terminal-refusal | skia-upstream |
| `skia` | `[350] imageshader_tinyscale` | passed | pass | skia-upstream |
| `skia` | `[1] lit_shader_linear_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[2] localmatriximageshader_filtering` | failure | terminal-refusal | skia-upstream |
| `skia` | `[3] localmatriximageshader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[4] localmatrixshader_nested` | failure | terminal-refusal | skia-upstream |
| `skia` | `[5] localmatrixshader_persp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[6] local_matrix_shader_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[7] localmatrix_order` | failure | terminal-refusal | skia-upstream |
| `skia` | `[8] makecolorspace` | failure | terminal-refusal | skia-upstream |
| `skia` | `[9] makecolortypeandspace` | passed | pass | skia-upstream |
| `skia` | `[10] makeRasterImage` | failure | terminal-refusal | skia-upstream |
| `skia` | `[11] mipmap_gray8_srgb` | failure | terminal-refusal | skia-upstream |
| `skia` | `[12] mipmap_srgb` | failure | terminal-refusal | skia-upstream |
| `skia` | `[13] mirror_tile` | failure | terminal-refusal | skia-upstream |
| `skia` | `[14] nearest_half_pixel_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[15] new_texture_image` | passed | pass | skia-upstream |
| `skia` | `[16] ninepatch-stretch` | failure | terminal-refusal | skia-upstream |
| `skia` | `[17] not_native32_bitmap_config` | failure | terminal-refusal | skia-upstream |
| `skia` | `[18] null_child_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[19] p3` | failure | terminal-refusal | skia-upstream |
| `skia` | `[20] persp_images` | failure | terminal-refusal | skia-upstream |
| `skia` | `[21] pictureimagefilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[22] pictureimagegenerator` | failure | terminal-refusal | skia-upstream |
| `skia` | `[23] pictureshader_alpha` | failure | terminal-refusal | skia-upstream |
| `skia` | `[24] pictureshadercache` | failure | terminal-refusal | skia-upstream |
| `skia` | `[25] pictureshader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[26] pictureshader_localwrapper` | failure | terminal-refusal | skia-upstream |
| `skia` | `[27] pictureshader_persp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[28] pictureshadertile` | failure | terminal-refusal | skia-upstream |
| `skia` | `[29] poster_circle` | passed | pass | skia-upstream |
| `skia` | `[30] raw_image_shader_normals_rt` | passed | pass | skia-upstream |
| `skia` | `[31] readpixelscodec` | failure | terminal-refusal | skia-upstream |
| `skia` | `[32] readpixelspicture` | failure | terminal-refusal | skia-upstream |
| `skia` | `[33] reinterpretcolorspace` | failure | terminal-refusal | skia-upstream |
| `skia` | `[34] repeated_bitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[35] repeated_bitmap_jpg` | failure | terminal-refusal | skia-upstream |
| `skia` | `[36] scale-pixels` | failure | size-mismatch | skia-upstream |
| `skia` | `[37] scaled_tilemode_bitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[38] scaled_tilemodes` | passed | pass | skia-upstream |
| `skia` | `[39] scaled_tilemodes_npot` | passed | pass | skia-upstream |
| `skia` | `[40] scalepixels_unpremul` | failure | terminal-refusal | skia-upstream |
| `skia` | `[41] shaderpath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[42] showmiplevels_explicit` | passed | pass | skia-upstream |
| `skia` | `[43] skbug_8664` | failure | terminal-refusal | skia-upstream |
| `skia` | `[44] skbug_9819` | failure | terminal-refusal | skia-upstream |
| `skia` | `[45] stoplight_animated_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[46] surface_underdraw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[47] texture` | failure | terminal-refusal | skia-upstream |
| `skia` | `[48] textureimage_and_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[49] tiled_picture_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[50] tiledscaledbitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[51] tilemode_decal` | failure | terminal-refusal | skia-upstream |
| `skia` | `[52] tilemodes_alpha` | failure | terminal-refusal | skia-upstream |
| `skia` | `[53] tilemodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[54] tilemode_bitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[55] tilemode_gradient` | failure | terminal-refusal | skia-upstream |
| `skia` | `[56] tinybitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[57] unpremul` | failure | terminal-refusal | skia-upstream |
| `skia` | `[58] verylargebitmap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[59] verylarge_picture_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[60] wacky_yuv_formats` | failure | missing-reference | skia-upstream |
| `skia` | `[61] yuv420_odd_dim` | passed | pass | skia-upstream |
| `skia` | `[62] yuv420_odd_dim_repeat` | passed | pass | skia-upstream |
| `skia` | `[63] custommesh_cs` | failure | terminal-refusal | skia-upstream |
| `skia` | `[64] custommesh_cs_uniforms` | failure | terminal-refusal | skia-upstream |
| `skia` | `[65] custommesh` | failure | terminal-refusal | skia-upstream |
| `skia` | `[66] custommesh_uniforms` | skipped | skip | skia-upstream |
| `skia` | `[67] mesh_updates` | failure | terminal-refusal | skia-upstream |
| `skia` | `[68] mesh_with_effects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[69] mesh_with_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[70] mesh_with_paint_color` | passed | pass | skia-upstream |
| `skia` | `[71] mesh_with_paint_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[72] mesh_zero_init` | failure | implementation-failure | skia-upstream |
| `skia` | `[73] picture_mesh` | failure | terminal-refusal | skia-upstream |
| `skia` | `[74] skbug_13047` | failure | terminal-refusal | skia-upstream |
| `skia` | `[75] vertices_batching` | failure | terminal-refusal | skia-upstream |
| `skia` | `[76] vertices_collapsed` | failure | terminal-refusal | skia-upstream |
| `skia` | `[77] vertices` | failure | terminal-refusal | skia-upstream |
| `skia` | `[78] vertices_perspective` | failure | terminal-refusal | skia-upstream |
| `skia` | `[79] bezier_conic_effects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[80] bezier_quad_effects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[81] bug41422450` | failure | terminal-refusal | skia-upstream |
| `skia` | `[82] ctmpatheffect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[83] circle_sizes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[84] clockwise` | failure | terminal-refusal | skia-upstream |
| `skia` | `[85] concavepaths` | failure | terminal-refusal | skia-upstream |
| `skia` | `[86] conicpaths` | failure | terminal-refusal | skia-upstream |
| `skia` | `[87] convex_lineonly_paths` | failure | terminal-refusal | skia-upstream |
| `skia` | `[88] convex_lineonly_paths_stroke_and_fill` | failure | terminal-refusal | skia-upstream |
| `skia` | `[89] convexpaths` | failure | terminal-refusal | skia-upstream |
| `skia` | `[90] convex-polygon-inset` | failure | implementation-failure | skia-upstream |
| `skia` | `[91] crbug_640176` | passed | pass | skia-upstream |
| `skia` | `[92] crbug_691386` | failure | terminal-refusal | skia-upstream |
| `skia` | `[93] cubicclosepath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[94] cubicpath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[95] cubicpath_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[96] dashcircle` | failure | terminal-refusal | skia-upstream |
| `skia` | `[97] dashtextcaps` | failure | terminal-refusal | skia-upstream |
| `skia` | `[98] dashing5_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[99] dashing` | failure | terminal-refusal | skia-upstream |
| `skia` | `[100] drawlines_with_local_matrix` | failure | terminal-refusal | skia-upstream |
| `skia` | `[1] drawregionmodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[2] fancy_gradients` | failure | implementation-failure | skia-upstream |
| `skia` | `[3] fatpathfill` | failure | terminal-refusal | skia-upstream |
| `skia` | `[4] filltypespersp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[5] inner_join_geometry` | failure | terminal-refusal | skia-upstream |
| `skia` | `[6] lattice2` | failure | terminal-refusal | skia-upstream |
| `skia` | `[7] lineclosepath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[8] linepath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[9] macaatest` | failure | implementation-failure | skia-upstream |
| `skia` | `[10] mandoline` | failure | terminal-refusal | skia-upstream |
| `skia` | `[11] manycircles` | failure | terminal-refusal | skia-upstream |
| `skia` | `[12] manypathatlases` | failure | terminal-refusal | skia-upstream |
| `skia` | `[13] nested` | failure | terminal-refusal | skia-upstream |
| `skia` | `[14] nonclosedpaths` | failure | terminal-refusal | skia-upstream |
| `skia` | `[15] OverStroke` | failure | terminal-refusal | skia-upstream |
| `skia` | `[16] parsedpaths` | failure | terminal-refusal | skia-upstream |
| `skia` | `[17] path_huge_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[18] path_huge_aa_manual` | failure | terminal-refusal | skia-upstream |
| `skia` | `[19] path_mask_cache` | failure | terminal-refusal | skia-upstream |
| `skia` | `[20] pathops_blend` | failure | implementation-failure | skia-upstream |
| `skia` | `[21] pathopsinverse` | failure | implementation-failure | skia-upstream |
| `skia` | `[22] pathops_skbug_10155` | failure | terminal-refusal | skia-upstream |
| `skia` | `[23] path-reverse` | failure | terminal-refusal | skia-upstream |
| `skia` | `[24] path_stroke_clip_crbug1070835` | failure | terminal-refusal | skia-upstream |
| `skia` | `[25] points` | failure | terminal-refusal | skia-upstream |
| `skia` | `[26] poly2poly` | failure | terminal-refusal | skia-upstream |
| `skia` | `[27] polygons` | failure | terminal-refusal | skia-upstream |
| `skia` | `[28] preservefillrule_big` | failure | terminal-refusal | skia-upstream |
| `skia` | `[29] preservefillrule_little` | failure | terminal-refusal | skia-upstream |
| `skia` | `[30] quadclosepath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[31] quadpath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[32] roundrects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[33] shadow_utils_directional` | passed | pass | skia-upstream |
| `skia` | `[34] sharedcorners` | failure | terminal-refusal | skia-upstream |
| `skia` | `[35] simpleshapes_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[36] simpleshapes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[37] stlouisarch` | failure | terminal-refusal | skia-upstream |
| `skia` | `[38] stroke_rect_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[39] strokedline_caps` | failure | terminal-refusal | skia-upstream |
| `skia` | `[40] strokes3` | failure | terminal-refusal | skia-upstream |
| `skia` | `[41] strokes_round` | failure | terminal-refusal | skia-upstream |
| `skia` | `[42] teenyStrokes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[43] thin_aa_dash_lines` | failure | terminal-refusal | skia-upstream |
| `skia` | `[44] thinconcavepaths` | passed | pass | skia-upstream |
| `skia` | `[45] thinrects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[46] thinroundrects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[47] thinstrokedrects` | failure | terminal-refusal | skia-upstream |
| `skia` | `[48] tinyanglearcs` | failure | terminal-refusal | skia-upstream |
| `skia` | `[49] trickycubicstrokes_largeradius` | failure | terminal-refusal | skia-upstream |
| `skia` | `[50] trimpatheffect` | failure | terminal-refusal | skia-upstream |
| `skia` | `[51] widebuttcaps` | failure | terminal-refusal | skia-upstream |
| `skia` | `[52] zero_control_stroke` | failure | terminal-refusal | skia-upstream |
| `skia` | `[53] zeroPath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[54] zero_length_paths_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[55] zero_length_paths_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[56] zero_length_paths_dbl_aa` | failure | terminal-refusal | skia-upstream |
| `skia` | `[57] zero_length_paths_dbl_bw` | failure | terminal-refusal | skia-upstream |
| `skia` | `[58] zerolinedash` | failure | terminal-refusal | skia-upstream |
| `skia` | `[59] zerolinestroke` | failure | terminal-refusal | skia-upstream |
| `skia` | `[60] AlternateLuma` | failure | terminal-refusal | skia-upstream |
| `skia` | `[61] arithmode` | failure | terminal-refusal | skia-upstream |
| `skia` | `[62] color_cube_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[63] composeCF` | failure | terminal-refusal | skia-upstream |
| `skia` | `[64] runtime_intrinsics_common` | failure | terminal-refusal | skia-upstream |
| `skia` | `[65] runtime_intrinsics_exponential` | failure | terminal-refusal | skia-upstream |
| `skia` | `[66] runtime_intrinsics_geometric` | failure | terminal-refusal | skia-upstream |
| `skia` | `[67] runtime_intrinsics_matrix` | failure | terminal-refusal | skia-upstream |
| `skia` | `[68] runtime_intrinsics_relational` | failure | terminal-refusal | skia-upstream |
| `skia` | `[69] runtime_intrinsics_trig` | failure | terminal-refusal | skia-upstream |
| `skia` | `[70] kawase_blur_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[71] lineargradientrt` | failure | missing-reference | skia-upstream |
| `skia` | `[72] lumafilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[73] rippleshader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[74] rtif_distort` | failure | terminal-refusal | skia-upstream |
| `skia` | `[75] rtif_unsharp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[76] runtimecolorfilter` | failure | terminal-refusal | skia-upstream |
| `skia` | `[77] runtimefunctions` | failure | terminal-refusal | skia-upstream |
| `skia` | `[78] runtime_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[79] spiral_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[80] threshold_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[81] unsharp_rt` | failure | terminal-refusal | skia-upstream |
| `skia` | `[82] workingspace` | passed | pass | skia-upstream |
| `skia` | `[83] annotated_text` | failure | terminal-refusal | skia-upstream |
| `skia` | `[84] bigtext_crbug_1370488` | failure | terminal-refusal | skia-upstream |
| `skia` | `[85] bigtext` | failure | terminal-refusal | skia-upstream |
| `skia` | `[86] blob_rsxform_distortable` | failure | terminal-refusal | skia-upstream |
| `skia` | `[87] blob_rsxform` | failure | terminal-refusal | skia-upstream |
| `skia` | `[88] chrome_gradtext2` | failure | terminal-refusal | skia-upstream |
| `skia` | `[89] cliperror` | failure | terminal-refusal | skia-upstream |
| `skia` | `[90] coloremoji_colrv0` | failure | terminal-refusal | skia-upstream |
| `skia` | `[91] coloremoji` | failure | terminal-refusal | skia-upstream |
| `skia` | `[92] colorwheelnative` | failure | terminal-refusal | skia-upstream |
| `skia` | `[93] coloremoji_blendmodes_colrv0` | failure | terminal-refusal | skia-upstream |
| `skia` | `[94] coloremoji_blendmodes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[95] colrv1_gradient_stops_repeat` | failure | terminal-refusal | skia-upstream |
| `skia` | `[96] crbug_1073670` | failure | terminal-refusal | skia-upstream |
| `skia` | `[97] dftext_blob_persp` | failure | terminal-refusal | skia-upstream |
| `skia` | `[98] dftext` | failure | terminal-refusal | skia-upstream |
| `skia` | `[99] drawTextRSXform` | failure | terminal-refusal | skia-upstream |
| `skia` | `[100] fontcache` | passed | pass | skia-upstream |
| `skia` | `[101] fontmgr_bounds` | failure | terminal-refusal | skia-upstream |
| `skia` | `[102] fontmgr_iter` | passed | pass | skia-upstream |
| `skia` | `[103] fontmgr_match` | passed | pass | skia-upstream |
| `skia` | `[104] font_palette_default` | failure | terminal-refusal | skia-upstream |
| `skia` | `[105] fontregen` | passed | pass | skia-upstream |
| `skia` | `[106] fontscalerdistortable` | failure | terminal-refusal | skia-upstream |
| `skia` | `[107] fontscaler` | failure | terminal-refusal | skia-upstream |
| `skia` | `[108] gammagradienttext` | passed | pass | skia-upstream |
| `skia` | `[109] gammatext_color_shader` | failure | terminal-refusal | skia-upstream |
| `skia` | `[110] gammatext` | failure | terminal-refusal | skia-upstream |
| `skia` | `[111] getpostextpath` | failure | terminal-refusal | skia-upstream |
| `skia` | `[112] gradtext` | failure | terminal-refusal | skia-upstream |
| `skia` | `[113] largeglyphblur` | failure | terminal-refusal | skia-upstream |
| `skia` | `[114] lcdoverlap` | failure | terminal-refusal | skia-upstream |
| `skia` | `[115] macaa_colors` | failure | terminal-refusal | skia-upstream |
| `skia` | `[116] mixedtextblobs` | failure | terminal-refusal | skia-upstream |
| `skia` | `[117] overdraw_text_xform` | failure | terminal-refusal | skia-upstream |
| `skia` | `[118] palette` | passed | pass | skia-upstream |
| `skia` | `[119] pdf_never_embed` | failure | terminal-refusal | skia-upstream |
| `skia` | `[120] pdf_table_based_subset` | passed | pass | skia-upstream |
| `skia` | `[121] persptext` | failure | terminal-refusal | skia-upstream |
| `skia` | `[122] persptext_minimal` | failure | terminal-refusal | skia-upstream |
| `skia` | `[123] rsx_blob_shader` | passed | pass | skia-upstream |
| `skia` | `[124] scaledemojiperspective_colrv0` | failure | terminal-refusal | skia-upstream |
| `skia` | `[125] scaledemojipos_colrv0` | failure | terminal-refusal | skia-upstream |
| `skia` | `[126] scaledemoji_colrv0` | failure | terminal-refusal | skia-upstream |
| `skia` | `[127] scaledemoji_rendering` | failure | terminal-refusal | skia-upstream |
| `skia` | `[128] shadertext3` | failure | terminal-refusal | skia-upstream |
| `skia` | `[129] skbug_12212` | passed | pass | skia-upstream |
| `skia` | `[130] skbug_257` | failure | terminal-refusal | skia-upstream |
| `skia` | `[131] skbug_5321` | passed | pass | skia-upstream |
| `skia` | `[132] skbug_8955` | failure | terminal-refusal | skia-upstream |
| `skia` | `[133] slug` | failure | terminal-refusal | skia-upstream |
| `skia` | `[134] stroketext` | failure | terminal-refusal | skia-upstream |
| `skia` | `[135] stroketext_native` | failure | terminal-refusal | skia-upstream |
| `skia` | `[136] surfaceprops` | failure | terminal-refusal | skia-upstream |
| `skia` | `[137] textblobblockreordering` | failure | terminal-refusal | skia-upstream |
| `skia` | `[138] textblobcolortrans` | failure | terminal-refusal | skia-upstream |
| `skia` | `[139] textblobgeometrychange` | failure | terminal-refusal | skia-upstream |
| `skia` | `[140] textblob` | failure | terminal-refusal | skia-upstream |
| `skia` | `[141] textblob_intercepts` | failure | terminal-refusal | skia-upstream |
| `skia` | `[142] textblobmixedsizes` | failure | terminal-refusal | skia-upstream |
| `skia` | `[143] textblobrandomfont` | failure | terminal-refusal | skia-upstream |
| `skia` | `[144] textblobshader` | passed | pass | skia-upstream |
| `skia` | `[145] textblobtransforms` | failure | terminal-refusal | skia-upstream |
| `skia` | `[146] textblobuseaftergpufree` | failure | terminal-refusal | skia-upstream |
| `skia` | `[147] fancyblobunderline` | failure | terminal-refusal | skia-upstream |
| `skia` | `[148] textfilter_color` | failure | terminal-refusal | skia-upstream |
| `skia` | `[149] textfilter_image` | failure | terminal-refusal | skia-upstream |
| `skia` | `[150] text_scale_skew` | failure | similarity-failure | skia-upstream |
| `skia` | `[151] text_scale_skew_rotate` | failure | terminal-refusal | skia-upstream |
| `skia` | `[152] typefacerendering` | failure | terminal-refusal | skia-upstream |
| `skia` | `[153] typefacerendering_pfa` | passed | pass | skia-upstream |
| `skia` | `[154] typefacerendering_pfb` | passed | pass | skia-upstream |
| `skia` | `[155] typefacestyles` | failure | terminal-refusal | skia-upstream |
| `skia` | `[156] typefacestyles_kerning` | failure | terminal-refusal | skia-upstream |
| `skia` | `[157] typeface_styling` | failure | terminal-refusal | skia-upstream |
| `skia` | `[158] user_typeface` | failure | terminal-refusal | skia-upstream |
| `skia` | `jpg-color-cube` | failure | terminal-refusal | skia-upstream |
| `skia` | `drawregion` | failure | terminal-refusal | skia-upstream |
| `svg` | `test complex-paths-2()` | skipped | skip | svg |
| `svg` | `test complex-paths-3()` | skipped | skip | svg |
| `svg` | `test geometric-1()` | passed | pass | svg |
| `svg` | `test geometric-2()` | skipped | skip | svg |
| `svg` | `test geometric-3()` | passed | pass | svg |
| `svg` | `SVG expected unsupported classification is limited to the explicit allowlist()` | passed | pass | svg |
| `svg` | `test texture-2()` | passed | pass | svg |
| `svg` | `test texture-3()` | failure | similarity-failure | svg |
| `svg` | `test ghostscript-tiger()` | skipped | skip | svg |
| `svg` | `test laptop-computer()` | skipped | skip | svg |
| `svg` | `test shadow-2()` | skipped | skip | svg |
| `svg` | `test shadow-3()` | skipped | skip | svg |
| `svg` | `test layer-1()` | skipped | skip | svg |
| `svg` | `test gradient-1()` | skipped | skip | svg |
| `svg` | `test gradient-2()` | skipped | skip | svg |
| `svg` | `test gradient-3()` | skipped | skip | svg |
| `svg` | `test icon-computer()` | skipped | skip | svg |

## Non-claims

- Wave 0 population is historical context only; Wave 1 includes blocking rows and is population-shifted.
- Skia, SVG, test-oracle, and CPU-oracle rows remain separate evidence lanes.
- Route-only success is not promoted to pixel support.
- This report does not weaken global thresholds, assertions, reference policy, or memory budgets.

## SHA-256 Provenance

| Evidence | Path | SHA-256 |
| --- | --- | --- |
| `inputs.commandsJson` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/commands.json` | `f025512019734438079a0bfc9f6e996c32e788e0d9c5ee494cdf0947490cca87` |
| `inputs.cpuResults` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/cpu-results.json` | `fb2989323b717f2e869a06272d8ee4b35b4e516d6a555f4ce3d19f9ee3606ff0` |
| `inputs.dashboardDir` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/dashboard` | `f17c39bd4dd3e382be1e6d302f591c6e6891b25ef800e146d11425a7a30f4824` |
| `inputs.dashboardJson` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/skia-dashboard-gms.json` | `001629cf7866ccd00927ae14b1b43a60223e87f4ce77e7467c542a1d13f6eb99` |
| `inputs.environmentJson` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/environment.json` | `08437f141ac18455cba1804c5ae2849e60908a978be3c18ae4fecba850f7c187` |
| `inputs.evidenceIndex` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/evidence-index.json` | `39978e9e7dd7621cc05f8c9f8765b2f42d7993ce24aaf57d5f27cef8a0faee08` |
| `inputs.fp13Runner` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/fp13-runner.xml` | `b1c4e5059661828f523e761b58bfb56f3daec383095620328741fb4eb2245e28` |
| `inputs.generatedRenders` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/generated-renders` | `9633fd953668c7190428628841aaa71ba06ae3e5d321757413621e25827d71f5` |
| `inputs.gpuResults` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/gpu-results.json` | `fb2989323b717f2e869a06272d8ee4b35b4e516d6a555f4ce3d19f9ee3606ff0` |
| `inputs.scoresAfter` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/scores-after.properties` | `a938cdaa3954012464e9a07d149cc7a5f254727fb1084ac99f7c6a25ecd25325` |
| `inputs.scoresBefore` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/scores-before.properties` | `a938cdaa3954012464e9a07d149cc7a5f254727fb1084ac99f7c6a25ecd25325` |
| `inputs.skiaRunner` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/skia-gm-runner.xml` | `2e288e3419d1e8336a54a43efc18f76fc2d21cd2585f39cbbe93cb702fcbce9f` |
| `inputs.svgXml` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/svg-integration.xml` | `4d7aad68ee74260cb97258bb31bb1d6acb7cd04c1823fbdc574a4d55ea164ad1` |
| `outputs.dashboardOutput` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/dashboard/data/gms.json` | `001629cf7866ccd00927ae14b1b43a60223e87f4ce77e7467c542a1d13f6eb99` |
| `evidence.BlurBigSigma.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/BlurBigSigma.png` | `ec46119a04fb581a2fe3783c24534b90bdceffd352a9d3b8f732ba52cc645870` |
| `evidence.BlurBigSigma.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/BlurBigSigma.png` | `96da6cd526bfca25dbe2794e32e73a1cb8110b30fd595452fe3300984170c303` |
| `evidence.BlurSmallSigma.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/BlurSmallSigma.png` | `a95eb5d13ea1103e1f8c11687d3caa8fffa44d419e7887032fb6cdd0a655a90c` |
| `evidence.BlurSmallSigma.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/BlurSmallSigma.png` | `8f19248dc254e7118670702539781c8a7987a677590ae5c30772bc4c70279fb4` |
| `evidence.HSL_duck.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/HSL_duck.png` | `1e3865a4dfd45b5420dde153ca4d698ec066094ea3a96742166e3672ceaefa50` |
| `evidence.HSL_duck.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/HSL_duck.png` | `b40dfd80d947c56a8dc1c50d2db04fcf26c83d6bcf4dc0573bb0a20c5607d9c9` |
| `evidence.animatedGif.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/animatedGif.png` | `2076d518bba6b23b2c999aa2631037b1a67b578f6ab5f26c7facdf0f7ad93962` |
| `evidence.animatedGif.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/animatedGif.png` | `4c25141334dbca17cbd7e01b3a96ea445ab0cde2e65fc762f076fd3f562613bc` |
| `evidence.async_rescale_and_read_alpha_type.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/async_rescale_and_read_alpha_type.png` | `87b8c103cc1da81e5b0a14b2c0da667b67c092ae56ea2182129d43f9030af584` |
| `evidence.async_rescale_and_read_alpha_type.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/async_rescale_and_read_alpha_type.png` | `1e0c380353665da57720edd6c3539e7b85901519df15f3c1228509f28be6297f` |
| `evidence.async_rescale_and_read_no_bleed.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/async_rescale_and_read_no_bleed.png` | `b0b8927e6913c80fe11b8a42412d5eacdaf13f622a7d44343eb1f4faff0819f0` |
| `evidence.async_rescale_and_read_no_bleed.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/async_rescale_and_read_no_bleed.png` | `748cd3ad636b72bf786a43e91a53f698675914ddda53b2128992246b391c0f7f` |
| `evidence.async_rescale_and_read_rose.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/async_rescale_and_read_rose.png` | `1a7dbbf690150a139e27474965d809fe422dbc5b39793cdb8902e0ca718cfe96` |
| `evidence.async_rescale_and_read_rose.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/async_rescale_and_read_rose.png` | `2d5c0ad67c37d2e77855fc0bc26b37ffc32f730961dfda959e0eac97f1f00244` |
| `evidence.async_yuv_no_scale.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/async_yuv_no_scale.png` | `a4dc4da3707c037f6fca7341bd781843b04c31ff2d7ddcc2f315af46026295a7` |
| `evidence.async_yuv_no_scale.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/async_yuv_no_scale.png` | `408d826064134e32d9bcb6d8bf3db44091493b0b7fd2e2096d6c74b889adeb0f` |
| `evidence.bleed_downscale.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/bleed_downscale.png` | `9e200001ac739d66baa4a7404af4523a0393a7c61c8f5c7face1c40b28891563` |
| `evidence.bleed_downscale.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/bleed_downscale.png` | `b86d6650ff708c7cf5ea91cfb8933e335fa7ea70900b67f1c4747b8176eafb73` |
| `evidence.blur2rects.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/blur2rects.png` | `d01b6be47a36fad37563ed35397b2be84154933069a1e23128cd17a2fc09bef9` |
| `evidence.blur2rects.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/blur2rects.png` | `013921a8821bacce1ed3ec2c629d2e8047614e150e7c69069cf21e54c248f7ba` |
| `evidence.cgimage.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/cgimage.png` | `ee9c91538459c94c84412980b494196146bfd5e0e71488f0dcc2c5de607ad673` |
| `evidence.cgimage.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/cgimage.png` | `ecb30cfd1ab84fee4d5991c46fb3d4b66daabef8e1a0b074cf90dfbbd7ee6383` |
| `evidence.clip_shader.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/clip_shader.png` | `f3398f4273ec3df37dd025afe8eb4cf89d3558699c90a9bdb9dc89ae0420187a` |
| `evidence.clip_shader.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/clip_shader.png` | `699f2a85f5ef1669561e7e1ea2f8fcf58e2c68e50e2a19b76bc303eb9e419bfe` |
| `evidence.clip_shader_difference.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/clip_shader_difference.png` | `f29ec4c4650061353293d534a1bfa5f8d38ad8d19257e40014180be56889d1fc` |
| `evidence.clip_shader_difference.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/clip_shader_difference.png` | `376e09f8bbc02b92c476a49364c18135f561b6cef06f7be19ec977f92aa6812e` |
| `evidence.clip_shader_layer.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/clip_shader_layer.png` | `83ca32c16e81200d9d4a318b45408025cc23a559e243415b602156d1e7fb5dbb` |
| `evidence.clip_shader_layer.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/clip_shader_layer.png` | `6c1235feeaa181e0902cf87d6f929fec537c5284864d52ce2b45d54ec5172cb5` |
| `evidence.clip_shader_persp.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/clip_shader_persp.png` | `67cd910f1f4c09c866cbd06483dafa991f383af6de7749ab8279a962eba06d46` |
| `evidence.clip_shader_persp.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/clip_shader_persp.png` | `f3ea097bb819129770f5e90c82c13d40efabf8c1745df15a6aafbb5ee130532d` |
| `evidence.clipdrawdraw.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/clipdrawdraw.png` | `ce4d7e8eab5b0d1c81e64e389998a99d267f1b0d174ccdc800c9cba441e92f53` |
| `evidence.clipdrawdraw.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/clipdrawdraw.png` | `4162cbb2f066fbb1642d6acecd31faeca8dadeaf33c1b87b4d7202e4da7e2b7f` |
| `evidence.color4blendcf.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/color4blendcf.png` | `a6f500c726ae02f555690efbf62c6e04a5c6ccee2e4f06e1980baa5e606b3261` |
| `evidence.color4blendcf.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/color4blendcf.png` | `c6ca2d587c9522ea350567e97a5a6cab320c9d2583fcaaa3305eb43998647b1b` |
| `evidence.color4shader.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/color4shader.png` | `a6f500c726ae02f555690efbf62c6e04a5c6ccee2e4f06e1980baa5e606b3261` |
| `evidence.color4shader.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/color4shader.png` | `c6ca2d587c9522ea350567e97a5a6cab320c9d2583fcaaa3305eb43998647b1b` |
| `evidence.color_cube_cf_rt.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/color_cube_cf_rt.png` | `ce611c20822940e43a78a855c059b3e553baf9558b6fe72412a96eeb6661cb28` |
| `evidence.color_cube_cf_rt.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/color_cube_cf_rt.png` | `c1d8e3d76e78c499d98d9e131a41ba9b7c6fdf743a45771ce55cf66f82300fbe` |
| `evidence.compositor_quads_image.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/compositor_quads_image.png` | `3576d9bb2b10e80e250ac487428569902e2f6658903fd5aa7d7beb29a0562732` |
| `evidence.compositor_quads_image.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/compositor_quads_image.png` | `607e0923d5b1502ba974aceeca4fa2cb769bfb55ce6e1aa94e2a29fe99f199c2` |
| `evidence.coordclampshader.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/coordclampshader.png` | `bdb3d4672b712718345d080725997bd96fdff80c2ad9fa1e0f69685011a71a3e` |
| `evidence.coordclampshader.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/coordclampshader.png` | `45ae1c8768c1752fb1e0b2b2cdc9e6f3f8f9063d10c4619451cb160f888395d0` |
| `evidence.crbug_1167277.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/crbug_1167277.png` | `f273ed6e9fbb2f6fed886944625cddc6461ef47fdf0b92801c96a4df220c72f7` |
| `evidence.crbug_1167277.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/crbug_1167277.png` | `4bf35d6a5432d5835d27934ec3c2d81145678b32996997586994ed87217e2321` |
| `evidence.crbug_1174186.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/crbug_1174186.png` | `b4a0e384fec8250d85368582bf9fc319b3a780af8b2b7dda720ae11faf4bb1db` |
| `evidence.crbug_1174186.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/crbug_1174186.png` | `33edef5f86bbc91901547b4df53d44011a5d49c6dfeb95c87c35aa0fb38ab300` |
| `evidence.crbug_640176.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/crbug_640176.png` | `67c42780afdf03e1c57bf811fe4ba24b51f151803200d92c783cc801297db315` |
| `evidence.crbug_640176.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/crbug_640176.png` | `f0e9d9049f9388c9d3103860d81c4b39556a64b4a4f2276f1d8649521036edc0` |
| `evidence.crbug_899512.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/crbug_899512.png` | `8f8f2babaa2245620f6cafbbe38c8b43ee02c556345faeb1f5894579e55f1389` |
| `evidence.crbug_899512.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/crbug_899512.png` | `af9c167779deaf348a4492373e2c0bc7e4cdfddff8c6a74de9e457198bf6be99` |
| `evidence.ducky_yuv_blend.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/ducky_yuv_blend.png` | `ba0463e5216dd0aea17d9cf6284bead38b6e26684c219cb65d9464cd6c659fe4` |
| `evidence.ducky_yuv_blend.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/ducky_yuv_blend.png` | `9df0b5ef3bd8b61488bf021e3922e253de572aaf89baf67dd618d7eff7e98b2f` |
| `evidence.encode-alpha-jpeg.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/encode-alpha-jpeg.png` | `14912df60c1fe368684a463a3006f1b81ae9eb1275e427051be72874ef356296` |
| `evidence.encode-alpha-jpeg.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/encode-alpha-jpeg.png` | `d18f99505f90d32bea0a6eb1e83ba1f179fc68883e1bbec903dcdf4bf45af563` |
| `evidence.encode-platform.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/encode-platform.png` | `53101933fb04dc73f7b1f52ff5e835ab3762d27ee4e32df6225cbc6788c39771` |
| `evidence.encode-platform.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/encode-platform.png` | `1df6dc8eee7b60de8489e7a49f443670da6d4b727cce375cc8fc81b445b9cebd` |
| `evidence.fadefilter.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/fadefilter.png` | `60a2293f11e965c2f51a0911a419eb5ab25af0bcfff1fd0ba82e511083e9eeeb` |
| `evidence.fadefilter.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/fadefilter.png` | `a30924e582e82713114b4cc02a1c6adabd7d5d384594c63874a6173c1388418c` |
| `evidence.filterindiabox.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/filterindiabox.png` | `a321770cc4cca7cf4d60634bac95702a58a6b1ccccddf03a3bda661e8fc0f680` |
| `evidence.filterindiabox.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/filterindiabox.png` | `585b87fc42834459eab214cac83d8a95891c65ac8efdc40224d282b174b221f1` |
| `evidence.fontcache.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/fontcache.png` | `8a36872d552c8b971e266c54c9228dbdc2650c6e632e6b9c855da68d883810bb` |
| `evidence.fontcache.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/fontcache.png` | `b73856c53f8e4f1416d036b1aad31caa93df63222f9e5628196b4354fabab178` |
| `evidence.fontmgr_iter.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/fontmgr_iter.png` | `d70a372002a822a8fb3bea5b4a2634cb3fb3d78cfca73e1dd492457469c36a72` |
| `evidence.fontmgr_iter.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/fontmgr_iter.png` | `4858088d2304499340402d1df2e993fce9a377ac9844afe6aba21043c9fa1c74` |
| `evidence.fontmgr_match.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/fontmgr_match.png` | `5ac0a56b1b5393bd7adea486723ab61f1035575d005065591f262e8dcbd6219a` |
| `evidence.fontmgr_match.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/fontmgr_match.png` | `80ee0b767f54e552ca6f05113886897251223bfecbe054c23ad1c7701e083ff4` |
| `evidence.fontregen.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/fontregen.png` | `dbaa6d9fbc0bcf64d0ecac99b9bfcb234afaad1a696a0db50bd175750404f9fc` |
| `evidence.fontregen.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/fontregen.png` | `3d72b546a3fc1462e7158f6c400ebcf72f732d678281602ad523fdd327efebae` |
| `evidence.gammagradienttext.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/gammagradienttext.png` | `149dc039629a83dc8e7fc835a7dbd553da05e818728ced6616e09c005c16e04c` |
| `evidence.gammagradienttext.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/gammagradienttext.png` | `69d7d357ddbd4b4213b5cb0fadbffad27f201080c948adc1c4da0f56e6f5f464` |
| `evidence.grayscalejpg.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/grayscalejpg.png` | `abe8b51aa55ed819d239258440035923d7b01ebf72fb883d63547c9d14434f17` |
| `evidence.grayscalejpg.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/grayscalejpg.png` | `3b43a7aa9fd7dff81cf1ad51f5528f54abacc290dd94dba2dda4126350d2c3fc` |
| `evidence.imageblur.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imageblur.png` | `c6a6c07f7ee66213eee468933c650204077704a0a356c06dd57103c56d937505` |
| `evidence.imageblur.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imageblur.png` | `2a2dfc5064d3c41f3e9e7324351bc6971be5c03451282dea0682ee2b0d336c25` |
| `evidence.imageblur2.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imageblur2.png` | `cd0a5309ba740183d859f5bd3f95e6f741a24e209684024cf952c50ad3aa7e6d` |
| `evidence.imageblur2.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imageblur2.png` | `2f7761733a57b859142f2f91d1495c0894de1cfa57b3fdb0a17f197de37c321b` |
| `evidence.imagefilter_composed_transform.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imagefilter_composed_transform.png` | `3f4464f22b3036a1e6948dd3753a537f32d9c06999e5a412f49acaac01191a98` |
| `evidence.imagefilter_composed_transform.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imagefilter_composed_transform.png` | `376e09f8bbc02b92c476a49364c18135f561b6cef06f7be19ec977f92aa6812e` |
| `evidence.imagefilter_convolve_subset.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imagefilter_convolve_subset.png` | `4e73da34fa417715d6a64702de779850ca71434454b540559934b302f5671f5b` |
| `evidence.imagefilter_convolve_subset.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imagefilter_convolve_subset.png` | `5e42f1a863119aa27ae2fd95bce4e0515936c40d3fda8cfae91a7adfe5b3680b` |
| `evidence.imagefilter_matrix_localmatrix.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imagefilter_matrix_localmatrix.png` | `e6a614eae1e49199dcd562bf23d92805dbb20f1b4bfcfaeddfa1e6ad15069576` |
| `evidence.imagefilter_matrix_localmatrix.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imagefilter_matrix_localmatrix.png` | `376e09f8bbc02b92c476a49364c18135f561b6cef06f7be19ec977f92aa6812e` |
| `evidence.imagefilters_effect_order.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imagefilters_effect_order.png` | `257be5efd0558c7ee7b43a8a5850ff46e8abd2093d63e4ae610c081d8ce7d205` |
| `evidence.imagefilters_effect_order.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imagefilters_effect_order.png` | `376e09f8bbc02b92c476a49364c18135f561b6cef06f7be19ec977f92aa6812e` |
| `evidence.imageshader_tinyscale.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/imageshader_tinyscale.png` | `0faf9b4847340bff0dac3af4bfcb70dcd2ea49de59a37e56187d8d144b85ff21` |
| `evidence.imageshader_tinyscale.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/imageshader_tinyscale.png` | `62c188c06b50568135fcd904f8112e7da9e2071f3762ae3c1985706209e91a30` |
| `evidence.makecolortypeandspace.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/makecolortypeandspace.png` | `5a6a3bd188f46e40b6096ed80129fa12d762d850a337e4804ae0249f481eda9e` |
| `evidence.makecolortypeandspace.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/makecolortypeandspace.png` | `24602f9c3e5737746761b3b8acd8f1e2c0f779e071a5cfb54df87fce15cdce5f` |
| `evidence.matrixconvolution.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/matrixconvolution.png` | `fd788e3aa285160e232f5972a1b45b4643e45d2cd18f62e7c8088e8d87e94dc6` |
| `evidence.matrixconvolution.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/matrixconvolution.png` | `ca8af4fd68414f2a80ed75d1a17170d9e529b05ad37da591f0e3ec518d4ac6cc` |
| `evidence.matrixconvolution_big.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/matrixconvolution_big.png` | `2de3c4c8cff902b027dce1ce17cfd2d04cbb81f98a88209ae77dcd80da20620a` |
| `evidence.matrixconvolution_big.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/matrixconvolution_big.png` | `c1604ab86aad56f7e880f62b4c9cdc5f5b91f4d908e94641fbb1527c8f6a3887` |
| `evidence.matrixconvolution_big_color.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/matrixconvolution_big_color.png` | `7ff53e23a319ca659f578ea3d03f1b5aacc210f8eeaeea54cbb3b6e68c4961d1` |
| `evidence.matrixconvolution_big_color.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/matrixconvolution_big_color.png` | `33f6c685b8ca213465211718590f6f451c4c83046090adefec5e0ace5ff7bb30` |
| `evidence.matrixconvolution_bigger.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/matrixconvolution_bigger.png` | `78332ed6e3154dc97a50b479a148b7d11be33333d89db7d02d9a9cff198553cf` |
| `evidence.matrixconvolution_bigger.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/matrixconvolution_bigger.png` | `e2f643a51bda11891e2617d82f3febc31b61deaa4b34ec5a7253c9aee4b00b56` |
| `evidence.matrixconvolution_biggest.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/matrixconvolution_biggest.png` | `542e1fd0860b4e93fbc0b646ef9e44da78c47900ebaa1712395cdb4edfc39776` |
| `evidence.matrixconvolution_biggest.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/matrixconvolution_biggest.png` | `a10d900a754d6ed9efd526ecc45422c20ce19d9b532f04f92eaa416141211f0a` |
| `evidence.matriximagefilter.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/matriximagefilter.png` | `45f9aea0d67264c08363153861c069730ddbce4595ea8e5ddc1d8c871befe4ea` |
| `evidence.matriximagefilter.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/matriximagefilter.png` | `9c5a08dad209a480ede856d99f5425ec2e724d190f306df9e5f6b9b56b6f8c1d` |
| `evidence.mesh_with_paint_color.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/mesh_with_paint_color.png` | `3ce49f813f63e582544b5db0e980686301726b06b60f6af90ae050df9682c34e` |
| `evidence.mesh_with_paint_color.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/mesh_with_paint_color.png` | `3ce49f813f63e582544b5db0e980686301726b06b60f6af90ae050df9682c34e` |
| `evidence.modecolorfilters.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/modecolorfilters.png` | `8333189b6d0daf53e71dcc7c1909e85f833fb2ee01085ad6d978233e1d10e38f` |
| `evidence.modecolorfilters.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/modecolorfilters.png` | `91287cc6e4763e7a88cdf16ecccfaa8f7f9f8c77d8890cdafe82d3db07c47720` |
| `evidence.new_texture_image.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/new_texture_image.png` | `26aae436d5fce37fc33257e06e1ac01151f63bf36a9158a9c9c08e6df4e432ca` |
| `evidence.new_texture_image.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/new_texture_image.png` | `49a2132662a9dd4f1748ba3a4a5207f31730ced7ff7e6d10ff4d9dd74e926a43` |
| `evidence.palette.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/palette.png` | `d6414ae172d23cdfdc2d8556c0080b4bb1468c6ef0d5063c533d43cba96f6ec6` |
| `evidence.palette.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/palette.png` | `760c0bfa40d1e9618ff228c00a6101f95f31ed746682029d8557888063f82ad5` |
| `evidence.pdf_table_based_subset.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/pdf_table_based_subset.png` | `86a203a229cdd7a428fcb11906773d276758b9520cb837c15da4ce69e6b21272` |
| `evidence.pdf_table_based_subset.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/pdf_table_based_subset.png` | `4dddc66b7ed0f4af204d0b3aca6e889fa4695c0edfa8d0491b968fef2d316dcc` |
| `evidence.perlinnoise_localmatrix.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/perlinnoise_localmatrix.png` | `fb5f1cfa573b39498c37dad3822c258877658aa3c95ade28c3dafb2e0488d7f5` |
| `evidence.perlinnoise_localmatrix.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/perlinnoise_localmatrix.png` | `b6a9aab86f0beab96ed51382b7a057131ddde39900fa0c09013879a6c07e4732` |
| `evidence.poster_circle.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/poster_circle.png` | `4ad2cc2bc1f3d9bd13b0726fbd0906fc17af85b069462c81ed900a90ed9573f9` |
| `evidence.poster_circle.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/poster_circle.png` | `77f2906509596e51c23131f2a3cf326b7629cacd0aa3a63a4276db1f495ed79d` |
| `evidence.raw_image_shader_normals_rt.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/raw_image_shader_normals_rt.png` | `b074e56b22e3af29ec06bb41948837fc6290c714fbd25d2da7bd89da5a3ce9e6` |
| `evidence.raw_image_shader_normals_rt.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/raw_image_shader_normals_rt.png` | `0dd1b3e7fc235aff9332f6c2a971c7b00861af681c8b6cd2da4d21e0a0bb715b` |
| `evidence.rsx_blob_shader.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/rsx_blob_shader.png` | `0ba2231d9956cc19590ae39e77aa801dac3c22314d1bd5430a1cac71b1fcf481` |
| `evidence.rsx_blob_shader.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/rsx_blob_shader.png` | `cd66086f9aa75fae1507fe8044e20d7c83242cc18faad352dbd6585e6a036b7b` |
| `evidence.save_behind.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/save_behind.png` | `3a4ccdbd5bb5a88c4e77e03bb14f180ab959c82e673ca31b0382f15c6304289d` |
| `evidence.save_behind.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/save_behind.png` | `77198f610549cb1f63ec8176258b97c7f9a25c1aa2e06f700949251364ea3d44` |
| `evidence.savelayer_initfromprev.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/savelayer_initfromprev.png` | `e730754a588cd6c64194bf4ea9cfa28f574546b8d087c77321b83e1c4fe2480a` |
| `evidence.savelayer_initfromprev.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/savelayer_initfromprev.png` | `a9737998040643cd09a8d185cafb0d0e386b19b27ca005694c4d65dbc22f3453` |
| `evidence.scaled_tilemodes.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/scaled_tilemodes.png` | `8426abe5275e274285f0b1d34a026fba9472e5154078514b09d7038188798332` |
| `evidence.scaled_tilemodes.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/scaled_tilemodes.png` | `189c46122cb7866812e2aa1ec4396bd7d6a1d5ba153cb8bb98dc0cad9d185f3c` |
| `evidence.scaled_tilemodes_npot.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/scaled_tilemodes_npot.png` | `30c830a13c21864a08f257c28c05befad2b13072365052ccc52aa349025ef03c` |
| `evidence.scaled_tilemodes_npot.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/scaled_tilemodes_npot.png` | `189c46122cb7866812e2aa1ec4396bd7d6a1d5ba153cb8bb98dc0cad9d185f3c` |
| `evidence.shadow_utils_directional.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/shadow_utils_directional.png` | `e5f91cf399820662f47f916c61524e716af63afb66285bcdac27fb9732825a73` |
| `evidence.shadow_utils_directional.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/shadow_utils_directional.png` | `4eff94114636be1f829e4df7033bf1cc33d3d705e26e1afef2e6c4ce59abaf2a` |
| `evidence.showmiplevels_explicit.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/showmiplevels_explicit.png` | `a1b7a4a5bc0bd6f8443bb3665d1be8d10318b9f6ddead21b553eef6006d79e76` |
| `evidence.showmiplevels_explicit.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/showmiplevels_explicit.png` | `805dbadf5873a5ce8fa78b45bb4f813f92ec63d427b77b2dd92e413519597820` |
| `evidence.skbug_12212.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/skbug_12212.png` | `ddc62e77f132a9178f2ef80a9d592f3bc99c7b8d1928874725784ddef6ac5a15` |
| `evidence.skbug_12212.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/skbug_12212.png` | `64ea0fe357afa5b1820143c4ebff20b599017a6c0252eef99b4b6fbddd3d2756` |
| `evidence.skbug_14554.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/skbug_14554.png` | `3f8ca3b7360a402e2a78e77bb2cf667277e60d0e06ea020eb8d2a1d2af9d6ac0` |
| `evidence.skbug_14554.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/skbug_14554.png` | `154247babf2ddb918ca115e44121c1ce3828b9d2e017146132674f3546bf0eb1` |
| `evidence.skbug_5321.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/skbug_5321.png` | `e165c003b91da55fb567ac8c8ffac5387a138e5a8bcbc4e317d5c3c53e3cfd76` |
| `evidence.skbug_5321.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/skbug_5321.png` | `ea59783c7dd86b114ba232422733848798e2087faa632515e0adf84acb5170af` |
| `evidence.smallemboss.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/smallemboss.png` | `aae927655944e09aff37a7b0e6530845ac801f9116b31e111912b68c4f11d732` |
| `evidence.smallemboss.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/smallemboss.png` | `b446bde1baaca95c617211cf9a3613ee6c4a500bb1ef12e7cd4f7a5b958c2915` |
| `evidence.tablemaskfilter.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/tablemaskfilter.png` | `d33a41c0888bddb3b0d765698ad453bcbadf7472f048cabef1a9c68da58d8e27` |
| `evidence.tablemaskfilter.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/tablemaskfilter.png` | `a027c122c0fd7027c34f7961c628cfb9dbef11bca4e7124192ad4cf2bc0a9681` |
| `evidence.text_scale_skew.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/text_scale_skew.png` | `453f66d7690c6f3b69858b34530b662db232e49751a074e89bd64d56981492ae` |
| `evidence.text_scale_skew.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/text_scale_skew.png` | `2f64c7d6ba21e3bf299a9772cbe2bc981b22512e4945cb99747af5c0dd86be76` |
| `evidence.textblobshader.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/textblobshader.png` | `82fa7ff3a91ae4ca4efa0af10deceaa1d39bce2e61814fbe6e150ab88448b8af` |
| `evidence.textblobshader.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/textblobshader.png` | `5d770c255c17b978eaf83e32220c5d4645d786128af1d56117202f1b2cad9c74` |
| `evidence.thinconcavepaths.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/thinconcavepaths.png` | `eab72e6db89bab380bac6fca4cf3e8a2e712444de421cd790e7ff5e066c035a5` |
| `evidence.thinconcavepaths.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/thinconcavepaths.png` | `90ed12fb83a867ee2a32d0168fdc8a54f032f791ce6ae1f69f917515b2d40322` |
| `evidence.typefacerendering_pfa.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/typefacerendering_pfa.png` | `2a939f26efa8a901a6c26af636070783be0deec1bfb9fd077c4e11137603e8a2` |
| `evidence.typefacerendering_pfa.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/typefacerendering_pfa.png` | `c34094267bf81d86b2be37d2efd8a0edf68086c07bd98dc3497617e7cd2027e5` |
| `evidence.typefacerendering_pfb.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/typefacerendering_pfb.png` | `2a939f26efa8a901a6c26af636070783be0deec1bfb9fd077c4e11137603e8a2` |
| `evidence.typefacerendering_pfb.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/typefacerendering_pfb.png` | `c34094267bf81d86b2be37d2efd8a0edf68086c07bd98dc3497617e7cd2027e5` |
| `evidence.workingspace.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/workingspace.png` | `66643fc4a12f2fe259997dec7c8a5790d543ff588eb6dcdffad9e45d7dfcbea5` |
| `evidence.workingspace.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/workingspace.png` | `e10376459eccb33325f94872b87c1e540cbb74bb35a21b61c2aad2ef0b531fc2` |
| `evidence.yuv420_odd_dim.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/yuv420_odd_dim.png` | `e1dd1019eada2b3e9d069a67f6b30ef1f2eb8010952a911fe1ebdafcbc570e32` |
| `evidence.yuv420_odd_dim.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/yuv420_odd_dim.png` | `96317138376e7addf0d27424005c2c4fcf0ec4cd0a192f6de648c04de60411bf` |
| `evidence.yuv420_odd_dim_repeat.reference.0` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/reference/yuv420_odd_dim_repeat.png` | `9de64f713866abd7575d7ed552eb1ad3b2d8e721b128a41611bce3c71f9795b5` |
| `evidence.yuv420_odd_dim_repeat.render.1` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/../dashboard/images/generated/yuv420_odd_dim_repeat.png` | `8ee4563058884b8a6604dde99f33436791f129e819638ed141554790b3a73f06` |
| `commands` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/commands.json` | `f025512019734438079a0bfc9f6e996c32e788e0d9c5ee494cdf0947490cca87` |
| `environment` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/environment.json` | `08437f141ac18455cba1804c5ae2849e60908a978be3c18ae4fecba850f7c187` |
| `evidenceIndex` | `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/provenance/evidence-index.json` | `39978e9e7dd7621cc05f8c9f8765b2f42d7993ce24aaf57d5f27cef8a0faee08` |
