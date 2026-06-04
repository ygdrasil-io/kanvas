# FOR-337 CircularArcsStrokeButt F16 Color Policy Cross-Scene Evidence

Linear: `FOR-337`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_MIXED_REQUIRES_MORE_DATA`

FOR-337 aggregates the available evidence before any renderer color-policy
change. It compares the current Rec.2020 F16 working-space blend/export path
against the straight-sRGB candidate from FOR-335/FOR-336 where the repository
has comparable sample data.

## Result

The selected cell still shows a real improvement signal: residual
`132` ->
`52`, with
`2` worsened edge samples.

That is not enough for a renderer patch. Only
`1` of
`3`
required groups has comparable current-vs-candidate data. Missing comparable
groups: `adjacent_comparable_arc_stroke_cells`, `non_arc_blend_scene_available_but_not_f16_policy_comparable`.

## Evidence Groups

| group | samples | current residual | candidate residual | candidate-current delta | worsened samples | comparable |
|---|---:|---:|---:|---:|---:|---|
| selected_cell_instrumented_f16_rec2020 | 8 | 132 | 52 | -80 | 2 | yes |
| adjacent_comparable_arc_stroke_cells | 4 | n/a | n/a | n/a | n/a | no |
| non_arc_blend_scene_available_but_not_f16_policy_comparable | 10 | 856 | n/a | n/a | n/a | no |

## Limitations

- `selected_cell_instrumented_f16_rec2020`: Only one isolated selected cell is instrumented through the FOR-333/FOR-335 F16 trace. The evidence is CPU/readback-side; it does not prove a GPU renderer policy. Two antialiased edge samples worsen under the best straight-sRGB candidate.
- `adjacent_comparable_arc_stroke_cells`: Adjacent cells are identifiable from the GM grid, but no FOR-333-style runtime trace exists for them. No per-cell upstream Skia isolated reference or CPU F16 sample table exists for these candidates. Using the selected-cell residual for adjacent cells would be an extrapolation, so it is refused.
- `non_arc_blend_scene_available_but_not_f16_policy_comparable`: The available non-arc scene is a targetColorSpaceBlend diagnostic, not a Rec.2020 F16 CPU blend trace. It has reference/GPU high-delta samples, but no straight-sRGB candidate values. It proves that color/blend policy can be scene-sensitive, not that FOR-335's candidate is safe.

## Recommendation

Do not change `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
`SkBitmap.getPixelAsSrgb` from this ticket. The next ticket must go through
memory and add real comparable samples for adjacent arc-stroke cells plus a true
non-arc Rec.2020 F16 blend scene before an implementation ticket can be opened.

## Non-goals Preserved

- No renderer behavior change.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or score change.
- Historical artifacts FOR-329 through FOR-336 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337/circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337.json`
- Validator: `scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-337-circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence.md`

## Validation

- `rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py`
- `rtk python3 scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py`
- `rtk python3 scripts/validate_for335_circular_arcs_stroke_butt_selected_cell_f16_blend_policy.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
