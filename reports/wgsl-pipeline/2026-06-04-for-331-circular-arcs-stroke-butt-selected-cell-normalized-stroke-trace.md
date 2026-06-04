# FOR-331 CircularArcsStrokeButt Selected-Cell Normalized Stroke Trace

Linear: `FOR-331`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-normalized-stroke-color-cpu-trace-ticket`

Source finding:
`global/kanvas/findings/for-330-circular-arcs-stroke-butt-selected-cell-white-background-diff-residual-present-finding`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_COLORSPACE_PREMUL_SUSPECTED`

## Result

FOR-331 adds a selected-cell audit trace for the FOR-330 normalized-background
residual. It does not fix the CPU renderer and does not change GPU, WGSL,
threshold, fallback, Kadre, scene-promotion, or fidelity-score behavior.

Interpretation: Background and center-hole samples are exact after white normalization, and Skia-over-white matches the naive alpha-over-white model at sampled stroke pixels. The CPU still differs at full paint-alpha stroke centers, so the residual is not explained by background normalization or by edge coverage alone.

Most likely track: `colorspace/premul suspected` with `medium` confidence.

Recommended next action: Add an instrumented CPU trace at paint color conversion, premul/unpremul, F16/Rec.2020 to PNG encode, coverage, and SrcOver store boundaries before changing the CPU renderer.

## Inputs

| Input | Path | Source | Dimensions / decision | SHA-256 |
|---|---|---|---|---|
| FOR-330 audit | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330.json` | `FOR-330` / `circular-arcs-stroke-butt-selected-cell-white-background-diff-for330` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_RESIDUAL_PRESENT` | `0cc7c1d93225f8d4c2558ca18a8d3709aeb33003f349a6b175459640f4d2570c` |
| Skia | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png` | `FOR-327` / `circular-arcs-stroke-butt-selected-cell-skia-reference-for327` | `{"width": 80, "height": 80}` | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |
| Skia over white | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/skia-over-white.png` | `FOR-330` / `circular-arcs-stroke-butt-selected-cell-white-background-diff-for330` | `{"width": 80, "height": 80}` | `b267d3c48568338fe6b107c4a93f21bae9787ae5dc1fbd7fe39dac0b8fc51368` |
| CPU Kanvas | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `FOR-322` / `circular-arcs-stroke-butt-selected-cell-harness-for322` | `{"width": 80, "height": 80}` | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` |
| FOR-330 diff | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/cpu-vs-skia-over-white-diff.png` | `FOR-330` / `circular-arcs-stroke-butt-selected-cell-white-background-diff-for330` | `{"width": 80, "height": 80}` | `833b714cf11d34c6ee8e3163105adfcbef97fb79a431b42c10fe29fdfd733ff8` |

Input validation valid: `True`

Invalid reasons:

- none

## Normalized Stroke Trace

| Field | Value |
|---|---|
| total pixels | `6400` |
| different pixels | `2031` |
| matching pixels | `4369` |
| cell similarity percent | `68.265625` |
| max delta by channel | `{"r": 39, "g": 43, "b": 31, "a": 0}` |
| sum abs delta by channel | `{"r": 33893, "g": 18839, "b": 10795, "a": 0}` |
| sum abs delta total | `63527` |
| different pixel bounding box | `{"left": 12, "top": 12, "right": 67, "bottom": 67}` |
| expected stroke bounding box | `{"left": 12, "top": 12, "right": 67, "bottom": 67}` |
| different pixels outside expected stroke bbox | `0` |
| background and center-hole match | `True` |
| naive red alpha-over-white | `[255, 155, 155, 255]` |
| naive blue alpha-over-white | `[155, 155, 255, 255]` |

## Samples

