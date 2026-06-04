# FOR-358 F16 Real Additional Non-Arc Row

Linear: `FOR-358`

Decision: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-358 captures a real additional non-arc Rec.2020 `kRGBA_F16Norm`
`SrcOver` row that is distinct from FOR-345. It reuses the FOR-355 candidate
exactly and keeps the work evidence-only.

## Result

The row is a green non-arc solid rect with different dimensions, geometry,
paint color, sample names, and source files from FOR-345. The FOR-355
`stroke-center` rule does not apply to these non-arc fill/background samples,
so the candidate preserves the current Kanvas RGBA values for this guard row.

## Samples

| sample | x,y | zone | reference | current | candidate | FOR-355 rule applies | current residual | candidate residual | worsened |
|---|---|---|---|---|---|---:|---:|---:|---|
| for358_background_top_left | 0,0 | background | [255, 255, 255, 255] | [255, 255, 255, 255] | [255, 255, 255, 255] | no | 0 | 0 | no |
| for358_rect_center | 14,14 | fill-center | [137, 216, 142, 255] | [138, 216, 142, 255] | [138, 216, 142, 255] | no | 1 | 1 | no |
| for358_rect_left_inside | 5,14 | fill-left-inside | [137, 216, 142, 255] | [138, 216, 142, 255] | [138, 216, 142, 255] | no | 1 | 1 | no |
| for358_rect_bottom_inside | 14,20 | fill-bottom-inside | [137, 216, 142, 255] | [138, 216, 142, 255] | [138, 216, 142, 255] | no | 1 | 1 | no |

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | 4 |
| covered samples | 3 |
| current residual | 3 |
| candidate residual | 3 |
| residual delta | 0 |
| worsened samples | 0 |
| FOR-355 rule-applied samples | 0 |

## Prerequisites

- FOR-357 decision: `F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE`
- FOR-356 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL`
- FOR-355 decision: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`
- Source memory: `global/kanvas/ticket-drafts/draft-for-next-capture-real-additional-non-arc-f16-comparable-row-ticket`

## Non-goals Preserved

- No renderer behavior change.
- No candidate selection or implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-real-additional-non-arc-row-for358/f16-real-additional-non-arc-row-for358.json`
- Validator: `scripts/validate_for358_f16_real_additional_non_arc_row.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-358-f16-real-additional-non-arc-row.md`
- Skia reference source: `tools/skia-reference/non_arc_rec2020_f16_for358_reference.cpp`
- Skia reference builder: `tools/skia-reference/build_for358_f16_real_additional_non_arc_row.py`
- Current capture test: `skia-integration-tests/src/test/kotlin/org/skia/tests/For358RealAdditionalNonArcF16CurrentCaptureTest.kt`

## Validation

- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 tools/skia-reference/build_for358_f16_real_additional_non_arc_row.py`
- `KANVAS_FOR358_CURRENT_CAPTURE_OUTPUT=reports/wgsl-pipeline/scenes/artifacts/f16-real-additional-non-arc-row-for358/current-kanvas-samples.json rtk ./gradlew --no-daemon --rerun-tasks :skia-integration-tests:test --tests org.skia.tests.For358RealAdditionalNonArcF16CurrentCaptureTest`
- `rtk python3 scripts/validate_for357_f16_additional_non_arc_comparable_row.py`
- `rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 -m py_compile scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
