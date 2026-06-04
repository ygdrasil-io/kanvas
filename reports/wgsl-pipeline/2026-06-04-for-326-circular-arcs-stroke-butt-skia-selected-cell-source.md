# FOR-326 CircularArcsStrokeButt Skia Selected Cell Source

Linear: `FOR-326`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-repo-owned-skia-selected-cell-renderer-source-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SKIA_SELECTED_CELL_SOURCE_READY`

## Result

FOR-326 adds a repo-owned C++ source file for a future upstream Skia build to
render only the FOR-319 `CircularArcsStrokeButtGM` selected cell. The source is
ready: `True`. `skia.png` ready: `False`.

No Skia build or execution is performed by this ticket, so no `skia.png` and no
`skia-reference-provenance.json` are created. The remaining dependency is:

`remaining upstream Skia build/execution wiring is required before producing skia.png and skia-reference-provenance.json from the repo-owned source`

## Source Contract

| Field | Value |
|---|---|
| source path | `tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp` |
| output described | `skia.png` |
| dimensions | `{"width": 80, "height": 80}` |
| bounded rect | `[20, 20, 60, 60]` |
| compile or execution attempted | `False` |
| forbidden source terms absent | `True` |

The source draws exactly two arcs in an 80x80 surface: red `0..90` and blue
`0..-270`, with `useCenter=false`, `aa=true`, `strokeWidth=15`,
`SkPaint::kStroke_Style`, `SkPaint::kButt_Cap`, and alpha `100`.

## Selected Cell

| Field | Value |
|---|---|
| fixture id | `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true` |
| source GM | `CircularArcsStrokeButtGM` |
| source row | `circular-arcs-stroke-butt-webgpu` |
| row / column | `0` / `2` |
| start | `0` |
| sweep | `90` |
| complement | `-270` |
| useCenter | `False` |
| aa | `True` |
| stroke width | `15` |
| stroke cap | `kButt_Cap` |
| alpha | `100` |
| bounded rect | `[20, 20, 60, 60]` |
| drawArc calls | `2` |

## Reference Output Status

| Field | Value |
|---|---|
| path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-skia-selected-cell-source-for326/skia.png` |
| provenance path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-skia-selected-cell-source-for326/skia-reference-provenance.json` |
| present | `False` |
| accepted | `False` |
| status | `blocked-build-execution-not-wired` |

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG | `False` | full-GM PNG is not an isolated selected-cell render |
| image slicing | `False` | FOR-326 uses dedicated drawing source instead of slicing an existing image |
| FOR-322 `cpu.png` | `False` | FOR-322 cpu.png is Kanvas CPU output, not upstream Skia |
| similarity scores | `False` | scores are not selected-cell reference pixels |

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `not-supported` |
| full-GM substitution accepted | `False` |
| CPU Kanvas output accepted as Skia | `False` |
| GPU route status | `expected-unsupported` |
| GPU refusal reason | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| production renderer changed | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre/native dependency added | `False` |

## Validation

- `rtk python3 scripts/validate_for326_circular_arcs_stroke_butt_skia_selected_cell_source.py`
- `rtk python3 scripts/validate_for325_circular_arcs_stroke_butt_isolated_skia_reference_command.py`
- `rtk python3 scripts/validate_for324_circular_arcs_stroke_butt_isolated_skia_reference.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-skia-selected-cell-source-for326/circular-arcs-stroke-butt-skia-selected-cell-source-for326.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
