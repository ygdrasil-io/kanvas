# FOR-329 CircularArcsStrokeButt Selected-Cell CPU Raster Audit

Linear: `FOR-329`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED`

## Result

FOR-329 audits the FOR-328 selected-cell mismatch and does not modify any CPU
renderer, GPU path, WGSL, threshold, fallback, Kadre integration, scene support
status, or fidelity score. The primary cause candidate is
`CAUSE_CANDIDATE_BACKGROUND_ALPHA_CONTRACT_MISMATCH`.

Rationale: The Skia isolated reference clears to transparent and contains transparent background pixels, while the CPU harness renders the same GM through a default opaque white GM background. This explains the all-pixel FOR-328 alpha/background failure.

Residual: `CAUSE_RESIDUAL_REQUIRES_NORMALIZED_BACKGROUND_CPU_TRACE`. Compositing the Skia reference over opaque white improves the comparison but still leaves 2031 differing stroke pixels; those residuals need a normalized-background CPU trace before any renderer correction.

Recommended next action: First align the selected-cell reference/background contract or generate an apples-to-apples transparent CPU capture. Then trace the remaining normalized-background stroke/color residuals before changing CPU raster.

## Inputs

| Input | Path | Dimensions | SHA-256 | Expected SHA-256 source |
|---|---|---|---|---|
| Skia | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png` | `{"width": 80, "height": 80}` | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |
| CPU Kanvas | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `{"width": 80, "height": 80}` | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` | `8b57311de03c9771cd25327248d0b85afb7283e958dae11b6117790fea3f3b37` |
| FOR-328 diff | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/cpu-vs-skia-diff.png` | `{"width": 80, "height": 80}` | `53ebbd4785d9062df7264d43028aaea144b862922413236ed8c5462f27d9da04` | `53ebbd4785d9062df7264d43028aaea144b862922413236ed8c5462f27d9da04` |

Input validation valid: `True`

Invalid reasons:

- none

## Local Pixel Metrics

| Image | Transparent | Non-transparent | Opaque | Partial alpha | Non-transparent bbox | Top RGBA |
|---|---:|---:|---:|---:|---|---|
| Skia | `4366` | `2034` | `0` | `2034` | `{"left": 12, "top": 12, "right": 67, "bottom": 67}` | `[{"rgba": [0, 0, 0, 0], "pixels": 4366}, {"rgba": [0, 0, 255, 100], "pixels": 1310}, {"rgba": [255, 0, 0, 100], "pixels": 434}, {"rgba": [0, 0, 255, 7], "pixels": 10}]` |
| CPU Kanvas | `0` | `6400` | `6400` | `0` | `{"left": 0, "top": 0, "right": 79, "bottom": 79}` | `[{"rgba": [255, 255, 255, 255], "pixels": 4384}, {"rgba": [172, 160, 250, 255], "pixels": 1312}, {"rgba": [235, 178, 162, 255], "pixels": 432}, {"rgba": [224, 220, 253, 255], "pixels": 35}]` |
| FOR-328 diff | `0` | `6400` | `6400` | `0` | `{"left": 0, "top": 0, "right": 79, "bottom": 79}` | `[{"rgba": [255, 255, 255, 255], "pixels": 4365}, {"rgba": [172, 160, 5, 255], "pixels": 1312}, {"rgba": [20, 178, 162, 255], "pixels": 432}, {"rgba": [224, 220, 2, 255], "pixels": 35}]` |

Strict CPU-vs-Skia comparison:

| Field | Value |
|---|---|
| different pixels | `6400` / `6400` |
| matching pixels | `0` |
| similarity percent | `0.0` |
| max delta by channel | `{"r": 255, "g": 255, "b": 255, "a": 255}` |
| sum abs delta total | `5506316` |
| different pixel bounding box | `{"left": 0, "top": 0, "right": 79, "bottom": 79}` |

Skia-over-white probe compared to CPU:

| Field | Value |
|---|---|
| different pixels | `2031` / `6400` |
| matching pixels | `4369` |
| similarity percent | `68.265625` |
| max delta by channel | `{"r": 39, "g": 43, "b": 31, "a": 0}` |
| sum abs delta total | `63527` |
| different pixel bounding box | `{"left": 12, "top": 12, "right": 67, "bottom": 67}` |

