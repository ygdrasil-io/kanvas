# FOR-328 CircularArcsStrokeButt Selected-Cell Skia CPU Diff

Linear: `FOR-328`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-compare-circular-arcs-stroke-butt-selected-cell-skia-and-cpu-reference-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_DIFF_REQUIRES_RASTER_AUDIT`

## Result

FOR-328 compares only the `80x80` selected-cell pixels from the accepted
FOR-327 isolated Skia reference and the FOR-322 Kanvas CPU harness output.
The comparison uses strict RGBA pixels and does not use a full-GM PNG, crop,
global score, GPU render, fallback, threshold change, or scene promotion.

Raster audit required: `True`

Interpretation: strict selected-cell pixels differ; the gap is classified as a CPU raster audit input, not as a renderer fix or scene promotion.

## Inputs

| Input | Path | Source | Dimensions | SHA-256 |
|---|---|---|---|---|
| Skia | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png` | `FOR-327` / `circular-arcs-stroke-butt-selected-cell-skia-reference-for327` | `{"width": 80, "height": 80}` | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |
| CPU Kanvas | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `FOR-322` / `circular-arcs-stroke-butt-selected-cell-harness-for322` | `{"width": 80, "height": 80}` | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` |

Input validation valid: `True`

Invalid reasons:

- none

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
| full-GM rect | `[140, 520, 180, 560]` |
| bounded rect | `[20, 20, 60, 60]` |

## Cell Diff Stats

| Field | Value |
|---|---|
| diff PNG | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/cpu-vs-skia-diff.png` |
| diff encoding | `absolute RGB channel deltas with opaque changed pixels and transparent equal pixels` |
| total pixels | `6400` |
| different pixels | `6400` |
| matching pixels | `0` |
| cell similarity percent | `0.0` |
| max delta by channel | `{"r": 255, "g": 255, "b": 255, "a": 255}` |
| sum abs delta by channel | `{"r": 1394491, "g": 1461664, "b": 1206580, "a": 1443581}` |
| sum abs delta total | `5506316` |
| different pixel bounding box | `{"left": 0, "top": 0, "right": 79, "bottom": 79}` |

## Hashes

| Field | SHA-256 |
|---|---|
| Skia input | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |
| CPU input | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` |
| diff PNG | `53ebbd4785d9062df7264d43028aaea144b862922413236ed8c5462f27d9da04` |

## Qualification

The current decision is `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_DIFF_REQUIRES_RASTER_AUDIT`. A non-zero selected-cell pixel
diff is treated as evidence for a focused CPU raster audit only. This report
does not identify a CPU fix, does not reinterpret the FOR-327 Skia reference
as invalid when its provenance checks pass, and does not promote
`CircularArcsStrokeButtGM` or this selected cell.

## Preserved Contracts

| Field | Value |
|---|---|
| diff scope | `selected-cell-only` |
| full-GM substitution accepted | `False` |
| full-GM crop accepted | `False` |
| full-GM score accepted | `False` |
| production renderer changed | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre/native dependency added | `False` |
| GPU rendered | `False` |
| scene promotion changed | `False` |
| fidelity score counted | `False` |
| CPU renderer fixed | `False` |

## Validation

- `rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py`
- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
