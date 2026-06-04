# FOR-364 F16 Independent Comparable Arc Evidence

Linear: `FOR-364`

Decision: `F16_INDEPENDENT_COMPARABLE_ARC_EVIDENCE_CAPTURED`

FOR-364 captures a new independent Rec.2020 `kRGBA_F16Norm` `drawArc` row after
FOR-363. It is evidence-only: no candidate is defined, selected, implemented, or
used to raise score.

## Result

The new row `for364-independent-butt-cap-arc-rec2020-f16` has isolated Skia reference samples and current
Kanvas CPU samples. Future-candidate fields are present as explicit placeholders
for a later evaluation ticket, but remain null because FOR-364 does not select a
candidate formula.

## Preserved Gates

- FOR-363: `F16_CONSTRAINED_CANDIDATE_SEARCH_MATRIX_READY`
- FOR-362: `F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361`
- FOR-361: `F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE`
- FOR-358: `F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE`
- FOR-355: `F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE`
- FOR-345: `F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE`

The rejected FOR-355 candidate remains closed and unavailable:
`nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`.

## Samples

| sample | x,y | zone | raw reference | reference | current | current residual |
|---|---|---|---|---|---|---:|
| for364_background_top_left | 0,0 | background | [0, 0, 0, 0] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 |
| for364_arc_diagonal_stroke_center | 16,16 | stroke-center | [255, 0, 0, 128] | [255, 156, 144, 255] | [255, 156, 144, 255] | 0 |
| for364_arc_top_stroke_center | 32,10 | stroke-center | [255, 0, 0, 128] | [255, 156, 144, 255] | [255, 156, 144, 255] | 0 |
| for364_arc_interior_clear | 32,32 | interior-clear | [0, 0, 0, 0] | [255, 255, 255, 255] | [255, 255, 255, 255] | 0 |

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | 4 |
| covered samples | 2 |
| raw covered samples | 2 |
| current residual | 0 |
| future candidate residual | `None` |

## Independence Boundary

- Oval: `[10, 10, 44, 44]`
- Start/sweep: `180` / `100`
- Stroke cap: `kButt_Cap`
- Paint ARGB: `[128, 255, 0, 0]`
- Independent from FOR-361: `True`
- Independent from FOR-340/FOR-341 adjacent groups: `True`
- Selected-cell substitution used: `False`
- Full-GM crop used: `False`

## Refused Shortcuts

- `sceneIdBranch`: `True`
- `coordinateBranch`: `True`
- `selectedCellBranch`: `True`
- `fixtureOnlyRendererBranch`: `True`
- `fullGmCropBranch`: `True`
- `for361ReuseAsNewRow`: `True`
- `for340For341AdjacentGroupReuse`: `True`

## Non-goals Preserved

- No renderer behavior change.
- No selectable candidate, candidate formula, candidate evaluation, candidate
  implementation, score increase, or threshold change.
- No GPU/WGSL, geometry, coverage, fallback, promotion, Kadre,
  `SkBitmap.getPixel`, F16 premul, or blend behavior change.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-independent-comparable-arc-evidence-for364/f16-independent-comparable-arc-evidence-for364.json`
- Skia samples: `reports/wgsl-pipeline/scenes/artifacts/f16-independent-comparable-arc-evidence-for364/skia-reference-samples.json`
- Kanvas samples: `reports/wgsl-pipeline/scenes/artifacts/f16-independent-comparable-arc-evidence-for364/current-kanvas-samples.json`
- Skia PNG: `reports/wgsl-pipeline/scenes/artifacts/f16-independent-comparable-arc-evidence-for364/skia-reference.png`
- Validator: `scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-364-f16-independent-comparable-arc-evidence.md`

## Validation

- `rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- `rtk python3 tools/skia-reference/build_for364_f16_independent_comparable_arc_evidence.py`
- `KANVAS_FOR364_CURRENT_CAPTURE_OUTPUT=reports/wgsl-pipeline/scenes/artifacts/f16-independent-comparable-arc-evidence-for364/current-kanvas-samples.json rtk ./gradlew --no-daemon --rerun-tasks :skia-integration-tests:test --tests org.skia.tests.For364IndependentComparableArcF16CurrentCaptureTest`
- `rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 -m py_compile scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
