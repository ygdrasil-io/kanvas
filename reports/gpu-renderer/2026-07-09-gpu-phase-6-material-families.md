# GPU Phase 6 Material Families Evidence

## Summary

- Total GRADIENT + RUNTIME_EFFECT + COLOR rows: 101
- Families: {COLOR=20, GRADIENT=56, RUNTIME_EFFECT=25}
- Classifications: {expected-unsupported=15, instrumented-existing=51, no-score=35}
- Subfamilies: {color-alpha=1, color-filter-gated=1, color-processor-gated=1, color-solid=15, color-space-gated=2, gradient-color-space-gated=4, gradient-conical=5, gradient-hard-stops=3, gradient-linear=22, gradient-local-matrix=1, gradient-many-stops-gated=3, gradient-perspective-gated=4, gradient-radial=9, gradient-sweep=2, gradient-tile-mode=3, runtime-effect-color-filter=2, runtime-effect-registered=20, runtime-effect-unregistered-gated=3}

## Family Deltas

- Baseline source: `2026-07-09 local dashboard before material-family wave`
- Current dashboard: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json` (2026-07-09T02:48:34.808845)

| Family | Baseline | Current | Delta |
|---|---:|---:|---:|
| `COLOR` | 20 | 20 | +0 |
| `GRADIENT` | 56 | 56 | +0 |
| `RUNTIME_EFFECT` | 25 | 25 | +0 |

## Non-Claims

- No broad shader support is claimed from classification alone.
- No dynamic SkSL compiler or arbitrary SkRuntimeEffect support is added.
- Blend composition, compose-shader pipelines, filter-graph, saveLayer, and destination-read rows remain outside this material-family wave.
- Rows without route and material diagnostics remain instrumented rather than promoted.

## Reason Code Taxonomy

- Material `unsupported.material.*` and `unsupported.runtime_effect.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.

## Rows

| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback | No Score Cause |
|---|---|---|---|---|---:|---|---|
| `color` | `color` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `const_color_processor` | `const_color_processor` | `COLOR` | `color-processor-gated` | `no-score` | n/a | `unsupported.material.color_processor` | `reference-missing` |
| `dark` | `dark` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `debug` | `debug` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `default` | `default` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `encodesrgb` | `encodesrgb` | `COLOR` | `color-space-gated` | `no-score` | n/a | `unsupported.material.color_space` | `reference-missing` |
| `filter` | `filter` | `COLOR` | `color-filter-gated` | `no-score` | n/a | `unsupported.material.color_filter` | `reference-missing` |
| `high` | `high` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `light` | `light` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `low` | `low` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `med` | `med` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `name` | `name` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `none` | `none` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `one` | `one` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `orientation` | `orientation` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `p3_ovals` | `p3_ovals` | `COLOR` | `color-space-gated` | `expected-unsupported` | 85.03 | `unsupported.material.color_space` | `n/a` |
| `paint_alpha_normals_rt` | `paint_alpha_normals_rt` | `COLOR` | `color-alpha` | `no-score` | n/a | `none` | `generated-render-missing` |
| `raster` | `raster` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `rect` | `rect` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `shader` | `shader` | `COLOR` | `color-solid` | `no-score` | n/a | `none` | `reference-missing` |
| `alphagradients` | `alphagradients` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 39.62 | `none` | `n/a` |
| `analytic_gradients` | `analytic_gradients` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 59.33 | `none` | `n/a` |
| `bug6643` | `bug6643` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 1.38 | `none` | `n/a` |
| `clamped_gradients` | `clamped_gradients` | `GRADIENT` | `gradient-tile-mode` | `expected-unsupported` | 3.00 | `unsupported.material.gradient_tile_mode` | `n/a` |
| `gradients_2pt_conical_inside_nodither` | `gradients_2pt_conical_inside_nodither` | `GRADIENT` | `gradient-conical` | `instrumented-existing` | 0.02 | `none` | `n/a` |
| `gradients_2pt_conical_outside` | `gradients_2pt_conical_outside` | `GRADIENT` | `gradient-conical` | `instrumented-existing` | 0.01 | `none` | `n/a` |
| `conicalgradients` | `conicalgradients` | `GRADIENT` | `gradient-conical` | `no-score` | n/a | `none` | `reference-missing` |
| `crbug_938592` | `crbug_938592` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 88.00 | `none` | `n/a` |
| `degenerate_gradients` | `degenerate_gradients` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 65.59 | `none` | `n/a` |
| `emptyshader` | `emptyshader` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.89 | `none` | `n/a` |
| `fillrect_gradient` | `fillrect_gradient` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 33.60 | `none` | `n/a` |
| `gradient_dirty_laundry` | `gradient_dirty_laundry` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.55 | `none` | `n/a` |
| `gradients_many` | `gradients_many` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 42.64 | `none` | `n/a` |
| `gradient_many_hard_stops` | `gradient_many_hard_stops` | `GRADIENT` | `gradient-hard-stops` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `gradient_many_stops` | `gradient_many_stops` | `GRADIENT` | `gradient-many-stops-gated` | `expected-unsupported` | 0.00 | `unsupported.material.gradient_many_stops` | `n/a` |
| `gradient_matrix` | `gradient_matrix` | `GRADIENT` | `gradient-local-matrix` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `gradients_alpha_many_stops` | `gradients_alpha_many_stops` | `GRADIENT` | `gradient-many-stops-gated` | `expected-unsupported` | 0.00 | `unsupported.material.gradient_many_stops` | `n/a` |
| `gradients_color_space` | `gradients_color_space` | `GRADIENT` | `gradient-color-space-gated` | `expected-unsupported` | 0.25 | `unsupported.material.color_space` | `n/a` |
| `gradients_color_space_many_stops` | `gradients_color_space_many_stops` | `GRADIENT` | `gradient-many-stops-gated` | `expected-unsupported` | 0.00 | `unsupported.material.gradient_many_stops` | `n/a` |
| `gradients_color_space_tilemode` | `gradients_color_space_tilemode` | `GRADIENT` | `gradient-color-space-gated` | `expected-unsupported` | 0.00 | `unsupported.material.color_space` | `n/a` |
| `gradients_degenerate_2pt` | `gradients_degenerate_2pt` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `gradients_dup_color_stops` | `gradients_dup_color_stops` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 26.60 | `none` | `n/a` |
| `gradients` | `gradients` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.63 | `none` | `n/a` |
| `gradients_hue_method` | `gradients_hue_method` | `GRADIENT` | `gradient-color-space-gated` | `expected-unsupported` | 0.00 | `unsupported.material.color_space` | `n/a` |
| `gradients_interesting` | `gradients_interesting` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 13.46 | `none` | `n/a` |
| `gradients_local_perspective` | `gradients_local_perspective` | `GRADIENT` | `gradient-perspective-gated` | `expected-unsupported` | 2.05 | `unsupported.material.perspective_shader` | `n/a` |
| `gradients_no_texture` | `gradients_no_texture` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.13 | `none` | `n/a` |
| `gradients_powerless_hue` | `gradients_powerless_hue` | `GRADIENT` | `gradient-color-space-gated` | `no-score` | n/a | `unsupported.material.color_space` | `reference-missing` |
| `gradients_view_perspective` | `gradients_view_perspective` | `GRADIENT` | `gradient-perspective-gated` | `no-score` | n/a | `unsupported.material.perspective_shader` | `generated-render-missing` |
| `hardstop_gradients` | `hardstop_gradients` | `GRADIENT` | `gradient-hard-stops` | `instrumented-existing` | 4.78 | `none` | `n/a` |
| `hardstop_gradients_many` | `hardstop_gradients_many` | `GRADIENT` | `gradient-hard-stops` | `instrumented-existing` | 10.79 | `none` | `n/a` |
| `linear_gradient` | `linear_gradient` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 10.13 | `none` | `n/a` |
| `linear_gradient_rt` | `linear_gradient_rt` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 13.85 | `none` | `n/a` |
| `linear_gradient_tiny` | `linear_gradient_tiny` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 60.00 | `none` | `n/a` |
| `persp_shaders_bw` | `persp_shaders_bw` | `GRADIENT` | `gradient-perspective-gated` | `no-score` | n/a | `unsupported.material.perspective_shader` | `generated-render-missing` |
| `persp_shaders_aa` | `persp_shaders_aa` | `GRADIENT` | `gradient-perspective-gated` | `expected-unsupported` | 32.23 | `unsupported.material.perspective_shader` | `n/a` |
| `radial_gradient2` | `radial_gradient2` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 55.88 | `none` | `n/a` |
| `radial_gradient3` | `radial_gradient3` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 12.21 | `none` | `n/a` |
| `radial_gradient3_nodither` | `radial_gradient3_nodither` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 12.19 | `none` | `n/a` |
| `radial_gradient4` | `radial_gradient4` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 37.70 | `none` | `n/a` |
| `radial_gradient4_nodither` | `radial_gradient4_nodither` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 37.70 | `none` | `n/a` |
| `radial_gradient` | `radial_gradient` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 38.55 | `none` | `n/a` |
| `radial_gradient_precision` | `radial_gradient_precision` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 6.60 | `none` | `n/a` |
| `scaled_tilemode_gradient` | `scaled_tilemode_gradient` | `GRADIENT` | `gradient-tile-mode` | `expected-unsupported` | 52.41 | `unsupported.material.gradient_tile_mode` | `n/a` |
| `shallow_gradient_conical` | `shallow_gradient_conical` | `GRADIENT` | `gradient-conical` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallow_gradient_conical_nodither` | `shallow_gradient_conical_nodither` | `GRADIENT` | `gradient-conical` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallowgradient` | `shallowgradient` | `GRADIENT` | `gradient-linear` | `no-score` | n/a | `none` | `reference-missing` |
| `shallow_gradient_linear` | `shallow_gradient_linear` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallow_gradient_linear_nodither` | `shallow_gradient_linear_nodither` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallow_gradient_radial` | `shallow_gradient_radial` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallow_gradient_radial_nodither` | `shallow_gradient_radial_nodither` | `GRADIENT` | `gradient-radial` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallow_gradient_sweep` | `shallow_gradient_sweep` | `GRADIENT` | `gradient-sweep` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `shallow_gradient_sweep_nodither` | `shallow_gradient_sweep_nodither` | `GRADIENT` | `gradient-sweep` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `small_color_stop` | `small_color_stop` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `sweep_tiling` | `sweep_tiling` | `GRADIENT` | `gradient-tile-mode` | `expected-unsupported` | 13.04 | `unsupported.material.gradient_tile_mode` | `n/a` |
| `testgradient` | `testgradient` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 95.61 | `none` | `n/a` |
| `AlternateLuma` | `AlternateLuma` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `arithmode` | `arithmode` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `colorcubecolorfilterrt` | `colorcubecolorfilterrt` | `RUNTIME_EFFECT` | `runtime-effect-color-filter` | `no-score` | n/a | `none` | `reference-missing` |
| `color_cube_rt` | `color_cube_rt` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `composeCF` | `composeCF` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `runtime_intrinsics_common` | `runtime_intrinsics_common` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `runtime_intrinsics_exponential` | `runtime_intrinsics_exponential` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `runtime_intrinsics_geometric` | `runtime_intrinsics_geometric` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `runtime_intrinsics_matrix` | `runtime_intrinsics_matrix` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `runtime_intrinsics_relational` | `runtime_intrinsics_relational` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `runtime_intrinsics_trig` | `runtime_intrinsics_trig` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `kawase_blur_rt` | `kawase_blur_rt` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 76.67 | `none` | `n/a` |
| `lineargradientrt` | `lineargradientrt` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `reference-missing` |
| `lumafilter` | `lumafilter` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `size-mismatch` |
| `rippleshader` | `rippleshader` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `rtif_distort` | `rtif_distort` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `rtif_unsharp` | `rtif_unsharp` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.00 | `none` | `n/a` |
| `runtimecolorfilter` | `runtimecolorfilter` | `RUNTIME_EFFECT` | `runtime-effect-color-filter` | `instrumented-existing` | 16.67 | `none` | `n/a` |
| `runtimefunctions` | `runtimefunctions` | `RUNTIME_EFFECT` | `runtime-effect-unregistered-gated` | `expected-unsupported` | 10.26 | `unsupported.runtime_effect.unregistered_descriptor` | `n/a` |
| `runtime_shader` | `runtime_shader` | `RUNTIME_EFFECT` | `runtime-effect-unregistered-gated` | `expected-unsupported` | 50.00 | `unsupported.runtime_effect.unregistered_descriptor` | `n/a` |
| `simplert` | `simplert` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `no-score` | n/a | `none` | `reference-missing` |
| `spiral_rt` | `spiral_rt` | `RUNTIME_EFFECT` | `runtime-effect-unregistered-gated` | `expected-unsupported` | 0.00 | `unsupported.runtime_effect.unregistered_descriptor` | `n/a` |
| `threshold_rt` | `threshold_rt` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 0.01 | `none` | `n/a` |
| `unsharp_rt` | `unsharp_rt` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 1.02 | `none` | `n/a` |
| `workingspace` | `workingspace` | `RUNTIME_EFFECT` | `runtime-effect-registered` | `instrumented-existing` | 14.46 | `none` | `n/a` |

## Regeneration Notes

- Run `:integration-tests:skia:generateSkiaDashboard` before generating material-family evidence.
- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
- Non-material and composition/filter families are intentionally out of this material-family wave.

## Validation

- `:integration-tests:skia-evidence:test --rerun-tasks` passed.
- `:integration-tests:skia-evidence:generateGpuPhase6MaterialFamiliesEvidence --rerun-tasks` regenerated `evidence.json`, `classification.csv`, and this report.
- `git diff --check` passed.
