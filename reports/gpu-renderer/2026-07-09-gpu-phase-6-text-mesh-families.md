# GPU Phase 6 Text Mesh Families Evidence

## Summary

- Total TEXT + MESH rows: 93
- Families: {MESH=16, TEXT=77}
- Classifications: {expected-unsupported=38, instrumented-existing=32, no-score=23}
- Subfamilies: {mesh-basic-vertices=4, mesh-color-space-gated=1, mesh-custom-basic=1, mesh-custom-uniforms-gated=2, mesh-effect-dependency-gated=1, mesh-image-dependency-gated=1, mesh-paint-color-dependency-gated=1, mesh-paint-image-dependency-gated=1, mesh-perspective-gated=1, mesh-picture-dependency-gated=1, mesh-update-or-dynamic-gated=1, mesh-zero-init-gated=1, text-annotation-gated=1, text-basic-latin=31, text-blob-gated=11, text-clip-interaction-gated=1, text-color-font-gated=1, text-color-palette-gated=2, text-emoji-gated=7, text-filter-or-blur-gated=3, text-font-fallback-gated=1, text-font-manager-gated=3, text-large-or-cache=3, text-perspective-or-transform-gated=4, text-rsxform-gated=3, text-shader-or-gradient-gated=6}
- Promoted rows: 0
- Unexpected fails: 0
- No score: 23

## Family Deltas

- Baseline source: `2026-07-09 local dashboard before text-mesh-family wave`
- Current dashboard: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json` (2026-07-09T14:21:53.369111)

| Family | Baseline | Current | Delta |
|---|---:|---:|---:|
| `MESH` | 16 | 16 | +0 |
| `TEXT` | 77 | 77 | +0 |

## Non-Claims

- No broad TEXT or MESH support is claimed from classification alone.
- shaping, font fallback, glyph atlas, glyph cache, color fonts, emoji, palettes, transformed text, text filters, and clip/text interactions remain outside this evidence wave unless row diagnostics prove a bounded route.
- custom mesh, dynamic mesh updates, perspective mesh, picture mesh, image dependencies, paint-image dependencies, mesh effects, and arbitrary vertices remain outside this evidence wave unless row diagnostics prove a bounded route.
- Rows without route and text/mesh diagnostics remain instrumented rather than promoted.

## Reason Code Taxonomy

- Text/mesh `unsupported.text.*` and `unsupported.mesh.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.

## Follow-Up Candidates

| Root Cause | Classification | Rows | Samples |
|---|---|---:|---|
| `generated-render-missing` | `no-score` | 16 | `cliperror`, `colorwheelnative`, `dftext_blob_persp`, `drawTextRSXform`, `fontcache` |
| `reference-missing` | `no-score` | 6 | `coloremoji`, `coloremoji_blendmodes`, `crbug_478659067`, `custommesh_cs_uniforms`, `textblobrandomfont` |
| `size-mismatch` | `no-score` | 1 | `rsx_blob_shader` |
| `unsupported.mesh.color_space` | `expected-unsupported` | 1 | `custommesh_cs` |
| `unsupported.mesh.custom_uniforms` | `expected-unsupported` | 1 | `custommesh_uniforms` |
| `unsupported.mesh.dynamic_updates` | `expected-unsupported` | 1 | `mesh_updates` |
| `unsupported.mesh.picture_dependency` | `expected-unsupported` | 1 | `picture_mesh` |
| `unsupported.text.annotation` | `expected-unsupported` | 1 | `annotated_text` |
| `unsupported.text.color_font` | `expected-unsupported` | 1 | `colrv1_gradient_stops_repeat` |
| `unsupported.text.emoji` | `expected-unsupported` | 5 | `coloremoji_blendmodes_colrv0`, `coloremoji_colrv0`, `scaledemoji_colrv0`, `scaledemoji_rendering`, `scaledemojipos_colrv0` |
| `unsupported.text.filter_or_blur` | `expected-unsupported` | 3 | `largeglyphblur`, `textfilter_color`, `textfilter_image` |
| `unsupported.text.font_manager` | `expected-unsupported` | 3 | `fontmgr_bounds`, `fontmgr_iter`, `fontmgr_match` |
| `unsupported.text.glyph_cache` | `expected-unsupported` | 10 | `fancyblobunderline`, `mixedtextblobs`, `textblob`, `textblob_intercepts`, `textblobblockreordering` |
| `unsupported.text.palette` | `expected-unsupported` | 2 | `font_palette_default`, `palette` |
| `unsupported.text.perspective` | `expected-unsupported` | 2 | `persptext`, `scaledemojiperspective_colrv0` |
| `unsupported.text.rsxform` | `expected-unsupported` | 2 | `blob_rsxform`, `blob_rsxform_distortable` |
| `unsupported.text.shader_or_gradient` | `expected-unsupported` | 5 | `chrome_gradtext2`, `gammatext_color_shader`, `gradtext`, `shadertext3`, `textblobshader` |

