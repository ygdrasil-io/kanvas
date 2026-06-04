# FOR-367 M60 Bounded Stroke Cap/Join Comparable F16 Evidence

Linear: `FOR-367`

Decision: `M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED`

Classification: `still-missing-comparable-metadata`

FOR-367 is evidence-only. It consolidates the FOR-366 selected M60 positive
residual row without changing renderer behavior, scores, thresholds, GPU/WGSL,
geometry, coverage, fallback, Kadre, F16 premul, or blend code.

## Source Memory

- `global/kanvas/ticket-drafts/draft-for-next-m60-bounded-stroke-cap-join-comparable-f16-evidence-after-for-366`

## Input Gate

- FOR-366 decision required: `F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY`
- FOR-366 selected row: `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`
- FOR-366 selected residual: `856`

## Consolidated Line

- Family: `m60-bounded-stroke-cap-join-positive-residual-evidence-target`
- Row: `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`
- Source scene: `m60-bounded-stroke-cap-join`
- Current residual: `856`
- Sample count: `10`
- Reference/current comparable: `True`
- Reference/current/candidate comparable: `False`

The residual is recomputed as the sum of absolute RGBA channel deltas across
the committed high-delta samples and remains `856`.

## Readiness

Classification: `still-missing-comparable-metadata`

- candidatePolicyRgba samples are missing for the FOR-341/FOR-342 F16 policy
- source input RGBA and raw F16 premul/unpremul metadata are not present in committed M60 artifacts
- the current line is a targetColorSpaceBlend diagnostic, not an applied candidate policy row

## Available Metadata

- reference/current RGBA samples
- sample coordinates
- per-sample region and cap/join AA edge-zone classification
- coverage route, edge budget, stroke cap/join, device bounds, and residual summaries
- targetColorSpaceBlend/root-cause diagnostics

## Missing For Candidate Evaluation

- candidatePolicyRgba samples for the same coordinates
- source input/raw RGBA or explicit note from a capture that it cannot be exported
- explicit premul/blend metadata for the candidate evaluation row

## Sample Table

| # | x | y | region | reference RGBA | current RGBA | abs delta | residual |
|---:|---:|---:|---|---|---|---|---:|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[48, 41, 16, 0]` | 105 |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[48, 41, 16, 0]` | 105 |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[48, 41, 16, 0]` | 105 |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[48, 41, 16, 0]` | 105 |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[48, 41, 16, 0]` | 105 |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[48, 41, 16, 0]` | 105 |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[25, 22, 9, 0]` | 56 |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[24, 21, 7, 0]` | 52 |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[24, 20, 8, 0]` | 52 |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[24, 18, 24, 0]` | 66 |

## Boundaries Preserved

- FOR-355 candidate `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard` remains unselectable.
- FOR-365 candidate `covered_source_alpha_src_over_white_without_non_arc_guard_probe` remains rejected and unselectable.
- No renderer branch by scene id, coordinate, selected cell, fixture-only row,
  or full-GM crop.
- No score increase, threshold change, candidate implementation, or promotion.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join-comparable-f16-evidence-for367/m60-bounded-stroke-cap-join-comparable-f16-evidence-for367.json`
- Validator: `scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-05-for-367-m60-bounded-stroke-cap-join-comparable-f16-evidence.md`

## Validation

- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- `rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for367-pycache python3 -m py_compile scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
