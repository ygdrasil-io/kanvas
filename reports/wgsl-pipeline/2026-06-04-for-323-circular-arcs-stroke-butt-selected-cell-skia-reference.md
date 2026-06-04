# FOR-323 CircularArcsStrokeButt Selected-Cell Skia Reference Audit

Linear: `FOR-323`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-skia-reference-audit-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_FULL_GM_CROP_CONTAMINATED`

## Result

FOR-323 audits whether a valid selected-cell `skia.png` reference can be
produced for the FOR-319 `CircularArcsStrokeButtGM` cell. The current decision
is `CIRCULAR_ARCS_STROKE_BUTT_FULL_GM_CROP_CONTAMINATED`. `skia.png` ready: `False`.

No selected-cell Skia reference is produced by this ticket. The full-GM PNG is
available, but the 80x80 crop required to match the FOR-322 bounded harness is
rejected because neighboring stroke margins overlap the crop. FOR-322 `cpu.png`
is also rejected as Skia evidence because it is Kanvas CPU output.

## Selected Cell Geometry

| Field | Value |
|---|---|
| full-GM arc rect | `[140, 520, 180, 560]` |
| bounded arc rect | `[20, 20, 60, 60]` |
| start | `0` |
| sweep | `90` |
| complement | `-270` |
| useCenter | `False` |
| aa | `True` |
| stroke width | `15` |
| stroke cap | `kButt_Cap` |
| alpha | `100` |
| drawArc calls | `2` |

The selected cell is exactly `start=0`, `sweep=90`, `complement=-270`,
`useCenter=false`, `aa=true`, `strokeWidth=15`, `strokeCap=kButt_Cap`,
alpha `100`, with two arcs.

## Candidate Reference

| Field | Value |
|---|---|
| path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for323/skia.png` |
| present | `False` |
| accepted | `False` |
| status | `blocked-missing` |

## Full-GM Crop Audit

| Field | Value |
|---|---|
| source PNG | `skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png` |
| source dimensions | `{"width": 1000, "height": 1000}` |
| crop box | `[120, 500, 200, 580]` |
| crop dimensions | `{"width": 80, "height": 80}` |
| selected stroke bounds in crop | `[12.5, 12.5, 67.5, 67.5]` |
| stroke margin | `7.5` |
| cell step | `60` |
| contaminated | `True` |

Neighbor stroke overlap:

| Neighbor | Overlaps crop | Overlap LTRB |
|---|---|---|
| `leftColumnSameRow` | `True` | `[0.0, 12.5, 7.5, 67.5]` |
| `rightColumnSameRow` | `True` | `[72.5, 12.5, 80.0, 67.5]` |
| `nextRowSameColumn` | `True` | `[12.5, 72.5, 67.5, 80.0]` |

Pixel probes in crop margins:

| Region | Non-white pixels | Non-white bbox |
|---|---:|---|
| `leftNeighborMargin` | `226` / `640` | `[0, 0, 7, 58]` |
| `rightNeighborMargin` | `216` / `560` | `[73, 0, 79, 58]` |
| `lowerNeighborMargin` | `206` / `560` | `[21, 73, 58, 79]` |
| `topSeparatorMargin` | `80` / `640` | `[0, 0, 79, 0]` |

## Blocked Reasons

- no selected-cell skia.png is checked in under the FOR-323 artifact directory
- no strict-scope Skia selected-cell renderer artifact is available
- bounded 80x80 crop requires the selected stroke margin
- left/right/lower neighboring stroke bounds overlap that crop margin
- full-GM crop pixels are non-white in the overlapped margins
- crop provenance is full-GM, not an isolated selected-cell Skia render

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `not-supported` |
| full-GM substitution accepted | `False` |
| full-GM score evidence accepted | `False` |
| CPU Kanvas output accepted as Skia | `False` |
| GPU route status | `expected-unsupported` |
| GPU refusal reason | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| readiness movement | `False` |
| release gate changed | `False` |

## Non-Goals And Non-Changes

- no production renderer behavior changed
- no WGSL shader changed
- no threshold changed
- no fallback policy changed
- no scene support status changed
- no Kadre or native dependency introduced
- no full-GM PNG, full-GM score, unproven crop, or CPU Kanvas output accepted as skia.png

## Validation

- `rtk python3 scripts/validate_for323_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for323/circular-arcs-stroke-butt-selected-cell-skia-reference-for323.json >/dev/null`
- `rtk git diff --check origin/master...HEAD`
