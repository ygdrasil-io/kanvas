# FOR-357 F16 Additional Non-Arc Comparable Row

Linear: `FOR-357`

Decision: `F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE`

Candidate: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`

FOR-357 reuses the FOR-355/FOR-356 candidate exactly. It does not define,
select, or implement a new candidate.

## Result

The current artifact set still has no additional comparable non-arc F16 row distinct from FOR-345. FOR-357 records the insufficiency explicitly and does not convert missing reference data into acceptance.

No additional comparable non-arc row distinct from FOR-345 was found. The
ticket therefore records `PARTIAL_INSUFFICIENT_REFERENCE` instead of accepting
or rejecting the candidate from incomplete data.

## Additional Row Status

- Row id: `additional-non-arc-f16-comparable-row-reference-gap-for357`
- Reference accepted: `False`
- Current residual: `None`
- Candidate residual: `None`
- Candidate worsened samples: `None`

| sample | zone | reference accepted | current RGBA | candidate RGBA |
|---|---|---:|---|---|
| `additional_non_arc_reference_probe_background` | `background` | `False` | `None` | `None` |
| `additional_non_arc_reference_probe_covered_sample` | `non-arc-covered-sample` | `False` | `None` | `None` |

## Inventory

| row | source | status | reason |
|---|---|---|---|
| `non-arc-rec2020-f16-src-over-rect` | `FOR-345` | `rejected` | `FOR-357 requires a non-arc row distinct from FOR-345; this row remains only the existing guard.` |
| `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend` | `FOR-344` | `rejected` | `This is the available non-arc color/blend signal, but it is a targetColorSpaceBlend diagnostic and not a Rec.2020 F16 reference/current/candidate row for straight_srgb_quantized_alpha_src_over_white.` |
| `non-arc-m60-target-colorspace-neutral-aa-substitute-refused` | `FOR-344` | `rejected` | `The neutral AA fixture has a reference/current/targetBlend triple, but the candidate is the WebGPU targetColorSpaceBlend pilot, not the FOR-341/FOR-342 F16 CPU policy candidate. Using it as the non-arc F16 proof would be a fixture substitution.` |
| `non-arc-target-color-candidate-inventory-reference-gap` | `FOR-344` | `rejected` | `The inventory is useful cross-scene evidence for target-color candidates, but it does not contain per-sample Rec.2020 F16 reference/current/candidate rows.` |
| `non-arc-rec2020-f16-blend-target-no-fixture` | `FOR-338` | `rejected` | `FOR-338 records the desired non-arc target but explicitly lacks current/candidate comparable values.` |

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution,
  full-GM crop, or threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-additional-non-arc-comparable-row-for357/f16-additional-non-arc-comparable-row-for357.json`
- Validator: `scripts/validate_for357_f16_additional_non_arc_comparable_row.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-357-f16-additional-non-arc-comparable-row.md`

## Validation

- `rtk python3 scripts/validate_for357_f16_additional_non_arc_comparable_row.py`
- `rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 -m py_compile scripts/validate_for357_f16_additional_non_arc_comparable_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
