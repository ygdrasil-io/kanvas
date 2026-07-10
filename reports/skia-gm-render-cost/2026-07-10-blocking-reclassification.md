# GM render-cost measurement

- schemaVersion: `1`

## Provenance

- backend: `webgpu`
- gitHead: `d651ca2b68fa8a2afeceb2db5878620a4d65cda7`
- jdk: `unknown`
- os: `Darwin 25.5.0`

## Results

| Name | Tag | Median (ms) | Timeouts | Errors | Reason |
| --- | --- | ---: | ---: | ---: | --- |
| `animatedbackdropblur` | FAST | 816 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|0\|animatedbackdropblur\|816`, `PASS\|0\|animatedbackdropblur\|822`, `PASS\|0\|animatedbackdropblur\|802` |
| `blur_ignore_xform_rect` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|10\|blur_ignore_xform_rect\|65`, `PASS\|10\|blur_ignore_xform_rect\|23`, `PASS\|10\|blur_ignore_xform_rect\|24` |
| `light` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|100\|light\|15`, `PASS\|100\|light\|14`, `PASS\|100\|light\|16` |
| `low` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|101\|low\|17`, `PASS\|101\|low\|16`, `PASS\|101\|low\|17` |
| `med` | FAST | 23 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|102\|med\|23`, `PASS\|102\|med\|23`, `PASS\|102\|med\|23` |
| `name` | FAST | 22 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|103\|name\|25`, `PASS\|103\|name\|17`, `PASS\|103\|name\|22` |
| `none` | FAST | 788 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|104\|none\|788`, `PASS\|104\|none\|788`, `PASS\|104\|none\|842` |
| `one` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|105\|one\|10`, `PASS\|105\|one\|10`, `PASS\|105\|one\|12` |
| `orientation` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|106\|orientation\|14`, `PASS\|106\|orientation\|13`, `PASS\|106\|orientation\|15` |
| `paint_alpha_normals_rt` | FAST | 57 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|108\|paint_alpha_normals_rt\|57`, `PASS\|108\|paint_alpha_normals_rt\|59`, `PASS\|108\|paint_alpha_normals_rt\|57` |
| `raster` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|109\|raster\|26`, `PASS\|109\|raster\|25`, `PASS\|109\|raster\|22` |
| `rect` | FAST | 812 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|110\|rect\|808`, `PASS\|110\|rect\|818`, `PASS\|110\|rect\|812` |
| `shader` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|111\|shader\|21`, `PASS\|111\|shader\|27`, `PASS\|111\|shader\|20` |
| `colorcomposefilter_alpha` | FAST | 149 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|131\|colorcomposefilter_alpha\|149`, `PASS\|131\|colorcomposefilter_alpha\|150`, `PASS\|131\|colorcomposefilter_alpha\|148` |
| `colorcomposefilter_wacky` | FAST | 94 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|132\|colorcomposefilter_wacky\|89`, `PASS\|132\|colorcomposefilter_wacky\|98`, `PASS\|132\|colorcomposefilter_wacky\|94` |
| `composeshader_bitmap` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|135\|composeshader_bitmap\|12`, `PASS\|135\|composeshader_bitmap\|10`, `PASS\|135\|composeshader_bitmap\|9` |
| `composeshader_bitmap_lm` | FAST | 794 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|136\|composeshader_bitmap_lm\|767`, `PASS\|136\|composeshader_bitmap_lm\|794`, `PASS\|136\|composeshader_bitmap_lm\|806` |
| `composeCFIF` | FAST | 31 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|139\|composeCFIF\|30`, `PASS\|139\|composeCFIF\|31`, `PASS\|139\|composeCFIF\|89` |
| `destcolor` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|146\|destcolor\|20`, `PASS\|146\|destcolor\|19`, `PASS\|146\|destcolor\|21` |
| `blurrect_compare` | BLOCKING |  | 0 | 3 | incomplete-or-error: 7117\|arraycopy: last source index 120 out of bounds for byte[100], 7161\|arraycopy: last source index 120 out of bounds for byte[100], 7251\|arraycopy: last source index 120 out of bounds for byte[100] |
|  |  |  |  |  | Raw samples: `FAIL\|15\|blurrect_compare\|7161\|arraycopy: last source index 120 out of bounds for byte[100]`, `FAIL\|15\|blurrect_compare\|7117\|arraycopy: last source index 120 out of bounds for byte[100]`, `FAIL\|15\|blurrect_compare\|7251\|arraycopy: last source index 120 out of bounds for byte[100]` |
| `graphitestart` | FAST | 95 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|161\|graphitestart\|88`, `PASS\|161\|graphitestart\|95`, `PASS\|161\|graphitestart\|105` |
| `mixerCF` | FAST | 39 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|184\|mixerCF\|39`, `PASS\|184\|mixerCF\|35`, `PASS\|184\|mixerCF\|45` |
| `modecolorfilters` | MEDIUM | 1984 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|185\|modecolorfilters\|1975`, `PASS\|185\|modecolorfilters\|1984`, `PASS\|185\|modecolorfilters\|2860` |
| `patch_image` | BLOCKING |  | 0 | 3 | incomplete-or-error: 48\|GPU render pipeline validation failed: Validation Error, 50\|GPU render pipeline validation failed: Validation Error, 54\|GPU render pipeline validation failed: Validation Error |
|  |  |  |  |  | Raw samples: `FAIL\|192\|patch_image\|48\|GPU render pipeline validation failed: Validation Error`, `FAIL\|192\|patch_image\|50\|GPU render pipeline validation failed: Validation Error`, `FAIL\|192\|patch_image\|54\|GPU render pipeline validation failed: Validation Error` |
| `runtimecolorfilter_vertices_atlas_and_patch` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|203\|runtimecolorfilter_vertices_atlas_and_patch\|25`, `PASS\|203\|runtimecolorfilter_vertices_atlas_and_patch\|27`, `PASS\|203\|runtimecolorfilter_vertices_atlas_and_patch\|29` |
| `shadow_utils_gray` | FAST | 79 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|209\|shadow_utils_gray\|28`, `PASS\|209\|shadow_utils_gray\|79`, `PASS\|209\|shadow_utils_gray\|108` |
| `shadow_utils_occl` | FAST | 26 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|210\|shadow_utils_occl\|26`, `PASS\|210\|shadow_utils_occl\|27`, `PASS\|210\|shadow_utils_occl\|24` |
| `extractalpha` | BLOCKING |  | 0 | 3 | incomplete-or-error: 751\|arraycopy: last source index 10400 out of bounds for byte[10000], 784\|arraycopy: last source index 10400 out of bounds for byte[10000], 851\|arraycopy: last source index 10400 out of bounds for byte[10000] |
|  |  |  |  |  | Raw samples: `FAIL\|217\|extractalpha\|784\|arraycopy: last source index 10400 out of bounds for byte[10000]`, `FAIL\|217\|extractalpha\|751\|arraycopy: last source index 10400 out of bounds for byte[10000]`, `FAIL\|217\|extractalpha\|851\|arraycopy: last source index 10400 out of bounds for byte[10000]` |
| `tileimagefilter` | BLOCKING |  | 0 | 3 | incomplete-or-error: 119\|arraycopy: last source index 10240 out of bounds for byte[10000], 124\|arraycopy: last source index 10240 out of bounds for byte[10000], 126\|arraycopy: last source index 10240 out of bounds for byte[10000] |
|  |  |  |  |  | Raw samples: `FAIL\|218\|tileimagefilter\|124\|arraycopy: last source index 10240 out of bounds for byte[10000]`, `FAIL\|218\|tileimagefilter\|119\|arraycopy: last source index 10240 out of bounds for byte[10000]`, `FAIL\|218\|tileimagefilter\|126\|arraycopy: last source index 10240 out of bounds for byte[10000]` |
| `transparency_check` | BLOCKING |  | 3 | 0 | two or more attempts timed out |
|  |  |  |  |  | Raw samples: `TIMEOUT\|219\|transparency_check\|10000`, `TIMEOUT\|219\|transparency_check\|10000`, `TIMEOUT\|219\|transparency_check\|10000` |
| `xfermodeimagefilter` | BLOCKING |  | 0 | 3 | incomplete-or-error: measurement-incomplete, measurement-incomplete, measurement-incomplete |
|  |  |  |  |  | Raw samples: `FAIL\|220\|xfermodeimagefilter\|measurement-incomplete`, `FAIL\|220\|xfermodeimagefilter\|measurement-incomplete`, `FAIL\|220\|xfermodeimagefilter\|measurement-incomplete` |
| `xfermodes2` | BLOCKING |  | 0 | 3 | incomplete-or-error: measurement-incomplete, measurement-incomplete, measurement-incomplete |
|  |  |  |  |  | Raw samples: `FAIL\|221\|xfermodes2\|measurement-incomplete`, `FAIL\|221\|xfermodes2\|measurement-incomplete`, `FAIL\|221\|xfermodes2\|measurement-incomplete` |
| `xfermodes3` | BLOCKING |  | 0 | 3 | incomplete-or-error: 1069\|Offscreen texture not found: offscreenTex:kanvas:scene:30x30:rgba8unorm-srgb, 1081\|Offscreen texture not found: offscreenTex:kanvas:scene:30x30:rgba8unorm-srgb, 1558\|Offscreen texture not found: offscreenTex:kanvas:scene:30x30:rgba8unorm-srgb |
|  |  |  |  |  | Raw samples: `FAIL\|222\|xfermodes3\|1558\|Offscreen texture not found: offscreenTex:kanvas:scene:30x30:rgba8unorm-srgb`, `FAIL\|222\|xfermodes3\|1069\|Offscreen texture not found: offscreenTex:kanvas:scene:30x30:rgba8unorm-srgb`, `FAIL\|222\|xfermodes3\|1081\|Offscreen texture not found: offscreenTex:kanvas:scene:30x30:rgba8unorm-srgb` |
| `xfermodes` | FAST | 279 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|223\|xfermodes\|355`, `PASS\|223\|xfermodes\|279`, `PASS\|223\|xfermodes\|265` |
| `alphagradients` | FAST | 36 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|224\|alphagradients\|43`, `PASS\|224\|alphagradients\|34`, `PASS\|224\|alphagradients\|36` |
| `analytic_gradients` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|225\|analytic_gradients\|33`, `PASS\|225\|analytic_gradients\|29`, `PASS\|225\|analytic_gradients\|21` |
| `bug6643` | FAST | 8 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|226\|bug6643\|20`, `PASS\|226\|bug6643\|8`, `PASS\|226\|bug6643\|7` |
| `clamped_gradients` | FAST | 833 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|227\|clamped_gradients\|1145`, `PASS\|227\|clamped_gradients\|814`, `PASS\|227\|clamped_gradients\|833` |
| `gradients_2pt_conical_inside_nodither` | FAST | 74 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|228\|gradients_2pt_conical_inside_nodither\|167`, `PASS\|228\|gradients_2pt_conical_inside_nodither\|74`, `PASS\|228\|gradients_2pt_conical_inside_nodither\|72` |
| `gradients_2pt_conical_outside` | FAST | 35 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|229\|gradients_2pt_conical_outside\|44`, `PASS\|229\|gradients_2pt_conical_outside\|35`, `PASS\|229\|gradients_2pt_conical_outside\|35` |
| `conicalgradients` | FAST | 34 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|230\|conicalgradients\|36`, `PASS\|230\|conicalgradients\|34`, `PASS\|230\|conicalgradients\|33` |
| `crbug_938592` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|231\|crbug_938592\|12`, `PASS\|231\|crbug_938592\|18`, `PASS\|231\|crbug_938592\|18` |
| `degenerate_gradients` | FAST | 908 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|232\|degenerate_gradients\|911`, `PASS\|232\|degenerate_gradients\|893`, `PASS\|232\|degenerate_gradients\|908` |
| `emptyshader` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|233\|emptyshader\|29`, `PASS\|233\|emptyshader\|30`, `PASS\|233\|emptyshader\|29` |
| `fillrect_gradient` | FAST | 36 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|234\|fillrect_gradient\|36`, `PASS\|234\|fillrect_gradient\|37`, `PASS\|234\|fillrect_gradient\|35` |
| `gradient_dirty_laundry` | FAST | 13 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|235\|gradient_dirty_laundry\|13`, `PASS\|235\|gradient_dirty_laundry\|15`, `PASS\|235\|gradient_dirty_laundry\|13` |
| `gradients_many` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|236\|gradients_many\|21`, `PASS\|236\|gradients_many\|22`, `PASS\|236\|gradients_many\|20` |
| `gradient_many_hard_stops` | FAST | 818 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|237\|gradient_many_hard_stops\|825`, `PASS\|237\|gradient_many_hard_stops\|797`, `PASS\|237\|gradient_many_hard_stops\|818` |
| `gradient_many_stops` | FAST | 23 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|238\|gradient_many_stops\|23`, `PASS\|238\|gradient_many_stops\|23`, `PASS\|238\|gradient_many_stops\|23` |
| `gradient_matrix` | FAST | 51 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|239\|gradient_matrix\|56`, `PASS\|239\|gradient_matrix\|51`, `PASS\|239\|gradient_matrix\|49` |
| `gradients_alpha_many_stops` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|240\|gradients_alpha_many_stops\|8`, `PASS\|240\|gradients_alpha_many_stops\|12`, `PASS\|240\|gradients_alpha_many_stops\|12` |
| `gradients_color_space` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|241\|gradients_color_space\|10`, `PASS\|241\|gradients_color_space\|9`, `PASS\|241\|gradients_color_space\|9` |
| `gradients_color_space_many_stops` | FAST | 827 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|242\|gradients_color_space_many_stops\|827`, `PASS\|242\|gradients_color_space_many_stops\|907`, `PASS\|242\|gradients_color_space_many_stops\|785` |
| `gradients_color_space_tilemode` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|243\|gradients_color_space_tilemode\|24`, `PASS\|243\|gradients_color_space_tilemode\|25`, `PASS\|243\|gradients_color_space_tilemode\|24` |
| `gradients_degenerate_2pt` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|244\|gradients_degenerate_2pt\|18`, `PASS\|244\|gradients_degenerate_2pt\|19`, `PASS\|244\|gradients_degenerate_2pt\|29` |
| `gradients_dup_color_stops` | FAST | 47 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|245\|gradients_dup_color_stops\|50`, `PASS\|245\|gradients_dup_color_stops\|47`, `PASS\|245\|gradients_dup_color_stops\|43` |
| `gradients` | FAST | 43 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|246\|gradients\|38`, `PASS\|246\|gradients\|48`, `PASS\|246\|gradients\|43` |
| `gradients_hue_method` | FAST | 846 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|247\|gradients_hue_method\|861`, `PASS\|247\|gradients_hue_method\|813`, `PASS\|247\|gradients_hue_method\|846` |
| `gradients_interesting` | FAST | 69 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|248\|gradients_interesting\|74`, `PASS\|248\|gradients_interesting\|63`, `PASS\|248\|gradients_interesting\|69` |
| `gradients_local_perspective` | FAST | 49 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|249\|gradients_local_perspective\|49`, `PASS\|249\|gradients_local_perspective\|45`, `PASS\|249\|gradients_local_perspective\|51` |
| `gradients_no_texture` | FAST | 34 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|250\|gradients_no_texture\|34`, `PASS\|250\|gradients_no_texture\|34`, `PASS\|250\|gradients_no_texture\|26` |
| `gradients_powerless_hue` | FAST | 40 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|251\|gradients_powerless_hue\|40`, `PASS\|251\|gradients_powerless_hue\|38`, `PASS\|251\|gradients_powerless_hue\|40` |
| `gradients_view_perspective` | FAST | 867 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|252\|gradients_view_perspective\|867`, `PASS\|252\|gradients_view_perspective\|862`, `PASS\|252\|gradients_view_perspective\|1012` |
| `hardstop_gradients` | FAST | 56 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|253\|hardstop_gradients\|57`, `PASS\|253\|hardstop_gradients\|52`, `PASS\|253\|hardstop_gradients\|56` |
| `hardstop_gradients_many` | FAST | 111 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|254\|hardstop_gradients_many\|111`, `PASS\|254\|hardstop_gradients_many\|111`, `PASS\|254\|hardstop_gradients_many\|106` |
| `linear_gradient` | FAST | 95 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|255\|linear_gradient\|95`, `PASS\|255\|linear_gradient\|95`, `PASS\|255\|linear_gradient\|94` |
| `linear_gradient_rt` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|256\|linear_gradient_rt\|9`, `PASS\|256\|linear_gradient_rt\|9`, `PASS\|256\|linear_gradient_rt\|8` |
| `linear_gradient_tiny` | FAST | 835 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|257\|linear_gradient_tiny\|835`, `PASS\|257\|linear_gradient_tiny\|831`, `PASS\|257\|linear_gradient_tiny\|854` |
| `persp_shaders_bw` | FAST | 42 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|258\|persp_shaders_bw\|42`, `PASS\|258\|persp_shaders_bw\|43`, `PASS\|258\|persp_shaders_bw\|42` |
| `persp_shaders_aa` | FAST | 30 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|259\|persp_shaders_aa\|32`, `PASS\|259\|persp_shaders_aa\|30`, `PASS\|259\|persp_shaders_aa\|29` |
| `radial_gradient2` | FAST | 48 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|260\|radial_gradient2\|50`, `PASS\|260\|radial_gradient2\|48`, `PASS\|260\|radial_gradient2\|47` |
| `radial_gradient3` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|261\|radial_gradient3\|13`, `PASS\|261\|radial_gradient3\|12`, `PASS\|261\|radial_gradient3\|11` |
| `radial_gradient3_nodither` | FAST | 820 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|262\|radial_gradient3_nodither\|827`, `PASS\|262\|radial_gradient3_nodither\|815`, `PASS\|262\|radial_gradient3_nodither\|820` |
| `radial_gradient4` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|263\|radial_gradient4\|21`, `PASS\|263\|radial_gradient4\|21`, `PASS\|263\|radial_gradient4\|21` |
| `radial_gradient4_nodither` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|264\|radial_gradient4_nodither\|12`, `PASS\|264\|radial_gradient4_nodither\|12`, `PASS\|264\|radial_gradient4_nodither\|13` |
| `radial_gradient` | FAST | 40 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|265\|radial_gradient\|37`, `PASS\|265\|radial_gradient\|40`, `PASS\|265\|radial_gradient\|40` |
| `radial_gradient_precision` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|266\|radial_gradient_precision\|15`, `PASS\|266\|radial_gradient_precision\|16`, `PASS\|266\|radial_gradient_precision\|15` |
| `scaled_tilemode_gradient` | FAST | 831 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|267\|scaled_tilemode_gradient\|831`, `PASS\|267\|scaled_tilemode_gradient\|805`, `PASS\|267\|scaled_tilemode_gradient\|855` |
| `shallow_gradient_conical` | FAST | 38 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|268\|shallow_gradient_conical\|38`, `PASS\|268\|shallow_gradient_conical\|36`, `PASS\|268\|shallow_gradient_conical\|38` |
| `shallow_gradient_conical_nodither` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|269\|shallow_gradient_conical_nodither\|21`, `PASS\|269\|shallow_gradient_conical_nodither\|18`, `PASS\|269\|shallow_gradient_conical_nodither\|18` |
| `shallowgradient` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|270\|shallowgradient\|11`, `PASS\|270\|shallowgradient\|16`, `PASS\|270\|shallowgradient\|17` |
| `shallow_gradient_linear` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|271\|shallow_gradient_linear\|17`, `PASS\|271\|shallow_gradient_linear\|16`, `PASS\|271\|shallow_gradient_linear\|16` |
| `shallow_gradient_linear_nodither` | FAST | 850 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|272\|shallow_gradient_linear_nodither\|850`, `PASS\|272\|shallow_gradient_linear_nodither\|850`, `PASS\|272\|shallow_gradient_linear_nodither\|814` |
| `shallow_gradient_radial` | FAST | 35 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|273\|shallow_gradient_radial\|40`, `PASS\|273\|shallow_gradient_radial\|35`, `PASS\|273\|shallow_gradient_radial\|35` |
| `shallow_gradient_radial_nodither` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|274\|shallow_gradient_radial_nodither\|35`, `PASS\|274\|shallow_gradient_radial_nodither\|18`, `PASS\|274\|shallow_gradient_radial_nodither\|19` |
| `shallow_gradient_sweep` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|275\|shallow_gradient_sweep\|21`, `PASS\|275\|shallow_gradient_sweep\|25`, `PASS\|275\|shallow_gradient_sweep\|24` |
| `shallow_gradient_sweep_nodither` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|276\|shallow_gradient_sweep_nodither\|18`, `PASS\|276\|shallow_gradient_sweep_nodither\|15`, `PASS\|276\|shallow_gradient_sweep_nodither\|15` |
| `small_color_stop` | FAST | 794 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|277\|small_color_stop\|784`, `PASS\|277\|small_color_stop\|795`, `PASS\|277\|small_color_stop\|794` |
| `sweep_tiling` | FAST | 52 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|278\|sweep_tiling\|52`, `PASS\|278\|sweep_tiling\|57`, `PASS\|278\|sweep_tiling\|52` |
| `testgradient` | FAST | 33 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|279\|testgradient\|29`, `PASS\|279\|testgradient\|33`, `PASS\|279\|testgradient\|43` |
| `all` | FAST | 213 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|281\|all\|213`, `PASS\|281\|all\|219`, `PASS\|281\|all\|204` |
| `all_variants_8888` | MEDIUM | 1073 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|282\|all_variants_8888\|1073`, `PASS\|282\|all_variants_8888\|1127`, `PASS\|282\|all_variants_8888\|1037` |
| `alpha_bitmap_is_coverage_android` | FAST | 817 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|283\|alpha_bitmap_is_coverage_android\|817`, `PASS\|283\|alpha_bitmap_is_coverage_android\|822`, `PASS\|283\|alpha_bitmap_is_coverage_android\|779` |
| `alpha_image_alpha_tint` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|284\|alpha_image_alpha_tint\|31`, `PASS\|284\|alpha_image_alpha_tint\|27`, `PASS\|284\|alpha_image_alpha_tint\|27` |
| `alpha_image_shader_rt` | FAST | 37 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|285\|alpha_image_shader_rt\|36`, `PASS\|285\|alpha_image_shader_rt\|37`, `PASS\|285\|alpha_image_shader_rt\|38` |
| `animatedGif` | FAST | 80 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|286\|animatedGif\|90`, `PASS\|286\|animatedGif\|80`, `PASS\|286\|animatedGif\|79` |
| `anisomips` | FAST | 704 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|287\|anisomips\|712`, `PASS\|287\|anisomips\|691`, `PASS\|287\|anisomips\|704` |
| `anisotropic_image_scale` | FAST | 808 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|288\|anisotropic_image_scale\|815`, `PASS\|288\|anisotropic_image_scale\|808`, `PASS\|288\|anisotropic_image_scale\|807` |
| `anisotropic_image_scale_aniso` | FAST | 92 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|289\|anisotropic_image_scale_aniso\|92`, `PASS\|289\|anisotropic_image_scale_aniso\|84`, `PASS\|289\|anisotropic_image_scale_aniso\|98` |
| `anisotropic_image_scale_linear` | FAST | 48 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|290\|anisotropic_image_scale_linear\|48`, `PASS\|290\|anisotropic_image_scale_linear\|48`, `PASS\|290\|anisotropic_image_scale_linear\|52` |
| `anisotropic_image_scale_mip` | FAST | 42 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|291\|anisotropic_image_scale_mip\|52`, `PASS\|291\|anisotropic_image_scale_mip\|41`, `PASS\|291\|anisotropic_image_scale_mip\|42` |
| `async_rescale_and_read_alpha_type` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|292\|async_rescale_and_read_alpha_type\|22`, `PASS\|292\|async_rescale_and_read_alpha_type\|28`, `PASS\|292\|async_rescale_and_read_alpha_type\|30` |
| `async_rescale_and_read_rose` | FAST | 864 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|293\|async_rescale_and_read_rose\|864`, `PASS\|293\|async_rescale_and_read_rose\|844`, `PASS\|293\|async_rescale_and_read_rose\|1236` |
| `async_rescale_and_read_no_bleed` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|294\|async_rescale_and_read_no_bleed\|16`, `PASS\|294\|async_rescale_and_read_no_bleed\|17`, `PASS\|294\|async_rescale_and_read_no_bleed\|15` |
| `attributes` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|295\|attributes\|10`, `PASS\|295\|attributes\|9`, `PASS\|295\|attributes\|11` |
| `async_yuv_no_scale` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|296\|async_yuv_no_scale\|18`, `PASS\|296\|async_yuv_no_scale\|19`, `PASS\|296\|async_yuv_no_scale\|20` |
| `bc1_transparency` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|297\|bc1_transparency\|19`, `PASS\|297\|bc1_transparency\|18`, `PASS\|297\|bc1_transparency\|102` |
| `bicubic` | FAST | 843 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|298\|bicubic\|846`, `PASS\|298\|bicubic\|834`, `PASS\|298\|bicubic\|843` |
| `bigmatrix` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|299\|bigmatrix\|13`, `PASS\|299\|bigmatrix\|14`, `PASS\|299\|bigmatrix\|14` |
| `imagefilterstext_cf` | FAST | 818 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|30\|imagefilterstext_cf\|804`, `PASS\|30\|imagefilterstext_cf\|828`, `PASS\|30\|imagefilterstext_cf\|818` |
| `bitmapfilters` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|301\|bitmapfilters\|28`, `PASS\|301\|bitmapfilters\|28`, `PASS\|301\|bitmapfilters\|29` |
| `bitmap-image-srgb-legacy` | FAST | 606 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|302\|bitmap-image-srgb-legacy\|606`, `PASS\|302\|bitmap-image-srgb-legacy\|883`, `PASS\|302\|bitmap-image-srgb-legacy\|593` |
| `bitmap_premul` | FAST | 22 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|303\|bitmap_premul\|22`, `PASS\|303\|bitmap_premul\|80`, `PASS\|303\|bitmap_premul\|21` |
| `bitmaprect_rounding` | FAST | 829 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|304\|bitmaprect_rounding\|829`, `PASS\|304\|bitmaprect_rounding\|827`, `PASS\|304\|bitmaprect_rounding\|852` |
| `bitmap_subset_shader` | FAST | 30 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|306\|bitmap_subset_shader\|30`, `PASS\|306\|bitmap_subset_shader\|30`, `PASS\|306\|bitmap_subset_shader\|32` |
| `bleed_downscale` | FAST | 36 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|307\|bleed_downscale\|36`, `PASS\|307\|bleed_downscale\|38`, `PASS\|307\|bleed_downscale\|36` |
| `bmp_filter_quality_repeat` | FAST | 38 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|308\|bmp_filter_quality_repeat\|35`, `PASS\|308\|bmp_filter_quality_repeat\|38`, `PASS\|308\|bmp_filter_quality_repeat\|39` |
| `bug6783` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|309\|bug6783\|19`, `PASS\|309\|bug6783\|15`, `PASS\|309\|bug6783\|15` |
| `imagefilterstext_if` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|31\|imagefilterstext_if\|28`, `PASS\|31\|imagefilterstext_if\|17`, `PASS\|31\|imagefilterstext_if\|18` |
| `cgimage` | FAST | 832 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|310\|cgimage\|844`, `PASS\|310\|cgimage\|827`, `PASS\|310\|cgimage\|832` |
| `child_sampling_rt` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|311\|child_sampling_rt\|12`, `PASS\|311\|child_sampling_rt\|11`, `PASS\|311\|child_sampling_rt\|13` |
| `clippedbitmapshaders` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|312\|clippedbitmapshaders\|17`, `PASS\|312\|clippedbitmapshaders\|24`, `PASS\|312\|clippedbitmapshaders\|18` |
| `color_cube_cf_rt` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|313\|color_cube_cf_rt\|15`, `PASS\|313\|color_cube_cf_rt\|45`, `PASS\|313\|color_cube_cf_rt\|15` |
| `jpg-color-cube` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|314\|jpg-color-cube\|15`, `PASS\|314\|jpg-color-cube\|18`, `PASS\|314\|jpg-color-cube\|16` |
| `colorwheel_alphatypes` | FAST | 831 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|315\|colorwheel_alphatypes\|852`, `PASS\|315\|colorwheel_alphatypes\|831`, `PASS\|315\|colorwheel_alphatypes\|812` |
| `colorwheel` | FAST | 865 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|316\|colorwheel\|865`, `PASS\|316\|colorwheel\|854`, `PASS\|316\|colorwheel\|905` |
| `colorspace2` | FAST | 74 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|317\|colorspace2\|74`, `PASS\|317\|colorspace2\|112`, `PASS\|317\|colorspace2\|73` |
| `colorspace` | FAST | 43 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|318\|colorspace\|43`, `PASS\|318\|colorspace\|42`, `PASS\|318\|colorspace\|43` |
| `compositor_quads_image` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|319\|compositor_quads_image\|14`, `PASS\|319\|compositor_quads_image\|16`, `PASS\|319\|compositor_quads_image\|16` |
| `compressed_textures` | FAST | 836 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|320\|compressed_textures\|836`, `PASS\|320\|compressed_textures\|811`, `PASS\|320\|compressed_textures\|1008` |
| `coordclampshader` | FAST | 35 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|321\|coordclampshader\|34`, `PASS\|321\|coordclampshader\|35`, `PASS\|321\|coordclampshader\|35` |
| `crbug_224618` | FAST | 46 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|323\|crbug_224618\|43`, `PASS\|323\|crbug_224618\|46`, `PASS\|323\|crbug_224618\|82` |
| `crbug_404394639` | BLOCKING |  | 0 | 3 | incomplete-or-error: 12\|Byte array is too small for 40000 padded rows of 2048 bytes, 12\|Byte array is too small for 40000 padded rows of 2048 bytes, 13\|Byte array is too small for 40000 padded rows of 2048 bytes |
|  |  |  |  |  | Raw samples: `FAIL\|324\|crbug_404394639\|12\|Byte array is too small for 40000 padded rows of 2048 bytes`, `FAIL\|324\|crbug_404394639\|12\|Byte array is too small for 40000 padded rows of 2048 bytes`, `FAIL\|324\|crbug_404394639\|13\|Byte array is too small for 40000 padded rows of 2048 bytes` |
| `deferred_shader_rt` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|325\|deferred_shader_rt\|27`, `PASS\|325\|deferred_shader_rt\|32`, `PASS\|325\|deferred_shader_rt\|43` |
| `bitmaprect_s` | FAST | 852 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|326\|bitmaprect_s\|841`, `PASS\|326\|bitmaprect_s\|852`, `PASS\|326\|bitmaprect_s\|998` |
| `bitmaprect_i` | FAST | 39 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|327\|bitmaprect_i\|39`, `PASS\|327\|bitmaprect_i\|39`, `PASS\|327\|bitmaprect_i\|39` |
| `3x3bitmaprect` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|328\|3x3bitmaprect\|15`, `PASS\|328\|3x3bitmaprect\|13`, `PASS\|328\|3x3bitmaprect\|14` |
| `drawbitmaprect4` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|329\|drawbitmaprect4\|20`, `PASS\|329\|drawbitmaprect4\|22`, `PASS\|329\|drawbitmaprect4\|21` |
| `draw_bitmap_rect_skbug4734` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|330\|draw_bitmap_rect_skbug4734\|14`, `PASS\|330\|draw_bitmap_rect_skbug4734\|13`, `PASS\|330\|draw_bitmap_rect_skbug4734\|14` |
| `drawminibitmaprect` | MEDIUM | 1255 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|331\|drawminibitmaprect\|1255`, `PASS\|331\|drawminibitmaprect\|1432`, `PASS\|331\|drawminibitmaprect\|1247` |
| `drawimage_sampling` | BLOCKING |  | 0 | 3 | incomplete-or-error: 3\|null, 3\|null, 3\|null |
|  |  |  |  |  | Raw samples: `FAIL\|332\|drawimage_sampling\|3\|null`, `FAIL\|332\|drawimage_sampling\|3\|null`, `FAIL\|332\|drawimage_sampling\|3\|null` |
| `drawimagerect_filter` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|333\|drawimagerect_filter\|15`, `PASS\|333\|drawimagerect_filter\|16`, `PASS\|333\|drawimagerect_filter\|16` |
| `encode-alpha-jpeg` | FAST | 22 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|334\|encode-alpha-jpeg\|22`, `PASS\|334\|encode-alpha-jpeg\|23`, `PASS\|334\|encode-alpha-jpeg\|22` |
| `encode-color-types-webp-lossless` | FAST | 54 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|335\|encode-color-types-webp-lossless\|51`, `PASS\|335\|encode-color-types-webp-lossless\|55`, `PASS\|335\|encode-color-types-webp-lossless\|54` |
| `encode-alpha-jpeg-opts` | FAST | 779 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|336\|encode-alpha-jpeg-opts\|779`, `PASS\|336\|encode-alpha-jpeg-opts\|803`, `PASS\|336\|encode-alpha-jpeg-opts\|768` |
| `encode-platform` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|337\|encode-platform\|28`, `PASS\|337\|encode-platform\|29`, `PASS\|337\|encode-platform\|29` |
| `encode-srgb-png` | FAST | 217 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|338\|encode-srgb-png\|228`, `PASS\|338\|encode-srgb-png\|215`, `PASS\|338\|encode-srgb-png\|217` |
| `exoticformats` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|339\|exoticformats\|27`, `PASS\|339\|exoticformats\|33`, `PASS\|339\|exoticformats\|29` |
| `matrixconvolution` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|34\|matrixconvolution\|12`, `PASS\|34\|matrixconvolution\|19`, `PASS\|34\|matrixconvolution\|20` |
| `filterbug` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|340\|filterbug\|27`, `PASS\|340\|filterbug\|28`, `PASS\|340\|filterbug\|41` |
| `filterindiabox` | FAST | 807 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|341\|filterindiabox\|807`, `PASS\|341\|filterindiabox\|812`, `PASS\|341\|filterindiabox\|784` |
| `flight_animated_image` | FAST | 128 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|342\|flight_animated_image\|128`, `PASS\|342\|flight_animated_image\|129`, `PASS\|342\|flight_animated_image\|126` |
| `flippity` | FAST | 143 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|343\|flippity\|143`, `PASS\|343\|flippity\|152`, `PASS\|343\|flippity\|140` |
| `giantbitmap_clamp_bilerp_rotate` | FAST | 23 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|345\|giantbitmap_clamp_bilerp_rotate\|23`, `PASS\|345\|giantbitmap_clamp_bilerp_rotate\|23`, `PASS\|345\|giantbitmap_clamp_bilerp_rotate\|22` |
| `giantbitmap_clamp_bilerp_scale` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|346\|giantbitmap_clamp_bilerp_scale\|15`, `PASS\|346\|giantbitmap_clamp_bilerp_scale\|15`, `PASS\|346\|giantbitmap_clamp_bilerp_scale\|16` |
| `giantbitmap_clamp_point_rotate` | FAST | 862 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|347\|giantbitmap_clamp_point_rotate\|862`, `PASS\|347\|giantbitmap_clamp_point_rotate\|831`, `PASS\|347\|giantbitmap_clamp_point_rotate\|866` |
| `giantbitmap_clamp_point_scale` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|348\|giantbitmap_clamp_point_scale\|31`, `PASS\|348\|giantbitmap_clamp_point_scale\|32`, `PASS\|348\|giantbitmap_clamp_point_scale\|32` |
| `giantbitmap_mirror_bilerp_rotate` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|349\|giantbitmap_mirror_bilerp_rotate\|24`, `PASS\|349\|giantbitmap_mirror_bilerp_rotate\|24`, `PASS\|349\|giantbitmap_mirror_bilerp_rotate\|25` |
| `matrixconvolution_big_color` | FAST | 35 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|35\|matrixconvolution_big_color\|16`, `PASS\|35\|matrixconvolution_big_color\|35`, `PASS\|35\|matrixconvolution_big_color\|38` |
| `giantbitmap_mirror_bilerp_scale` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|350\|giantbitmap_mirror_bilerp_scale\|20`, `PASS\|350\|giantbitmap_mirror_bilerp_scale\|18`, `PASS\|350\|giantbitmap_mirror_bilerp_scale\|19` |
| `giantbitmap_mirror_point_rotate` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|351\|giantbitmap_mirror_point_rotate\|25`, `PASS\|351\|giantbitmap_mirror_point_rotate\|25`, `PASS\|351\|giantbitmap_mirror_point_rotate\|25` |
| `giantbitmap_mirror_point_scale` | FAST | 861 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|352\|giantbitmap_mirror_point_scale\|850`, `PASS\|352\|giantbitmap_mirror_point_scale\|861`, `PASS\|352\|giantbitmap_mirror_point_scale\|944` |
| `giantbitmap_repeat_bilerp_rotate` | FAST | 31 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|353\|giantbitmap_repeat_bilerp_rotate\|24`, `PASS\|353\|giantbitmap_repeat_bilerp_rotate\|31`, `PASS\|353\|giantbitmap_repeat_bilerp_rotate\|32` |
| `giantbitmap_repeat_bilerp_scale` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|354\|giantbitmap_repeat_bilerp_scale\|35`, `PASS\|354\|giantbitmap_repeat_bilerp_scale\|32`, `PASS\|354\|giantbitmap_repeat_bilerp_scale\|25` |
| `giantbitmap_repeat_point_rotate` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|355\|giantbitmap_repeat_point_rotate\|19`, `PASS\|355\|giantbitmap_repeat_point_rotate\|33`, `PASS\|355\|giantbitmap_repeat_point_rotate\|19` |
| `giantbitmap_repeat_point_scale` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|356\|giantbitmap_repeat_point_scale\|25`, `PASS\|356\|giantbitmap_repeat_point_scale\|25`, `PASS\|356\|giantbitmap_repeat_point_scale\|18` |
| `grayscalejpg` | FAST | 793 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|357\|grayscalejpg\|928`, `PASS\|357\|grayscalejpg\|793`, `PASS\|357\|grayscalejpg\|772` |
| `hugebitmapshader` | FAST | 23 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|358\|hugebitmapshader\|22`, `PASS\|358\|hugebitmapshader\|25`, `PASS\|358\|hugebitmapshader\|23` |
| `image-cacherator-from-picture` | FAST | 53 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|359\|image-cacherator-from-picture\|54`, `PASS\|359\|image-cacherator-from-picture\|46`, `PASS\|359\|image-cacherator-from-picture\|53` |
| `matrixconvolution_big` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|36\|matrixconvolution_big\|14`, `PASS\|36\|matrixconvolution_big\|10`, `PASS\|36\|matrixconvolution_big\|16` |
| `imagefilter_transformed_image` | FAST | 34 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|361\|imagefilter_transformed_image\|37`, `PASS\|361\|imagefilter_transformed_image\|34`, `PASS\|361\|imagefilter_transformed_image\|34` |
| `imagefilterscropexpand` | FAST | 110 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|362\|imagefilterscropexpand\|110`, `PASS\|362\|imagefilterscropexpand\|107`, `PASS\|362\|imagefilterscropexpand\|114` |
| `imagefiltersgraph` | FAST | 840 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|363\|imagefiltersgraph\|852`, `PASS\|363\|imagefiltersgraph\|819`, `PASS\|363\|imagefiltersgraph\|840` |
| `image-surface` | FAST | 153 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|364\|image-surface\|153`, `PASS\|364\|image-surface\|149`, `PASS\|364\|image-surface\|157` |
| `imagemagnifier_bounds` | MEDIUM | 1114 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|365\|imagemagnifier_bounds\|1103`, `PASS\|365\|imagemagnifier_bounds\|1114`, `PASS\|365\|imagemagnifier_bounds\|1202` |
| `imagemagnifier_cropped` | FAST | 34 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|366\|imagemagnifier_cropped\|30`, `PASS\|366\|imagemagnifier_cropped\|34`, `PASS\|366\|imagemagnifier_cropped\|35` |
| `imagemagnifier` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|367\|imagemagnifier\|19`, `PASS\|367\|imagemagnifier\|18`, `PASS\|367\|imagemagnifier\|18` |
| `imagemakewithfilter` | FAST | 925 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|368\|imagemakewithfilter\|925`, `PASS\|368\|imagemakewithfilter\|932`, `PASS\|368\|imagemakewithfilter\|901` |
| `imagemasksubset` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|369\|imagemasksubset\|27`, `PASS\|369\|imagemasksubset\|27`, `PASS\|369\|imagemasksubset\|25` |
| `matrixconvolution_bigger` | FAST | 802 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|37\|matrixconvolution_bigger\|801`, `PASS\|37\|matrixconvolution_bigger\|802`, `PASS\|37\|matrixconvolution_bigger\|831` |
| `image_out_of_gamut` | FAST | 8 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|370\|image_out_of_gamut\|8`, `PASS\|370\|image_out_of_gamut\|8`, `PASS\|370\|image_out_of_gamut\|9` |
| `image-picture` | FAST | 42 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|371\|image-picture\|41`, `PASS\|371\|image-picture\|42`, `PASS\|371\|image-picture\|52` |
| `imageresizetiled` | FAST | 195 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|372\|imageresizetiled\|187`, `PASS\|372\|imageresizetiled\|195`, `PASS\|372\|imageresizetiled\|241` |
| `image-shader` | FAST | 860 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|373\|image-shader\|860`, `PASS\|373\|image-shader\|924`, `PASS\|373\|image-shader\|843` |
| `imagesource` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|374\|imagesource\|23`, `PASS\|374\|imagesource\|29`, `PASS\|374\|imagesource\|24` |
| `image_subset` | FAST | 30 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|375\|image_subset\|32`, `PASS\|375\|image_subset\|30`, `PASS\|375\|image_subset\|29` |
| `imageshader_tinyscale` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|376\|imageshader_tinyscale\|20`, `PASS\|376\|imageshader_tinyscale\|21`, `PASS\|376\|imageshader_tinyscale\|20` |
| `jpeg_orientation` | FAST | 33 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|377\|jpeg_orientation\|28`, `PASS\|377\|jpeg_orientation\|33`, `PASS\|377\|jpeg_orientation\|34` |
| `jpg-color-cube` | BLOCKING |  | 3 | 0 | two or more attempts timed out |
|  |  |  |  |  | Raw samples: `TIMEOUT\|378\|jpg-color-cube\|10000`, `TIMEOUT\|378\|jpg-color-cube\|10000`, `TIMEOUT\|378\|jpg-color-cube\|10000` |
| `lazytiling` | BLOCKING |  | 0 | 3 | incomplete-or-error: measurement-incomplete, measurement-incomplete, measurement-incomplete |
|  |  |  |  |  | Raw samples: `FAIL\|379\|lazytiling\|measurement-incomplete`, `FAIL\|379\|lazytiling\|measurement-incomplete`, `FAIL\|379\|lazytiling\|measurement-incomplete` |
| `matrixconvolution_biggest` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|38\|matrixconvolution_biggest\|23`, `PASS\|38\|matrixconvolution_biggest\|19`, `PASS\|38\|matrixconvolution_biggest\|19` |
| `lit_shader_linear_rt` | BLOCKING |  | 0 | 3 | incomplete-or-error: measurement-incomplete, measurement-incomplete, measurement-incomplete |
|  |  |  |  |  | Raw samples: `FAIL\|380\|lit_shader_linear_rt\|measurement-incomplete`, `FAIL\|380\|lit_shader_linear_rt\|measurement-incomplete`, `FAIL\|380\|lit_shader_linear_rt\|measurement-incomplete` |
| `localmatriximageshader_filtering` | BLOCKING |  | 0 | 3 | incomplete-or-error: measurement-incomplete, measurement-incomplete, measurement-incomplete |
|  |  |  |  |  | Raw samples: `FAIL\|381\|localmatriximageshader_filtering\|measurement-incomplete`, `FAIL\|381\|localmatriximageshader_filtering\|measurement-incomplete`, `FAIL\|381\|localmatriximageshader_filtering\|measurement-incomplete` |
| `localmatriximageshader` | BLOCKING |  | 0 | 3 | incomplete-or-error: measurement-incomplete, measurement-incomplete, measurement-incomplete |
|  |  |  |  |  | Raw samples: `FAIL\|382\|localmatriximageshader\|measurement-incomplete`, `FAIL\|382\|localmatriximageshader\|measurement-incomplete`, `FAIL\|382\|localmatriximageshader\|measurement-incomplete` |
| `localmatrixshader_nested` | FAST | 863 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|383\|localmatrixshader_nested\|1061`, `PASS\|383\|localmatrixshader_nested\|863`, `PASS\|383\|localmatrixshader_nested\|835` |
| `localmatrixshader_persp` | BLOCKING |  | 0 | 3 | incomplete-or-error: 14\|null, 14\|null, 15\|null |
|  |  |  |  |  | Raw samples: `FAIL\|384\|localmatrixshader_persp\|14\|null`, `FAIL\|384\|localmatrixshader_persp\|15\|null`, `FAIL\|384\|localmatrixshader_persp\|14\|null` |
| `local_matrix_shader_rt` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|385\|local_matrix_shader_rt\|19`, `PASS\|385\|local_matrix_shader_rt\|18`, `PASS\|385\|local_matrix_shader_rt\|19` |
| `localmatrix_order` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|386\|localmatrix_order\|20`, `PASS\|386\|localmatrix_order\|19`, `PASS\|386\|localmatrix_order\|20` |
| `makecolorspace` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|387\|makecolorspace\|29`, `PASS\|387\|makecolorspace\|28`, `PASS\|387\|makecolorspace\|29` |
| `makecolortypeandspace` | FAST | 841 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|388\|makecolortypeandspace\|841`, `PASS\|388\|makecolortypeandspace\|896`, `PASS\|388\|makecolortypeandspace\|824` |
| `makeRasterImage` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|389\|makeRasterImage\|24`, `PASS\|389\|makeRasterImage\|25`, `PASS\|389\|makeRasterImage\|27` |
| `matrixconvolution_color` | FAST | 39 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|39\|matrixconvolution_color\|39`, `PASS\|39\|matrixconvolution_color\|39`, `PASS\|39\|matrixconvolution_color\|39` |
| `mipmap_gray8_srgb` | FAST | 22 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|390\|mipmap_gray8_srgb\|19`, `PASS\|390\|mipmap_gray8_srgb\|31`, `PASS\|390\|mipmap_gray8_srgb\|22` |
| `mipmap_srgb` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|391\|mipmap_srgb\|14`, `PASS\|391\|mipmap_srgb\|14`, `PASS\|391\|mipmap_srgb\|25` |
| `mirror_tile` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|392\|mirror_tile\|18`, `PASS\|392\|mirror_tile\|16`, `PASS\|392\|mirror_tile\|24` |
| `nearest_half_pixel_image` | FAST | 825 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|393\|nearest_half_pixel_image\|825`, `PASS\|393\|nearest_half_pixel_image\|807`, `PASS\|393\|nearest_half_pixel_image\|858` |
| `new_texture_image` | FAST | 6 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|394\|new_texture_image\|6`, `PASS\|394\|new_texture_image\|6`, `PASS\|394\|new_texture_image\|6` |
| `not_native32_bitmap_config` | FAST | 7 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|396\|not_native32_bitmap_config\|7`, `PASS\|396\|not_native32_bitmap_config\|6`, `PASS\|396\|not_native32_bitmap_config\|7` |
| `null_child_rt` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|397\|null_child_rt\|29`, `PASS\|397\|null_child_rt\|28`, `PASS\|397\|null_child_rt\|27` |
| `p3` | FAST | 41 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|398\|p3\|79`, `PASS\|398\|p3\|37`, `PASS\|398\|p3\|41` |
| `persp_images` | FAST | 860 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|399\|persp_images\|896`, `PASS\|399\|persp_images\|857`, `PASS\|399\|persp_images\|860` |
| `pictureimagegenerator` | BLOCKING |  | 0 | 3 | incomplete-or-error: 125\|arraycopy: last source index 80800 out of bounds for byte[80000], 128\|arraycopy: last source index 80800 out of bounds for byte[80000], 137\|arraycopy: last source index 80800 out of bounds for byte[80000] |
|  |  |  |  |  | Raw samples: `FAIL\|400\|pictureimagegenerator\|137\|arraycopy: last source index 80800 out of bounds for byte[80000]`, `FAIL\|400\|pictureimagegenerator\|125\|arraycopy: last source index 80800 out of bounds for byte[80000]`, `FAIL\|400\|pictureimagegenerator\|128\|arraycopy: last source index 80800 out of bounds for byte[80000]` |
| `picture` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|401\|picture\|19`, `PASS\|401\|picture\|19`, `PASS\|401\|picture\|18` |
| `pictureimagefilter` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|402\|pictureimagefilter\|20`, `PASS\|402\|pictureimagefilter\|16`, `PASS\|402\|pictureimagefilter\|24` |
| `pictureimagegenerator` | BLOCKING |  | 0 | 3 | incomplete-or-error: 87\|arraycopy: last source index 80800 out of bounds for byte[80000], 89\|arraycopy: last source index 80800 out of bounds for byte[80000], 90\|arraycopy: last source index 80800 out of bounds for byte[80000] |
|  |  |  |  |  | Raw samples: `FAIL\|403\|pictureimagegenerator\|89\|arraycopy: last source index 80800 out of bounds for byte[80000]`, `FAIL\|403\|pictureimagegenerator\|87\|arraycopy: last source index 80800 out of bounds for byte[80000]`, `FAIL\|403\|pictureimagegenerator\|90\|arraycopy: last source index 80800 out of bounds for byte[80000]` |
| `pictureshader_alpha` | MEDIUM | 1165 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|404\|pictureshader_alpha\|1165`, `PASS\|404\|pictureshader_alpha\|1180`, `PASS\|404\|pictureshader_alpha\|1128` |
| `pictureshadercache` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|405\|pictureshadercache\|12`, `PASS\|405\|pictureshadercache\|13`, `PASS\|405\|pictureshadercache\|11` |
| `pictureshader` | FAST | 242 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|406\|pictureshader\|242`, `PASS\|406\|pictureshader\|246`, `PASS\|406\|pictureshader\|240` |
| `pictureshader_localwrapper` | FAST | 203 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|407\|pictureshader_localwrapper\|194`, `PASS\|407\|pictureshader_localwrapper\|213`, `PASS\|407\|pictureshader_localwrapper\|203` |
| `pictureshader_persp` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|408\|pictureshader_persp\|54`, `PASS\|408\|pictureshader_persp\|14`, `PASS\|408\|pictureshader_persp\|16` |
| `pictureshadertile` | MEDIUM | 1060 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|409\|pictureshadertile\|1125`, `PASS\|409\|pictureshadertile\|1060`, `PASS\|409\|pictureshadertile\|1044` |
| `poster_circle` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|410\|poster_circle\|14`, `PASS\|410\|poster_circle\|15`, `PASS\|410\|poster_circle\|14` |
| `raw_image_shader_normals_rt` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|411\|raw_image_shader_normals_rt\|15`, `PASS\|411\|raw_image_shader_normals_rt\|21`, `PASS\|411\|raw_image_shader_normals_rt\|20` |
| `readpixelscodec` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|412\|readpixelscodec\|32`, `PASS\|412\|readpixelscodec\|25`, `PASS\|412\|readpixelscodec\|22` |
| `readpixelspicture` | FAST | 46 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|413\|readpixelspicture\|46`, `PASS\|413\|readpixelspicture\|49`, `PASS\|413\|readpixelspicture\|46` |
| `rectangle_texture` | FAST | 831 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|414\|rectangle_texture\|851`, `PASS\|414\|rectangle_texture\|831`, `PASS\|414\|rectangle_texture\|806` |
| `reinterpretcolorspace` | FAST | 45 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|415\|reinterpretcolorspace\|45`, `PASS\|415\|reinterpretcolorspace\|44`, `PASS\|415\|reinterpretcolorspace\|46` |
| `repeated_bitmap` | MEDIUM | 1172 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|416\|repeated_bitmap\|1172`, `PASS\|416\|repeated_bitmap\|1156`, `PASS\|416\|repeated_bitmap\|1214` |
| `repeated_bitmap_jpg` | MEDIUM | 1071 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|417\|repeated_bitmap_jpg\|1031`, `PASS\|417\|repeated_bitmap_jpg\|1071`, `PASS\|417\|repeated_bitmap_jpg\|1183` |
| `scale-pixels` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|418\|scale-pixels\|16`, `PASS\|418\|scale-pixels\|14`, `PASS\|418\|scale-pixels\|16` |
| `scaled_tilemode_bitmap` | FAST | 838 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|419\|scaled_tilemode_bitmap\|871`, `PASS\|419\|scaled_tilemode_bitmap\|838`, `PASS\|419\|scaled_tilemode_bitmap\|821` |
| `scaled_tilemodes` | FAST | 31 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|420\|scaled_tilemodes\|33`, `PASS\|420\|scaled_tilemodes\|31`, `PASS\|420\|scaled_tilemodes\|31` |
| `scaled_tilemodes_npot` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|421\|scaled_tilemodes_npot\|21`, `PASS\|421\|scaled_tilemodes_npot\|21`, `PASS\|421\|scaled_tilemodes_npot\|21` |
| `scaledtiling` | FAST | 59 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|422\|scaledtiling\|58`, `PASS\|422\|scaledtiling\|59`, `PASS\|422\|scaledtiling\|60` |
| `scalepixels_unpremul` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|423\|scalepixels_unpremul\|29`, `PASS\|423\|scalepixels_unpremul\|27`, `PASS\|423\|scalepixels_unpremul\|22` |
| `shaderpath` | FAST | 889 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|424\|shaderpath\|953`, `PASS\|424\|shaderpath\|889`, `PASS\|424\|shaderpath\|884` |
| `showmiplevels_explicit` | FAST | 38 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|425\|showmiplevels_explicit\|38`, `PASS\|425\|showmiplevels_explicit\|37`, `PASS\|425\|showmiplevels_explicit\|40` |
| `skbug_8664` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|426\|skbug_8664\|33`, `PASS\|426\|skbug_8664\|27`, `PASS\|426\|skbug_8664\|28` |
| `skbug_9819` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|427\|skbug_9819\|16`, `PASS\|427\|skbug_9819\|15`, `PASS\|427\|skbug_9819\|15` |
| `srcrectconstraint` | FAST | 6 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|428\|srcrectconstraint\|6`, `PASS\|428\|srcrectconstraint\|5`, `PASS\|428\|srcrectconstraint\|6` |
| `stoplight_animated_image` | FAST | 928 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|429\|stoplight_animated_image\|928`, `PASS\|429\|stoplight_animated_image\|909`, `PASS\|429\|stoplight_animated_image\|1039` |
| `surface_underdraw` | FAST | 36 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|430\|surface_underdraw\|40`, `PASS\|430\|surface_underdraw\|36`, `PASS\|430\|surface_underdraw\|36` |
| `texelsubset` | FAST | 122 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|431\|texelsubset\|120`, `PASS\|431\|texelsubset\|122`, `PASS\|431\|texelsubset\|123` |
| `texture` | FAST | 76 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|432\|texture\|78`, `PASS\|432\|texture\|75`, `PASS\|432\|texture\|76` |
| `textureimage_and_shader` | FAST | 7 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|433\|textureimage_and_shader\|6`, `PASS\|433\|textureimage_and_shader\|7`, `PASS\|433\|textureimage_and_shader\|7` |
| `tiled_picture_shader` | FAST | 860 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|434\|tiled_picture_shader\|860`, `PASS\|434\|tiled_picture_shader\|942`, `PASS\|434\|tiled_picture_shader\|846` |
| `tiledscaledbitmap` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|435\|tiledscaledbitmap\|70`, `PASS\|435\|tiledscaledbitmap\|32`, `PASS\|435\|tiledscaledbitmap\|31` |
| `tilemode_decal` | FAST | 73 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|436\|tilemode_decal\|70`, `PASS\|436\|tilemode_decal\|73`, `PASS\|436\|tilemode_decal\|76` |
| `tilemodes_alpha` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|437\|tilemodes_alpha\|24`, `PASS\|437\|tilemodes_alpha\|17`, `PASS\|437\|tilemodes_alpha\|25` |
| `tilemodes` | FAST | 77 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|438\|tilemodes\|86`, `PASS\|438\|tilemodes\|77`, `PASS\|438\|tilemodes\|70` |
| `tilemode_bitmap` | FAST | 873 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|439\|tilemode_bitmap\|873`, `PASS\|439\|tilemode_bitmap\|876`, `PASS\|439\|tilemode_bitmap\|852` |
| `tilemode_gradient` | FAST | 43 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|440\|tilemode_gradient\|44`, `PASS\|440\|tilemode_gradient\|43`, `PASS\|440\|tilemode_gradient\|42` |
| `tiling` | FAST | 136 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|441\|tiling\|137`, `PASS\|441\|tiling\|136`, `PASS\|441\|tiling\|132` |
| `tinybitmap` | FAST | 5 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|442\|tinybitmap\|5`, `PASS\|442\|tinybitmap\|6`, `PASS\|442\|tinybitmap\|5` |
| `unpremul` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|443\|unpremul\|15`, `PASS\|443\|unpremul\|16`, `PASS\|443\|unpremul\|16` |
| `verylargebitmap` | BLOCKING |  | 0 | 3 | incomplete-or-error: 780\|paddedBytesPerRow 32768 must be >= tight row size 135168, 803\|paddedBytesPerRow 32768 must be >= tight row size 135168, 807\|paddedBytesPerRow 32768 must be >= tight row size 135168 |
|  |  |  |  |  | Raw samples: `FAIL\|444\|verylargebitmap\|780\|paddedBytesPerRow 32768 must be >= tight row size 135168`, `FAIL\|444\|verylargebitmap\|807\|paddedBytesPerRow 32768 must be >= tight row size 135168`, `FAIL\|444\|verylargebitmap\|803\|paddedBytesPerRow 32768 must be >= tight row size 135168` |
| `verylarge_picture_image` | BLOCKING |  | 0 | 3 | incomplete-or-error: 13\|paddedBytesPerRow 32768 must be >= tight row size 135168, 14\|paddedBytesPerRow 32768 must be >= tight row size 135168, 14\|paddedBytesPerRow 32768 must be >= tight row size 135168 |
|  |  |  |  |  | Raw samples: `FAIL\|445\|verylarge_picture_image\|13\|paddedBytesPerRow 32768 must be >= tight row size 135168`, `FAIL\|445\|verylarge_picture_image\|14\|paddedBytesPerRow 32768 must be >= tight row size 135168`, `FAIL\|445\|verylarge_picture_image\|14\|paddedBytesPerRow 32768 must be >= tight row size 135168` |
| `videodecoder` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|446\|videodecoder\|24`, `PASS\|446\|videodecoder\|27`, `PASS\|446\|videodecoder\|23` |
| `wacky_yuv_formats` | FAST | 58 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|447\|wacky_yuv_formats\|58`, `PASS\|447\|wacky_yuv_formats\|61`, `PASS\|447\|wacky_yuv_formats\|58` |
| `ycbcrimage` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|448\|ycbcrimage\|8`, `PASS\|448\|ycbcrimage\|10`, `PASS\|448\|ycbcrimage\|9` |
| `yuv420_odd_dim` | FAST | 764 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|449\|yuv420_odd_dim\|764`, `PASS\|449\|yuv420_odd_dim\|817`, `PASS\|449\|yuv420_odd_dim\|763` |
| `yuv420_odd_dim_repeat` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|450\|yuv420_odd_dim_repeat\|29`, `PASS\|450\|yuv420_odd_dim_repeat\|28`, `PASS\|450\|yuv420_odd_dim_repeat\|28` |
| `custommesh_cs` | FAST | 34 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|451\|custommesh_cs\|73`, `PASS\|451\|custommesh_cs\|34`, `PASS\|451\|custommesh_cs\|34` |
| `custommesh_cs_uniforms` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|452\|custommesh_cs_uniforms\|28`, `PASS\|452\|custommesh_cs_uniforms\|32`, `PASS\|452\|custommesh_cs_uniforms\|28` |
| `custommesh` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|453\|custommesh\|36`, `PASS\|453\|custommesh\|27`, `PASS\|453\|custommesh\|32` |
| `custommesh_uniforms` | FAST | 806 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|454\|custommesh_uniforms\|835`, `PASS\|454\|custommesh_uniforms\|799`, `PASS\|454\|custommesh_uniforms\|806` |
| `mesh_updates` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|455\|mesh_updates\|21`, `PASS\|455\|mesh_updates\|18`, `PASS\|455\|mesh_updates\|18` |
| `mesh_with_effects` | BLOCKING |  | 0 | 3 | incomplete-or-error: 30\|GPU render pipeline validation failed: Validation Error, 32\|GPU render pipeline validation failed: Validation Error, 33\|GPU render pipeline validation failed: Validation Error |
|  |  |  |  |  | Raw samples: `FAIL\|456\|mesh_with_effects\|30\|GPU render pipeline validation failed: Validation Error`, `FAIL\|456\|mesh_with_effects\|33\|GPU render pipeline validation failed: Validation Error`, `FAIL\|456\|mesh_with_effects\|32\|GPU render pipeline validation failed: Validation Error` |
| `mesh_with_image` | BLOCKING |  | 0 | 3 | incomplete-or-error: 11\|GPU render pipeline validation failed: Validation Error, 8\|GPU render pipeline validation failed: Validation Error, 9\|GPU render pipeline validation failed: Validation Error |
|  |  |  |  |  | Raw samples: `FAIL\|457\|mesh_with_image\|8\|GPU render pipeline validation failed: Validation Error`, `FAIL\|457\|mesh_with_image\|9\|GPU render pipeline validation failed: Validation Error`, `FAIL\|457\|mesh_with_image\|11\|GPU render pipeline validation failed: Validation Error` |
| `mesh_with_paint_color` | BLOCKING |  | 0 | 3 | incomplete-or-error: 7\|GPU render pipeline validation failed: Validation Error, 8\|GPU render pipeline validation failed: Validation Error, 8\|GPU render pipeline validation failed: Validation Error |
|  |  |  |  |  | Raw samples: `FAIL\|458\|mesh_with_paint_color\|8\|GPU render pipeline validation failed: Validation Error`, `FAIL\|458\|mesh_with_paint_color\|8\|GPU render pipeline validation failed: Validation Error`, `FAIL\|458\|mesh_with_paint_color\|7\|GPU render pipeline validation failed: Validation Error` |
| `mesh_with_paint_image` | BLOCKING |  | 0 | 3 | incomplete-or-error: 783\|GPU render pipeline validation failed: Validation Error, 789\|GPU render pipeline validation failed: Validation Error, 805\|GPU render pipeline validation failed: Validation Error |
|  |  |  |  |  | Raw samples: `FAIL\|459\|mesh_with_paint_image\|789\|GPU render pipeline validation failed: Validation Error`, `FAIL\|459\|mesh_with_paint_image\|805\|GPU render pipeline validation failed: Validation Error`, `FAIL\|459\|mesh_with_paint_image\|783\|GPU render pipeline validation failed: Validation Error` |
| `bug339297_as_clip` | BLOCKING |  | 0 | 3 | incomplete-or-error: 18\|Path flattened to 131073 vertices, exceeds budget of 131072, 22\|Path flattened to 131073 vertices, exceeds budget of 131072, 22\|Path flattened to 131073 vertices, exceeds budget of 131072 |
|  |  |  |  |  | Raw samples: `FAIL\|46\|bug339297_as_clip\|18\|Path flattened to 131073 vertices, exceeds budget of 131072`, `FAIL\|46\|bug339297_as_clip\|22\|Path flattened to 131073 vertices, exceeds budget of 131072`, `FAIL\|46\|bug339297_as_clip\|22\|Path flattened to 131073 vertices, exceeds budget of 131072` |
| `mesh_zero_init` | BLOCKING |  | 0 | 3 | incomplete-or-error: 0\|STUB.MESH.GPU_ZERO_INIT, 0\|STUB.MESH.GPU_ZERO_INIT, 0\|STUB.MESH.GPU_ZERO_INIT |
|  |  |  |  |  | Raw samples: `FAIL\|460\|mesh_zero_init\|0\|STUB.MESH.GPU_ZERO_INIT`, `FAIL\|460\|mesh_zero_init\|0\|STUB.MESH.GPU_ZERO_INIT`, `FAIL\|460\|mesh_zero_init\|0\|STUB.MESH.GPU_ZERO_INIT` |
| `picture_mesh` | FAST | 59 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|461\|picture_mesh\|107`, `PASS\|461\|picture_mesh\|58`, `PASS\|461\|picture_mesh\|59` |
| `skbug_13047` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|462\|skbug_13047\|9`, `PASS\|462\|skbug_13047\|9`, `PASS\|462\|skbug_13047\|9` |
| `vertices_batching` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|463\|vertices_batching\|26`, `PASS\|463\|vertices_batching\|34`, `PASS\|463\|vertices_batching\|28` |
| `vertices_collapsed` | FAST | 802 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|464\|vertices_collapsed\|802`, `PASS\|464\|vertices_collapsed\|818`, `PASS\|464\|vertices_collapsed\|786` |
| `vertices` | FAST | 233 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|465\|vertices\|280`, `PASS\|465\|vertices\|224`, `PASS\|465\|vertices\|233` |
| `vertices_perspective` | BLOCKING |  | 0 | 3 | incomplete-or-error: 17\|GPU render pipeline validation failed: Validation Error, 17\|GPU render pipeline validation failed: Validation Error, 18\|GPU render pipeline validation failed: Validation Error |
|  |  |  |  |  | Raw samples: `FAIL\|466\|vertices_perspective\|17\|GPU render pipeline validation failed: Validation Error`, `FAIL\|466\|vertices_perspective\|17\|GPU render pipeline validation failed: Validation Error`, `FAIL\|466\|vertices_perspective\|18\|GPU render pipeline validation failed: Validation Error` |
| `aa_rect_effect` | BLOCKING |  | 0 | 3 | incomplete-or-error: 0\|An operation is not implemented: STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only), 0\|An operation is not implemented: STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only), 0\|An operation is not implemented: STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only) |
|  |  |  |  |  | Raw samples: `FAIL\|467\|aa_rect_effect\|0\|An operation is not implemented: STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only)`, `FAIL\|467\|aa_rect_effect\|0\|An operation is not implemented: STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only)`, `FAIL\|467\|aa_rect_effect\|0\|An operation is not implemented: STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only)` |
| `bezier_conic_effects` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|468\|bezier_conic_effects\|28`, `PASS\|468\|bezier_conic_effects\|29`, `PASS\|468\|bezier_conic_effects\|29` |
| `bezier_quad_effects` | FAST | 841 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|469\|bezier_quad_effects\|841`, `PASS\|469\|bezier_quad_effects\|858`, `PASS\|469\|bezier_quad_effects\|840` |
| `bug41422450` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|470\|bug41422450\|16`, `PASS\|470\|bug41422450\|15`, `PASS\|470\|bug41422450\|17` |
| `ctmpatheffect` | FAST | 33 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|471\|ctmpatheffect\|33`, `PASS\|471\|ctmpatheffect\|32`, `PASS\|471\|ctmpatheffect\|35` |
| `big_rrect_circle_aa_effect` | FAST | 33 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|472\|big_rrect_circle_aa_effect\|33`, `PASS\|472\|big_rrect_circle_aa_effect\|34`, `PASS\|472\|big_rrect_circle_aa_effect\|29` |
| `circle` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|473\|circle\|16`, `PASS\|473\|circle\|16`, `PASS\|473\|circle\|16` |
| `circle_sizes` | FAST | 812 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|474\|circle_sizes\|812`, `PASS\|474\|circle_sizes\|839`, `PASS\|474\|circle_sizes\|808` |
| `circular_arcs` | FAST | 36 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|475\|circular_arcs\|32`, `PASS\|475\|circular_arcs\|37`, `PASS\|475\|circular_arcs\|36` |
| `circular_corner` | FAST | 26 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|476\|circular_corner\|26`, `PASS\|476\|circular_corner\|29`, `PASS\|476\|circular_corner\|23` |
| `clockwise` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|477\|clockwise\|16`, `PASS\|477\|clockwise\|17`, `PASS\|477\|clockwise\|22` |
| `concavepaths` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|478\|concavepaths\|32`, `PASS\|478\|concavepaths\|32`, `PASS\|478\|concavepaths\|29` |
| `conicpaths` | MEDIUM | 1019 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|479\|conicpaths\|1019`, `PASS\|479\|conicpaths\|1117`, `PASS\|479\|conicpaths\|987` |
| `convex_lineonly_paths` | FAST | 99 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|480\|convex_lineonly_paths\|99`, `PASS\|480\|convex_lineonly_paths\|102`, `PASS\|480\|convex_lineonly_paths\|90` |
| `convex_lineonly_paths_stroke_and_fill` | FAST | 68 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|481\|convex_lineonly_paths_stroke_and_fill\|68`, `PASS\|481\|convex_lineonly_paths_stroke_and_fill\|67`, `PASS\|481\|convex_lineonly_paths_stroke_and_fill\|68` |
| `convexpaths` | FAST | 59 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|482\|convexpaths\|54`, `PASS\|482\|convexpaths\|66`, `PASS\|482\|convexpaths\|59` |
| `convex_poly_effect` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|483\|convex_poly_effect\|19`, `PASS\|483\|convex_poly_effect\|31`, `PASS\|483\|convex_poly_effect\|25` |
| `convex-polygon-inset` | BLOCKING |  | 0 | 3 | incomplete-or-error: 1\|An operation is not implemented: STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility), 1\|An operation is not implemented: STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility), 1\|An operation is not implemented: STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility) |
|  |  |  |  |  | Raw samples: `FAIL\|484\|convex-polygon-inset\|1\|An operation is not implemented: STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility)`, `FAIL\|484\|convex-polygon-inset\|1\|An operation is not implemented: STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility)`, `FAIL\|484\|convex-polygon-inset\|1\|An operation is not implemented: STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility)` |
| `crbug_640176` | FAST | 803 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|485\|crbug_640176\|803`, `PASS\|485\|crbug_640176\|822`, `PASS\|485\|crbug_640176\|795` |
| `crbug_691386` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|486\|crbug_691386\|12`, `PASS\|486\|crbug_691386\|13`, `PASS\|486\|crbug_691386\|12` |
| `cubicclosepath` | FAST | 135 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|487\|cubicclosepath\|132`, `PASS\|487\|cubicclosepath\|135`, `PASS\|487\|cubicclosepath\|137` |
| `cubicpath` | FAST | 66 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|488\|cubicpath\|66`, `PASS\|488\|cubicpath\|67`, `PASS\|488\|cubicpath\|66` |
| `cubicpath_shader` | FAST | 833 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|489\|cubicpath_shader\|812`, `PASS\|489\|cubicpath_shader\|833`, `PASS\|489\|cubicpath_shader\|852` |
| `dashcircle` | FAST | 164 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|490\|dashcircle\|165`, `PASS\|490\|dashcircle\|164`, `PASS\|490\|dashcircle\|162` |
| `dashtextcaps` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|491\|dashtextcaps\|12`, `PASS\|491\|dashtextcaps\|11`, `PASS\|491\|dashtextcaps\|18` |
| `dashing5_aa` | FAST | 144 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|492\|dashing5_aa\|148`, `PASS\|492\|dashing5_aa\|143`, `PASS\|492\|dashing5_aa\|144` |
| `dashing` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|493\|dashing\|23`, `PASS\|493\|dashing\|20`, `PASS\|493\|dashing\|20` |
| `drawlines_with_local_matrix` | FAST | 818 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|494\|drawlines_with_local_matrix\|821`, `PASS\|494\|drawlines_with_local_matrix\|808`, `PASS\|494\|drawlines_with_local_matrix\|818` |
| `drawregion` | MEDIUM | 2952 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|495\|drawregion\|2943`, `PASS\|495\|drawregion\|2952`, `PASS\|495\|drawregion\|3093` |
| `drawregionmodes` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|496\|drawregionmodes\|20`, `PASS\|496\|drawregionmodes\|20`, `PASS\|496\|drawregionmodes\|21` |
| `ellipse` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|497\|ellipse\|13`, `PASS\|497\|ellipse\|14`, `PASS\|497\|ellipse\|14` |
| `elliptical_corner` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|498\|elliptical_corner\|16`, `PASS\|498\|elliptical_corner\|16`, `PASS\|498\|elliptical_corner\|16` |
| `fancy_gradients` | BLOCKING |  | 0 | 3 | incomplete-or-error: 1\|An operation is not implemented: STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition, 1\|An operation is not implemented: STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition, 1\|An operation is not implemented: STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition |
|  |  |  |  |  | Raw samples: `FAIL\|499\|fancy_gradients\|1\|An operation is not implemented: STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition`, `FAIL\|499\|fancy_gradients\|1\|An operation is not implemented: STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition`, `FAIL\|499\|fancy_gradients\|1\|An operation is not implemented: STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition` |
| `fatpathfill` | FAST | 933 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|500\|fatpathfill\|989`, `PASS\|500\|fatpathfill\|933`, `PASS\|500\|fatpathfill\|929` |
| `filltypespersp` | FAST | 66 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|501\|filltypespersp\|67`, `PASS\|501\|filltypespersp\|66`, `PASS\|501\|filltypespersp\|64` |
| `inner_join_geometry` | FAST | 44 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|502\|inner_join_geometry\|44`, `PASS\|502\|inner_join_geometry\|44`, `PASS\|502\|inner_join_geometry\|46` |
| `lattice2` | BLOCKING |  | 0 | 3 | incomplete-or-error: 0\|An operation is not implemented: STUB.LATTICE2 — requires drawImageLattice API, 0\|An operation is not implemented: STUB.LATTICE2 — requires drawImageLattice API, 0\|An operation is not implemented: STUB.LATTICE2 — requires drawImageLattice API |
|  |  |  |  |  | Raw samples: `FAIL\|503\|lattice2\|0\|An operation is not implemented: STUB.LATTICE2 — requires drawImageLattice API`, `FAIL\|503\|lattice2\|0\|An operation is not implemented: STUB.LATTICE2 — requires drawImageLattice API`, `FAIL\|503\|lattice2\|0\|An operation is not implemented: STUB.LATTICE2 — requires drawImageLattice API` |
| `lineclosepath` | FAST | 989 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|504\|lineclosepath\|1013`, `PASS\|504\|lineclosepath\|968`, `PASS\|504\|lineclosepath\|989` |
| `linepath` | FAST | 43 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|505\|linepath\|43`, `PASS\|505\|linepath\|43`, `PASS\|505\|linepath\|37` |
| `longrect_dash` | MEDIUM | 4332 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|506\|longrect_dash\|4471`, `PASS\|506\|longrect_dash\|4332`, `PASS\|506\|longrect_dash\|4193` |
| `macaatest` | BLOCKING |  | 0 | 3 | incomplete-or-error: 0\|An operation is not implemented: STUB.MACAATEST — requires macOS CoreText APIs (platform-specific), 0\|An operation is not implemented: STUB.MACAATEST — requires macOS CoreText APIs (platform-specific), 0\|An operation is not implemented: STUB.MACAATEST — requires macOS CoreText APIs (platform-specific) |
|  |  |  |  |  | Raw samples: `FAIL\|507\|macaatest\|0\|An operation is not implemented: STUB.MACAATEST — requires macOS CoreText APIs (platform-specific)`, `FAIL\|507\|macaatest\|0\|An operation is not implemented: STUB.MACAATEST — requires macOS CoreText APIs (platform-specific)`, `FAIL\|507\|macaatest\|0\|An operation is not implemented: STUB.MACAATEST — requires macOS CoreText APIs (platform-specific)` |
| `mandoline` | FAST | 62 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|508\|mandoline\|60`, `PASS\|508\|mandoline\|62`, `PASS\|508\|mandoline\|63` |
| `manycircles` | SLOW | 7065 | 0 | 0 | median below 10000 ms |
|  |  |  |  |  | Raw samples: `PASS\|509\|manycircles\|7214`, `PASS\|509\|manycircles\|6963`, `PASS\|509\|manycircles\|7065` |
| `manypathatlases` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|510\|manypathatlases\|16`, `PASS\|510\|manypathatlases\|17`, `PASS\|510\|manypathatlases\|17` |
| `nested` | FAST | 257 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|511\|nested\|260`, `PASS\|511\|nested\|256`, `PASS\|511\|nested\|257` |
| `nested` | FAST | 179 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|512\|nested\|179`, `PASS\|512\|nested\|176`, `PASS\|512\|nested\|184` |
| `nonclosedpaths` | FAST | 127 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|513\|nonclosedpaths\|132`, `PASS\|513\|nonclosedpaths\|127`, `PASS\|513\|nonclosedpaths\|126` |
| `OverStroke` | FAST | 992 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|514\|OverStroke\|1023`, `PASS\|514\|OverStroke\|992`, `PASS\|514\|OverStroke\|981` |
| `parsedpaths` | FAST | 113 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|515\|parsedpaths\|118`, `PASS\|515\|parsedpaths\|113`, `PASS\|515\|parsedpaths\|111` |
| `path_huge_aa` | BLOCKING |  | 0 | 3 | incomplete-or-error: 15\|paddedBytesPerRow 32768 must be >= tight row size 409600, 15\|paddedBytesPerRow 32768 must be >= tight row size 409600, 20\|paddedBytesPerRow 32768 must be >= tight row size 409600 |
|  |  |  |  |  | Raw samples: `FAIL\|516\|path_huge_aa\|15\|paddedBytesPerRow 32768 must be >= tight row size 409600`, `FAIL\|516\|path_huge_aa\|20\|paddedBytesPerRow 32768 must be >= tight row size 409600`, `FAIL\|516\|path_huge_aa\|15\|paddedBytesPerRow 32768 must be >= tight row size 409600` |
| `path_huge_aa_manual` | BLOCKING |  | 0 | 3 | incomplete-or-error: 10\|paddedBytesPerRow 32768 must be >= tight row size 409600, 10\|paddedBytesPerRow 32768 must be >= tight row size 409600, 10\|paddedBytesPerRow 32768 must be >= tight row size 409600 |
|  |  |  |  |  | Raw samples: `FAIL\|517\|path_huge_aa_manual\|10\|paddedBytesPerRow 32768 must be >= tight row size 409600`, `FAIL\|517\|path_huge_aa_manual\|10\|paddedBytesPerRow 32768 must be >= tight row size 409600`, `FAIL\|517\|path_huge_aa_manual\|10\|paddedBytesPerRow 32768 must be >= tight row size 409600` |
| `path_mask_cache` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|518\|path_mask_cache\|23`, `PASS\|518\|path_mask_cache\|21`, `PASS\|518\|path_mask_cache\|20` |
| `PathMeasure_explosion` | FAST | 818 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|519\|PathMeasure_explosion\|818`, `PASS\|519\|PathMeasure_explosion\|876`, `PASS\|519\|PathMeasure_explosion\|817` |
| `pathops_blend` | BLOCKING |  | 0 | 3 | incomplete-or-error: 0\|An operation is not implemented: STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing, 0\|An operation is not implemented: STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing, 0\|An operation is not implemented: STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing |
|  |  |  |  |  | Raw samples: `FAIL\|520\|pathops_blend\|0\|An operation is not implemented: STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing`, `FAIL\|520\|pathops_blend\|0\|An operation is not implemented: STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing`, `FAIL\|520\|pathops_blend\|0\|An operation is not implemented: STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing` |
| `pathopsinverse` | BLOCKING |  | 0 | 3 | incomplete-or-error: 0\|An operation is not implemented: STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types, 0\|An operation is not implemented: STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types, 0\|An operation is not implemented: STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types |
|  |  |  |  |  | Raw samples: `FAIL\|521\|pathopsinverse\|0\|An operation is not implemented: STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types`, `FAIL\|521\|pathopsinverse\|0\|An operation is not implemented: STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types`, `FAIL\|521\|pathopsinverse\|0\|An operation is not implemented: STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types` |
| `pathops_skbug_10155` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|522\|pathops_skbug_10155\|18`, `PASS\|522\|pathops_skbug_10155\|19`, `PASS\|522\|pathops_skbug_10155\|17` |
| `path-reverse` | FAST | 73 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|523\|path-reverse\|73`, `PASS\|523\|path-reverse\|76`, `PASS\|523\|path-reverse\|70` |
| `path_stroke_clip_crbug1070835` | FAST | 801 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|524\|path_stroke_clip_crbug1070835\|791`, `PASS\|524\|path_stroke_clip_crbug1070835\|815`, `PASS\|524\|path_stroke_clip_crbug1070835\|801` |
| `points` | FAST | 213 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|525\|points\|220`, `PASS\|525\|points\|213`, `PASS\|525\|points\|210` |
| `poly2poly` | FAST | 54 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|526\|poly2poly\|54`, `PASS\|526\|poly2poly\|54`, `PASS\|526\|poly2poly\|53` |
| `polygons` | FAST | 75 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|527\|polygons\|73`, `PASS\|527\|polygons\|75`, `PASS\|527\|polygons\|77` |
| `preservefillrule_big` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|528\|preservefillrule_big\|11`, `PASS\|528\|preservefillrule_big\|10`, `PASS\|528\|preservefillrule_big\|10` |
| `preservefillrule` | FAST | 815 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|529\|preservefillrule\|836`, `PASS\|529\|preservefillrule\|804`, `PASS\|529\|preservefillrule\|815` |
| `preservefillrule_little` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|530\|preservefillrule_little\|15`, `PASS\|530\|preservefillrule_little\|15`, `PASS\|530\|preservefillrule_little\|16` |
| `quadclosepath` | FAST | 107 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|531\|quadclosepath\|105`, `PASS\|531\|quadclosepath\|107`, `PASS\|531\|quadclosepath\|111` |
| `quadpath` | FAST | 59 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|532\|quadpath\|59`, `PASS\|532\|quadpath\|59`, `PASS\|532\|quadpath\|63` |
| `roundrects` | FAST | 94 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|533\|roundrects\|94`, `PASS\|533\|roundrects\|91`, `PASS\|533\|roundrects\|94` |
| `shadow_utils_directional` | FAST | 841 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|534\|shadow_utils_directional\|802`, `PASS\|534\|shadow_utils_directional\|841`, `PASS\|534\|shadow_utils_directional\|856` |
| `sharedcorners` | FAST | 67 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|535\|sharedcorners\|65`, `PASS\|535\|sharedcorners\|68`, `PASS\|535\|sharedcorners\|67` |
| `simpleshapes_bw` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|536\|simpleshapes_bw\|24`, `PASS\|536\|simpleshapes_bw\|25`, `PASS\|536\|simpleshapes_bw\|25` |
| `simpleshapes` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|537\|simpleshapes\|26`, `PASS\|537\|simpleshapes\|43`, `PASS\|537\|simpleshapes\|27` |
| `stlouisarch` | FAST | 49 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|538\|stlouisarch\|48`, `PASS\|538\|stlouisarch\|53`, `PASS\|538\|stlouisarch\|49` |
| `stroke_rect_shader` | FAST | 915 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|539\|stroke_rect_shader\|968`, `PASS\|539\|stroke_rect_shader\|830`, `PASS\|539\|stroke_rect_shader\|915` |
| `clipsuperrrect` | FAST | 26 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|54\|clipsuperrrect\|29`, `PASS\|54\|clipsuperrrect\|25`, `PASS\|54\|clipsuperrrect\|26` |
| `strokedline_caps` | FAST | 51 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|540\|strokedline_caps\|51`, `PASS\|540\|strokedline_caps\|51`, `PASS\|540\|strokedline_caps\|55` |
| `strokes3` | FAST | 302 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|541\|strokes3\|297`, `PASS\|541\|strokes3\|302`, `PASS\|541\|strokes3\|309` |
| `zero_control_stroke` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|542\|zero_control_stroke\|16`, `PASS\|542\|zero_control_stroke\|15`, `PASS\|542\|zero_control_stroke\|16` |
| `strokes_round` | FAST | 150 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|543\|strokes_round\|150`, `PASS\|543\|strokes_round\|146`, `PASS\|543\|strokes_round\|162` |
| `teenyStrokes` | FAST | 823 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|544\|teenyStrokes\|823`, `PASS\|544\|teenyStrokes\|813`, `PASS\|544\|teenyStrokes\|857` |
| `thin_aa_dash_lines` | FAST | 170 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|545\|thin_aa_dash_lines\|170`, `PASS\|545\|thin_aa_dash_lines\|181`, `PASS\|545\|thin_aa_dash_lines\|170` |
| `thinconcavepaths` | FAST | 57 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|546\|thinconcavepaths\|57`, `PASS\|546\|thinconcavepaths\|57`, `PASS\|546\|thinconcavepaths\|59` |
| `thinrects` | FAST | 146 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|547\|thinrects\|143`, `PASS\|547\|thinrects\|146`, `PASS\|547\|thinrects\|146` |
| `thinroundrects` | FAST | 110 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|548\|thinroundrects\|106`, `PASS\|548\|thinroundrects\|110`, `PASS\|548\|thinroundrects\|113` |
| `thinstrokedrects` | FAST | 941 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|549\|thinstrokedrects\|940`, `PASS\|549\|thinstrokedrects\|947`, `PASS\|549\|thinstrokedrects\|941` |
| `complexclip3_complex` | FAST | 904 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|55\|complexclip3_complex\|904`, `PASS\|55\|complexclip3_complex\|881`, `PASS\|55\|complexclip3_complex\|917` |
| `tinyanglearcs` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|550\|tinyanglearcs\|15`, `PASS\|550\|tinyanglearcs\|14`, `PASS\|550\|tinyanglearcs\|16` |
| `trickycubicstrokes_largeradius` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|551\|trickycubicstrokes_largeradius\|9`, `PASS\|551\|trickycubicstrokes_largeradius\|14`, `PASS\|551\|trickycubicstrokes_largeradius\|16` |
| `trimpatheffect` | FAST | 170 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|552\|trimpatheffect\|163`, `PASS\|552\|trimpatheffect\|173`, `PASS\|552\|trimpatheffect\|170` |
| `widebuttcaps` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|553\|widebuttcaps\|26`, `PASS\|553\|widebuttcaps\|28`, `PASS\|553\|widebuttcaps\|29` |
| `zero_control_stroke` | FAST | 864 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|554\|zero_control_stroke\|864`, `PASS\|554\|zero_control_stroke\|820`, `PASS\|554\|zero_control_stroke\|882` |
| `zeroPath` | FAST | 41 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|555\|zeroPath\|41`, `PASS\|555\|zeroPath\|39`, `PASS\|555\|zeroPath\|42` |
| `zero_length_paths_aa` | FAST | 80 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|556\|zero_length_paths_aa\|77`, `PASS\|556\|zero_length_paths_aa\|80`, `PASS\|556\|zero_length_paths_aa\|83` |
| `zero_length_paths_bw` | FAST | 64 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|557\|zero_length_paths_bw\|64`, `PASS\|557\|zero_length_paths_bw\|68`, `PASS\|557\|zero_length_paths_bw\|63` |
| `zero_length_paths_dbl_aa` | FAST | 172 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|558\|zero_length_paths_dbl_aa\|173`, `PASS\|558\|zero_length_paths_dbl_aa\|172`, `PASS\|558\|zero_length_paths_dbl_aa\|169` |
| `zero_length_paths_dbl_bw` | MEDIUM | 1067 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|559\|zero_length_paths_dbl_bw\|1080`, `PASS\|559\|zero_length_paths_dbl_bw\|1050`, `PASS\|559\|zero_length_paths_dbl_bw\|1067` |
| `complexclip3_simple` | FAST | 67 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|56\|complexclip3_simple\|61`, `PASS\|56\|complexclip3_simple\|67`, `PASS\|56\|complexclip3_simple\|67` |
| `zerolinedash` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|560\|zerolinedash\|13`, `PASS\|560\|zerolinedash\|17`, `PASS\|560\|zerolinedash\|18` |
| `zerolinestroke` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|561\|zerolinestroke\|10`, `PASS\|561\|zerolinestroke\|10`, `PASS\|561\|zerolinestroke\|11` |
| `AlternateLuma` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|562\|AlternateLuma\|25`, `PASS\|562\|AlternateLuma\|25`, `PASS\|562\|AlternateLuma\|28` |
| `arithmode` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|563\|arithmode\|21`, `PASS\|563\|arithmode\|27`, `PASS\|563\|arithmode\|20` |
| `colorcubecolorfilterrt` | FAST | 895 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|564\|colorcubecolorfilterrt\|895`, `PASS\|564\|colorcubecolorfilterrt\|876`, `PASS\|564\|colorcubecolorfilterrt\|928` |
| `color_cube_rt` | FAST | 44 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|565\|color_cube_rt\|44`, `PASS\|565\|color_cube_rt\|43`, `PASS\|565\|color_cube_rt\|47` |
| `composeCF` | FAST | 29 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|566\|composeCF\|71`, `PASS\|566\|composeCF\|29`, `PASS\|566\|composeCF\|23` |
| `runtime_intrinsics_common` | FAST | 38 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|567\|runtime_intrinsics_common\|22`, `PASS\|567\|runtime_intrinsics_common\|45`, `PASS\|567\|runtime_intrinsics_common\|38` |
| `runtime_intrinsics_exponential` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|568\|runtime_intrinsics_exponential\|7`, `PASS\|568\|runtime_intrinsics_exponential\|9`, `PASS\|568\|runtime_intrinsics_exponential\|9` |
| `runtime_intrinsics_geometric` | FAST | 914 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|569\|runtime_intrinsics_geometric\|835`, `PASS\|569\|runtime_intrinsics_geometric\|974`, `PASS\|569\|runtime_intrinsics_geometric\|914` |
| `complexclip4_aa` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|57\|complexclip4_aa\|28`, `PASS\|57\|complexclip4_aa\|23`, `PASS\|57\|complexclip4_aa\|32` |
| `runtime_intrinsics_matrix` | FAST | 13 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|570\|runtime_intrinsics_matrix\|13`, `PASS\|570\|runtime_intrinsics_matrix\|12`, `PASS\|570\|runtime_intrinsics_matrix\|13` |
| `runtime_intrinsics_relational` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|571\|runtime_intrinsics_relational\|20`, `PASS\|571\|runtime_intrinsics_relational\|27`, `PASS\|571\|runtime_intrinsics_relational\|38` |
| `runtime_intrinsics_trig` | FAST | 19 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|572\|runtime_intrinsics_trig\|12`, `PASS\|572\|runtime_intrinsics_trig\|27`, `PASS\|572\|runtime_intrinsics_trig\|19` |
| `kawase_blur_rt` | FAST | 40 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|573\|kawase_blur_rt\|40`, `PASS\|573\|kawase_blur_rt\|39`, `PASS\|573\|kawase_blur_rt\|40` |
| `lineargradientrt` | FAST | 873 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|574\|lineargradientrt\|921`, `PASS\|574\|lineargradientrt\|873`, `PASS\|574\|lineargradientrt\|870` |
| `lumafilter` | FAST | 39 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|575\|lumafilter\|38`, `PASS\|575\|lumafilter\|39`, `PASS\|575\|lumafilter\|41` |
| `rippleshader` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|576\|rippleshader\|20`, `PASS\|576\|rippleshader\|21`, `PASS\|576\|rippleshader\|20` |
| `rtif_distort` | FAST | 438 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|577\|rtif_distort\|447`, `PASS\|577\|rtif_distort\|396`, `PASS\|577\|rtif_distort\|438` |
| `rtif_unsharp` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|578\|rtif_unsharp\|15`, `PASS\|578\|rtif_unsharp\|16`, `PASS\|578\|rtif_unsharp\|16` |
| `runtimecolorfilter` | FAST | 842 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|579\|runtimecolorfilter\|842`, `PASS\|579\|runtimecolorfilter\|819`, `PASS\|579\|runtimecolorfilter\|953` |
| `complexclip4_bw` | FAST | 22 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|58\|complexclip4_bw\|22`, `PASS\|58\|complexclip4_bw\|22`, `PASS\|58\|complexclip4_bw\|23` |
| `runtimefunctions` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|580\|runtimefunctions\|12`, `PASS\|580\|runtimefunctions\|12`, `PASS\|580\|runtimefunctions\|14` |
| `runtime_shader` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|581\|runtime_shader\|21`, `PASS\|581\|runtime_shader\|21`, `PASS\|581\|runtime_shader\|21` |
| `simplert` | FAST | 24 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|582\|simplert\|22`, `PASS\|582\|simplert\|24`, `PASS\|582\|simplert\|27` |
| `spiral_rt` | FAST | 8 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|583\|spiral_rt\|8`, `PASS\|583\|spiral_rt\|8`, `PASS\|583\|spiral_rt\|11` |
| `threshold_rt` | FAST | 836 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|584\|threshold_rt\|826`, `PASS\|584\|threshold_rt\|836`, `PASS\|584\|threshold_rt\|838` |
| `unsharp_rt` | FAST | 31 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|585\|unsharp_rt\|73`, `PASS\|585\|unsharp_rt\|31`, `PASS\|585\|unsharp_rt\|31` |
| `workingspace` | FAST | 54 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|586\|workingspace\|55`, `PASS\|586\|workingspace\|49`, `PASS\|586\|workingspace\|54` |
| `annotated_text` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|587\|annotated_text\|27`, `PASS\|587\|annotated_text\|28`, `PASS\|587\|annotated_text\|28` |
| `bigtext_crbug_1370488` | FAST | 62 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|588\|bigtext_crbug_1370488\|69`, `PASS\|588\|bigtext_crbug_1370488\|57`, `PASS\|588\|bigtext_crbug_1370488\|62` |
| `bigtext` | FAST | 842 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|589\|bigtext\|832`, `PASS\|589\|bigtext\|842`, `PASS\|589\|bigtext\|871` |
| `complexclip_aa` | FAST | 74 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|59\|complexclip_aa\|74`, `PASS\|59\|complexclip_aa\|80`, `PASS\|59\|complexclip_aa\|70` |
| `blob_rsxform_distortable` | FAST | 18 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|590\|blob_rsxform_distortable\|17`, `PASS\|590\|blob_rsxform_distortable\|18`, `PASS\|590\|blob_rsxform_distortable\|18` |
| `blob_rsxform` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|591\|blob_rsxform\|17`, `PASS\|591\|blob_rsxform\|21`, `PASS\|591\|blob_rsxform\|23` |
| `chrome_gradtext2` | FAST | 117 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|592\|chrome_gradtext2\|117`, `PASS\|592\|chrome_gradtext2\|122`, `PASS\|592\|chrome_gradtext2\|84` |
| `cliperror` | FAST | 40 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|593\|cliperror\|68`, `PASS\|593\|cliperror\|40`, `PASS\|593\|cliperror\|35` |
| `coloremoji_colrv0` | FAST | 912 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|594\|coloremoji_colrv0\|904`, `PASS\|594\|coloremoji_colrv0\|912`, `PASS\|594\|coloremoji_colrv0\|924` |
| `coloremoji` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|595\|coloremoji\|21`, `PASS\|595\|coloremoji\|17`, `PASS\|595\|coloremoji\|17` |
| `colorwheelnative` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|596\|colorwheelnative\|12`, `PASS\|596\|colorwheelnative\|12`, `PASS\|596\|colorwheelnative\|14` |
| `coloremoji_blendmodes_colrv0` | FAST | 48 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|597\|coloremoji_blendmodes_colrv0\|45`, `PASS\|597\|coloremoji_blendmodes_colrv0\|48`, `PASS\|597\|coloremoji_blendmodes_colrv0\|49` |
| `coloremoji_blendmodes` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|598\|coloremoji_blendmodes\|17`, `PASS\|598\|coloremoji_blendmodes\|16`, `PASS\|598\|coloremoji_blendmodes\|17` |
| `colrv1_gradient_stops_repeat` | FAST | 919 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|599\|colrv1_gradient_stops_repeat\|857`, `PASS\|599\|colrv1_gradient_stops_repeat\|1342`, `PASS\|599\|colrv1_gradient_stops_repeat\|919` |
| `complexclip_aa_invert` | FAST | 952 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|60\|complexclip_aa_invert\|1036`, `PASS\|60\|complexclip_aa_invert\|917`, `PASS\|60\|complexclip_aa_invert\|952` |
| `crbug_1073670` | FAST | 36 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|600\|crbug_1073670\|33`, `PASS\|600\|crbug_1073670\|102`, `PASS\|600\|crbug_1073670\|36` |
| `crbug_478659067` | FAST | 93 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|601\|crbug_478659067\|87`, `PASS\|601\|crbug_478659067\|93`, `PASS\|601\|crbug_478659067\|100` |
| `dftext_blob_persp` | FAST | 38 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|602\|dftext_blob_persp\|30`, `PASS\|602\|dftext_blob_persp\|40`, `PASS\|602\|dftext_blob_persp\|38` |
| `dftext` | FAST | 108 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|603\|dftext\|95`, `PASS\|603\|dftext\|108`, `PASS\|603\|dftext\|112` |
| `drawTextRSXform` | MEDIUM | 1114 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|604\|drawTextRSXform\|1070`, `PASS\|604\|drawTextRSXform\|1114`, `PASS\|604\|drawTextRSXform\|1177` |
| `fontcache` | FAST | 242 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|605\|fontcache\|242`, `PASS\|605\|fontcache\|238`, `PASS\|605\|fontcache\|331` |
| `fontmgr_bounds` | FAST | 40 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|606\|fontmgr_bounds\|40`, `PASS\|606\|fontmgr_bounds\|36`, `PASS\|606\|fontmgr_bounds\|87` |
| `fontmgr_iter` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|607\|fontmgr_iter\|24`, `PASS\|607\|fontmgr_iter\|28`, `PASS\|607\|fontmgr_iter\|27` |
| `fontmgr_match` | FAST | 17 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|608\|fontmgr_match\|18`, `PASS\|608\|fontmgr_match\|17`, `PASS\|608\|fontmgr_match\|16` |
| `font_palette_default` | FAST | 835 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|609\|font_palette_default\|806`, `PASS\|609\|font_palette_default\|901`, `PASS\|609\|font_palette_default\|835` |
| `complexclip_aa_layer` | FAST | 84 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|61\|complexclip_aa_layer\|83`, `PASS\|61\|complexclip_aa_layer\|85`, `PASS\|61\|complexclip_aa_layer\|84` |
| `fontregen` | FAST | 48 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|610\|fontregen\|48`, `PASS\|610\|fontregen\|48`, `PASS\|610\|fontregen\|56` |
| `fontscalerdistortable` | FAST | 192 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|611\|fontscalerdistortable\|192`, `PASS\|611\|fontscalerdistortable\|163`, `PASS\|611\|fontscalerdistortable\|196` |
| `fontscaler` | FAST | 163 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|612\|fontscaler\|163`, `PASS\|612\|fontscaler\|156`, `PASS\|612\|fontscaler\|301` |
| `gammagradienttext` | FAST | 46 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|613\|gammagradienttext\|45`, `PASS\|613\|gammagradienttext\|46`, `PASS\|613\|gammagradienttext\|56` |
| `gammatext_color_shader` | FAST | 861 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|614\|gammatext_color_shader\|861`, `PASS\|614\|gammatext_color_shader\|854`, `PASS\|614\|gammatext_color_shader\|878` |
| `gammatext` | FAST | 123 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|615\|gammatext\|123`, `PASS\|615\|gammatext\|112`, `PASS\|615\|gammatext\|130` |
| `getpostextpath` | FAST | 61 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|616\|getpostextpath\|95`, `PASS\|616\|getpostextpath\|61`, `PASS\|616\|getpostextpath\|44` |
| `gradtext` | FAST | 186 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|617\|gradtext\|186`, `PASS\|617\|gradtext\|183`, `PASS\|617\|gradtext\|237` |
| `largeglyphblur` | FAST | 43 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|618\|largeglyphblur\|40`, `PASS\|618\|largeglyphblur\|43`, `PASS\|618\|largeglyphblur\|114` |
| `lcdoverlap` | FAST | 880 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|619\|lcdoverlap\|918`, `PASS\|619\|lcdoverlap\|859`, `PASS\|619\|lcdoverlap\|880` |
| `complexclip_aa_layer_invert` | FAST | 56 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|62\|complexclip_aa_layer_invert\|61`, `PASS\|62\|complexclip_aa_layer_invert\|56`, `PASS\|62\|complexclip_aa_layer_invert\|54` |
| `macaa_colors` | FAST | 82 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|620\|macaa_colors\|111`, `PASS\|620\|macaa_colors\|82`, `PASS\|620\|macaa_colors\|78` |
| `mixedtextblobs` | FAST | 42 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|621\|mixedtextblobs\|56`, `PASS\|621\|mixedtextblobs\|42`, `PASS\|621\|mixedtextblobs\|37` |
| `overdraw_text_xform` | FAST | 46 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|622\|overdraw_text_xform\|43`, `PASS\|622\|overdraw_text_xform\|51`, `PASS\|622\|overdraw_text_xform\|46` |
| `palette` | FAST | 5 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|623\|palette\|5`, `PASS\|623\|palette\|6`, `PASS\|623\|palette\|5` |
| `pdf_never_embed` | FAST | 820 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|624\|pdf_never_embed\|866`, `PASS\|624\|pdf_never_embed\|820`, `PASS\|624\|pdf_never_embed\|815` |
| `pdf_table_based_subset` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|625\|pdf_table_based_subset\|9`, `PASS\|625\|pdf_table_based_subset\|9`, `PASS\|625\|pdf_table_based_subset\|10` |
| `persptext` | FAST | 73 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|626\|persptext\|75`, `PASS\|626\|persptext\|73`, `PASS\|626\|persptext\|71` |
| `persptext_minimal` | FAST | 51 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|627\|persptext_minimal\|44`, `PASS\|627\|persptext_minimal\|51`, `PASS\|627\|persptext_minimal\|51` |
| `rsx_blob_shader` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|628\|rsx_blob_shader\|16`, `PASS\|628\|rsx_blob_shader\|15`, `PASS\|628\|rsx_blob_shader\|15` |
| `scaledemojiperspective_colrv0` | FAST | 827 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|629\|scaledemojiperspective_colrv0\|834`, `PASS\|629\|scaledemojiperspective_colrv0\|821`, `PASS\|629\|scaledemojiperspective_colrv0\|827` |
| `scaledemojipos_colrv0` | FAST | 46 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|630\|scaledemojipos_colrv0\|46`, `PASS\|630\|scaledemojipos_colrv0\|47`, `PASS\|630\|scaledemojipos_colrv0\|46` |
| `scaledemoji_colrv0` | FAST | 32 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|631\|scaledemoji_colrv0\|32`, `PASS\|631\|scaledemoji_colrv0\|34`, `PASS\|631\|scaledemoji_colrv0\|31` |
| `scaledemoji_rendering` | FAST | 77 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|632\|scaledemoji_rendering\|77`, `PASS\|632\|scaledemoji_rendering\|78`, `PASS\|632\|scaledemoji_rendering\|74` |
| `shadertext3` | FAST | 89 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|633\|shadertext3\|89`, `PASS\|633\|shadertext3\|90`, `PASS\|633\|shadertext3\|88` |
| `skbug_12212` | FAST | 803 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|634\|skbug_12212\|803`, `PASS\|634\|skbug_12212\|817`, `PASS\|634\|skbug_12212\|793` |
| `skbug_257` | FAST | 465 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|635\|skbug_257\|465`, `PASS\|635\|skbug_257\|464`, `PASS\|635\|skbug_257\|475` |
| `skbug_5321` | FAST | 8 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|636\|skbug_5321\|7`, `PASS\|636\|skbug_5321\|8`, `PASS\|636\|skbug_5321\|8` |
| `skbug_8955` | FAST | 6 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|637\|skbug_8955\|6`, `PASS\|637\|skbug_8955\|6`, `PASS\|637\|skbug_8955\|6` |
| `slug` | FAST | 64 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|638\|slug\|65`, `PASS\|638\|slug\|64`, `PASS\|638\|slug\|62` |
| `stroketext` | FAST | 917 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|639\|stroketext\|930`, `PASS\|639\|stroketext\|886`, `PASS\|639\|stroketext\|917` |
| `complexclip_bw` | FAST | 55 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|64\|complexclip_bw\|56`, `PASS\|64\|complexclip_bw\|51`, `PASS\|64\|complexclip_bw\|55` |
| `stroketext_native` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|640\|stroketext_native\|20`, `PASS\|640\|stroketext_native\|20`, `PASS\|640\|stroketext_native\|20` |
| `surfaceprops` | FAST | 95 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|641\|surfaceprops\|90`, `PASS\|641\|surfaceprops\|95`, `PASS\|641\|surfaceprops\|99` |
| `textblobblockreordering` | FAST | 13 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|642\|textblobblockreordering\|15`, `PASS\|642\|textblobblockreordering\|12`, `PASS\|642\|textblobblockreordering\|13` |
| `textblobcolortrans` | FAST | 26 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|643\|textblobcolortrans\|26`, `PASS\|643\|textblobcolortrans\|25`, `PASS\|643\|textblobcolortrans\|31` |
| `textblobgeometrychange` | FAST | 796 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|644\|textblobgeometrychange\|825`, `PASS\|644\|textblobgeometrychange\|796`, `PASS\|644\|textblobgeometrychange\|792` |
| `textblob` | FAST | 46 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|645\|textblob\|45`, `PASS\|645\|textblob\|46`, `PASS\|645\|textblob\|46` |
| `textblob_intercepts` | FAST | 48 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|646\|textblob_intercepts\|87`, `PASS\|646\|textblob_intercepts\|48`, `PASS\|646\|textblob_intercepts\|47` |
| `textblobmixedsizes` | FAST | 99 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|647\|textblobmixedsizes\|98`, `PASS\|647\|textblobmixedsizes\|99`, `PASS\|647\|textblobmixedsizes\|99` |
| `textblobrandomfont` | FAST | 86 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|648\|textblobrandomfont\|94`, `PASS\|648\|textblobrandomfont\|84`, `PASS\|648\|textblobrandomfont\|86` |
| `textblobshader` | MEDIUM | 1004 | 0 | 0 | median below 5000 ms |
|  |  |  |  |  | Raw samples: `PASS\|649\|textblobshader\|1003`, `PASS\|649\|textblobshader\|1016`, `PASS\|649\|textblobshader\|1004` |
| `complexclip_bw_invert` | FAST | 44 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|65\|complexclip_bw_invert\|68`, `PASS\|65\|complexclip_bw_invert\|44`, `PASS\|65\|complexclip_bw_invert\|44` |
| `textblobtransforms` | FAST | 93 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|650\|textblobtransforms\|93`, `PASS\|650\|textblobtransforms\|86`, `PASS\|650\|textblobtransforms\|98` |
| `textblobuseaftergpufree` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|651\|textblobuseaftergpufree\|9`, `PASS\|651\|textblobuseaftergpufree\|9`, `PASS\|651\|textblobuseaftergpufree\|8` |
| `fancyblobunderline` | FAST | 64 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|652\|fancyblobunderline\|100`, `PASS\|652\|fancyblobunderline\|64`, `PASS\|652\|fancyblobunderline\|60` |
| `textfilter_color` | FAST | 20 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|653\|textfilter_color\|16`, `PASS\|653\|textfilter_color\|21`, `PASS\|653\|textfilter_color\|20` |
| `textfilter_image` | FAST | 840 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|654\|textfilter_image\|818`, `PASS\|654\|textfilter_image\|845`, `PASS\|654\|textfilter_image\|840` |
| `text_scale_skew` | FAST | 25 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|655\|text_scale_skew\|22`, `PASS\|655\|text_scale_skew\|27`, `PASS\|655\|text_scale_skew\|25` |
| `text_scale_skew_rotate` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|656\|text_scale_skew_rotate\|20`, `PASS\|656\|text_scale_skew_rotate\|23`, `PASS\|656\|text_scale_skew_rotate\|21` |
| `typefacerendering` | FAST | 28 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|657\|typefacerendering\|28`, `PASS\|657\|typefacerendering\|34`, `PASS\|657\|typefacerendering\|28` |
| `typefacerendering_pfa` | FAST | 14 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|658\|typefacerendering_pfa\|14`, `PASS\|658\|typefacerendering_pfa\|12`, `PASS\|658\|typefacerendering_pfa\|15` |
| `typefacerendering_pfb` | FAST | 846 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|659\|typefacerendering_pfb\|858`, `PASS\|659\|typefacerendering_pfb\|825`, `PASS\|659\|typefacerendering_pfb\|846` |
| `complexclip_bw_layer` | FAST | 924 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|66\|complexclip_bw_layer\|1400`, `PASS\|66\|complexclip_bw_layer\|907`, `PASS\|66\|complexclip_bw_layer\|924` |
| `typefacestyles` | FAST | 51 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|660\|typefacestyles\|48`, `PASS\|660\|typefacestyles\|51`, `PASS\|660\|typefacestyles\|53` |
| `typefacestyles_kerning` | FAST | 27 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|661\|typefacestyles_kerning\|30`, `PASS\|661\|typefacestyles_kerning\|24`, `PASS\|661\|typefacestyles_kerning\|27` |
| `typeface_styling` | FAST | 74 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|662\|typeface_styling\|74`, `PASS\|662\|typeface_styling\|41`, `PASS\|662\|typeface_styling\|91` |
| `user_typeface` | FAST | 9 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|663\|user_typeface\|8`, `PASS\|663\|user_typeface\|9`, `PASS\|663\|user_typeface\|9` |
| `variedtext` | FAST | 915 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|664\|variedtext\|915`, `PASS\|664\|variedtext\|891`, `PASS\|664\|variedtext\|930` |
| `complexclip_bw_layer_invert` | FAST | 83 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|67\|complexclip_bw_layer_invert\|83`, `PASS\|67\|complexclip_bw_layer_invert\|83`, `PASS\|67\|complexclip_bw_layer_invert\|85` |
| `fast_constraint_red_is_allowed` | FAST | 11 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|72\|fast_constraint_red_is_allowed\|11`, `PASS\|72\|fast_constraint_red_is_allowed\|11`, `PASS\|72\|fast_constraint_red_is_allowed\|14` |
| `fast_constraint_red_is_allowed_manual` | FAST | 6 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|73\|fast_constraint_red_is_allowed_manual\|6`, `PASS\|73\|fast_constraint_red_is_allowed_manual\|6`, `PASS\|73\|fast_constraint_red_is_allowed_manual\|8` |
| `manypathatlases_128` | FAST | 7 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|75\|manypathatlases_128\|158`, `PASS\|75\|manypathatlases_128\|6`, `PASS\|75\|manypathatlases_128\|7` |
| `manypathatlases_2048` | FAST | 807 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|76\|manypathatlases_2048\|848`, `PASS\|76\|manypathatlases_2048\|780`, `PASS\|76\|manypathatlases_2048\|807` |
| `blur_ignore_xform_circle` | FAST | 72 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|8\|blur_ignore_xform_circle\|69`, `PASS\|8\|blur_ignore_xform_circle\|72`, `PASS\|8\|blur_ignore_xform_circle\|76` |
| `simpleaaclip_path` | FAST | 40 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|82\|simpleaaclip_path\|43`, `PASS\|82\|simpleaaclip_path\|40`, `PASS\|82\|simpleaaclip_path\|40` |
| `simpleaaclip_rect` | FAST | 21 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|83\|simpleaaclip_rect\|23`, `PASS\|83\|simpleaaclip_rect\|21`, `PASS\|83\|simpleaaclip_rect\|19` |
| `simpleclip` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|84\|simpleclip\|10`, `PASS\|84\|simpleclip\|10`, `PASS\|84\|simpleclip\|10` |
| `strict_constraint_batch_no_red_allowed` | FAST | 13 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|87\|strict_constraint_batch_no_red_allowed\|13`, `PASS\|87\|strict_constraint_batch_no_red_allowed\|13`, `PASS\|87\|strict_constraint_batch_no_red_allowed\|15` |
| `strict_constraint_batch_no_red_allowed_manual` | FAST | 824 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|88\|strict_constraint_batch_no_red_allowed_manual\|824`, `PASS\|88\|strict_constraint_batch_no_red_allowed_manual\|801`, `PASS\|88\|strict_constraint_batch_no_red_allowed_manual\|969` |
| `strict_constraint_no_red_allowed` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|89\|strict_constraint_no_red_allowed\|12`, `PASS\|89\|strict_constraint_no_red_allowed\|11`, `PASS\|89\|strict_constraint_no_red_allowed\|26` |
| `blur_ignore_xform_rrect` | FAST | 30 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|9\|blur_ignore_xform_rrect\|30`, `PASS\|9\|blur_ignore_xform_rrect\|30`, `PASS\|9\|blur_ignore_xform_rrect\|30` |
| `strict_constraint_no_red_allowed_manual` | FAST | 12 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|90\|strict_constraint_no_red_allowed_manual\|12`, `PASS\|90\|strict_constraint_no_red_allowed_manual\|12`, `PASS\|90\|strict_constraint_no_red_allowed_manual\|31` |
| `color` | FAST | 13 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|92\|color\|10`, `PASS\|92\|color\|13`, `PASS\|92\|color\|41` |
| `const_color_processor` | FAST | 117 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|93\|const_color_processor\|117`, `PASS\|93\|const_color_processor\|113`, `PASS\|93\|const_color_processor\|403` |
| `dark` | FAST | 840 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|94\|dark\|840`, `PASS\|94\|dark\|780`, `PASS\|94\|dark\|846` |
| `debug` | FAST | 15 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|95\|debug\|15`, `PASS\|95\|debug\|15`, `PASS\|95\|debug\|15` |
| `default` | FAST | 16 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|96\|default\|16`, `PASS\|96\|default\|16`, `PASS\|96\|default\|16` |
| `encodesrgb` | FAST | 37 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|97\|encodesrgb\|37`, `PASS\|97\|encodesrgb\|37`, `PASS\|97\|encodesrgb\|43` |
| `filter` | FAST | 10 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|98\|filter\|10`, `PASS\|98\|filter\|12`, `PASS\|98\|filter\|10` |
| `high` | FAST | 815 | 0 | 0 | median below 1000 ms |
|  |  |  |  |  | Raw samples: `PASS\|99\|high\|911`, `PASS\|99\|high\|800`, `PASS\|99\|high\|815` |

## Timed-out batches

- None
