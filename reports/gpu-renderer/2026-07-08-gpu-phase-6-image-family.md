# GPU Phase 6 IMAGE Family Evidence

## Summary

- Total IMAGE rows: 173
- Classifications: {expected-unsupported=29, instrumented-existing=73, no-score=71}
- Subfamilies: {animation-gated=11, bitmap-shader-affine=16, color-management-gated=6, image-filter-gated=11, local-matrix-affine=5, mipmap-gated=5, perspective-gated=3, readpixels-or-snapshot-gated=3, sampler-policy-candidate=20, simple-image-rect=7, strict-nearest-linear=6, texture-cache-candidate=76, yuv-gated=4}

## Non-Claims

- No broad IMAGE support is claimed from classification alone.
- No codec, animation, mipmap, YUV, perspective, image-filter, picture-shader, or broad color-management support is added.
- CPU-oracle rows do not count as Skia-comparable fidelity.

## Resource And Cache Evidence

- Row id: `phase6-image-repeated-texture-sampler`
- `resource-provider.cache lane=texture-sampler result=create key=target=phase6-image-target;layout=layout-image-sampler-v1;binding=sampled-texture.phase6-checker;owner=phase6-image-cache;lifetime=recording-local;release=submission-complete;canAliasScratch=false;texture=64x64:rgba8unorm:samples=1:usage=copy_dst+texture_binding;view=texture:phase6-checker:2d:0..0:0..0;sampler=clamp-to-edge:clamp-to-edge:nearest:nearest:none:0:0:none:1:;deviceGeneration=17;resourceGeneration=3 subject=sampled-texture.phase6-checker`
- `resource-provider.cache lane=texture-sampler result=reuse key=target=phase6-image-target;layout=layout-image-sampler-v1;binding=sampled-texture.phase6-checker;owner=phase6-image-cache;lifetime=recording-local;release=submission-complete;canAliasScratch=false;texture=64x64:rgba8unorm:samples=1:usage=copy_dst+texture_binding;view=texture:phase6-checker:2d:0..0:0..0;sampler=clamp-to-edge:clamp-to-edge:nearest:nearest:none:0:0:none:1:;deviceGeneration=17;resourceGeneration=3 subject=sampled-texture.phase6-checker`
- `no-broad-image-support`
- `no-codec-support`
- `no-animation-support`
- `no-mipmap-support`
- `no-yuv-support`
- `no-image-filter-support`
- `no-perspective-support`
- `no-picture-shader-support`
- `no-broad-color-management-support`

## Rows

