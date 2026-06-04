# FOR-342 CircularArcsStrokeButt Adjacent F16 Color Policy Scoped Implementation

Linear: `FOR-342`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_PARTIAL_REQUIRES_SAFER_ROUTE`

FOR-342 reuses the FOR-341 authorization for
`adjacent_circular_arcs_stroke_butt_f16_straight_srgb_quantized_alpha_src_over_white` and evaluates the smallest possible implementation
route for the exact two adjacent `CircularArcsStrokeButt` F16 cells. No renderer
behavior is changed in this ticket.

## Decision

The scoped renderer implementation is refused for this ticket because the safe
intervention point is not bounded enough. The only concrete hooks are global
F16 color conversion, global F16 blending, and the global
`SkBitmap.getPixelAsSrgb` encoded-export boundary. A renderer change limited to
these two cells would require fixture/coordinate-specific branching, while a
general change would be a broader color-management migration.

The stable follow-up route is to introduce an explicit color-policy boundary
with broader F16 evidence before changing global F16 renderer behavior.

## Residuals

- Samples: `12`
- Stroke samples: `10`
- Old/current Skia-over-white residual: `375`
- Actual-new renderer residual: `375`
- Candidate-new policy residual: `0`
- Candidate residual reduction if a safe route exists: `375`
- Raw transparent PNG residual rejected: `7065`

## Old/New Pixel Evidence

`actual new` equals `old current` because the renderer was intentionally left
unchanged. `candidate new` records the FOR-341 authorized policy result and
matches Skia-over-white for all ten stroke samples.

| group | sample | zone | old current | actual new | candidate new | Skia over white | old abs | actual abs | candidate abs |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| adjacent_arc_stroke_start0_sweep45_target | sweep45_background_top_left | background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_blue_top_stroke_center | stroke-center | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | [155, 155, 255, 255] | 37 | 37 | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_blue_left_stroke_center | stroke-center | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | [155, 155, 255, 255] | 37 | 37 | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_red_right_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 179, 169, 255] | [255, 155, 155, 255] | [255, 155, 155, 255] | 38 | 38 | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_red_inner_sweep_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 179, 169, 255] | [255, 155, 155, 255] | [255, 155, 155, 255] | 38 | 38 | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_blue_bottom_stroke_center | stroke-center | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | [155, 155, 255, 255] | 37 | 37 | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_background_top_left | background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_blue_top_stroke_center | stroke-center | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | [155, 155, 255, 255] | 37 | 37 | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_red_right_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 179, 169, 255] | [255, 155, 155, 255] | [255, 155, 155, 255] | 38 | 38 | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_red_bottom_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 179, 169, 255] | [255, 155, 155, 255] | [255, 155, 155, 255] | 38 | 38 | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_red_inner_sweep_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 179, 169, 255] | [255, 155, 155, 255] | [255, 155, 155, 255] | 38 | 38 | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_blue_left_stroke_center | stroke-center | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | [155, 155, 255, 255] | 37 | 37 | 0 |

## Rejected Routes

- Fixture or coordinate-specific renderer branch for only the two
  FOR-339/FOR-340 cells.
- Global `colorToF16Premul` change.
- Global `blendF16PremulMode` change.
- Global `SkBitmap.getPixelAsSrgb` export-boundary change.
- Selected-cell extrapolation, crop evidence, or FOR-327 substitution.

## Non-goals Preserved

- No change to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No GPU/WGSL, geometry, coverage, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-341 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342/circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342.json`
- Validator: `scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-342-circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation.md`

## Validation

- `rtk python3 scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py`
- `rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py`
- `rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- `rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
