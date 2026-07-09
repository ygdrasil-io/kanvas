# GM Fidelity Wave 3 Score-First Evidence

Source: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`

## Guardrails

- Do not modify `integration-tests/skia/src/test/resources/reference/**`.
- Do not lower `minSimilarity` thresholds.
- Keep noReference, renderFailed, sizeMismatch, and unsupported rows visible.
- First slice: Work Group A.

## Group Summary

| Group | Name | Candidates | unmatchedPixels |
|---|---|---:|---:|
| A | Image, bitmap, and shader sampling | 177 | 22581459 |
| B | Composite, color filters, and blend | 114 | 19854884 |
| C | Clip and path residuals | 140 | 9526509 |
| D | Runtime effect cleanup | 23 | 1962442 |
| Z | Later score-first backlog | 210 | 32169331 |

## Ranked Candidates

| Group | Family | GM | Similarity | Threshold | Status | unmatchedPixels |
|---|---|---|---:|---:|---|---:|
| B | COMPOSITE | transparency_check | 1.6674933862433863 | 0.0 | pass | 1903088 |
| Z | GRADIENT | hardstop_gradients_many | 10.792 | 0.0 | pass | 1784160 |
| A | IMAGE | animatedGif | 37.50159022703549 | 0.0 | pass | 1532761 |
| Z | TEXT | scaledemoji_colrv0 | 0.008888888888888889 | 0.0 | pass | 1439872 |
| Z | TEXT | scaledemojipos_colrv0 | 0.017777777777777778 | 0.0 | pass | 1439744 |
| Z | TEXT | scaledemojiperspective_colrv0 | 0.04555555555555556 | 0.0 | pass | 1439344 |
| Z | TEXT | scaledemoji_rendering | 0.07930555555555555 | 0.0 | pass | 1438858 |
| A | IMAGE | pictureshader | 30.83743842364532 | 0.0 | pass | 1404000 |
| A | IMAGE | pictureshader_alpha | 30.83743842364532 | 0.0 | pass | 1404000 |
| A | IMAGE | pictureshader_localwrapper | 30.83743842364532 | 0.0 | pass | 1404000 |
| Z | TEXT | textblobtransforms | 0.0 | 0.0 | pass | 1200000 |
| Z | TEXT | textblobcolortrans | 0.0 | 0.0 | pass | 1080000 |
| Z | GRADIENT | radial_gradient | 34.87353515625 | 33.5 | pass | 1067032 |
| B | COMPOSITE | draw_image_set_rect_to_rect | 0.025788235294117647 | 0.0 | pass | 1062226 |
| Z | BLUR | BlurBigSigma | 0.0 | 0.0 | pass | 1048576 |
| A | IMAGE | async_rescale_and_read_rose | 0.001288915328177672 | 0.0 | pass | 1008587 |
| A | IMAGE | imageshader_tinyscale | 0.0 | 0.0 | pass | 1000000 |
| Z | TEXT | mixedtextblobs | 0.8509714285714285 | 0.0 | pass | 867554 |
| A | IMAGE | encode-platform | 14.018351236979168 | 0.0 | pass | 845234 |
| C | PATH | nonclosedpaths | 64.8406762295082 | 59.8 | pass | 823572 |
| Z | TEXT | largeglyphblur | 30.239149305555557 | 0.0 | pass | 803645 |
| A | IMAGE | imagemakewithfilter | 50.22807128412538 | 0.0 | pass | 787591 |
| Z | BLUR | TiledBlurBigSigma | 0.0 | 0.0 | pass | 786432 |
| Z | TEXT | coloremoji_colrv0 | 0.20692307692307693 | 0.0 | pass | 778386 |
| B | COMPOSITE | tablecolorfilter | 39.10857142857142 | 0.0 | pass | 703296 |
| Z | GRADIENT | gradients_interesting | 17.215384615384615 | 10.8 | pass | 688768 |
| Z | TEXT | surfaceprops | 5.830555555555556 | 0.0 | pass | 678020 |
| Z | GRADIENT | gradients_local_perspective | 6.115980134385043 | 0.0 | pass | 642730 |
| Z | GRADIENT | shallow_gradient_conical | 0.0 | 0.0 | pass | 640000 |
| Z | GRADIENT | shallow_gradient_conical_nodither | 0.0 | 0.0 | pass | 640000 |
| A | IMAGE | crbug_224618 | 0.23171874999999997 | 0.0 | pass | 638517 |
| C | CLIP | complexclip4_aa | 15.907877346021676 | 0.0 | pass | 636241 |
| C | CLIP | complexclip4_bw | 15.948321438012158 | 0.0 | pass | 635935 |
| B | COMPOSITE | filterfastbounds | 0.048412698412698414 | 0.0 | pass | 629695 |
| B | COMPOSITE | hslcolorfilter | 32.89123376623377 | 0.0 | pass | 620085 |
| C | PATH | filltypespersp | 11.96036498431708 | 0.0 | pass | 617510 |
| B | COMPOSITE | xfermodes | 45.62673014193776 | 45.0 | pass | 616756 |
| B | COMPOSITE | composeshader_grid | 21.272772147407714 | 4.5 | pass | 612438 |
| B | COMPOSITE | ducky_yuv_blend | 6.619469026548673 | 0.0 | pass | 590912 |
| A | IMAGE | tilemode_decal | 27.45669191919192 | 0.0 | pass | 574543 |
| A | IMAGE | colorspace | 0.7244001116071428 | 0.0 | pass | 569286 |
| A | IMAGE | colorspace2 | 0.7244001116071428 | 0.0 | pass | 569286 |
| Z | TEXT | textblobmixedsizes | 86.23213032581454 | 85.0 | pass | 549338 |
| B | COMPOSITE | HSL_duck | 23.26474861958733 | 0.0 | pass | 528092 |
| A | IMAGE | encode | 14.51123046875 | 0.0 | pass | 525243 |
| A | IMAGE | coordclampshader | 38.83559959242472 | 0.0 | pass | 522240 |
| B | COMPOSITE | save_behind | 7.930228376191333 | 0.0 | pass | 512000 |
| B | COMPOSITE | aaxfermodes | 17.382764227642276 | 14.0 | pass | 508096 |
| A | IMAGE | flippity | 44.158822518807874 | 0.0 | pass | 494047 |
| Z | MESH | vertices | 58.26417894162576 | 0.0 | pass | 478136 |
| C | CLIP | convex_poly_clip | 0.04555129842486164 | 0.0 | pass | 469586 |
| B | COMPOSITE | patch_alpha | 71.76333333333334 | 0.0 | pass | 465905 |
| B | COMPOSITE | patch_image_persp | 71.79842424242425 | 0.0 | pass | 465326 |
| Z | BLUR | imageblurrepeatmode | 40.62148337595908 | 0.0 | pass | 464340 |
| B | COMPOSITE | patch_primitive | 72.24490909090909 | 0.0 | pass | 457959 |
| Z | BLUR | blurcircles2 | 53.96823947234907 | 49.2 | pass | 453643 |
| B | COMPOSITE | modecolorfilters | 14.703369140625 | 0.0 | pass | 447200 |
| B | COMPOSITE | modecolorfilters | 14.703369140625 | 0.0 | pass | 447200 |
| A | IMAGE | anisotropic_image_scale_mip | 39.667758991022744 | 0.0 | pass | 438181 |
| Z | BLUR | blurrect_gallery | 65.05875651041667 | 64.5 | pass | 429358 |
| B | COMPOSITE | lcdblendmodes | 20.614814814814814 | 0.0 | pass | 428680 |
| A | IMAGE | bmp_filter_quality_repeat | 0.00375 | 0.0 | pass | 399985 |
| B | COMPOSITE | srgb_colorfilter | 0.049336751302083336 | 0.0 | pass | 393022 |
| Z | BLUR | blurrects | 44.389534883720934 | 44.0 | pass | 392165 |
| C | PATH | manycircles | 20.5175 | 0.0 | pass | 381516 |
| D | RUNTIME_EFFECT | rtif_distort | 0.0 | 0.0 | pass | 375000 |
| A | IMAGE | skbug_8664 | 17.898795180722892 | 0.0 | pass | 374792 |
| A | IMAGE | scaled_tilemodes | 51.74457644628099 | 0.0 | pass | 373690 |
| A | IMAGE | anisotropic_image_scale_aniso | 49.658120834939695 | 0.0 | pass | 365623 |
| A | IMAGE | anisotropic_image_scale_linear | 49.658120834939695 | 0.0 | pass | 365623 |
| B | COMPOSITE | dstreadshuffle | 0.0 | 0.0 | pass | 360400 |
| A | IMAGE | drawminibitmaprect | 66.90826416015625 | 0.0 | pass | 346992 |
| Z | TEXT | shadertext3 | 55.0034093889326 | 0.0 | pass | 343144 |
| A | IMAGE | imagemagnifier_bounds | 15.037790934244791 | 0.0 | pass | 334085 |
| B | COMPOSITE | clip_shader_persp | 76.54000425200198 | 0.0 | pass | 331044 |
| B | RUNTIME_EFFECT | runtimecolorfilter | 16.666666666666664 | 0.0 | pass | 327680 |
| B | COMPOSITE | compare_atlas_vertices | 0.010378510378510378 | 0.0 | pass | 327566 |
| A | IMAGE | imagefilterscropexpand | 34.5940990516333 | 0.0 | pass | 310351 |
| B | COMPOSITE | backdrop_hintrect_clipping | 42.755889892578125 | 0.0 | pass | 300124 |
| A | IMAGE | 3x3bitmaprect | 3.662109375 | 0.0 | pass | 295950 |
| C | PATH | trimpatheffect | 79.099 | 74.1 | pass | 292614 |
| Z | GRADIENT | sweep_tiling | 20.28985507246377 | 10.4 | pass | 281600 |
| B | COMPOSITE | lighting | 35.48393021120294 | 0.0 | pass | 281032 |
| Z | BLUR | blurredclippedcircle | 71.01875894456299 | 70.5 | pass | 270548 |
| Z | TEXT | gammatext | 45.05716959635417 | 10.0 | pass | 270055 |
| Z | GRADIENT | gradients_2pt_conical_inside_nodither | 60.58895705521472 | 0.0 | pass | 269808 |
| B | COMPOSITE | imagefilterscropped | 29.89921875 | 0.0 | pass | 269187 |
| A | IMAGE | pictureshadertile | 44.07666666666666 | 0.0 | pass | 268432 |
| Z | GRADIENT | gradients_dup_color_stops | 33.600801901998715 | 21.6 | pass | 263642 |
| A | IMAGE | async_rescale_and_read_alpha_type | 0.0 | 0.0 | pass | 262144 |
| B | COMPOSITE | clip_shader_difference | 0.0 | 0.0 | pass | 262144 |
| D | RUNTIME_EFFECT | color_cube_rt | 0.0 | 0.0 | pass | 262144 |
| D | RUNTIME_EFFECT | rippleshader | 0.0 | 0.0 | pass | 262144 |
| D | RUNTIME_EFFECT | spiral_rt | 0.0 | 0.0 | pass | 262144 |
| A | IMAGE | jpg-color-cube | 7.62939453125E-4 | 0.0 | pass | 262142 |
| A | IMAGE | jpg-color-cube | 7.62939453125E-4 | 0.0 | pass | 262142 |
| C | CLIP | complexclip_blur_tiled | 0.3963470458984375 | 0.0 | pass | 261105 |
| B | COMPOSITE | imagefilters_effect_order | 1.019287109375 | 0.0 | pass | 259472 |
| B | TEXT | coloremoji_blendmodes_colrv0 | 0.0328125 | 0.0 | pass | 255916 |
| Z | GRADIENT | gradients | 63.08486707566462 | 0.0 | pass | 252721 |
| A | IMAGE | encode-srgb-png | 48.592936197916664 | 0.0 | pass | 252676 |
| A | IMAGE | bug6783 | 0.0 | 0.0 | pass | 250000 |
| B | COMPOSITE | perlinnoise_layered | 0.0 | 0.0 | pass | 250000 |
| Z | GRADIENT | gradients_color_space_many_stops | 0.0 | 0.0 | pass | 250000 |
| C | PATH | polygons | 73.89327485380117 | 69.0 | pass | 249998 |
| Z | TEXT | fontscaler | 77.51328735632184 | 0.0 | pass | 244543 |
| B | COMPOSITE | arithmode_blender | 43.46030245746692 | 0.0 | pass | 239276 |
| Z | TEXT | bigtext_crbug_1370488 | 8.739089965820312 | 5.0 | pass | 239235 |
| Z | BLUR | hdr-pip-blur | 0.0 | 0.0 | pass | 230400 |
| Z | TEXT | fancyblobunderline | 88.75612025068547 | 85.0 | pass | 229645 |
| D | RUNTIME_EFFECT | kawase_blur_rt | 76.66666666666667 | 76.66666666666667 | pass | 229376 |
| Z | GRADIENT | hardstop_gradients | 13.701629638671875 | 0.0 | pass | 226226 |
| B | COMPOSITE | colorfiltershader | 39.272507390486425 | 34.3 | pass | 225967 |
| Z | BLUR | imageblur2 | 9.9844 | 5.0 | pass | 225039 |
| Z | GRADIENT | linear_gradient | 9.9948 | 0.0 | pass | 225013 |
| Z | GRADIENT | degenerate_gradients | 65.5884375 | 0.0 | pass | 220234 |
| Z | GRADIENT | alphagradients | 28.816080729166664 | 0.0 | pass | 218677 |
| C | CLIP | complexclip_aa_layer_invert | 28.17472905101771 | 0.0 | pass | 217372 |
| C | CLIP | complexclip_bw_layer_invert | 28.31086439333862 | 0.0 | pass | 216960 |
| Z | BLUR | blurrect_compare | 80.53752276867031 | 79.7 | pass | 213698 |
| Z | GRADIENT | analytic_gradients | 59.41009521484375 | 0.0 | pass | 212808 |
| A | IMAGE | scaled_tilemodes_npot | 72.57102272727273 | 0.0 | pass | 212410 |
| B | COMPOSITE | savelayer_f16 | 21.565555555555555 | 0.0 | pass | 211773 |
| C | CLIP | complexclip_aa_layer | 30.39551942902458 | 0.0 | pass | 210651 |
| B | COMPOSITE | lightingcolorfilter | 21.230307576894223 | 0.0 | pass | 210000 |
| C | CLIP | complexclip_bw_layer | 30.668781390430876 | 0.0 | pass | 209824 |
| C | CLIP | perspective_clip | 67.21953125 | 0.0 | pass | 209795 |
| B | COMPOSITE | clip_shader | 61.901282051282045 | 0.0 | pass | 208019 |
| C | CLIP | windowrectangles | 42.43666666666667 | 0.0 | pass | 207228 |
| Z | TEXT | font_palette_default | 48.925000000000004 | 0.0 | pass | 204300 |
| B | COMPOSITE | draw-atlas-colors | 17.58347481607244 | 0.0 | pass | 203882 |
| Z | TEXT | annotated_text | 22.241973876953125 | 0.0 | pass | 203838 |
| Z | GRADIENT | gradients_many | 42.64204545454545 | 0.0 | pass | 201900 |
| Z | TEXT | dftext | 74.45996602376303 | 73.0 | pass | 200855 |
| A | IMAGE | compositor_quads_image | 70.00516819090848 | 0.0 | pass | 200229 |
| B | COMPOSITE | xfermodeimagefilter | 52.41714285714286 | 0.0 | pass | 199848 |
| C | PATH | dashcircle | 81.56722222222223 | 36.5 | pass | 199074 |
| B | COMPOSITE | draw_image_set | 72.94137931034483 | 0.0 | pass | 196175 |
| A | IMAGE | tilemodes_alpha | 25.47454833984375 | 0.0 | pass | 195364 |
| Z | GRADIENT | gradient_many_stops | 22.1832 | 0.0 | pass | 194542 |
| C | CLIP | croppedrects | 22.2144 | 0.0 | pass | 194464 |
| B | COMPOSITE | highcontrastfilter | 42.74077380952381 | 0.0 | pass | 192391 |
| A | IMAGE | tilemode_bitmap | 52.05699873896595 | 0.0 | pass | 190094 |
| Z | GRADIENT | gradient_matrix | 70.64765625 | 0.0 | pass | 187855 |
| C | CLIP | clipdrawdraw | 29.035568237304688 | 24.0 | pass | 186029 |
| C | CLIP | complexclip_aa_invert | 40.202550885540575 | 0.0 | pass | 180971 |
| C | CLIP | complexclip_bw_invert | 40.356529209621996 | 0.0 | pass | 180505 |
| A | IMAGE | pictureimagefilter | 0.16666666666666669 | 0.0 | pass | 179700 |
| C | CLIP | pdf_crbug_772685 | 63.34587707136726 | 0.0 | pass | 177664 |
| Z | BLUR | imageblur | 30.0068 | 27.9 | pass | 174983 |
| A | IMAGE | image-cacherator-from-picture | 59.645138888888894 | 0.0 | pass | 174333 |
| A | IMAGE | poster_circle | 36.965185185185184 | 0.0 | pass | 170194 |
| Z | BLUR | imageblurclampmode | 78.27314578005115 | 0.0 | pass | 169904 |
| A | IMAGE | tiled_picture_shader | 0.0 | 0.0 | pass | 160000 |
| Z | TEXT | skbug_12212 | 0.0 | 0.0 | pass | 160000 |
| A | IMAGE | tilemode_gradient | 60.18259773013871 | 0.0 | pass | 157876 |
| A | IMAGE | image-picture | 58.809150326797386 | 0.0 | pass | 157555 |
| A | GRADIENT | scaled_tilemode_gradient | 60.53972257250946 | 0.0 | pass | 156460 |
| C | CLIP | complexclip_aa | 48.370671424795134 | 0.0 | pass | 156251 |
| Z | TEXT | skbug_257 | 40.58113098144531 | 0.0 | pass | 155763 |
| Z | TEXT | fontmgr_bounds | 82.23000919117646 | 0.0 | pass | 154670 |
| C | CLIP | complexclip_bw | 48.916864922019556 | 0.0 | pass | 154598 |
| Z | GRADIENT | gradient_many_hard_stops | 38.2688 | 0.0 | pass | 154328 |
| Z | BLUR | simpleblurroundrect | 69.214 | 64.3 | pass | 153930 |
| A | IMAGE | tilemodes | 69.25243506493507 | 0.0 | pass | 151524 |
| Z | TEXT | overdraw_text_xform | 42.391204833984375 | 41.9 | pass | 151018 |
| C | PATH | points | 52.719068877551024 | 0.0 | pass | 148273 |
| Z | TEXT | fontmgr_iter | 87.65530056423611 | 0.0 | pass | 145624 |
| A | IMAGE | all_variants_8888 | 0.0 | 0.0 | pass | 144172 |
| B | COMPOSITE | aarectmodes | 53.12988281249999 | 30.8 | pass | 143985 |
| B | COMPOSITE | localmatriximagefilter | 64.876220703125 | 0.0 | pass | 143867 |
| C | PATH | widebuttcaps | 40.3775 | 26.3 | pass | 143094 |
| Z | GRADIENT | radial_gradient2 | 55.88375 | 50.9 | pass | 141172 |
| C | CLIP | circular-clips | 11.87125 | 0.0 | pass | 141006 |
| A | IMAGE | tiledscaledbitmap | 77.4726135085387 | 0.0 | pass | 140989 |
| A | IMAGE | image-surface | 87.79418402777777 | 0.0 | pass | 140611 |
| Z | TEXT | stroketext | 75.621875 | 75.0 | pass | 140418 |
| B | COMPOSITE | composeshader_alpha | 15.151515151515152 | 12.1 | pass | 140000 |
| A | IMAGE | readpixelspicture | 7.535807291666667 | 0.0 | pass | 136344 |
| B | COMPOSITE | backdrop_imagefilter_croprect | 54.800000000000004 | 0.0 | pass | 135600 |
| Z | BLUR | embossmaskfilter | 78.10009765625 | 0.0 | pass | 134553 |
| C | CLIP | rrect_clip_aa | 56.81738281249999 | 0.0 | pass | 132657 |
| Z | MESH | mesh_updates | 0.0 | 0.0 | pass | 132300 |
| B | COMPOSITE | xfermodes2 | 38.79976865240023 | 33.8 | pass | 132269 |
| C | CLIP | rrect_clip_bw | 56.96126302083333 | 0.0 | pass | 132215 |
| D | RUNTIME_EFFECT | rtif_unsharp | 7.62939453125E-4 | 0.00152587890625 | fail | 131071 |
| B | COMPOSITE | rasterallocator | 27.282222222222224 | 0.0 | pass | 130892 |
| D | RUNTIME_EFFECT | unsharp_rt | 1.007080078125 | 1.0223388671875 | fail | 129752 |
| B | COMPOSITE | srcmode | 74.02898848684211 | 0.0 | pass | 126323 |
| Z | BLUR | blurcircles | 86.05861495844876 | 81.6 | pass | 125821 |
| A | IMAGE | imagemagnifier | 51.8372 | 0.0 | pass | 120407 |
| B | COMPOSITE | color4blendcf | 30.555555555555557 | 0.0 | pass | 120000 |
| Z | GRADIENT | linear_gradient_tiny | 60.0 | 55.0 | pass | 120000 |
| B | COMPOSITE | hairmodes | 61.368815104166664 | 41.5 | pass | 118675 |
| C | CLIP | simpleaaclip_path | 2.2508333333333335 | 0.0 | pass | 117299 |

## Group B Next Slice

The first composite slice is limited to the top three present Group B rows from
`composite-candidates.tsv`. The implementation worker must choose tests from
the specific mechanism visible in the source row:

- `transparency_check`: premul/alpha compositing boundary.
- `draw_image_set_rect_to_rect`: image-set rect mapping and clipping boundary.
- `tablecolorfilter` or `hslcolorfilter`: color-filter math boundary.

No global blend or color-filter behavior may change without focused unit tests.
| C | CLIP | simpleaaclip_rect | 2.2666666666666666 | 0.0 | pass | 117280 |
| C | PATH | stroke_rect_shader | 43.38937198067633 | 42.8 | pass | 117184 |
| B | COMPOSITE | imagefiltersclipped | 73.14093023255815 | 0.0 | pass | 115494 |
| A | IMAGE | anisomips | 14.792899408284024 | 0.0 | pass | 115200 |
| B | COMPOSITE | perlinnoise_localmatrix | 62.5 | 0.0 | pass | 115200 |
| B | COMPOSITE | skbug_14554 | 41.75627240143369 | 0.0 | pass | 113750 |
| C | PATH | drawlines_with_local_matrix | 54.901199999999996 | 0.0 | pass | 112747 |
| C | PATH | strokes_round | 64.7728125 | 17.3 | pass | 112727 |
| Z | GRADIENT | gradients_2pt_conical_outside | 84.41075080338885 | 0.0 | pass | 106724 |
| Z | BLUR | bigblurs | 35.28076171875 | 31.0 | pass | 106036 |
| Z | TEXT | lcdoverlap | 81.55893333333334 | 0.0 | pass | 103731 |
| Z | TEXT | textblob_intercepts | 86.27393617021276 | 85.0 | pass | 103220 |
| Z | GRADIENT | gradients_degenerate_2pt | 0.0 | 0.0 | pass | 102400 |
| B | COMPOSITE | imagefilters_xfermodes | 56.76953125000001 | 0.0 | pass | 99603 |
| B | COMPOSITE | imagefilterstransformed | 2.9990079365079367 | 0.0 | pass | 97777 |
| B | COMPOSITE | rotate_imagefilter | 61.842 | 0.0 | pass | 95395 |
| Z | GRADIENT | gradients_color_space | 0.6207812915227212 | 0.0 | pass | 93491 |
| Z | BLUR | fast_slow_blurimagefilter | 42.01240694789082 | 0.0 | pass | 93476 |
| B | COMPOSITE | imagefiltersbase | 73.33542857142858 | 0.0 | pass | 93326 |
| C | PATH | quadclosepath | 81.04962779156327 | 0.0 | pass | 91644 |
| C | PATH | cubicclosepath | 81.05355665839537 | 0.0 | pass | 91625 |
| C | PATH | linepath | 81.07361455748553 | 0.0 | pass | 91528 |
| C | PATH | quadpath | 81.10794044665013 | 0.0 | pass | 91362 |
| C | PATH | cubicpath | 81.11145574855252 | 0.0 | pass | 91345 |
| B | COMPOSITE | draw_quad_set | 85.74640624999999 | 0.0 | pass | 91223 |
| Z | TEXT | bigtext | 71.416015625 | 65.0 | pass | 87810 |
| Z | GRADIENT | gradients_no_texture | 78.927337398374 | 0.0 | pass | 82942 |
| Z | TEXT | typefacerendering_pfa | 84.59877232142857 | 0.0 | pass | 82797 |
| Z | TEXT | typefacerendering_pfb | 84.59877232142857 | 0.0 | pass | 82797 |
| Z | MESH | custommesh | 84.3304110656536 | 84.0 | pass | 80432 |
| B | COMPOSITE | patch_alpha_test | 41.78690909090909 | 0.0 | pass | 80043 |
| A | IMAGE | encode-alpha-jpeg | 0.0 | 0.0 | pass | 80000 |
| B | COMPOSITE | draw_image_set_alpha_only | 0.0 | 0.0 | pass | 80000 |
| C | PATH | dashing5_aa | 0.0 | 0.0 | pass | 80000 |
| B | COMPOSITE | recordopts | 0.0 | 0.0 | pass | 78030 |
| Z | TEXT | persptext | 90.45270284016928 | 90.0 | pass | 75083 |
| Z | TEXT | user_typeface | 80.64978695509669 | 0.0 | pass | 70845 |
| A | IMAGE | p3 | 87.95470085470085 | 0.0 | pass | 70465 |
| A | IMAGE | bitmapfilters | 48.06296296296296 | 0.0 | pass | 70115 |
| B | COMPOSITE | overdrawcolorfilter | 12.5 | 10.0 | pass | 70000 |
| B | COMPOSITE | perlinnoise | 48.72287390029326 | 0.0 | pass | 69942 |
| A | IMAGE | colorwheel | 31.499226888020832 | 0.0 | pass | 67339 |
| B | COMPOSITE | imagefilter_composed_transform | 74.57275390625 | 0.0 | pass | 66656 |
| A | IMAGE | encode-color-types-webp-lossless | 42.173549107142854 | 0.0 | pass | 66320 |
| B | COMPOSITE | imagefilter_matrix_localmatrix | 74.76692199707031 | 0.0 | pass | 66147 |
| A | IMAGE | localmatriximageshader_filtering | 0.0 | 0.0 | pass | 65536 |
| B | COMPOSITE | crbug_918512 | 0.0 | 0.0 | pass | 65536 |
| D | RUNTIME_EFFECT | runtime_shader | 50.0 | 50.0 | pass | 65536 |
| D | RUNTIME_EFFECT | threshold_rt | 0.006103515625 | 0.006103515625 | pass | 65532 |
| C | CLIP | clip_region | 3.814697265625 | 0.0 | pass | 63036 |
| B | COMPOSITE | clip_shader_nested | 6.781005859375 | 0.0 | pass | 61092 |
| A | IMAGE | imagemasksubset | 73.95833333333334 | 0.0 | pass | 60000 |
| A | IMAGE | bleed_downscale | 30.561342592592595 | 0.0 | pass | 59995 |
| A | IMAGE | all_bitmap_configs | 39.51416015625 | 0.0 | pass | 59460 |
| Z | TEXT | typefacerendering | 88.97395833333334 | 0.0 | pass | 59276 |
| D | RUNTIME_EFFECT | runtimefunctions | 10.21575927734375 | 10.25848388671875 | fail | 58841 |
| B | COMPOSITE | clip_shader_layer | 58.080668604651166 | 0.0 | pass | 57681 |
| B | COMPOSITE | savelayer_initfromprev | 12.26654052734375 | 0.0 | pass | 57497 |
| C | CLIP | crbug_892988 | 12.60223388671875 | 9.5 | pass | 57277 |
| Z | BLUR | rrect_blurs | 52.27916666666667 | 0.0 | pass | 57265 |
| A | IMAGE | imageresizetiled | 81.85221354166666 | 0.0 | pass | 55750 |
| Z | TEXT | gradtext | 77.41333333333333 | 77.3 | pass | 54208 |
| C | PATH | strokedline_caps | 94.80569498069498 | 92.0 | pass | 53813 |
| Z | TEXT | gammatext_color_shader | 34.80242424242424 | 0.0 | pass | 53788 |
| Z | TEXT | textblobblockreordering | 3.314545454545455 | 0.0 | pass | 53177 |
| A | IMAGE | async_yuv_no_scale | 56.666666666666664 | 0.0 | pass | 52000 |
| Z | TEXT | macaa_colors | 87.18825 | 0.0 | pass | 51247 |
| Z | GRADIENT | persp_shaders_aa | 33.352 | 0.0 | pass | 49986 |
| D | RUNTIME_EFFECT | AlternateLuma | 0.0 | 0.0 | pass | 49152 |
| B | COMPOSITE | colormatrix | 38.585 | 0.0 | pass | 49132 |
| Z | TEXT | stroketext_native | 82.1098901098901 | 0.0 | pass | 48840 |
| Z | BLUR | inverse_fill_filters | 1.812744140625 | 0.0 | pass | 48261 |
| B | COMPOSITE | shadermaskfilter_gradient | 81.62384033203125 | 0.0 | pass | 48172 |
| Z | TEXT | textfilter_image | 72.7836028874269 | 65.0 | pass | 47657 |
| Z | GRADIENT | gradients_hue_method | 0.13582342954159593 | 0.0 | pass | 44115 |
| B | COMPOSITE | sk3d_simple | 52.285555555555554 | 0.0 | pass | 42943 |
| Z | TEXT | textblobshader | 86.05859375 | 85.0 | pass | 42828 |
| A | IMAGE | surface_underdraw | 35.31341552734375 | 0.0 | pass | 42393 |
| B | COMPOSITE | matriximagefilter | 0.023809523809523808 | 0.0 | pass | 41990 |
| C | CLIP | clipcubic | 74.46585365853659 | 61.9 | pass | 41876 |
| B | COMPOSITE | displacement | 86.134 | 0.0 | pass | 41598 |
| Z | BLUR | blur_image | 83.3652 | 78.4 | pass | 41587 |
| Z | TEXT | typeface_styling | 83.89593114241002 | 0.0 | pass | 41162 |
| D | RUNTIME_EFFECT | composeCF | 0.0 | 0.0 | pass | 40000 |
| Z | MESH | skbug_13047 | 0.0 | 0.0 | pass | 40000 |
| C | PATH | stlouisarch | 39.17083740234375 | 0.0 | pass | 39865 |
| Z | GRADIENT | bug6643 | 1.38 | 0.0 | pass | 39448 |
| Z | TEXT | textblob | 87.21354166666667 | 86.5 | pass | 39280 |
| B | COMPOSITE | imagefiltersstroked | 91.03581395348837 | 0.0 | pass | 38546 |
| B | COMPOSITE | overdraw_canvas | 84.9812 | 0.0 | pass | 37547 |
| C | CLIP | clip_sierpinski_region | 46.28373982564444 | 0.0 | pass | 37155 |
| Z | TEXT | colrv1_gradient_stops_repeat | 97.43625 | 90.0 | pass | 36918 |
| A | IMAGE | skbug_9819 | 43.9697265625 | 0.0 | pass | 36720 |
| Z | BLUR | blur_matrix_rect | 91.77967434025828 | 87.0 | pass | 36601 |
| C | PATH | path-reverse | 88.10221354166666 | 0.0 | pass | 36550 |
| Z | TEXT | fontmgr_match | 94.68460083007812 | 0.0 | pass | 34835 |
| Z | TEXT | fontscalerdistortable | 90.9638961038961 | 0.0 | pass | 34789 |
| Z | TEXT | textfilter_color | 80.49045138888889 | 75.0 | pass | 34162 |
| A | IMAGE | image-shader | 91.1686274509804 | 0.0 | pass | 33780 |
| C | PATH | preservefillrule_big | 79.159375 | 47.7 | pass | 33345 |
| A | IMAGE | exoticformats | 9.37225636523266 | 0.0 | pass | 33032 |
| Z | BLUR | imageblurrepeatunclipped | 0.23193359375 | 0.0 | pass | 32692 |
| Z | GRADIENT | linear_gradient_rt | 14.603817235396182 | 0.0 | pass | 32483 |
| B | COMPOSITE | draw-atlas | 89.66341145833333 | 0.0 | pass | 31754 |
| D | RUNTIME_EFFECT | workingspace | 54.64285714285714 | 14.464285714285715 | pass | 31750 |
| C | CLIP | rrect_clip_draw_paint | 52.325439453125 | 0.0 | pass | 31244 |
| C | CLIP | cliplargerect | 52.734375 | 47.7 | pass | 30976 |
| B | COMPOSITE | color4shader | 82.11805555555556 | 0.0 | pass | 30900 |
| Z | BLUR | emboss | 58.55555555555556 | 55.5 | pass | 29840 |
| Z | GRADIENT | gradient_dirty_laundry | 92.89989837398373 | 0.0 | pass | 27946 |
| C | PATH | mandoline | 89.77142857142857 | 84.8 | pass | 27208 |
| Z | TEXT | blob_rsxform | 46.482 | 20.0 | pass | 26759 |
| Z | BLUR | tablemaskfilter | 83.443125 | 0.0 | pass | 26491 |
| A | IMAGE | imagefiltersgraph | 70.64111111111112 | 0.0 | pass | 26423 |
| C | PATH | dashtextcaps | 90.09742736816406 | 0.0 | pass | 25959 |
| A | IMAGE | unpremul | 40.12 | 0.0 | pass | 23952 |
| Z | GRADIENT | fillrect_gradient | 64.00925925925925 | 28.6 | pass | 23322 |
| A | IMAGE | mirror_tile | 55.521235521235525 | 0.0 | pass | 23040 |
| A | IMAGE | colorwheel_alphatypes | 29.9102783203125 | 0.0 | pass | 22967 |
| B | COMPOSITE | perlinnoise_rotated | 68.109375 | 0.0 | pass | 22451 |
| Z | TEXT | gammagradienttext | 75.84666666666666 | 75.3 | pass | 21738 |
| Z | TEXT | getpostextpath | 94.19978632478633 | 90.0 | pass | 21716 |
| Z | BLUR | crbug_899512 | 92.14275147928994 | 87.7 | pass | 21246 |
| A | IMAGE | bitmap_subset_shader | 67.61322021484375 | 0.0 | pass | 21225 |
| A | IMAGE | stoplight_animated_image | 77.31886253462605 | 0.0 | pass | 20961 |
| A | IMAGE | pictureshader_persp | 12.076109936575053 | 0.0 | pass | 20794 |
| C | CLIP | skbug_9319 | 84.23309326171875 | 0.0 | pass | 20666 |
| Z | MESH | custommesh_cs | 83.27039024713443 | 83.0 | pass | 20200 |
| B | COMPOSITE | PlusMergesAA | 69.482421875 | 0.0 | pass | 20000 |
| Z | GRADIENT | clamped_gradients | 93.89522058823529 | 0.0 | pass | 19926 |
| B | COMPOSITE | simple-offsetimagefilter | 84.6015625 | 0.0 | pass | 19710 |
| B | COMPOSITE | imagefilter_convolve_subset | 32.177083333333336 | 0.0 | pass | 19533 |
| Z | BLUR | BlurDrawImage | 70.751953125 | 0.0 | pass | 19168 |
| A | IMAGE | bicubic | 80.75 | 0.0 | pass | 18480 |
| Z | TEXT | crbug_1073670 | 70.6528 | 0.0 | pass | 18342 |
| Z | BLUR | blurquickreject | 79.77888888888889 | 70.5 | pass | 18199 |
| A | GRADIENT | gradients_color_space_tilemode | 51.90476190476191 | 0.0 | pass | 18180 |
| Z | GRADIENT | crbug_938592 | 88.0 | 83.0 | pass | 18000 |
| C | CLIP | skbug1719 | 40.61 | 0.0 | pass | 17817 |
| Z | BLUR | BlurSmallSigma | 86.71875 | 82.9 | pass | 17408 |
| B | COMPOSITE | compositor_quads_color | 92.20325921896602 | 0.0 | pass | 17200 |
| A | IMAGE | imagefilter_transformed_image | 73.78692626953125 | 0.0 | pass | 17179 |
| Z | COLOR | p3_ovals | 88.24722222222222 | 0.0 | pass | 16924 |
| Z | MESH | vertices_batching | 66.208 | 0.0 | pass | 16896 |
| Z | BLUR | blur2rectsnonninepatch | 95.24971428571428 | 90.5 | pass | 16626 |
| A | IMAGE | compressed_textures | 49.54617446393762 | 0.0 | pass | 16565 |
| A | IMAGE | grayscalejpg | 0.0 | 0.0 | pass | 16384 |
| A | IMAGE | nearest_half_pixel_image | 73.59123146357189 | 0.0 | pass | 16384 |
| B | COMPOSITE | fadefilter | 75.0 | 70.0 | pass | 16384 |
| C | PATH | thinstrokedrects | 79.02994791666667 | 75.2 | pass | 16105 |
| B | COMPOSITE | colorfilterimagefilter | 69.79693486590038 | 44.8 | pass | 15766 |
| B | COMPOSITE | clipshadermatrix | 17.106681034482758 | 0.0 | pass | 15385 |
| Z | TEXT | typefacestyles_kerning | 95.103515625 | 0.0 | pass | 15042 |
| Z | GRADIENT | small_color_stop | 0.0 | 0.0 | pass | 15000 |
| B | COMPOSITE | offsetimagefilter | 76.57833333333333 | 0.0 | pass | 14053 |
| C | PATH | concavepaths | 95.392 | 93.0 | pass | 13824 |
| Z | BLUR | blur2rects | 96.10885714285715 | 91.4 | pass | 13619 |
| Z | BLUR | inverse_windingmode_filters | 48.35546875 | 0.0 | pass | 13221 |
| B | COMPOSITE | hsl | 78.68333333333334 | 0.0 | pass | 12790 |
| A | IMAGE | bitmapshaders | 16.786666666666665 | 0.0 | pass | 12482 |
| Z | TEXT | blob_rsxform_distortable | 78.00200000000001 | 45.0 | pass | 10999 |
| A | IMAGE | imagemagnifier_cropped | 83.831787109375 | 0.0 | pass | 10596 |
| A | IMAGE | localmatriximageshader | 83.8688 | 0.0 | pass | 10082 |
| A | IMAGE | tinybitmap | 0.0 | 0.0 | pass | 10000 |
| B | COMPOSITE | composeshader | 30.555555555555557 | 25.6 | pass | 10000 |
| B | COMPOSITE | discard | 0.0 | 0.0 | pass | 10000 |
| Z | TEXT | typefacestyles | 96.7705078125 | 0.0 | pass | 9921 |
| Z | GRADIENT | radial_gradient_precision | 76.725 | 0.0 | pass | 9310 |
| C | CLIP | clip_strokerect | 89.17 | 71.8 | pass | 8664 |
| C | PATH | trickycubicstrokes_largeradius | 73.9288330078125 | 68.9 | pass | 8543 |
| Z | GRADIENT | testgradient | 98.68515624999999 | 0.0 | pass | 8415 |
| A | IMAGE | shaderpath | 98.89732494099135 | 0.0 | pass | 8409 |
| C | PATH | zeroPath | 97.4003125 | 92.4 | pass | 8319 |
| Z | GRADIENT | gradients_alpha_many_stops | 18.0 | 0.0 | pass | 8200 |
| B | COMPOSITE | dropshadow_pseudopersp | 67.10926118626432 | 0.0 | pass | 7902 |
| A | IMAGE | hugebitmapshader | 21.15 | 0.0 | pass | 7885 |
| A | IMAGE | drawimagerect_filter | 29.666666666666668 | 0.0 | pass | 7596 |
| A | IMAGE | alpha_image_alpha_tint | 38.94736842105263 | 0.0 | pass | 7424 |
| C | PATH | dashing | 96.63051470588235 | 84.8 | pass | 7332 |
| C | PATH | thin_aa_dash_lines | 79.86776859504133 | 75.6 | pass | 7308 |
| C | PATH | convexpaths | 99.45257575757576 | 0.0 | pass | 7226 |
| C | PATH | zero_control_stroke | 97.860625 | 0.0 | pass | 6846 |
| C | PATH | zero_control_stroke | 97.860625 | 0.0 | pass | 6846 |
| C | PATH | thinroundrects | 91.1328125 | 84.5 | pass | 6810 |
| C | PATH | thinconcavepaths | 96.93136363636363 | 94.0 | pass | 6751 |
| Z | TEXT | chrome_gradtext2 | 97.30541666666667 | 95.0 | pass | 6467 |
| B | COMPOSITE | badpaint | 36.0 | 31.0 | pass | 6400 |
| C | PATH | thinrects | 91.82161458333333 | 85.1 | pass | 6281 |
| Z | BLUR | check_small_sigma_offset | 97.42041666666667 | 91.7 | pass | 6191 |
| A | IMAGE | imagesource | 91.81866666666667 | 0.0 | pass | 6136 |
| Z | TEXT | text_scale_skew | 81.585693359375 | 80.0 | pass | 6034 |
| A | IMAGE | image_subset | 94.10640495867769 | 0.0 | pass | 5705 |
| C | PATH | teenyStrokes | 98.22375 | 93.2 | pass | 5684 |
| A | COMPOSITE | composeshader_bitmap | 37.142857142857146 | 0.0 | pass | 5500 |
| A | IMAGE | filterbug | 77.77777777777779 | 0.0 | pass | 5000 |
| A | IMAGE | pictureshadercache | 53.72 | 0.0 | pass | 4628 |
| B | COMPOSITE | imagefiltersunpremul | 0.0 | 0.0 | pass | 4096 |
| A | IMAGE | not_native32_bitmap_config | 75.738525390625 | 0.0 | pass | 3975 |
| A | IMAGE | makeRasterImage | 78.717041015625 | 0.0 | pass | 3487 |
| Z | GRADIENT | emptyshader | 69.10511363636364 | 0.0 | pass | 3480 |
| Z | TEXT | textblobgeometrychange | 91.9975 | 90.0 | pass | 3201 |
| A | IMAGE | image_out_of_gamut | 0.0 | 0.0 | pass | 3157 |
| A | IMAGE | filterindiabox | 96.47285067873304 | 0.0 | pass | 3118 |
| C | PATH | zerolinestroke | 74.05555555555556 | 69.1 | pass | 2802 |
| Z | TEXT | palette | 97.9888916015625 | 0.0 | pass | 2636 |
| B | COMPOSITE | gpusamplerstress | 99.14713541666667 | 0.0 | pass | 2620 |
| B | COMPOSITE | internal_links | 99.27228571428572 | 0.0 | pass | 2547 |
| C | CLIP | aaclip | 91.18402777777777 | 88.2 | pass | 2539 |
| Z | TEXT | textblobuseaftergpufree | 93.7775 | 90.0 | pass | 2489 |
| B | COMPOSITE | crbug_1167277 | 96.80978260869566 | 0.0 | pass | 2348 |
| A | IMAGE | bigmatrix | 7.960000000000001 | 0.0 | pass | 2301 |
| A | IMAGE | draw_bitmap_rect_skbug4734 | 45.3125 | 0.0 | pass | 2240 |
| C | PATH | ctmpatheffect | 99.54020833333334 | 0.0 | pass | 2207 |
| C | CLIP | inverseclip | 98.73125 | 52.1 | pass | 2030 |
| B | COMPOSITE | crbug_1162942 | 98.64112903225806 | 0.0 | pass | 1685 |
| Z | MESH | vertices_collapsed | 36.0 | 0.0 | pass | 1600 |
| C | PATH | circle_sizes | 92.071533203125 | 87.3 | pass | 1299 |
| B | COMPOSITE | colorfilterimagefilter_layer | 0.0 | 0.0 | pass | 1024 |
| Z | TEXT | skbug_5321 | 94.9462890625 | 0.0 | pass | 828 |
| C | PATH | preservefillrule_little | 66.8125 | 40.4 | pass | 531 |
| A | IMAGE | bitmaprect_rounding | 99.869140625 | 0.0 | pass | 402 |
| Z | BLUR | smallemboss | 84.0 | 0.0 | pass | 400 |
| C | PATH | tinyanglearcs | 99.84506353861192 | 94.9 | pass | 317 |
| A | IMAGE | bc1_transparency | 56.547619047619044 | 0.0 | pass | 292 |
| B | COMPOSITE | luminosity_overflow | 99.62158203125 | 0.0 | pass | 248 |
| B | COMPOSITE | crbug_1177833 | 99.84625 | 0.0 | pass | 246 |
| Z | TEXT | skbug_8955 | 97.69 | 0.0 | pass | 231 |
| B | COMPOSITE | crbug_1174186 | 99.99729166666667 | 0.0 | pass | 39 |
| C | PATH | zerolinedash | 99.98626708984375 | 80.0 | pass | 9 |

## Visible No-Score Rows

| Group | Family | GM | Status | matchingPixels | totalPixels |
|---|---|---|---|---:|---:|
| A | IMAGE | all | no-reference |  |  |
| A | IMAGE | alpha_bitmap_is_coverage_android | no-reference |  |  |
| A | IMAGE | alpha_image_shader_rt | render-failed |  |  |
| A | IMAGE | anisotropic_image_scale | no-reference |  |  |
| A | IMAGE | async_rescale_and_read_no_bleed | size-mismatch |  |  |
| A | IMAGE | attributes | no-reference |  |  |
| A | IMAGE | bitmap-image-srgb-legacy | render-failed |  |  |
| A | IMAGE | bitmapcopy | render-failed |  |  |
| A | IMAGE | bitmaprect_i | render-failed |  |  |
| A | IMAGE | bitmaprect_s | render-failed |  |  |
| A | IMAGE | cgimage | render-failed |  |  |
| A | IMAGE | child_sampling_rt | render-failed |  |  |
| A | IMAGE | clippedbitmapshaders | no-reference |  |  |
| A | IMAGE | color_cube_cf_rt | render-failed |  |  |
| A | COMPOSITE | composeshader_bitmap_lm | render-failed |  |  |
| A | IMAGE | copyTo4444 | render-failed |  |  |
| A | IMAGE | crbug_404394639 | render-failed |  |  |
| A | IMAGE | deferred_shader_rt | render-failed |  |  |
| A | IMAGE | drawbitmaprect4 | no-reference |  |  |
| A | IMAGE | drawimage_sampling | render-failed |  |  |
| A | IMAGE | encode-alpha-jpeg-opts | no-reference |  |  |
| A | IMAGE | flight_animated_image | size-mismatch |  |  |
| A | IMAGE | format4444 | render-failed |  |  |
| A | IMAGE | giantbitmap_clamp_bilerp_rotate | render-failed |  |  |
| A | IMAGE | giantbitmap_clamp_bilerp_scale | render-failed |  |  |
| A | IMAGE | giantbitmap_clamp_point_rotate | render-failed |  |  |
| A | IMAGE | giantbitmap_clamp_point_scale | render-failed |  |  |
| A | IMAGE | giantbitmap_mirror_bilerp_rotate | render-failed |  |  |
| A | IMAGE | giantbitmap_mirror_bilerp_scale | render-failed |  |  |
| A | IMAGE | giantbitmap_mirror_point_rotate | render-failed |  |  |
| A | IMAGE | giantbitmap_mirror_point_scale | render-failed |  |  |
| A | IMAGE | giantbitmap_repeat_bilerp_rotate | render-failed |  |  |
| A | IMAGE | giantbitmap_repeat_bilerp_scale | render-failed |  |  |
| A | IMAGE | giantbitmap_repeat_point_rotate | render-failed |  |  |
| A | IMAGE | giantbitmap_repeat_point_scale | render-failed |  |  |
| A | IMAGE | image_dither | render-failed |  |  |
| A | IMAGE | jpeg_orientation | no-reference |  |  |
| A | IMAGE | lazytiling | no-reference |  |  |
| A | IMAGE | lit_shader_linear_rt | render-failed |  |  |
| A | IMAGE | local_matrix_shader_rt | render-failed |  |  |
| A | IMAGE | localmatrix_order | render-failed |  |  |
| A | IMAGE | localmatrixshader_nested | render-failed |  |  |
| A | IMAGE | localmatrixshader_persp | render-failed |  |  |
| A | IMAGE | makecolorspace | render-failed |  |  |
| A | IMAGE | makecolortypeandspace | render-failed |  |  |
| A | IMAGE | mipmap_gray8_srgb | render-failed |  |  |
| A | IMAGE | mipmap_srgb | render-failed |  |  |
| A | IMAGE | new_texture_image | no-reference |  |  |
| A | IMAGE | ninepatch-stretch | render-failed |  |  |
| A | IMAGE | null_child_rt | render-failed |  |  |
| A | IMAGE | path_huge_aa | render-failed |  |  |
| A | IMAGE | persp_images | render-failed |  |  |
| A | IMAGE | picture | no-reference |  |  |
| A | IMAGE | pictureimagegenerator | render-failed |  |  |
| A | IMAGE | pictureimagegenerator | render-failed |  |  |
| A | IMAGE | raw_image_shader_normals_rt | render-failed |  |  |
| A | IMAGE | readpixelscodec | render-failed |  |  |
| A | IMAGE | rectangle_texture | no-reference |  |  |
| A | IMAGE | reinterpretcolorspace | render-failed |  |  |
| A | IMAGE | repeated_bitmap | render-failed |  |  |
| A | IMAGE | repeated_bitmap_jpg | render-failed |  |  |
| A | IMAGE | scale-pixels | render-failed |  |  |
| A | IMAGE | scaled_tilemode_bitmap | render-failed |  |  |
| A | IMAGE | scaledtiling | no-reference |  |  |
| A | IMAGE | scalepixels_unpremul | render-failed |  |  |
| A | IMAGE | showmiplevels_explicit | render-failed |  |  |
| A | IMAGE | srcrectconstraint | no-reference |  |  |
| A | IMAGE | texelsubset | no-reference |  |  |
| A | IMAGE | texture | no-reference |  |  |
| A | IMAGE | textureimage_and_shader | render-failed |  |  |
| A | IMAGE | tiling | no-reference |  |  |
| A | IMAGE | verylarge_picture_image | render-failed |  |  |
| A | IMAGE | verylargebitmap | render-failed |  |  |
| A | IMAGE | videodecoder | no-reference |  |  |
| A | IMAGE | wacky_yuv_formats | no-reference |  |  |
| A | IMAGE | ycbcrimage | no-reference |  |  |
| A | IMAGE | yuv420_odd_dim | no-reference |  |  |
| A | IMAGE | yuv420_odd_dim_repeat | no-reference |  |  |
| B | COMPOSITE | colorcomposefilter_alpha | render-failed |  |  |
| B | COMPOSITE | colorcomposefilter_wacky | render-failed |  |  |
| B | RUNTIME_EFFECT | colorcubecolorfilterrt | no-reference |  |  |
| B | TEXT | coloremoji_blendmodes | no-reference |  |  |
| B | COMPOSITE | composeCFIF | render-failed |  |  |
| B | COMPOSITE | destcolor | render-failed |  |  |
| B | COMPOSITE | extractalpha | render-failed |  |  |
| B | COMPOSITE | graphitestart | no-reference |  |  |
| B | COMPOSITE | mixerCF | render-failed |  |  |
| B | COMPOSITE | patch_image | render-failed |  |  |
| B | PATH | pathops_blend | render-failed |  |  |
| B | COMPOSITE | runtimecolorfilter_vertices_atlas_and_patch | render-failed |  |  |
| B | COMPOSITE | shadow_utils_gray | render-failed |  |  |
| B | COMPOSITE | shadow_utils_occl | render-failed |  |  |
| B | COMPOSITE | tileimagefilter | render-failed |  |  |
| B | COMPOSITE | xfermodes3 | render-failed |  |  |
| C | PATH | OverStroke | render-failed |  |  |
| C | PATH | PathMeasure_explosion | no-reference |  |  |
| C | PATH | aa_rect_effect | no-reference |  |  |
| C | PATH | bezier_conic_effects | no-reference |  |  |
| C | PATH | bezier_quad_effects | no-reference |  |  |
| C | PATH | big_rrect_circle_aa_effect | no-reference |  |  |
| C | CLIP | bug339297_as_clip | render-failed |  |  |
| C | PATH | circle | no-reference |  |  |
| C | PATH | circular_arcs | no-reference |  |  |
| C | PATH | circular_corner | no-reference |  |  |
| C | CLIP | clipsuperrrect | no-reference |  |  |
| C | PATH | clockwise | no-reference |  |  |
| C | CLIP | complexclip3_complex | render-failed |  |  |
| C | CLIP | complexclip3_simple | render-failed |  |  |
| C | PATH | conicpaths | render-failed |  |  |
| C | PATH | convex-polygon-inset | render-failed |  |  |
| C | PATH | convex_lineonly_paths | no-reference |  |  |
| C | PATH | convex_lineonly_paths_stroke_and_fill | no-reference |  |  |
| C | PATH | convex_poly_effect | no-reference |  |  |
| C | PATH | crbug_640176 | render-failed |  |  |
| C | PATH | crbug_691386 | render-failed |  |  |
| C | PATH | cubicpath_shader | render-failed |  |  |
| C | PATH | drawregion | render-failed |  |  |
| C | PATH | drawregionmodes | render-failed |  |  |
| C | PATH | ellipse | no-reference |  |  |
| C | PATH | elliptical_corner | no-reference |  |  |
| C | PATH | fancy_gradients | render-failed |  |  |
| C | CLIP | fast_constraint_red_is_allowed | render-failed |  |  |
| C | CLIP | fast_constraint_red_is_allowed_manual | render-failed |  |  |
| C | PATH | fatpathfill | render-failed |  |  |
| C | PATH | inner_join_geometry | render-failed |  |  |
| C | PATH | lattice2 | render-failed |  |  |
| C | PATH | lineclosepath | render-failed |  |  |
| C | PATH | longrect_dash | no-reference |  |  |
| C | PATH | macaatest | render-failed |  |  |
| C | PATH | manypathatlases | no-reference |  |  |
| C | CLIP | manypathatlases_128 | render-failed |  |  |
| C | CLIP | manypathatlases_2048 | render-failed |  |  |
| C | PATH | nested | no-reference |  |  |
| C | PATH | nested | no-reference |  |  |
| C | PATH | parsedpaths | render-failed |  |  |
| C | PATH | path_huge_aa_manual | render-failed |  |  |
| C | PATH | path_mask_cache | render-failed |  |  |
| C | PATH | path_stroke_clip_crbug1070835 | render-failed |  |  |
| C | PATH | pathops_skbug_10155 | render-failed |  |  |
| C | PATH | pathopsinverse | render-failed |  |  |
| C | PATH | poly2poly | render-failed |  |  |
| C | PATH | preservefillrule | no-reference |  |  |
| C | PATH | roundrects | render-failed |  |  |
| C | PATH | shadow_utils_directional | render-failed |  |  |
| C | PATH | sharedcorners | render-failed |  |  |
| C | CLIP | simpleclip | no-reference |  |  |
| C | PATH | simpleshapes | render-failed |  |  |
| C | PATH | simpleshapes_bw | render-failed |  |  |
| C | CLIP | strict_constraint_batch_no_red_allowed | render-failed |  |  |
| C | CLIP | strict_constraint_batch_no_red_allowed_manual | render-failed |  |  |
| C | CLIP | strict_constraint_no_red_allowed | render-failed |  |  |
| C | CLIP | strict_constraint_no_red_allowed_manual | render-failed |  |  |
| C | PATH | strokes3 | render-failed |  |  |
| C | PATH | zero_length_paths_aa | render-failed |  |  |
| C | PATH | zero_length_paths_bw | render-failed |  |  |
| C | PATH | zero_length_paths_dbl_aa | render-failed |  |  |
| C | PATH | zero_length_paths_dbl_bw | render-failed |  |  |
| D | RUNTIME_EFFECT | arithmode | size-mismatch |  |  |
| D | RUNTIME_EFFECT | lineargradientrt | no-reference |  |  |
| D | RUNTIME_EFFECT | lumafilter | size-mismatch |  |  |
| D | RUNTIME_EFFECT | runtime_intrinsics_common | size-mismatch |  |  |
| D | RUNTIME_EFFECT | runtime_intrinsics_exponential | size-mismatch |  |  |
| D | RUNTIME_EFFECT | runtime_intrinsics_geometric | size-mismatch |  |  |
| D | RUNTIME_EFFECT | runtime_intrinsics_matrix | size-mismatch |  |  |
| D | RUNTIME_EFFECT | runtime_intrinsics_relational | size-mismatch |  |  |
| D | RUNTIME_EFFECT | runtime_intrinsics_trig | size-mismatch |  |  |
| D | RUNTIME_EFFECT | simplert | no-reference |  |  |
| Z | BLUR | animatedbackdropblur | no-reference |  |  |
| Z | BLUR | blur_ignore_xform_circle | render-failed |  |  |
| Z | BLUR | blur_ignore_xform_rect | render-failed |  |  |
| Z | BLUR | blur_ignore_xform_rrect | render-failed |  |  |
| Z | TEXT | cliperror | render-failed |  |  |
| Z | COLOR | color | no-reference |  |  |
| Z | TEXT | coloremoji | no-reference |  |  |
| Z | TEXT | colorwheelnative | render-failed |  |  |
| Z | GRADIENT | conicalgradients | no-reference |  |  |
| Z | COLOR | const_color_processor | no-reference |  |  |
| Z | TEXT | crbug_478659067 | no-reference |  |  |
| Z | MESH | custommesh_cs_uniforms | no-reference |  |  |
| Z | COLOR | dark | no-reference |  |  |
| Z | COLOR | debug | no-reference |  |  |
| Z | COLOR | default | no-reference |  |  |
| Z | TEXT | dftext_blob_persp | render-failed |  |  |
| Z | TEXT | drawTextRSXform | render-failed |  |  |
| Z | COLOR | encodesrgb | no-reference |  |  |
| Z | COLOR | filter | no-reference |  |  |
| Z | TEXT | fontcache | render-failed |  |  |
| Z | TEXT | fontregen | render-failed |  |  |
| Z | GRADIENT | gradients_powerless_hue | no-reference |  |  |
| Z | GRADIENT | gradients_view_perspective | render-failed |  |  |
| Z | COLOR | high | no-reference |  |  |
| Z | BLUR | imagefilterstext_cf | no-reference |  |  |
| Z | BLUR | imagefilterstext_if | no-reference |  |  |
| Z | COLOR | light | no-reference |  |  |
| Z | COLOR | low | no-reference |  |  |
| Z | BLUR | matrixconvolution | render-failed |  |  |
| Z | BLUR | matrixconvolution_big | render-failed |  |  |
| Z | BLUR | matrixconvolution_big_color | render-failed |  |  |
| Z | BLUR | matrixconvolution_bigger | render-failed |  |  |
| Z | BLUR | matrixconvolution_biggest | render-failed |  |  |
| Z | BLUR | matrixconvolution_color | render-failed |  |  |
| Z | COLOR | med | no-reference |  |  |
| Z | MESH | mesh_with_effects | render-failed |  |  |
| Z | MESH | mesh_with_image | render-failed |  |  |
| Z | MESH | mesh_with_paint_color | render-failed |  |  |
| Z | MESH | mesh_with_paint_image | render-failed |  |  |
| Z | MESH | mesh_zero_init | render-failed |  |  |
| Z | COLOR | name | no-reference |  |  |
| Z | COLOR | none | no-reference |  |  |
| Z | COLOR | one | no-reference |  |  |
| Z | COLOR | orientation | no-reference |  |  |
| Z | COLOR | paint_alpha_normals_rt | render-failed |  |  |
| Z | TEXT | pdf_never_embed | render-failed |  |  |
| Z | TEXT | pdf_table_based_subset | render-failed |  |  |
| Z | GRADIENT | persp_shaders_bw | render-failed |  |  |
| Z | TEXT | persptext_minimal | render-failed |  |  |
| Z | COLOR | raster | no-reference |  |  |
| Z | COLOR | rect | no-reference |  |  |
| Z | TEXT | rsx_blob_shader | size-mismatch |  |  |
| Z | COLOR | shader | no-reference |  |  |
| Z | GRADIENT | shallowgradient | no-reference |  |  |
| Z | TEXT | slug | render-failed |  |  |
| Z | TEXT | textblobrandomfont | no-reference |  |  |
| Z | TEXT | variedtext | no-reference |  |  |
| Z | MESH | vertices_perspective | render-failed |  |  |
