# GPU Phase 6 IMAGE Family Evidence

## Summary

- Total IMAGE rows: 121
- Classifications: {expected-unsupported=31, instrumented-existing=64, no-score=26}
- Subfamilies: {animation-gated=10, bitmap-shader-affine=14, color-management-gated=5, image-filter-gated=14, local-matrix-affine=1, mipmap-gated=3, perspective-gated=2, readpixels-or-snapshot-gated=3, sampler-policy-candidate=10, simple-image-rect=2, strict-nearest-linear=3, texture-cache-candidate=53, yuv-gated=1}

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

## Rows

| Row | Subfamily | Classification | Similarity | Fallback |
|---|---|---|---:|---|
| `encode` | `animation-gated` | `expected-unsupported` | 14.61 | `dependency.image.codec.unregistered` |
| `all_bitmap_configs` | `texture-cache-candidate` | `instrumented-existing` | 0.47 | `none` |
| `all_variants_8888` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `alpha_image_alpha_tint` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `animatedGif` | `animation-gated` | `expected-unsupported` | 37.50 | `dependency.image.codec.unregistered` |
| `anisomips` | `mipmap-gated` | `expected-unsupported` | 14.80 | `unsupported.image.mipmap_budget_exceeded` |
| `anisotropic_image_scale` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `anisotropic_image_scale_aniso` | `texture-cache-candidate` | `instrumented-existing` | 49.66 | `none` |
| `anisotropic_image_scale_linear` | `strict-nearest-linear` | `instrumented-existing` | 49.66 | `none` |
| `anisotropic_image_scale_mip` | `mipmap-gated` | `expected-unsupported` | 39.67 | `unsupported.image.mipmap_budget_exceeded` |
| `async_rescale_and_read_alpha_type` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `async_rescale_and_read_rose` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `async_rescale_and_read_no_bleed` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `attributes` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `async_yuv_no_scale` | `yuv-gated` | `expected-unsupported` | 56.67 | `unsupported.color.yuv_conversion` |
| `bc1_transparency` | `texture-cache-candidate` | `instrumented-existing` | 14.29 | `none` |
| `bicubic` | `texture-cache-candidate` | `instrumented-existing` | 78.88 | `none` |
| `bigmatrix` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `bitmapcopy` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `bitmapfilters` | `image-filter-gated` | `expected-unsupported` | 1.07 | `unsupported.filter.node_unimplemented` |
| `bitmap-image-srgb-legacy` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `bitmap_premul` | `texture-cache-candidate` | `instrumented-existing` | 100.00 | `none` |
| `bitmapshaders` | `bitmap-shader-affine` | `instrumented-existing` | 0.00 | `none` |
| `bitmap_subset_shader` | `bitmap-shader-affine` | `instrumented-existing` | 67.61 | `none` |
| `bleed_downscale` | `texture-cache-candidate` | `instrumented-existing` | 30.56 | `none` |
| `bmp_filter_quality_repeat` | `image-filter-gated` | `expected-unsupported` | 0.00 | `unsupported.filter.node_unimplemented` |
| `bug6783` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `jpg-color-cube` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `colorwheel_alphatypes` | `texture-cache-candidate` | `instrumented-existing` | 29.91 | `none` |
| `colorwheel` | `texture-cache-candidate` | `instrumented-existing` | 8.18 | `none` |
| `colorspace2` | `color-management-gated` | `expected-unsupported` | 0.72 | `unsupported.color.image_profile_conversion` |
| `colorspace` | `color-management-gated` | `expected-unsupported` | 0.72 | `unsupported.color.image_profile_conversion` |
| `compositor_quads_image` | `texture-cache-candidate` | `instrumented-existing` | 70.01 | `none` |
| `compressed_textures` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `coordclampshader` | `bitmap-shader-affine` | `instrumented-existing` | 38.84 | `none` |
| `copyTo4444` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `crbug_224618` | `texture-cache-candidate` | `instrumented-existing` | 0.23 | `none` |
| `crbug_404394639` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `3x3bitmaprect` | `simple-image-rect` | `instrumented-existing` | 2.44 | `none` |
| `draw_bitmap_rect_skbug4734` | `texture-cache-candidate` | `instrumented-existing` | 45.31 | `none` |
| `drawminibitmaprect` | `simple-image-rect` | `instrumented-existing` | 66.82 | `none` |
| `drawimage_sampling` | `strict-nearest-linear` | `no-score` | n/a | `none` |
| `drawimagerect_filter` | `image-filter-gated` | `expected-unsupported` | 29.67 | `unsupported.filter.node_unimplemented` |
| `encode-alpha-jpeg` | `animation-gated` | `expected-unsupported` | 0.00 | `dependency.image.codec.unregistered` |
| `encode-color-types-webp-lossless` | `animation-gated` | `expected-unsupported` | 42.71 | `dependency.image.codec.unregistered` |
| `encode-alpha-jpeg-opts` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `encode-platform` | `animation-gated` | `expected-unsupported` | 14.02 | `dependency.image.codec.unregistered` |
| `encode-srgb-png` | `animation-gated` | `expected-unsupported` | 47.30 | `dependency.image.codec.unregistered` |
| `exoticformats` | `texture-cache-candidate` | `instrumented-existing` | 7.27 | `none` |
| `filterbug` | `image-filter-gated` | `expected-unsupported` | 8.00 | `unsupported.filter.node_unimplemented` |
| `filterindiabox` | `image-filter-gated` | `expected-unsupported` | 96.47 | `unsupported.filter.node_unimplemented` |
| `flight_animated_image` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `flippity` | `texture-cache-candidate` | `instrumented-existing` | 44.16 | `none` |
| `format4444` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `grayscalejpg` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `hugebitmapshader` | `bitmap-shader-affine` | `instrumented-existing` | 21.15 | `none` |
| `image-cacherator-from-picture` | `texture-cache-candidate` | `instrumented-existing` | 59.65 | `none` |
| `imagefilter_transformed_image` | `image-filter-gated` | `expected-unsupported` | 73.43 | `unsupported.filter.node_unimplemented` |
| `imagefilterscropexpand` | `image-filter-gated` | `expected-unsupported` | 37.14 | `unsupported.filter.node_unimplemented` |
| `imagefiltersgraph` | `image-filter-gated` | `expected-unsupported` | 70.95 | `unsupported.filter.node_unimplemented` |
| `image-surface` | `readpixels-or-snapshot-gated` | `expected-unsupported` | 87.84 | `unsupported.destination_read.strategy_unaccepted` |
| `imagemagnifier_bounds` | `image-filter-gated` | `expected-unsupported` | 14.71 | `unsupported.filter.node_unimplemented` |
| `imagemagnifier_cropped` | `image-filter-gated` | `expected-unsupported` | 81.14 | `unsupported.filter.node_unimplemented` |
| `imagemagnifier` | `image-filter-gated` | `expected-unsupported` | 52.14 | `unsupported.filter.node_unimplemented` |
| `imagemakewithfilter` | `image-filter-gated` | `expected-unsupported` | 49.60 | `unsupported.filter.node_unimplemented` |
| `imagemasksubset` | `texture-cache-candidate` | `instrumented-existing` | 73.96 | `none` |
| `image_out_of_gamut` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `image-picture` | `texture-cache-candidate` | `instrumented-existing` | 58.68 | `none` |
| `imageresizetiled` | `sampler-policy-candidate` | `instrumented-existing` | 81.85 | `none` |
| `image-shader` | `bitmap-shader-affine` | `instrumented-existing` | 84.47 | `none` |
| `imagesource` | `texture-cache-candidate` | `instrumented-existing` | 91.86 | `none` |
| `image_subset` | `texture-cache-candidate` | `instrumented-existing` | 94.11 | `none` |
| `imageshader_tinyscale` | `bitmap-shader-affine` | `instrumented-existing` | 0.00 | `none` |
| `jpg-color-cube` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `localmatriximageshader_filtering` | `image-filter-gated` | `expected-unsupported` | 0.00 | `unsupported.filter.node_unimplemented` |
| `localmatriximageshader` | `local-matrix-affine` | `instrumented-existing` | 83.87 | `none` |
| `makecolorspace` | `color-management-gated` | `no-score` | n/a | `unsupported.color.image_profile_conversion` |
| `makecolortypeandspace` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `makeRasterImage` | `texture-cache-candidate` | `instrumented-existing` | 69.29 | `none` |
| `mirror_tile` | `sampler-policy-candidate` | `instrumented-existing` | 55.52 | `none` |
| `nearest_half_pixel_image` | `strict-nearest-linear` | `instrumented-existing` | 73.59 | `none` |
| `ninepatch-stretch` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `not_native32_bitmap_config` | `texture-cache-candidate` | `instrumented-existing` | 75.74 | `none` |
| `p3` | `color-management-gated` | `expected-unsupported` | 86.73 | `unsupported.color.image_profile_conversion` |
| `persp_images` | `perspective-gated` | `no-score` | n/a | `unsupported.transform.perspective_route_rejected` |
| `pictureimagegenerator` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `pictureimagefilter` | `image-filter-gated` | `expected-unsupported` | 0.24 | `unsupported.filter.node_unimplemented` |
| `pictureimagegenerator` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `pictureshader_alpha` | `bitmap-shader-affine` | `instrumented-existing` | 30.84 | `none` |
| `pictureshadercache` | `bitmap-shader-affine` | `instrumented-existing` | 53.72 | `none` |
| `pictureshader` | `bitmap-shader-affine` | `instrumented-existing` | 30.84 | `none` |
| `pictureshader_localwrapper` | `bitmap-shader-affine` | `instrumented-existing` | 30.84 | `none` |
| `pictureshader_persp` | `perspective-gated` | `expected-unsupported` | 11.86 | `unsupported.transform.perspective_route_rejected` |
| `pictureshadertile` | `bitmap-shader-affine` | `instrumented-existing` | 44.08 | `none` |
| `poster_circle` | `texture-cache-candidate` | `instrumented-existing` | 36.97 | `none` |
| `readpixelscodec` | `animation-gated` | `no-score` | n/a | `dependency.image.codec.unregistered` |
| `readpixelspicture` | `readpixels-or-snapshot-gated` | `expected-unsupported` | 7.54 | `unsupported.destination_read.strategy_unaccepted` |
| `reinterpretcolorspace` | `color-management-gated` | `no-score` | n/a | `unsupported.color.image_profile_conversion` |
| `repeated_bitmap` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `repeated_bitmap_jpg` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `scaled_tilemodes` | `sampler-policy-candidate` | `instrumented-existing` | 51.74 | `none` |
| `scaled_tilemodes_npot` | `sampler-policy-candidate` | `instrumented-existing` | 72.57 | `none` |
| `scalepixels_unpremul` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `shaderpath` | `bitmap-shader-affine` | `instrumented-existing` | 0.04 | `none` |
| `showmiplevels_explicit` | `mipmap-gated` | `no-score` | n/a | `unsupported.image.mipmap_budget_exceeded` |
| `skbug_8664` | `texture-cache-candidate` | `instrumented-existing` | 0.04 | `none` |
| `skbug_9819` | `texture-cache-candidate` | `instrumented-existing` | 43.97 | `none` |
| `srcrectconstraint` | `texture-cache-candidate` | `no-score` | n/a | `none` |
| `stoplight_animated_image` | `animation-gated` | `expected-unsupported` | 77.32 | `dependency.image.codec.unregistered` |
| `surface_underdraw` | `readpixels-or-snapshot-gated` | `expected-unsupported` | 0.00 | `unsupported.destination_read.strategy_unaccepted` |
| `textureimage_and_shader` | `bitmap-shader-affine` | `no-score` | n/a | `none` |
| `tiled_picture_shader` | `bitmap-shader-affine` | `instrumented-existing` | 0.00 | `none` |
| `tiledscaledbitmap` | `sampler-policy-candidate` | `instrumented-existing` | 77.47 | `none` |
| `tilemode_decal` | `sampler-policy-candidate` | `instrumented-existing` | 25.38 | `none` |
| `tilemodes_alpha` | `sampler-policy-candidate` | `instrumented-existing` | 25.47 | `none` |
| `tilemodes` | `sampler-policy-candidate` | `instrumented-existing` | 69.25 | `none` |
| `tilemode_bitmap` | `sampler-policy-candidate` | `instrumented-existing` | 52.29 | `none` |
| `tilemode_gradient` | `sampler-policy-candidate` | `instrumented-existing` | 52.29 | `none` |
| `tinybitmap` | `texture-cache-candidate` | `instrumented-existing` | 0.00 | `none` |
| `unpremul` | `texture-cache-candidate` | `instrumented-existing` | 40.12 | `none` |
| `verylargebitmap` | `texture-cache-candidate` | `no-score` | n/a | `none` |