## Rows

| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback | No Score Cause |
|---|---|---|---|---|---:|---|---|
| `custommesh_cs` | `custommesh_cs` | `MESH` | `mesh-color-space-gated` | `expected-unsupported` | 83.27 | `unsupported.mesh.color_space` | `n/a` |
| `custommesh_cs_uniforms` | `custommesh_cs_uniforms` | `MESH` | `mesh-custom-uniforms-gated` | `no-score` | n/a | `unsupported.mesh.custom_uniforms` | `reference-missing` |
| `custommesh` | `custommesh` | `MESH` | `mesh-custom-basic` | `instrumented-existing` | 84.33 | `none` | `n/a` |
| `custommesh_uniforms` | `custommesh_uniforms` | `MESH` | `mesh-custom-uniforms-gated` | `expected-unsupported` | 100.00 | `unsupported.mesh.custom_uniforms` | `n/a` |
| `mesh_updates` | `mesh_updates` | `MESH` | `mesh-update-or-dynamic-gated` | `expected-unsupported` | 0.00 | `unsupported.mesh.dynamic_updates` | `n/a` |
| `mesh_with_effects` | `mesh_with_effects` | `MESH` | `mesh-effect-dependency-gated` | `no-score` | n/a | `unsupported.mesh.effect_dependency` | `generated-render-missing` |
| `mesh_with_image` | `mesh_with_image` | `MESH` | `mesh-image-dependency-gated` | `no-score` | n/a | `unsupported.mesh.image_dependency` | `generated-render-missing` |
| `mesh_with_paint_color` | `mesh_with_paint_color` | `MESH` | `mesh-paint-color-dependency-gated` | `no-score` | n/a | `unsupported.mesh.paint_color_dependency` | `generated-render-missing` |
| `mesh_with_paint_image` | `mesh_with_paint_image` | `MESH` | `mesh-paint-image-dependency-gated` | `no-score` | n/a | `unsupported.mesh.paint_image_dependency` | `generated-render-missing` |
| `mesh_zero_init` | `mesh_zero_init` | `MESH` | `mesh-zero-init-gated` | `no-score` | n/a | `unsupported.mesh.zero_init` | `generated-render-missing` |
| `picture_mesh` | `picture_mesh` | `MESH` | `mesh-picture-dependency-gated` | `expected-unsupported` | 100.00 | `unsupported.mesh.picture_dependency` | `n/a` |
| `skbug_13047` | `skbug_13047` | `MESH` | `mesh-basic-vertices` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `vertices_batching` | `vertices_batching` | `MESH` | `mesh-basic-vertices` | `instrumented-existing` | 66.21 | `none` | `n/a` |
| `vertices_collapsed` | `vertices_collapsed` | `MESH` | `mesh-basic-vertices` | `instrumented-existing` | 36.00 | `none` | `n/a` |
| `vertices` | `vertices` | `MESH` | `mesh-basic-vertices` | `instrumented-existing` | 58.26 | `none` | `n/a` |
| `vertices_perspective` | `vertices_perspective` | `MESH` | `mesh-perspective-gated` | `no-score` | n/a | `unsupported.mesh.perspective` | `generated-render-missing` |
| `annotated_text` | `annotated_text` | `TEXT` | `text-annotation-gated` | `expected-unsupported` | 22.24 | `unsupported.text.annotation` | `n/a` |
| `bigtext_crbug_1370488` | `bigtext_crbug_1370488` | `TEXT` | `text-large-or-cache` | `instrumented-existing` | 8.74 | `none` | `n/a` |
| `bigtext` | `bigtext` | `TEXT` | `text-large-or-cache` | `instrumented-existing` | 71.42 | `none` | `n/a` |
| `blob_rsxform_distortable` | `blob_rsxform_distortable` | `TEXT` | `text-rsxform-gated` | `expected-unsupported` | 78.00 | `unsupported.text.rsxform` | `n/a` |
| `blob_rsxform` | `blob_rsxform` | `TEXT` | `text-rsxform-gated` | `expected-unsupported` | 46.48 | `unsupported.text.rsxform` | `n/a` |
| `chrome_gradtext2` | `chrome_gradtext2` | `TEXT` | `text-shader-or-gradient-gated` | `expected-unsupported` | 97.31 | `unsupported.text.shader_or_gradient` | `n/a` |
| `cliperror` | `cliperror` | `TEXT` | `text-clip-interaction-gated` | `no-score` | n/a | `unsupported.text.clip_interaction` | `generated-render-missing` |
| `coloremoji_colrv0` | `coloremoji_colrv0` | `TEXT` | `text-emoji-gated` | `expected-unsupported` | 0.21 | `unsupported.text.emoji` | `n/a` |
| `coloremoji` | `coloremoji` | `TEXT` | `text-emoji-gated` | `no-score` | n/a | `unsupported.text.emoji` | `reference-missing` |
| `colorwheelnative` | `colorwheelnative` | `TEXT` | `text-basic-latin` | `no-score` | n/a | `none` | `generated-render-missing` |
| `coloremoji_blendmodes_colrv0` | `coloremoji_blendmodes_colrv0` | `TEXT` | `text-emoji-gated` | `expected-unsupported` | 0.03 | `unsupported.text.emoji` | `n/a` |
| `coloremoji_blendmodes` | `coloremoji_blendmodes` | `TEXT` | `text-emoji-gated` | `no-score` | n/a | `unsupported.text.emoji` | `reference-missing` |
| `colrv1_gradient_stops_repeat` | `colrv1_gradient_stops_repeat` | `TEXT` | `text-color-font-gated` | `expected-unsupported` | 97.44 | `unsupported.text.color_font` | `n/a` |
| `crbug_1073670` | `crbug_1073670` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 70.65 | `none` | `n/a` |
| `crbug_478659067` | `crbug_478659067` | `TEXT` | `text-basic-latin` | `no-score` | n/a | `none` | `reference-missing` |
| `dftext_blob_persp` | `dftext_blob_persp` | `TEXT` | `text-perspective-or-transform-gated` | `no-score` | n/a | `unsupported.text.perspective` | `generated-render-missing` |
| `dftext` | `dftext` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 74.46 | `none` | `n/a` |
| `drawTextRSXform` | `drawTextRSXform` | `TEXT` | `text-rsxform-gated` | `no-score` | n/a | `unsupported.text.rsxform` | `generated-render-missing` |
| `fontcache` | `fontcache` | `TEXT` | `text-large-or-cache` | `no-score` | n/a | `none` | `generated-render-missing` |
| `fontmgr_bounds` | `fontmgr_bounds` | `TEXT` | `text-font-manager-gated` | `expected-unsupported` | 82.23 | `unsupported.text.font_manager` | `n/a` |
| `fontmgr_iter` | `fontmgr_iter` | `TEXT` | `text-font-manager-gated` | `expected-unsupported` | 87.66 | `unsupported.text.font_manager` | `n/a` |
| `fontmgr_match` | `fontmgr_match` | `TEXT` | `text-font-manager-gated` | `expected-unsupported` | 94.68 | `unsupported.text.font_manager` | `n/a` |
| `font_palette_default` | `font_palette_default` | `TEXT` | `text-color-palette-gated` | `expected-unsupported` | 48.93 | `unsupported.text.palette` | `n/a` |
| `fontregen` | `fontregen` | `TEXT` | `text-font-fallback-gated` | `no-score` | n/a | `unsupported.text.font_fallback` | `generated-render-missing` |
| `fontscalerdistortable` | `fontscalerdistortable` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 90.96 | `none` | `n/a` |
| `fontscaler` | `fontscaler` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 77.51 | `none` | `n/a` |
| `gammagradienttext` | `gammagradienttext` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 75.85 | `none` | `n/a` |
| `gammatext_color_shader` | `gammatext_color_shader` | `TEXT` | `text-shader-or-gradient-gated` | `expected-unsupported` | 34.80 | `unsupported.text.shader_or_gradient` | `n/a` |
| `gammatext` | `gammatext` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 45.06 | `none` | `n/a` |
| `getpostextpath` | `getpostextpath` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 94.20 | `none` | `n/a` |
| `gradtext` | `gradtext` | `TEXT` | `text-shader-or-gradient-gated` | `expected-unsupported` | 77.41 | `unsupported.text.shader_or_gradient` | `n/a` |
| `largeglyphblur` | `largeglyphblur` | `TEXT` | `text-filter-or-blur-gated` | `expected-unsupported` | 30.24 | `unsupported.text.filter_or_blur` | `n/a` |
| `lcdoverlap` | `lcdoverlap` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 81.56 | `none` | `n/a` |
| `macaa_colors` | `macaa_colors` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 87.19 | `none` | `n/a` |
| `mixedtextblobs` | `mixedtextblobs` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 0.85 | `unsupported.text.glyph_cache` | `n/a` |
| `overdraw_text_xform` | `overdraw_text_xform` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 42.39 | `none` | `n/a` |
| `palette` | `palette` | `TEXT` | `text-color-palette-gated` | `expected-unsupported` | 97.99 | `unsupported.text.palette` | `n/a` |
| `pdf_never_embed` | `pdf_never_embed` | `TEXT` | `text-basic-latin` | `no-score` | n/a | `none` | `generated-render-missing` |
| `pdf_table_based_subset` | `pdf_table_based_subset` | `TEXT` | `text-basic-latin` | `no-score` | n/a | `none` | `generated-render-missing` |
| `persptext` | `persptext` | `TEXT` | `text-perspective-or-transform-gated` | `expected-unsupported` | 90.45 | `unsupported.text.perspective` | `n/a` |
| `persptext_minimal` | `persptext_minimal` | `TEXT` | `text-perspective-or-transform-gated` | `no-score` | n/a | `unsupported.text.perspective` | `generated-render-missing` |
| `rsx_blob_shader` | `rsx_blob_shader` | `TEXT` | `text-shader-or-gradient-gated` | `no-score` | n/a | `unsupported.text.shader_or_gradient` | `size-mismatch` |
| `scaledemojiperspective_colrv0` | `scaledemojiperspective_colrv0` | `TEXT` | `text-perspective-or-transform-gated` | `expected-unsupported` | 0.05 | `unsupported.text.perspective` | `n/a` |
| `scaledemojipos_colrv0` | `scaledemojipos_colrv0` | `TEXT` | `text-emoji-gated` | `expected-unsupported` | 0.02 | `unsupported.text.emoji` | `n/a` |
| `scaledemoji_colrv0` | `scaledemoji_colrv0` | `TEXT` | `text-emoji-gated` | `expected-unsupported` | 0.01 | `unsupported.text.emoji` | `n/a` |
| `scaledemoji_rendering` | `scaledemoji_rendering` | `TEXT` | `text-emoji-gated` | `expected-unsupported` | 0.08 | `unsupported.text.emoji` | `n/a` |
| `shadertext3` | `shadertext3` | `TEXT` | `text-shader-or-gradient-gated` | `expected-unsupported` | 55.00 | `unsupported.text.shader_or_gradient` | `n/a` |
| `skbug_12212` | `skbug_12212` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `skbug_257` | `skbug_257` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 40.58 | `none` | `n/a` |
| `skbug_5321` | `skbug_5321` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 94.95 | `none` | `n/a` |
| `skbug_8955` | `skbug_8955` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 97.69 | `none` | `n/a` |
| `slug` | `slug` | `TEXT` | `text-basic-latin` | `no-score` | n/a | `none` | `generated-render-missing` |
| `stroketext` | `stroketext` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 75.62 | `none` | `n/a` |
| `stroketext_native` | `stroketext_native` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 82.11 | `none` | `n/a` |
| `surfaceprops` | `surfaceprops` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 5.83 | `none` | `n/a` |
| `textblobblockreordering` | `textblobblockreordering` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 3.31 | `unsupported.text.glyph_cache` | `n/a` |
| `textblobcolortrans` | `textblobcolortrans` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 0.00 | `unsupported.text.glyph_cache` | `n/a` |
| `textblobgeometrychange` | `textblobgeometrychange` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 92.00 | `unsupported.text.glyph_cache` | `n/a` |
| `textblob` | `textblob` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 87.21 | `unsupported.text.glyph_cache` | `n/a` |
| `textblob_intercepts` | `textblob_intercepts` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 86.27 | `unsupported.text.glyph_cache` | `n/a` |
| `textblobmixedsizes` | `textblobmixedsizes` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 86.23 | `unsupported.text.glyph_cache` | `n/a` |
| `textblobrandomfont` | `textblobrandomfont` | `TEXT` | `text-blob-gated` | `no-score` | n/a | `unsupported.text.glyph_cache` | `reference-missing` |
| `textblobshader` | `textblobshader` | `TEXT` | `text-shader-or-gradient-gated` | `expected-unsupported` | 86.06 | `unsupported.text.shader_or_gradient` | `n/a` |
| `textblobtransforms` | `textblobtransforms` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 0.00 | `unsupported.text.glyph_cache` | `n/a` |
| `textblobuseaftergpufree` | `textblobuseaftergpufree` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 93.78 | `unsupported.text.glyph_cache` | `n/a` |
| `fancyblobunderline` | `fancyblobunderline` | `TEXT` | `text-blob-gated` | `expected-unsupported` | 88.76 | `unsupported.text.glyph_cache` | `n/a` |
| `textfilter_color` | `textfilter_color` | `TEXT` | `text-filter-or-blur-gated` | `expected-unsupported` | 80.49 | `unsupported.text.filter_or_blur` | `n/a` |
| `textfilter_image` | `textfilter_image` | `TEXT` | `text-filter-or-blur-gated` | `expected-unsupported` | 72.78 | `unsupported.text.filter_or_blur` | `n/a` |
| `text_scale_skew` | `text_scale_skew` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 81.59 | `none` | `n/a` |
| `typefacerendering` | `typefacerendering` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 88.97 | `none` | `n/a` |
| `typefacerendering_pfa` | `typefacerendering_pfa` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 84.60 | `none` | `n/a` |
| `typefacerendering_pfb` | `typefacerendering_pfb` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 84.60 | `none` | `n/a` |
| `typefacestyles` | `typefacestyles` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 96.77 | `none` | `n/a` |
| `typefacestyles_kerning` | `typefacestyles_kerning` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 95.10 | `none` | `n/a` |
| `typeface_styling` | `typeface_styling` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 83.90 | `none` | `n/a` |
| `user_typeface` | `user_typeface` | `TEXT` | `text-basic-latin` | `instrumented-existing` | 80.65 | `none` | `n/a` |
| `variedtext` | `variedtext` | `TEXT` | `text-basic-latin` | `no-score` | n/a | `none` | `reference-missing` |

## Regeneration Notes

- Run `:integration-tests:skia:generateSkiaDashboard` before generating text-mesh-family evidence.
- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
- Non-TEXT/MESH families and cross-family dependencies are intentionally out of this evidence wave.
