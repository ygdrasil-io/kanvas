# FOR-341 CircularArcsStrokeButt Adjacent F16 Color Policy Decision

Linear: `FOR-341`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION`

FOR-341 reads the FOR-339 runtime trace and the FOR-340 isolated upstream Skia
references for the exact adjacent `CircularArcsStrokeButt` F16 cells. It is a
decision artifact only; no renderer behavior is changed in this ticket.

## Decision

A future scoped implementation ticket is allowed for
`adjacent_circular_arcs_stroke_butt_f16_straight_srgb_quantized_alpha_src_over_white`.

The raw transparent PNG residual remains large (`7065`)
because the Skia reference PNGs have transparent background/alpha while the
FOR-339 export side is opaque `SkBitmap.getPixelAsSrgb` output. That raw basis is
not accepted for implementation.

The Skia-over-white basis is the implementation basis. It reduces the current
FOR-339 export residual from `375` to
`0` across `12`
samples. The ten stroke samples match the existing FOR-339
`straight_srgb_quantized_alpha_src_over_white` candidate exactly.

## Cell Totals

| group | column | sweep | samples | raw residual | over-white residual | candidate over-white residual | reduction |
|---|---:|---:|---:|---:|---:|---:|---:|
| adjacent_arc_stroke_start0_sweep45_target | 1 | 45 | 6 | 3532 | 187 | 0 | 187 |
| adjacent_arc_stroke_start0_sweep130_target | 3 | 130 | 6 | 3533 | 188 | 0 | 188 |

## Sample Deltas

| group | sample | zone | current export | Skia over white | current abs | candidate | candidate abs |
|---|---|---|---:|---:|---:|---:|---:|
| adjacent_arc_stroke_start0_sweep45_target | sweep45_background_top_left | background | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | [255, 255, 255, 255] | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_blue_top_stroke_center | stroke-center | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 | [155, 155, 255, 255] | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_blue_left_stroke_center | stroke-center | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 | [155, 155, 255, 255] | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_red_right_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 | [255, 155, 155, 255] | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_red_inner_sweep_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 | [255, 155, 155, 255] | 0 |
| adjacent_arc_stroke_start0_sweep45_target | sweep45_blue_bottom_stroke_center | stroke-center | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 | [155, 155, 255, 255] | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_background_top_left | background | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | [255, 255, 255, 255] | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_blue_top_stroke_center | stroke-center | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 | [155, 155, 255, 255] | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_red_right_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 | [255, 155, 155, 255] | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_red_bottom_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 | [255, 155, 155, 255] | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_red_inner_sweep_stroke_center | stroke-center | [255, 179, 169, 255] | [255, 155, 155, 255] | 38 | [255, 155, 155, 255] | 0 |
| adjacent_arc_stroke_start0_sweep130_target | sweep130_blue_left_stroke_center | stroke-center | [180, 167, 255, 255] | [155, 155, 255, 255] | 37 | [155, 155, 255, 255] | 0 |

## Future Implementation Scope

- Keep the implementation scoped to the two FOR-339/FOR-340 adjacent cells unless new evidence expands it.
- Add old/new pixel evidence for every sample listed in this artifact.
- Keep SkBitmap.getPixel as the internal renderer/test oracle unless an explicit oracle migration is approved.
- Keep SkBitmap.getPixelAsSrgb as the encoded export boundary unless an explicit export migration is approved.
- Do not change geometry, coverage, GPU/WGSL, thresholds, fallback policy, Kadre, promotion, or score.

## Rejected Inputs

- FOR-327 selected-cell reference is not used as adjacent-cell evidence.
- Full-GM PNGs and crops are not accepted.
- FOR-339 runtime export is only the current Kanvas comparison side, never the
  upstream Skia reference.
- The raw transparent PNG residual is recorded as evidence but is not accepted
  as the renderer policy basis.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-340 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341/circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341.json`
- Validator: `scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-341-circular-arcs-stroke-butt-adjacent-f16-color-policy-decision.md`

## Validation

- `rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py`
- `rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- `rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
