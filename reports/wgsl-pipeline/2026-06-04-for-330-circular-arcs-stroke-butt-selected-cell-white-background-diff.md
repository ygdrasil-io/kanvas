# FOR-330 CircularArcsStrokeButt Selected-Cell White-Background Diff

Linear: `FOR-330`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-white-background-skia-cpu-comparison-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_RESIDUAL_PRESENT`

## Result

FOR-330 materializes the selected-cell diagnostic from FOR-329: the accepted
FOR-327 Skia transparent reference is alpha-composited over opaque white and
then compared with the FOR-322 Kanvas CPU selected-cell PNG. This is a
diagnostic probe only, not a CPU renderer fix, not a fidelity score, and not a
scene promotion.

Interpretation: FOR-329 normalized-background metrics are reproduced: background/alpha is isolated, while stroke/color residuals remain in the selected cell.

Recommended next action: Trace the normalized-background CPU stroke/color path before any CPU renderer correction or support claim.

## Inputs

| Input | Path | Source | Dimensions / decision | SHA-256 |
|---|---|---|---|---|
| Skia | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png` | `FOR-327` / `circular-arcs-stroke-butt-selected-cell-skia-reference-for327` | `{"width": 80, "height": 80}` | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |
| CPU Kanvas | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `FOR-322` / `circular-arcs-stroke-butt-selected-cell-harness-for322` | `{"width": 80, "height": 80}` | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` |
| FOR-329 audit | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329.json` | `FOR-329` / `circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED` | `e89d99fd930ccf903fc62166bb8c8716b789ed7006dcd45df092fadfd174ae26` |

Input validation valid: `True`

Invalid reasons:

- none

## White-Background Comparison

| Field | Value |
|---|---|
| Skia-over-white PNG | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/skia-over-white.png` |
| normalization method | `PIL Image.alpha_composite(opaque white RGBA background, FOR-327 skia.png)` |
| diff PNG | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/cpu-vs-skia-over-white-diff.png` |
| diff encoding | `absolute RGB channel deltas with opaque changed pixels and transparent equal pixels` |
| total pixels | `6400` |
| different pixels | `2031` |
| matching pixels | `4369` |
| cell similarity percent | `68.265625` |
| max delta by channel | `{"r": 39, "g": 43, "b": 31, "a": 0}` |
| sum abs delta by channel | `{"r": 33893, "g": 18839, "b": 10795, "a": 0}` |
| sum abs delta total | `63527` |
| different pixel bounding box | `{"left": 12, "top": 12, "right": 67, "bottom": 67}` |

## Hashes

| Field | SHA-256 |
|---|---|
| Skia input | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |
| CPU input | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` |
| FOR-329 artifact | `e89d99fd930ccf903fc62166bb8c8716b789ed7006dcd45df092fadfd174ae26` |
| Skia over white | `b267d3c48568338fe6b107c4a93f21bae9787ae5dc1fbd7fe39dac0b8fc51368` |
| normalized diff | `833b714cf11d34c6ee8e3163105adfcbef97fb79a431b42c10fe29fdfd733ff8` |

## Samples

| Sample | XY | Skia RGBA | Skia over white RGBA | CPU RGBA | CPU vs Skia-over-white abs delta |
|---|---|---|---|---|---|
| `top_left_background` | `0,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `top_edge_background` | `40,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `left_edge_background` | `0,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `arc_rect_top_left` | `20,20` | `[0, 0, 255, 40]` | `[215, 215, 255, 255]` | `[224, 220, 253, 255]` | `[9, 5, 2, 0]` |
| `blue_top_stroke_center` | `40,20` | `[0, 0, 255, 100]` | `[155, 155, 255, 255]` | `[172, 160, 250, 255]` | `[17, 5, 5, 0]` |
| `red_right_stroke_center` | `60,40` | `[255, 0, 0, 100]` | `[255, 155, 155, 255]` | `[235, 178, 162, 255]` | `[20, 23, 7, 0]` |
| `red_bottom_stroke_center` | `40,60` | `[255, 0, 0, 100]` | `[255, 155, 155, 255]` | `[235, 178, 162, 255]` | `[20, 23, 7, 0]` |
| `cell_center_hole` | `40,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `bottom_right_background` | `79,79` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |

## Preserved Contracts

| Field | Value |
|---|---|
| diff scope | `selected-cell-only` |
| diagnostic only | `True` |
| full-GM substitution accepted | `False` |
| full-GM crop accepted | `False` |
| full-GM score accepted | `False` |
| production renderer changed | `False` |
| CPU renderer fixed | `False` |
| GPU rendered | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre/native dependency added | `False` |
| scene promotion changed | `False` |
| fidelity score counted | `False` |

## Validation

- `rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py`
- `rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py`
- `rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py`
- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
