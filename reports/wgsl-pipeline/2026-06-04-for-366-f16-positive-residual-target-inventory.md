# FOR-366 F16 Positive Residual Target Inventory

Linear: `FOR-366`

Decision: `F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY`

FOR-366 does not implement a candidate. It inventories current F16 rows with
positive residuals after FOR-365 rejected a candidate that worsened every guard.

## Source Memory

- `global/kanvas/ticket-drafts/draft-for-next-f16-positive-residual-target-inventory-after-for-365`

## Rejected Candidate Boundaries

- FOR-355: `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard` remains closed and unselectable.
- FOR-365: `covered_source_alpha_src_over_white_without_non_arc_guard_probe` remains rejected and unselectable.

## Mandatory Guards

| source | row | kind | current residual | FOR-365 candidate residual | classification |
|---|---|---|---:|---:|---|
| `FOR-345` | `non-arc-rec2020-f16-src-over-rect` | `non-arc` | 0 | 111 | `mandatory-zero-guard` |
| `FOR-358` | `for358-non-arc-rec2020-f16-src-over-green-rect` | `non-arc` | 3 | 150 | `low-positive-mandatory-guard` |
| `FOR-361` | `for361-bounded-independent-round-cap-arc-rec2020-f16` | `arc` | 0 | 74 | `mandatory-zero-guard` |
| `FOR-364` | `for364-independent-butt-cap-arc-rec2020-f16` | `arc` | 0 | 92 | `mandatory-zero-guard` |

## Positive Residual Inventory

| rank | row | family | kind | current residual | comparable | guard risk | next use |
|---:|---|---|---|---:|---|---|---|
| 1 | `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend` | `bounded-stroke-cap-join` | `non-arc` | 856 | `False` | `evidence-target-before-candidate` | `capture-comparable-reference-current-candidate-evidence-first` |
| 2 | `arc-prerequisite-for342-adjacent-cells` | `circular-arcs-stroke-butt` | `arc` | 375 | `True` | `high` | `candidate-evaluation-target` |
| 3 | `non-arc-m60-target-colorspace-neutral-aa-substitute-refused` | `target-colorspace-neutral-aa` | `non-arc` | 13 | `False` | `metadata-gap` | `capture-comparable-reference-current-candidate-evidence-first` |

## Proposed Next Evidence Target

- Family: `m60-bounded-stroke-cap-join-positive-residual-evidence-target`
- Row: `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`
- Current residual: `856`
- Next ticket type: `capture-comparable-reference-current-candidate-evidence-first`

Reason: the selected row has the largest non-arc positive residual in the
committed F16 evidence, but it is not yet a comparable reference/current/candidate
row. The next safe move is to capture comparable evidence before evaluating or
implementing another policy.

## Non-goals Preserved

- No renderer behavior change.
- No score increase, threshold change, candidate implementation, or promotion.
- No GPU/WGSL, geometry, coverage, fallback, Kadre, F16 premul, blend, or
  `SkBitmap.getPixel` change.
- No scene-id, coordinate, selected-cell, fixture-only, or full-GM-crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-positive-residual-target-inventory-for366/f16-positive-residual-target-inventory-for366.json`
- Validator: `scripts/validate_for366_f16_positive_residual_target_inventory.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-366-f16-positive-residual-target-inventory.md`

## Validation

- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- `rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 -m py_compile scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
