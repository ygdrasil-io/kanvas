# FOR-340 CircularArcsStrokeButt Adjacent F16 Skia Reference

Linear: `FOR-340`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_REFERENCE_CAPTURED`

FOR-340 captures isolated upstream Skia references for the two exact adjacent
`CircularArcsStrokeButt` cells measured by FOR-339. It does not change renderer
behavior, color conversion, geometry, coverage, GPU, WGSL, thresholds, fallback
policy, Kadre, promotion, or score.

## Result

The reference boundary is accessible for `2` /
`2` cells. Residuals are computed per
sample against the current FOR-339 `SkBitmap.getPixelAsSrgb` export values.

Because the upstream Skia PNGs are transparent N32 premul references while the
FOR-339 export samples are opaque output bytes, the artifact records both raw
Skia RGBA residuals and Skia-over-white residuals.

| group | column | sweep | samples | raw residual | over-white residual | reference |
|---|---:|---:|---:|---:|---:|---|
| adjacent_arc_stroke_start0_sweep45_target | 1 | 45 | 6 | 3532 | 187 | reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/sweep45-skia.png |
| adjacent_arc_stroke_start0_sweep130_target | 3 | 130 | 6 | 3533 | 188 | reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/sweep130-skia.png |

Total raw residual: `7065`.
Total Skia-over-white residual: `375`.

## Provenance

| Field | Value |
|---|---|
| sourceType | `isolated-skia-adjacent-cell-render` |
| sourceImplementation | `tools/skia-reference/circular_arcs_stroke_butt_adjacent_f16_reference.cpp` |
| source sha256 | `f4197ab5c360b6d44eaab0f32e8f93be0f92cc887ec94520fc8cac2edc211a71` |
| upstream Skia root | `/Users/chaos/workspace/kanvas-forge/skia-main` |
| upstream Skia revision | `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a` |
| command | `rtk python3 tools/skia-reference/build_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py` |
| dimensions | `{"width": 80, "height": 80}` |
| fullGmCrop | `False` |
| selectedCellExtrapolationUsed | `False` |

## Rejected Substitutions

- FOR-327 selected-cell `skia.png` is not used as adjacent evidence.
- Full-GM PNGs and crops are not accepted.
- FOR-339 runtime export is used only as the current Kanvas comparison side,
  never as upstream Skia reference.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/circular-arcs-stroke-butt-adjacent-f16-reference-for340.json`
- PNG: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/sweep45-skia.png`
- PNG: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/sweep130-skia.png`
- Provenance: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-reference-for340/skia-reference-provenance.json`
- Source: `tools/skia-reference/circular_arcs_stroke_butt_adjacent_f16_reference.cpp`
- Runner: `tools/skia-reference/build_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- Validator: `scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-340-circular-arcs-stroke-butt-adjacent-f16-reference.md`

## Validation

- `rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- `rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py`
- `rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