| Row ID | Row | Subfamily | Classification | Similarity | Fallback |
|---|---|---|---|---:|---|
| `encode` | `encode` | `animation-gated` | `expected-unsupported` | 14.51 | `dependency.image.codec.unregistered` |
| `all_bitmap_configs` | `all_bitmap_configs` | `texture-cache-candidate` | `instrumented-existing` | 39.51 | `none` |
| `all` | `all` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `all_variants_8888` | `all_variants_8888` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `alpha_bitmap_is_coverage_android` | `alpha_bitmap_is_coverage_android` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `alpha_image_alpha_tint` | `alpha_image_alpha_tint` | `texture-cache-candidate` | `instrumented-existing` | 38.95 | `none` |
| `alpha_image_shader_rt` | `alpha_image_shader_rt` | `bitmap-shader-affine` | `no-score` | n/a | `none` |
| `animatedGif` | `animatedGif` | `animation-gated` | `expected-unsupported` | 37.50 | `dependency.image.codec.unregistered` |
| `anisomips` | `anisomips` | `mipmap-gated` | `expected-unsupported` | 14.79 | `unsupported.image.mipmap_budget_exceeded` |
| `anisotropic_image_scale` | `anisotropic_image_scale` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `anisotropic_image_scale_aniso` | `anisotropic_image_scale_aniso` | `texture-cache-candidate` | `instrumented-existing` | 49.66 | `none` |
| `anisotropic_image_scale_linear` | `anisotropic_image_scale_linear` | `strict-nearest-linear` | `instrumented-existing` | 49.66 | `none` |
| `anisotropic_image_scale_mip` | `anisotropic_image_scale_mip` | `mipmap-gated` | `expected-unsupported` | 39.67 | `unsupported.image.mipmap_budget_exceeded` |
| `async_rescale_and_read_alpha_type` | `async_rescale_and_read_alpha_type` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `async_rescale_and_read_rose` | `async_rescale_and_read_rose` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `async_rescale_and_read_no_bleed` | `async_rescale_and_read_no_bleed` | `texture-cache-candidate` | `instrumented-existing` | 22.72 | `none` |
| `attributes` | `attributes` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `async_yuv_no_scale` | `async_yuv_no_scale` | `yuv-gated` | `expected-unsupported` | 56.67 | `unsupported.color.yuv_conversion` |
| `bc1_transparency` | `bc1_transparency` | `texture-cache-candidate` | `instrumented-existing` | 56.55 | `none` |
| `bicubic` | `bicubic` | `texture-cache-candidate` | `instrumented-existing` | 80.75 | `none` |
| `bigmatrix` | `bigmatrix` | `texture-cache-candidate` | `instrumented-existing` | 7.96 | `none` |
| `bitmapcopy` | `bitmapcopy` | `texture-cache-candidate` | `instrumented-existing` | 95.47 | `none` |
| `bitmapfilters` | `bitmapfilters` | `image-filter-gated` | `expected-unsupported` | 48.06 | `unsupported.filter.node_unimplemented` |
| `bitmap-image-srgb-legacy` | `bitmap-image-srgb-legacy` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `bitmap_premul` | `bitmap_premul` | `texture-cache-candidate` | `instrumented-existing` | 100.00 | `none` |
| `bitmaprect_rounding` | `bitmaprect_rounding` | `simple-image-rect` | `instrumented-existing` | 99.87 | `none` |
| `bitmapshaders` | `bitmapshaders` | `bitmap-shader-affine` | `instrumented-existing` | 16.79 | `none` |
| `bitmap_subset_shader` | `bitmap_subset_shader` | `bitmap-shader-affine` | `instrumented-existing` | 67.61 | `none` |
| `bleed_downscale` | `bleed_downscale` | `texture-cache-candidate` | `instrumented-existing` | 30.56 | `none` |
| `bmp_filter_quality_repeat` | `bmp_filter_quality_repeat` | `sampler-policy-candidate` | `instrumented-existing` | 0.00 | `none` |
| `bug6783` | `bug6783` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `cgimage` | `cgimage` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `child_sampling_rt` | `child_sampling_rt` | `strict-nearest-linear` | `no-score` | n/a | `none` |
| `clippedbitmapshaders` | `clippedbitmapshaders` | `bitmap-shader-affine` | `no-score` | n/a | `none` |
| `color_cube_cf_rt` | `color_cube_cf_rt` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `jpg-color-cube` | `jpg-color-cube` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `colorwheel_alphatypes` | `colorwheel_alphatypes` | `texture-cache-candidate` | `instrumented-existing` | 29.91 | `none` |
| `colorwheel` | `colorwheel` | `texture-cache-candidate` | `instrumented-existing` | 31.50 | `none` |
| `colorspace2` | `colorspace2` | `color-management-gated` | `expected-unsupported` | 0.72 | `unsupported.color.image_profile_conversion` |
| `colorspace` | `colorspace` | `color-management-gated` | `expected-unsupported` | 0.72 | `unsupported.color.image_profile_conversion` |
| `compositor_quads_image` | `compositor_quads_image` | `texture-cache-candidate` | `instrumented-existing` | 70.01 | `none` |
| `compressed_textures` | `compressed_textures` | `texture-cache-candidate` | `instrumented-existing` | 49.55 | `none` |
| `coordclampshader` | `coordclampshader` | `bitmap-shader-affine` | `instrumented-existing` | 38.84 | `none` |
| `copyTo4444` | `copyTo4444` | `texture-cache-candidate` | `instrumented-existing` | 0.07 | `none` |
| `crbug_224618` | `crbug_224618` | `texture-cache-candidate` | `instrumented-existing` | 0.23 | `none` |
| `crbug_404394639` | `crbug_404394639` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `deferred_shader_rt` | `deferred_shader_rt` | `bitmap-shader-affine` | `no-score` | n/a | `none` |
| `bitmaprect_s` | `bitmaprect_s` | `simple-image-rect` | `no-score` | n/a | `none` |
| `bitmaprect_i` | `bitmaprect_i` | `simple-image-rect` | `no-score` | n/a | `none` |
| `3x3bitmaprect` | `3x3bitmaprect` | `simple-image-rect` | `instrumented-existing` | 3.66 | `none` |
| `drawbitmaprect4` | `drawbitmaprect4` | `simple-image-rect` | `no-score` | n/a | `none` |
| `draw_bitmap_rect_skbug4734` | `draw_bitmap_rect_skbug4734` | `simple-image-rect` | `instrumented-existing` | 45.31 | `none` |
| `drawminibitmaprect` | `drawminibitmaprect` | `simple-image-rect` | `instrumented-existing` | 66.91 | `none` |
| `drawimage_sampling` | `drawimage_sampling` | `strict-nearest-linear` | `no-score` | n/a | `none` |
| `drawimagerect_filter` | `drawimagerect_filter` | `strict-nearest-linear` | `instrumented-existing` | 29.67 | `none` |
| `encode-alpha-jpeg` | `encode-alpha-jpeg` | `animation-gated` | `expected-unsupported` | 0.00 | `dependency.image.codec.unregistered` |
| `encode-color-types-webp-lossless` | `encode-color-types-webp-lossless` | `animation-gated` | `expected-unsupported` | 42.17 | `dependency.image.codec.unregistered` |
| `encode-alpha-jpeg-opts` | `encode-alpha-jpeg-opts` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `encode-platform` | `encode-platform` | `animation-gated` | `expected-unsupported` | 14.02 | `dependency.image.codec.unregistered` |
| `encode-srgb-png` | `encode-srgb-png` | `animation-gated` | `expected-unsupported` | 48.59 | `dependency.image.codec.unregistered` |
| `exoticformats` | `exoticformats` | `texture-cache-candidate` | `instrumented-existing` | 9.37 | `none` |
| `filterbug` | `filterbug` | `image-filter-gated` | `expected-unsupported` | 77.78 | `unsupported.filter.node_unimplemented` |
| `filterindiabox` | `filterindiabox` | `image-filter-gated` | `expected-unsupported` | 96.47 | `unsupported.filter.node_unimplemented` |
| `flight_animated_image` | `flight_animated_image` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `flippity` | `flippity` | `texture-cache-candidate` | `instrumented-existing` | 44.16 | `none` |
| `format4444` | `format4444` | `texture-cache-candidate` | `instrumented-existing` | 87.50 | `none` |
| `giantbitmap_clamp_bilerp_rotate` | `giantbitmap_clamp_bilerp_rotate` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_clamp_bilerp_scale` | `giantbitmap_clamp_bilerp_scale` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_clamp_point_rotate` | `giantbitmap_clamp_point_rotate` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_clamp_point_scale` | `giantbitmap_clamp_point_scale` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_mirror_bilerp_rotate` | `giantbitmap_mirror_bilerp_rotate` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_mirror_bilerp_scale` | `giantbitmap_mirror_bilerp_scale` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_mirror_point_rotate` | `giantbitmap_mirror_point_rotate` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_mirror_point_scale` | `giantbitmap_mirror_point_scale` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_repeat_bilerp_rotate` | `giantbitmap_repeat_bilerp_rotate` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_repeat_bilerp_scale` | `giantbitmap_repeat_bilerp_scale` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_repeat_point_rotate` | `giantbitmap_repeat_point_rotate` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `giantbitmap_repeat_point_scale` | `giantbitmap_repeat_point_scale` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `grayscalejpg` | `grayscalejpg` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `hugebitmapshader` | `hugebitmapshader` | `bitmap-shader-affine` | `instrumented-existing` | 21.15 | `none` |
| `image-cacherator-from-picture` | `image-cacherator-from-picture` | `texture-cache-candidate` | `instrumented-existing` | 59.65 | `none` |
| `image_dither` | `image_dither` | `texture-cache-candidate` | `instrumented-existing` | 18.26 | `none` |
| `imagefilter_transformed_image` | `imagefilter_transformed_image` | `image-filter-gated` | `expected-unsupported` | 73.79 | `unsupported.filter.node_unimplemented` |
| `imagefilterscropexpand` | `imagefilterscropexpand` | `image-filter-gated` | `expected-unsupported` | 34.59 | `unsupported.filter.node_unimplemented` |
| `imagefiltersgraph` | `imagefiltersgraph` | `image-filter-gated` | `expected-unsupported` | 70.64 | `unsupported.filter.node_unimplemented` |
| `image-surface` | `image-surface` | `readpixels-or-snapshot-gated` | `expected-unsupported` | 87.79 | `unsupported.destination_read.strategy_unaccepted` |
| `imagemagnifier_bounds` | `imagemagnifier_bounds` | `image-filter-gated` | `expected-unsupported` | 15.04 | `unsupported.filter.node_unimplemented` |
| `imagemagnifier_cropped` | `imagemagnifier_cropped` | `image-filter-gated` | `expected-unsupported` | 83.83 | `unsupported.filter.node_unimplemented` |
| `imagemagnifier` | `imagemagnifier` | `image-filter-gated` | `expected-unsupported` | 51.84 | `unsupported.filter.node_unimplemented` |
| `imagemakewithfilter` | `imagemakewithfilter` | `image-filter-gated` | `expected-unsupported` | 50.23 | `unsupported.filter.node_unimplemented` |
| `imagemasksubset` | `imagemasksubset` | `texture-cache-candidate` | `instrumented-existing` | 73.96 | `none` |
| `image_out_of_gamut` | `image_out_of_gamut` | `color-management-gated` | `expected-unsupported` | 0.00 | `unsupported.color.image_profile_conversion` |
| `image-picture` | `image-picture` | `texture-cache-candidate` | `instrumented-existing` | 58.81 | `none` |
| `imageresizetiled` | `imageresizetiled` | `sampler-policy-candidate` | `instrumented-existing` | 81.85 | `none` |
| `image-shader` | `image-shader` | `bitmap-shader-affine` | `instrumented-existing` | 91.17 | `none` |
| `imagesource` | `imagesource` | `texture-cache-candidate` | `instrumented-existing` | 91.82 | `none` |
| `image_subset` | `image_subset` | `texture-cache-candidate` | `instrumented-existing` | 94.11 | `none` |
| `imageshader_tinyscale` | `imageshader_tinyscale` | `bitmap-shader-affine` | `instrumented-existing` | 0.00 | `none` |
| `jpeg_orientation` | `jpeg_orientation` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `jpg-color-cube#2` | `jpg-color-cube` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `lazytiling` | `lazytiling` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `lit_shader_linear_rt` | `lit_shader_linear_rt` | `strict-nearest-linear` | `no-score` | n/a | `none` |
| `localmatriximageshader_filtering` | `localmatriximageshader_filtering` | `local-matrix-affine` | `instrumented-existing` | 0.00 | `none` |
| `localmatriximageshader` | `localmatriximageshader` | `local-matrix-affine` | `instrumented-existing` | 83.87 | `none` |
| `localmatrixshader_nested` | `localmatrixshader_nested` | `local-matrix-affine` | `no-score` | n/a | `none` |
| `localmatrixshader_persp` | `localmatrixshader_persp` | `perspective-gated` | `no-score` | n/a | `unsupported.transform.perspective_route_rejected` |
| `local_matrix_shader_rt` | `local_matrix_shader_rt` | `local-matrix-affine` | `no-score` | n/a | `none` |
| `localmatrix_order` | `localmatrix_order` | `local-matrix-affine` | `no-score` | n/a | `none` |
| `makecolorspace` | `makecolorspace` | `color-management-gated` | `no-score` | n/a | `unsupported.color.image_profile_conversion` |
| `makecolortypeandspace` | `makecolortypeandspace` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `makeRasterImage` | `makeRasterImage` | `texture-cache-candidate` | `instrumented-existing` | 78.72 | `none` |
| `mipmap_gray8_srgb` | `mipmap_gray8_srgb` | `mipmap-gated` | `no-score` | n/a | `unsupported.image.mipmap_budget_exceeded` |
| `mipmap_srgb` | `mipmap_srgb` | `mipmap-gated` | `no-score` | n/a | `unsupported.image.mipmap_budget_exceeded` |
| `mirror_tile` | `mirror_tile` | `sampler-policy-candidate` | `instrumented-existing` | 55.52 | `none` |
| `nearest_half_pixel_image` | `nearest_half_pixel_image` | `strict-nearest-linear` | `instrumented-existing` | 73.59 | `none` |
| `new_texture_image` | `new_texture_image` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `ninepatch-stretch` | `ninepatch-stretch` | `texture-cache-candidate` | `instrumented-existing` | 78.48 | `none` |
| `not_native32_bitmap_config` | `not_native32_bitmap_config` | `texture-cache-candidate` | `instrumented-existing` | 75.74 | `none` |
| `null_child_rt` | `null_child_rt` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `p3` | `p3` | `color-management-gated` | `expected-unsupported` | 87.95 | `unsupported.color.image_profile_conversion` |
| `persp_images` | `persp_images` | `perspective-gated` | `no-score` | n/a | `unsupported.transform.perspective_route_rejected` |
| `pictureimagegenerator` | `pictureimagegenerator` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `picture` | `picture` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `pictureimagefilter` | `pictureimagefilter` | `image-filter-gated` | `expected-unsupported` | 0.17 | `unsupported.filter.node_unimplemented` |
| `pictureimagegenerator#2` | `pictureimagegenerator` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `pictureshader_alpha` | `pictureshader_alpha` | `bitmap-shader-affine` | `instrumented-existing` | 30.84 | `none` |
| `pictureshadercache` | `pictureshadercache` | `bitmap-shader-affine` | `instrumented-existing` | 53.72 | `none` |
| `pictureshader` | `pictureshader` | `bitmap-shader-affine` | `instrumented-existing` | 30.84 | `none` |
| `pictureshader_localwrapper` | `pictureshader_localwrapper` | `bitmap-shader-affine` | `instrumented-existing` | 30.84 | `none` |
| `pictureshader_persp` | `pictureshader_persp` | `perspective-gated` | `expected-unsupported` | 12.08 | `unsupported.transform.perspective_route_rejected` |
| `pictureshadertile` | `pictureshadertile` | `sampler-policy-candidate` | `instrumented-existing` | 44.08 | `none` |
| `poster_circle` | `poster_circle` | `texture-cache-candidate` | `instrumented-existing` | 36.97 | `none` |
| `raw_image_shader_normals_rt` | `raw_image_shader_normals_rt` | `bitmap-shader-affine` | `no-score` | n/a | `none` |
| `readpixelscodec` | `readpixelscodec` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `readpixelspicture` | `readpixelspicture` | `readpixels-or-snapshot-gated` | `expected-unsupported` | 7.54 | `unsupported.destination_read.strategy_unaccepted` |
| `rectangle_texture` | `rectangle_texture` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `reinterpretcolorspace` | `reinterpretcolorspace` | `color-management-gated` | `no-score` | n/a | `unsupported.color.image_profile_conversion` |
| `repeated_bitmap` | `repeated_bitmap` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `repeated_bitmap_jpg` | `repeated_bitmap_jpg` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `scale-pixels` | `scale-pixels` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `scaled_tilemode_bitmap` | `scaled_tilemode_bitmap` | `sampler-policy-candidate` | `no-score` | n/a | `none` |
| `scaled_tilemodes` | `scaled_tilemodes` | `sampler-policy-candidate` | `instrumented-existing` | 51.74 | `none` |
| `scaled_tilemodes_npot` | `scaled_tilemodes_npot` | `sampler-policy-candidate` | `instrumented-existing` | 72.57 | `none` |
| `scaledtiling` | `scaledtiling` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `scalepixels_unpremul` | `scalepixels_unpremul` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `shaderpath` | `shaderpath` | `bitmap-shader-affine` | `instrumented-existing` | 98.90 | `none` |
| `showmiplevels_explicit` | `showmiplevels_explicit` | `mipmap-gated` | `no-score` | n/a | `unsupported.image.mipmap_budget_exceeded` |
| `skbug_8664` | `skbug_8664` | `texture-cache-candidate` | `instrumented-existing` | 17.90 | `none` |
| `skbug_9819` | `skbug_9819` | `texture-cache-candidate` | `instrumented-existing` | 43.97 | `none` |
| `srcrectconstraint` | `srcrectconstraint` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `stoplight_animated_image` | `stoplight_animated_image` | `animation-gated` | `expected-unsupported` | 77.32 | `dependency.image.codec.unregistered` |
| `surface_underdraw` | `surface_underdraw` | `readpixels-or-snapshot-gated` | `expected-unsupported` | 35.31 | `unsupported.destination_read.strategy_unaccepted` |
| `texelsubset` | `texelsubset` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `texture` | `texture` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `textureimage_and_shader` | `textureimage_and_shader` | `bitmap-shader-affine` | `no-score` | n/a | `none` |
| `tiled_picture_shader` | `tiled_picture_shader` | `sampler-policy-candidate` | `instrumented-existing` | 0.00 | `none` |
| `tiledscaledbitmap` | `tiledscaledbitmap` | `sampler-policy-candidate` | `instrumented-existing` | 77.47 | `none` |
| `tilemode_decal` | `tilemode_decal` | `sampler-policy-candidate` | `instrumented-existing` | 27.46 | `none` |
| `tilemodes_alpha` | `tilemodes_alpha` | `sampler-policy-candidate` | `instrumented-existing` | 25.47 | `none` |
| `tilemodes` | `tilemodes` | `sampler-policy-candidate` | `instrumented-existing` | 69.25 | `none` |
| `tilemode_bitmap` | `tilemode_bitmap` | `sampler-policy-candidate` | `instrumented-existing` | 52.06 | `none` |
| `tilemode_gradient` | `tilemode_gradient` | `sampler-policy-candidate` | `instrumented-existing` | 60.18 | `none` |
| `tiling` | `tiling` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `tinybitmap` | `tinybitmap` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `unpremul` | `unpremul` | `texture-cache-candidate` | `instrumented-existing` | 40.12 | `none` |
| `verylargebitmap` | `verylargebitmap` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `verylarge_picture_image` | `verylarge_picture_image` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `videodecoder` | `videodecoder` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `wacky_yuv_formats` | `wacky_yuv_formats` | `yuv-gated` | `no-score` | n/a | `unsupported.color.yuv_conversion` |
| `ycbcrimage` | `ycbcrimage` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `yuv420_odd_dim` | `yuv420_odd_dim` | `yuv-gated` | `no-score` | n/a | `unsupported.color.yuv_conversion` |
| `yuv420_odd_dim_repeat` | `yuv420_odd_dim_repeat` | `yuv-gated` | `no-score` | n/a | `unsupported.color.yuv_conversion` |
| `path_huge_aa` | `path_huge_aa` | `texture-cache-candidate` | `no-score` | n/a | `none` |

## Regeneration Notes

- IMAGE GM regeneration may require blocking rows; use `-Pgm.includeBlocking=true` when calling `:integration-tests:skia:generateSkiaRenders` or `:integration-tests:skia:generateSkiaRendersFor`.
- `generateGpuPhase6ImageFamilyEvidence` inherits that property through `:integration-tests:skia:generateSkiaDashboard` -> `:integration-tests:skia:generateSkiaRenders`.
