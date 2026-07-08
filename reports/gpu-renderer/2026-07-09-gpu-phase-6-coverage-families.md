# GPU Phase 6 Coverage Families Evidence

## Summary

- Total PATH + CLIP rows: 141
- Families: {CLIP=47, PATH=94}
- Classifications: {instrumented-existing=76, no-score=65}
- Subfamilies: {clip-complex-gated=9, clip-convex=1, clip-inverse-gated=5, clip-large-budget-gated=2, clip-nested-bounded=19, clip-path-aa-gated=2, clip-perspective-gated=1, clip-rect=4, clip-rrect=4, path-dash-gated=9, path-fill-concave=16, path-fill-convex=4, path-fill-simple=34, path-hairline-gated=9, path-large-budget-gated=2, path-ops-gated=6, path-perspective-gated=1, path-shader-material-gated=2, path-stroke-basic=2, path-stroke-caps-joins=9}

## Family Deltas

- Baseline source: `2026-07-08 local dashboard before #2010`
- Current dashboard: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json` (2026-07-09T01:11:39.100531)

| Family | Baseline | Current | Delta |
|---|---:|---:|---:|
| `CLIP` | 32 | 47 | +15 |
| `PATH` | 58 | 94 | +36 |

## Non-Claims

- No broad Path AA support is claimed from classification alone.
- No broad clip stack, inverse clip, perspective clip, path boolean, or global coverage budget support is added.
- Rows without route diagnostics remain instrumented rather than promoted.

## Reason Code Taxonomy

- Coverage `unsupported.coverage.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.

## Rows

| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback |
|---|---|---|---|---|---:|---|
| `aaclip` | `aaclip` | `CLIP` | `clip-path-aa-gated` | `instrumented-existing` | 89.64 | `none` |
| `bug339297_as_clip` | `bug339297_as_clip` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `circular-clips` | `circular-clips` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 11.86 | `none` |
| `clipcubic` | `clipcubic` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 66.87 | `none` |
| `clipdrawdraw` | `clipdrawdraw` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 29.04 | `none` |
| `cliplargerect` | `cliplargerect` | `CLIP` | `clip-large-budget-gated` | `instrumented-existing` | 52.73 | `none` |
| `clip_region` | `clip_region` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 0.00 | `none` |
| `clip_sierpinski_region` | `clip_sierpinski_region` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 18.88 | `none` |
| `clip_strokerect` | `clip_strokerect` | `CLIP` | `clip-rect` | `instrumented-existing` | 88.31 | `none` |
| `clipsuperrrect` | `clipsuperrrect` | `CLIP` | `clip-rrect` | `no-score` | n/a | `none` |
| `complexclip3_complex` | `complexclip3_complex` | `CLIP` | `clip-complex-gated` | `no-score` | n/a | `unsupported.coverage.complex_clip` |
| `complexclip3_simple` | `complexclip3_simple` | `CLIP` | `clip-complex-gated` | `no-score` | n/a | `unsupported.coverage.complex_clip` |
| `complexclip4_aa` | `complexclip4_aa` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 0.00 | `none` |
| `complexclip4_bw` | `complexclip4_bw` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 0.00 | `none` |
| `complexclip_aa` | `complexclip_aa` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 9.87 | `none` |
| `complexclip_aa_invert` | `complexclip_aa_invert` | `CLIP` | `clip-inverse-gated` | `instrumented-existing` | 3.49 | `none` |
| `complexclip_aa_layer` | `complexclip_aa_layer` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 4.83 | `none` |
| `complexclip_aa_layer_invert` | `complexclip_aa_layer_invert` | `CLIP` | `clip-inverse-gated` | `instrumented-existing` | 2.61 | `none` |
| `complexclip_blur_tiled` | `complexclip_blur_tiled` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 3.35 | `none` |
| `complexclip_bw` | `complexclip_bw` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 10.41 | `none` |
| `complexclip_bw_invert` | `complexclip_bw_invert` | `CLIP` | `clip-inverse-gated` | `instrumented-existing` | 3.64 | `none` |
| `complexclip_bw_layer` | `complexclip_bw_layer` | `CLIP` | `clip-complex-gated` | `instrumented-existing` | 5.10 | `none` |
| `complexclip_bw_layer_invert` | `complexclip_bw_layer_invert` | `CLIP` | `clip-inverse-gated` | `instrumented-existing` | 2.75 | `none` |
| `convex_poly_clip` | `convex_poly_clip` | `CLIP` | `clip-convex` | `instrumented-existing` | 0.04 | `none` |
| `crbug_892988` | `crbug_892988` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 12.15 | `none` |
| `croppedrects` | `croppedrects` | `CLIP` | `clip-rect` | `instrumented-existing` | 32.92 | `none` |
| `distantclip` | `distantclip` | `CLIP` | `clip-large-budget-gated` | `instrumented-existing` | 0.00 | `none` |
| `fast_constraint_red_is_allowed` | `fast_constraint_red_is_allowed` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `fast_constraint_red_is_allowed_manual` | `fast_constraint_red_is_allowed_manual` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `inverseclip` | `inverseclip` | `CLIP` | `clip-inverse-gated` | `instrumented-existing` | 57.10 | `none` |
| `manypathatlases_128` | `manypathatlases_128` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `manypathatlases_2048` | `manypathatlases_2048` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `pdf_crbug_772685` | `pdf_crbug_772685` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 63.35 | `none` |
| `perspective_clip` | `perspective_clip` | `CLIP` | `clip-perspective-gated` | `instrumented-existing` | 66.21 | `none` |
| `rrect_clip_aa` | `rrect_clip_aa` | `CLIP` | `clip-rrect` | `instrumented-existing` | 0.27 | `none` |
| `rrect_clip_bw` | `rrect_clip_bw` | `CLIP` | `clip-rrect` | `instrumented-existing` | 0.27 | `none` |
| `rrect_clip_draw_paint` | `rrect_clip_draw_paint` | `CLIP` | `clip-rrect` | `instrumented-existing` | 0.00 | `none` |
| `simpleaaclip_path` | `simpleaaclip_path` | `CLIP` | `clip-path-aa-gated` | `instrumented-existing` | 1.96 | `none` |
| `simpleaaclip_rect` | `simpleaaclip_rect` | `CLIP` | `clip-rect` | `instrumented-existing` | 1.97 | `none` |
| `simpleclip` | `simpleclip` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `skbug1719` | `skbug1719` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 0.01 | `none` |
| `skbug_9319` | `skbug_9319` | `CLIP` | `clip-nested-bounded` | `instrumented-existing` | 84.23 | `none` |
| `strict_constraint_batch_no_red_allowed` | `strict_constraint_batch_no_red_allowed` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `strict_constraint_batch_no_red_allowed_manual` | `strict_constraint_batch_no_red_allowed_manual` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `strict_constraint_no_red_allowed` | `strict_constraint_no_red_allowed` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `strict_constraint_no_red_allowed_manual` | `strict_constraint_no_red_allowed_manual` | `CLIP` | `clip-nested-bounded` | `no-score` | n/a | `none` |
| `windowrectangles` | `windowrectangles` | `CLIP` | `clip-rect` | `instrumented-existing` | 0.00 | `none` |
| `aa_rect_effect` | `aa_rect_effect` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `bezier_conic_effects` | `bezier_conic_effects` | `PATH` | `path-fill-concave` | `no-score` | n/a | `none` |
| `bezier_quad_effects` | `bezier_quad_effects` | `PATH` | `path-fill-concave` | `no-score` | n/a | `none` |
| `bug41422450` | `bug41422450` | `PATH` | `path-fill-simple` | `instrumented-existing` | 100.00 | `none` |
| `ctmpatheffect` | `ctmpatheffect` | `PATH` | `path-dash-gated` | `instrumented-existing` | 98.79 | `none` |
| `big_rrect_circle_aa_effect` | `big_rrect_circle_aa_effect` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `circle` | `circle` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `circle_sizes` | `circle_sizes` | `PATH` | `path-fill-simple` | `instrumented-existing` | 92.35 | `none` |
| `circular_arcs` | `circular_arcs` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `circular_corner` | `circular_corner` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `clockwise` | `clockwise` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `concavepaths` | `concavepaths` | `PATH` | `path-fill-concave` | `instrumented-existing` | 95.41 | `none` |
| `conicpaths` | `conicpaths` | `PATH` | `path-fill-concave` | `no-score` | n/a | `none` |
| `convex_lineonly_paths` | `convex_lineonly_paths` | `PATH` | `path-fill-convex` | `no-score` | n/a | `none` |
| `convex_lineonly_paths_stroke_and_fill` | `convex_lineonly_paths_stroke_and_fill` | `PATH` | `path-stroke-caps-joins` | `no-score` | n/a | `none` |
| `convexpaths` | `convexpaths` | `PATH` | `path-fill-convex` | `instrumented-existing` | 0.00 | `none` |
| `convex_poly_effect` | `convex_poly_effect` | `PATH` | `path-fill-convex` | `no-score` | n/a | `none` |
| `convex-polygon-inset` | `convex-polygon-inset` | `PATH` | `path-fill-convex` | `no-score` | n/a | `none` |
| `crbug_640176` | `crbug_640176` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `crbug_691386` | `crbug_691386` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `cubicclosepath` | `cubicclosepath` | `PATH` | `path-fill-concave` | `instrumented-existing` | 79.88 | `none` |
| `cubicpath` | `cubicpath` | `PATH` | `path-fill-concave` | `instrumented-existing` | 79.94 | `none` |
| `cubicpath_shader` | `cubicpath_shader` | `PATH` | `path-shader-material-gated` | `no-score` | n/a | `unsupported.material.shader_on_path` |
| `dashcircle` | `dashcircle` | `PATH` | `path-dash-gated` | `instrumented-existing` | 41.44 | `none` |
| `dashtextcaps` | `dashtextcaps` | `PATH` | `path-dash-gated` | `instrumented-existing` | 90.10 | `none` |
| `dashing5_aa` | `dashing5_aa` | `PATH` | `path-dash-gated` | `instrumented-existing` | 0.00 | `none` |
| `dashing` | `dashing` | `PATH` | `path-dash-gated` | `instrumented-existing` | 89.81 | `none` |
| `drawlines_with_local_matrix` | `drawlines_with_local_matrix` | `PATH` | `path-fill-simple` | `instrumented-existing` | 0.00 | `none` |
| `drawregion` | `drawregion` | `PATH` | `path-ops-gated` | `no-score` | n/a | `unsupported.coverage.path_ops` |
| `drawregionmodes` | `drawregionmodes` | `PATH` | `path-ops-gated` | `no-score` | n/a | `unsupported.coverage.path_ops` |
| `ellipse` | `ellipse` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `elliptical_corner` | `elliptical_corner` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `fancy_gradients` | `fancy_gradients` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `fatpathfill` | `fatpathfill` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `filltypespersp` | `filltypespersp` | `PATH` | `path-perspective-gated` | `instrumented-existing` | 11.61 | `none` |
| `inner_join_geometry` | `inner_join_geometry` | `PATH` | `path-stroke-caps-joins` | `no-score` | n/a | `none` |
| `lattice2` | `lattice2` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `lineclosepath` | `lineclosepath` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `linepath` | `linepath` | `PATH` | `path-stroke-basic` | `instrumented-existing` | 77.09 | `none` |
| `longrect_dash` | `longrect_dash` | `PATH` | `path-dash-gated` | `no-score` | n/a | `unsupported.coverage.dash_pattern` |
| `macaatest` | `macaatest` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `mandoline` | `mandoline` | `PATH` | `path-fill-concave` | `instrumented-existing` | 89.77 | `none` |
| `manycircles` | `manycircles` | `PATH` | `path-fill-concave` | `instrumented-existing` | 0.00 | `none` |
| `manypathatlases` | `manypathatlases` | `PATH` | `path-large-budget-gated` | `no-score` | n/a | `unsupported.coverage.verb_budget_exceeded` |
| `nested` | `nested` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `nested#2` | `nested` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `nonclosedpaths` | `nonclosedpaths` | `PATH` | `path-fill-simple` | `instrumented-existing` | 64.84 | `none` |
| `OverStroke` | `OverStroke` | `PATH` | `path-stroke-caps-joins` | `no-score` | n/a | `none` |
| `parsedpaths` | `parsedpaths` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `path_huge_aa_manual` | `path_huge_aa_manual` | `PATH` | `path-large-budget-gated` | `no-score` | n/a | `unsupported.coverage.verb_budget_exceeded` |
| `path_mask_cache` | `path_mask_cache` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `PathMeasure_explosion` | `PathMeasure_explosion` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `pathops_blend` | `pathops_blend` | `PATH` | `path-ops-gated` | `no-score` | n/a | `unsupported.coverage.path_ops` |
| `pathopsinverse` | `pathopsinverse` | `PATH` | `path-ops-gated` | `no-score` | n/a | `unsupported.coverage.path_ops` |
| `pathops_skbug_10155` | `pathops_skbug_10155` | `PATH` | `path-ops-gated` | `no-score` | n/a | `unsupported.coverage.path_ops` |
| `path-reverse` | `path-reverse` | `PATH` | `path-fill-simple` | `instrumented-existing` | 88.10 | `none` |
| `path_stroke_clip_crbug1070835` | `path_stroke_clip_crbug1070835` | `PATH` | `path-stroke-caps-joins` | `no-score` | n/a | `none` |
| `points` | `points` | `PATH` | `path-stroke-basic` | `instrumented-existing` | 38.75 | `none` |
| `poly2poly` | `poly2poly` | `PATH` | `path-ops-gated` | `no-score` | n/a | `unsupported.coverage.path_ops` |
| `polygons` | `polygons` | `PATH` | `path-fill-concave` | `instrumented-existing` | 72.47 | `none` |
| `preservefillrule_big` | `preservefillrule_big` | `PATH` | `path-fill-concave` | `instrumented-existing` | 52.72 | `none` |
| `preservefillrule` | `preservefillrule` | `PATH` | `path-fill-concave` | `no-score` | n/a | `none` |
| `preservefillrule_little` | `preservefillrule_little` | `PATH` | `path-fill-concave` | `instrumented-existing` | 45.38 | `none` |
| `quadclosepath` | `quadclosepath` | `PATH` | `path-fill-concave` | `instrumented-existing` | 80.20 | `none` |
| `quadpath` | `quadpath` | `PATH` | `path-fill-concave` | `instrumented-existing` | 80.26 | `none` |
| `roundrects` | `roundrects` | `PATH` | `path-fill-concave` | `no-score` | n/a | `none` |
| `shadow_utils_directional` | `shadow_utils_directional` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `sharedcorners` | `sharedcorners` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `simpleshapes_bw` | `simpleshapes_bw` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `simpleshapes` | `simpleshapes` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `stlouisarch` | `stlouisarch` | `PATH` | `path-fill-concave` | `instrumented-existing` | 94.70 | `none` |
| `stroke_rect_shader` | `stroke_rect_shader` | `PATH` | `path-shader-material-gated` | `instrumented-existing` | 52.76 | `none` |
| `strokedline_caps` | `strokedline_caps` | `PATH` | `path-stroke-caps-joins` | `instrumented-existing` | 94.81 | `none` |
| `strokes3` | `strokes3` | `PATH` | `path-stroke-caps-joins` | `no-score` | n/a | `none` |
| `zero_control_stroke` | `zero_control_stroke` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 93.90 | `none` |
| `strokes_round` | `strokes_round` | `PATH` | `path-stroke-caps-joins` | `instrumented-existing` | 21.65 | `none` |
| `teenyStrokes` | `teenyStrokes` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 98.22 | `none` |
| `thin_aa_dash_lines` | `thin_aa_dash_lines` | `PATH` | `path-dash-gated` | `instrumented-existing` | 80.56 | `none` |
| `thinconcavepaths` | `thinconcavepaths` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 96.95 | `none` |
| `thinrects` | `thinrects` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 90.06 | `none` |
| `thinroundrects` | `thinroundrects` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 89.54 | `none` |
| `thinstrokedrects` | `thinstrokedrects` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 80.21 | `none` |
| `tinyanglearcs` | `tinyanglearcs` | `PATH` | `path-fill-simple` | `instrumented-existing` | 99.91 | `none` |
| `trickycubicstrokes_largeradius` | `trickycubicstrokes_largeradius` | `PATH` | `path-stroke-caps-joins` | `instrumented-existing` | 73.93 | `none` |
| `trimpatheffect` | `trimpatheffect` | `PATH` | `path-dash-gated` | `instrumented-existing` | 79.13 | `none` |
| `widebuttcaps` | `widebuttcaps` | `PATH` | `path-stroke-caps-joins` | `instrumented-existing` | 31.29 | `none` |
| `zero_control_stroke#2` | `zero_control_stroke` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 93.90 | `none` |
| `zeroPath` | `zeroPath` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 97.40 | `none` |
| `zero_length_paths_aa` | `zero_length_paths_aa` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `zero_length_paths_bw` | `zero_length_paths_bw` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `zero_length_paths_dbl_aa` | `zero_length_paths_dbl_aa` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `zero_length_paths_dbl_bw` | `zero_length_paths_dbl_bw` | `PATH` | `path-fill-simple` | `no-score` | n/a | `none` |
| `zerolinedash` | `zerolinedash` | `PATH` | `path-dash-gated` | `no-score` | n/a | `unsupported.coverage.dash_pattern` |
| `zerolinestroke` | `zerolinestroke` | `PATH` | `path-hairline-gated` | `instrumented-existing` | 74.06 | `none` |

## Regeneration Notes

- Run `:integration-tests:skia:generateSkiaDashboard` before generating coverage-family evidence.
- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
- Source counts changed after `#2010`; the regenerated dashboard is the evidence source of truth.

## Validation

- `:integration-tests:skia-evidence:test` passed for coverage and image evidence tests.
- `generateGpuPhase6CoverageFamiliesEvidence` regenerated dashboard-backed PATH + CLIP evidence.
- `git diff --check` reported no whitespace errors.
- This wave adds evidence and classification only; renderer fixes remain out of scope.