## Samples

| Sample | XY | Skia RGBA | CPU RGBA | Skia over white RGBA | CPU vs Skia-over-white abs delta |
|---|---|---|---|---|---|
| `top_left_background` | `0,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `top_edge_background` | `40,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `left_edge_background` | `0,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `arc_rect_top_left` | `20,20` | `[0, 0, 255, 40]` | `[224, 220, 253, 255]` | `[215, 215, 255, 255]` | `[9, 5, 2, 0]` |
| `blue_top_stroke_center` | `40,20` | `[0, 0, 255, 100]` | `[172, 160, 250, 255]` | `[155, 155, 255, 255]` | `[17, 5, 5, 0]` |
| `red_right_stroke_center` | `60,40` | `[255, 0, 0, 100]` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` |
| `red_bottom_stroke_center` | `40,60` | `[255, 0, 0, 100]` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` |
| `cell_center_hole` | `40,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `bottom_right_background` | `79,79` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |


## Source Comparison

| Topic | Skia FOR-326/FOR-327 | CPU FOR-322 |
|---|---|---|
| surface | `SkImageInfo::MakeN32Premul(80, 80, SkColorSpace::MakeSRGB())` | `RasterSinkF16 with SkColorType.kRGBA_F16Norm and DM_REFERENCE_COLOR_SPACE` |
| clear / background | `canvas->clear(SK_ColorTRANSPARENT)` | `bitmap.eraseColor(src.bgColor()); GM default bgColor is SK_ColorWHITE` |
| arc bounds | `[20, 20, 60, 60]` | `[20, 20, 60, 60]` |
| local coordinates | `direct device-space rect` | `local SkRect(0,0,40,40) after c.translate(20,20)` |
| colors / alpha | `[{"name": "red", "argb": [100, 255, 0, 0]}, {"name": "blue", "argb": [100, 0, 0, 255]}]` | `[{"name": "red", "argb": [100, 255, 0, 0]}, {"name": "blue", "argb": [100, 0, 0, 255]}]` |
| anti-aliasing | `True` | `True` |
| stroke width | `15` | `15` |
| cap butt | `kButt_Cap` | `kButt_Cap` |
| arc order | `[{"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90, "useCenter": false}, {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270, "useCenter": false}]` | `[{"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90, "useCenter": false}, {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270, "useCenter": false}]` |
| color conversion / PNG encoding | `SkPngEncoder over SkPixmap from N32 premul sRGB surface` | `SkPngEncoder.Encode(bitmap) after F16/Rec.2020 CPU raster output` |

Matching source facts:

- 80x80 surface size
- device arc bounds [20, 20, 60, 60]
- red arc 0..90 before blue complement 0..-270
- useCenter=false
- aa=true
- strokeWidth=15
- strokeCap=kButt_Cap
- paint alpha=100

Divergent source facts:

- Skia source clears to transparent
- CPU harness uses GM default white background through TestUtils.runGmTest/RasterSinkF16
- Skia source uses N32 premul sRGB, CPU harness renders through F16 Rec.2020 then PNG encodes

Not supported as the primary cause by source comparison:

- arc order
- arc bounds
- stroke cap
- stroke width
- useCenter
- local placement

## Conclusion

The FOR-328 all-pixel diff is not primarily explained by arc order, bounds,
stroke placement, butt cap, or local coordinates: those facts match after the
CPU harness translation is applied. The auditable primary mismatch is the
background/alpha contract and premultiplication boundary: Skia clears a premul
sRGB surface to transparent, whereas the CPU harness renders through
`TestUtils.runGmTest` into an F16 bitmap prefilled with the GM default white
background.

The Skia-over-white probe is diagnostic only and is not counted as a fidelity
score. Its remaining stroke/color differences keep a residual CPU trace risk
after the background contract is normalized.

## Preserved Contracts

| Field | Value |
|---|---|
| audit scope | `selected-cell-only` |
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

- `rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py`
- `rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py`
- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
