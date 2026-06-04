# FOR-338 CircularArcsStrokeButt F16 Color Policy Comparable Samples

Linear: `FOR-338`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_PARTIAL_REQUIRES_MORE_INSTRUMENTATION`

FOR-338 keeps the color-policy work as evidence only. It does not change
renderer behavior, geometry, coverage, GPU, WGSL, thresholds, fallback policy,
Kadre, promotion, or score.

## Result

The requested adjacent and non-arc comparable samples are not captured yet.
The missing instrumentation is wider than a report-only patch because it needs
both generalized Kotlin F16 runtime traces and isolated upstream Skia references
for cells that FOR-337 only identified.

Captured comparable groups for this ticket:

- adjacent arc-stroke groups: `0` /
  `2`
- non-arc Rec.2020 F16 SrcOver/blend groups:
  `0` /
  `1`

Missing comparable groups: `adjacent_arc_stroke_start0_sweep45_target`, `adjacent_arc_stroke_start0_sweep130_target`, `non_arc_rec2020_f16_src_over_blend_target`.

## Evidence Groups

| group | samples | current residual | candidate residual | worsened samples | comparable | measured |
|---|---:|---:|---:|---:|---|---|
| selected_cell_prerequisite_from_for337 | 8 | 132 | 52 | 2 | yes | yes |
| adjacent_arc_stroke_start0_sweep45_target | 0 | n/a | n/a | n/a | no | no |
| adjacent_arc_stroke_start0_sweep130_target | 0 | n/a | n/a | n/a | no | no |
| non_arc_rec2020_f16_src_over_blend_target | 0 | n/a | n/a | n/a | no | no |

## Interpretation

The selected-cell signal from FOR-337 remains real: current residual `132`,
candidate residual `52`, and `2` worsened edge samples. FOR-338 refuses to
project those numbers onto adjacent cells. The adjacent groups and the non-arc
group are recorded as concrete instrumentation targets, not as measured policy
proof.

## Required Next Instrumentation

- `adjacent-arc-kotlin-runtime-trace`: Generalize the FOR-333 opt-in Kotlin trace so it can render at least two adjacent CircularArcsStrokeButt cells and write per-sample F16 store/readback data.
- `adjacent-arc-isolated-skia-reference`: Produce isolated upstream Skia references for those exact adjacent cells. Do not substitute the selected-cell FOR-327 image or the full-GM crop.
- `non-arc-rec2020-f16-src-over-fixture`: Add a bounded non-arc Rec.2020 kRGBA_F16Norm SrcOver/blend fixture with current, candidate, and reference/expected sample values.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- Historical artifacts FOR-329 through FOR-337 are not rewritten.
- The next ticket must pass through Basic Memory before Linear writing or implementation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338/circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338.json`
- Validator: `scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-338-circular-arcs-stroke-butt-f16-color-policy-comparable-samples.md`

## Validation

- `rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py`
- `rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py`
- `rtk python3 scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
