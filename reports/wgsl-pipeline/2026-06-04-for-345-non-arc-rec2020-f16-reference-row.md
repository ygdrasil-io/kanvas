# FOR-345 Non-Arc Rec.2020 F16 Reference Row

Linear: `FOR-345`

Decision: `F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE`

FOR-345 creates one comparable non-arc Rec.2020 `kRGBA_F16Norm` `SrcOver`
reference/current/candidate row for `straight_srgb_quantized_alpha_src_over_white`. It is evidence-only.

## Result

The row is a non-arc solid rect, explicitly outside `circular_arcs_stroke_butt`.
The isolated Skia reference and current Kanvas CPU F16 samples are aligned on
the same four pixels. Current Kanvas matches the reference on this row.

The candidate policy is not safe for this row: it worsens `3`
covered samples and raises residual from `0` to
`111`.

## Samples

| sample | x,y | zone | reference | current | candidate | current residual | candidate residual | worsened |
|---|---|---|---|---|---|---:|---:|---|
| background_top_left | 0,0 | background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 | 0 | no |
| rect_center | 16,16 | fill-center | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | 0 | 37 | yes |
| rect_left_inside | 8,16 | fill-left-inside | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | 0 | 37 | yes |
| rect_right_inside | 23,16 | fill-right-inside | [180, 167, 255, 255] | [180, 167, 255, 255] | [155, 155, 255, 255] | 0 | 37 | yes |

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | 4 |
| covered samples | 3 |
| current residual | 0 |
| candidate residual | 111 |
| candidate minus current | 111 |
| worsened samples | 3 |
| improved samples | 0 |
| unchanged samples | 1 |

## Provenance

- Source memory: `global/kanvas/ticket-drafts/draft-for-next-non-arc-rec2020-f16-comparable-reference-row-ticket`
- Source finding: `global/kanvas/findings/for-344-broader-non-arc-f16-color-evidence-partial-finding`
- FOR-344 gate: `F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS`
- Skia reference source: `tools/skia-reference/non_arc_rec2020_f16_reference.cpp`
- Skia reference capture command: `rtk python3 tools/skia-reference/build_for345_non_arc_rec2020_f16_reference.py`
- Current capture method: `Kanvas SkBitmap + SkCanvas public CPU path, sampled through SkBitmap.getPixelAsSrgb`

## Non-goals Preserved

- No renderer behavior change.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/non-arc-rec2020-f16-reference-row-for345/non-arc-rec2020-f16-reference-row-for345.json`
- Validator: `scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-345-non-arc-rec2020-f16-reference-row.md`
- Skia reference source: `tools/skia-reference/non_arc_rec2020_f16_reference.cpp`
- Skia reference builder: `tools/skia-reference/build_for345_non_arc_rec2020_f16_reference.py`

## Validation

- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 tools/skia-reference/build_for345_non_arc_rec2020_f16_reference.py`
- `KANVAS_FOR345_CURRENT_CAPTURE_OUTPUT=reports/wgsl-pipeline/scenes/artifacts/non-arc-rec2020-f16-reference-row-for345/current-kanvas-samples.json rtk ./gradlew --no-daemon --rerun-tasks :skia-integration-tests:test --tests org.skia.tests.For345NonArcRec2020F16CurrentCaptureTest`
- `rtk python3 scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py`
- `rtk python3 scripts/validate_for343_f16_color_policy_boundary.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk python3 -m py_compile scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