| Sample | Zone | XY | Skia RGBA | Skia over white RGBA | CPU RGBA | Naive alpha-over-white RGBA | CPU vs Skia-over-white abs delta |
|---|---|---|---|---|---|---|---|
| `top_left_background` | `background` | `0,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` |
| `top_edge_background` | `background` | `40,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` |
| `left_edge_background` | `background` | `0,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` |
| `blue_left_aa_edge` | `stroke-aa-edge` | `12,40` | `[0, 0, 255, 45]` | `[210, 210, 255, 255]` | `[214, 208, 253, 255]` | `[210, 210, 255, 255]` | `[4, 2, 2, 0]` |
| `blue_top_outer_edge` | `stroke-aa-edge` | `40,12` | `[0, 0, 255, 46]` | `[209, 209, 255, 255]` | `[214, 208, 253, 255]` | `[209, 209, 255, 255]` | `[5, 1, 2, 0]` |
| `arc_rect_top_left` | `stroke-aa-edge` | `20,20` | `[0, 0, 255, 40]` | `[215, 215, 255, 255]` | `[224, 220, 253, 255]` | `[215, 215, 255, 255]` | `[9, 5, 2, 0]` |
| `blue_top_stroke_center` | `stroke-center` | `40,20` | `[0, 0, 255, 100]` | `[155, 155, 255, 255]` | `[172, 160, 250, 255]` | `[155, 155, 255, 255]` | `[17, 5, 5, 0]` |
| `red_right_stroke_center` | `stroke-center` | `60,40` | `[255, 0, 0, 100]` | `[255, 155, 155, 255]` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` |
| `red_bottom_stroke_center` | `stroke-center` | `40,60` | `[255, 0, 0, 100]` | `[255, 155, 155, 255]` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` |
| `red_outer_edge` | `stroke-aa-edge` | `67,40` | `[255, 0, 0, 45]` | `[255, 210, 210, 255]` | `[248, 227, 221, 255]` | `[255, 210, 210, 255]` | `[7, 17, 11, 0]` |
| `red_bottom_outer_edge` | `stroke-aa-edge` | `40,67` | `[255, 0, 0, 46]` | `[255, 209, 209, 255]` | `[248, 227, 221, 255]` | `[255, 209, 209, 255]` | `[7, 18, 12, 0]` |
| `cell_center_hole` | `center-hole` | `40,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` |
| `bottom_right_background` | `background` | `79,79` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` |

## Weighted Hypotheses

| Hypothesis | Weight | Status | Evidence |
|---|---|---|---|
| `colorspace-premul-or-png-encode` | `0.62` | `most-likely` | stroke-center samples differ after background normalization; Skia-over-white equals the naive alpha-over-white sample model; FOR-329 source audit found CPU uses F16/Rec.2020 output before PNG encoding while Skia reference is N32 premul sRGB |
| `stroke-coverage-aa` | `0.24` | `secondary` | residual bbox is exactly [12,12]-[67,67]; AA-edge samples also differ; coverage cannot explain the stroke-center color offsets by itself from the current samples |
| `paint-alpha-or-src-over-formula` | `0.09` | `less-likely-from-samples` | naive 100/255 alpha-over-white produces Skia center values [255,155,155,255] and [155,155,255,255]; CPU remains offset from those values |
| `requires-instrumented-cpu-trace-before-fix` | `0.05` | `required-next-evidence` | artifact-level PNG samples cannot separate color-space conversion, premul/unpremul, coverage, and store boundaries; no renderer correction is made by this ticket |

## Non-Goals And Preserved Contracts

| Field | Value |
|---|---|
| audit scope | `selected-cell-only` |
| diagnostic only | `True` |
| production renderer changed | `False` |
| CPU renderer fixed | `False` |
| GPU rendered | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre/native dependency added | `False` |
| scene promotion changed | `False` |
| fidelity score counted | `False` |
| full-GM score accepted | `False` |
| full-GM crop accepted | `False` |
| full-GM substitution accepted | `False` |

## Validation

- `rtk python3 scripts/validate_for331_circular_arcs_stroke_butt_selected_cell_normalized_stroke_trace.py`
- `rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py`
- `rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py`
- `rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py`
- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331/circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
