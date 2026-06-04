# FOR-332 CircularArcsStrokeButt Selected-Cell CPU Color Pipeline Trace

Linear: `FOR-332`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-instrumented-cpu-color-pipeline-trace-ticket`

Source finding:
`global/kanvas/findings/for-331-circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-colorspace-premul-suspected-finding`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION`

## Result

FOR-332 adds a selected-cell CPU color pipeline boundary trace for the FOR-331
residual. It does not fix the CPU renderer and does not change GPU, WGSL,
threshold, fallback, Kadre, scene-promotion, or fidelity-score behavior.

Conclusion: `kotlin-instrumentation-required`.

Correction targetable: `False`.

Kotlin instrumentation required: `True`.

Interpretation: FOR-331 final-pixel evidence and static source inspection keep the residual in the color-space/premul/F16-readback/PNG cluster, but no inspected boundary captures runtime pre/post values for the selected samples. A renderer correction is not targetable yet.

Recommended next action: Add targeted Kotlin instrumentation around transformPaintColor/colorToF16Premul, coverage, blendF16PremulMode store, SkBitmap.getPixel F16 readback, and SkPngEncoder.rgbaRow for the selected FOR-331 samples.

## Inputs

| Input | Path | Source | Required decision | SHA-256 |
|---|---|---|---|---|
| FOR-331 trace | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331/circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331.json` | `FOR-331` / `circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_COLORSPACE_PREMUL_SUSPECTED` | `d264023b29b71f41749fc0ee7ad17696b06cd2d95b09dc35f354ed11d323a47b` |
| FOR-330 white diff | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330/circular-arcs-stroke-butt-selected-cell-white-background-diff-for330.json` | `FOR-330` / `circular-arcs-stroke-butt-selected-cell-white-background-diff-for330` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_RESIDUAL_PRESENT` | `0cc7c1d93225f8d4c2558ca18a8d3709aeb33003f349a6b175459640f4d2570c` |
| FOR-329 CPU audit | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329.json` | `FOR-329` / `circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED` | `e89d99fd930ccf903fc62166bb8c8716b789ed7006dcd45df092fadfd174ae26` |
| FOR-327 Skia reference | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/circular-arcs-stroke-butt-selected-cell-skia-reference-for327.json` | `FOR-327` / `circular-arcs-stroke-butt-selected-cell-skia-reference-for327` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY` | `4e716ebe4f04b3f65abad88e53796b1793ca66342af1009ac29384b4749c348c` |
| FOR-322 harness | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/circular-arcs-stroke-butt-selected-cell-harness-for322.json` | `FOR-322` / `circular-arcs-stroke-butt-selected-cell-harness-for322` | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY` | `4a060140307f3cfe3d6d876a1b088243013f4a23ac6b66595fa8f2503f436906` |

Input validation valid: `True`

Invalid reasons:

- none

## FOR-331 Metrics Gate

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
| different pixels outside expected stroke bbox | `0` |
| primary FOR-331 hypothesis | `colorspace-premul-or-png-encode` |
| primary FOR-331 hypothesis weight | `0.62` |

## Samples

| Sample | Zone | XY | Skia RGBA | Skia over white RGBA | CPU RGBA | Naive alpha-over-white RGBA | CPU vs Skia abs delta | Probable boundary classification |
|---|---|---|---|---|---|---|---|---|
| `top_left_background` | `background` | `0,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` | `background-or-center-hole-exact-after-white-normalization` |
| `top_edge_background` | `background` | `40,0` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` | `background-or-center-hole-exact-after-white-normalization` |
| `left_edge_background` | `background` | `0,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` | `background-or-center-hole-exact-after-white-normalization` |
| `blue_left_aa_edge` | `stroke-aa-edge` | `12,40` | `[0, 0, 255, 45]` | `[210, 210, 255, 255]` | `[214, 208, 253, 255]` | `[210, 210, 255, 255]` | `[4, 2, 2, 0]` | `coverage-plus-colorspace-premul-boundary-candidate-runtime-unresolved` |
| `blue_top_outer_edge` | `stroke-aa-edge` | `40,12` | `[0, 0, 255, 46]` | `[209, 209, 255, 255]` | `[214, 208, 253, 255]` | `[209, 209, 255, 255]` | `[5, 1, 2, 0]` | `coverage-plus-colorspace-premul-boundary-candidate-runtime-unresolved` |
| `arc_rect_top_left` | `stroke-aa-edge` | `20,20` | `[0, 0, 255, 40]` | `[215, 215, 255, 255]` | `[224, 220, 253, 255]` | `[215, 215, 255, 255]` | `[9, 5, 2, 0]` | `coverage-plus-colorspace-premul-boundary-candidate-runtime-unresolved` |
| `blue_top_stroke_center` | `stroke-center` | `40,20` | `[0, 0, 255, 100]` | `[155, 155, 255, 255]` | `[172, 160, 250, 255]` | `[155, 155, 255, 255]` | `[17, 5, 5, 0]` | `colorspace-premul-or-png-encode-boundary-candidate-runtime-unresolved` |
| `red_right_stroke_center` | `stroke-center` | `60,40` | `[255, 0, 0, 100]` | `[255, 155, 155, 255]` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` | `colorspace-premul-or-png-encode-boundary-candidate-runtime-unresolved` |
| `red_bottom_stroke_center` | `stroke-center` | `40,60` | `[255, 0, 0, 100]` | `[255, 155, 155, 255]` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` | `colorspace-premul-or-png-encode-boundary-candidate-runtime-unresolved` |
| `red_outer_edge` | `stroke-aa-edge` | `67,40` | `[255, 0, 0, 45]` | `[255, 210, 210, 255]` | `[248, 227, 221, 255]` | `[255, 210, 210, 255]` | `[7, 17, 11, 0]` | `coverage-plus-colorspace-premul-boundary-candidate-runtime-unresolved` |
| `red_bottom_outer_edge` | `stroke-aa-edge` | `40,67` | `[255, 0, 0, 46]` | `[255, 209, 209, 255]` | `[248, 227, 221, 255]` | `[255, 209, 209, 255]` | `[7, 18, 12, 0]` | `coverage-plus-colorspace-premul-boundary-candidate-runtime-unresolved` |
| `cell_center_hole` | `center-hole` | `40,40` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` | `background-or-center-hole-exact-after-white-normalization` |
| `bottom_right_background` | `background` | `79,79` | `[0, 0, 0, 0]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `None` | `[0, 0, 0, 0]` | `background-or-center-hole-exact-after-white-normalization` |

## CPU Pipeline Boundary Audit

| Boundary | Inspected files | Proved | Not proved | Runtime values captured |
|---|---|---|---|---|
| `selected-cell-input-and-paint` | `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt` | paint source alpha is 100/255 for both arcs; geometry and draw order match the selected cell audited by FOR-329/FOR-331 | post-color-xform float RGBA for any selected sample; post-store F16 premul value for any selected sample | `False` |
| `testutils-raster-sink-f16` | `cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt`, `cpu-raster/src/main/kotlin/org/skia/dm/RasterSinkF16.kt` | Kanvas CPU selected-cell output is rendered into F16 Rec.2020 before PNG encoding; the CPU harness starts from GM background white, not transparent | whether the residual is introduced before store or only during F16 readback/PNG encode | `False` |
| `paint-color-xform-and-premul` | `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt` | the selected-cell CPU stroke uses a color-space conversion plus premul source contract before F16 blend | the exact converted red/blue float values at the sampled stroke pixels; whether converted values already match the final CPU residual direction | `False` |
| `stroke-coverage` | `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt` | coverage participates before the F16 SrcOver store boundary; FOR-331 residual remains inside the expected stroke bbox with 0 different pixels outside | per-sample coverage values for the FOR-331 stroke-center and AA-edge samples; whether AA coverage alone can explain edge samples after color/readback effects are removed | `False` |
| `src-over-f16-store` | `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt` | the final CPU store before PNG is premul float in the destination bitmap buffer | stored premul float RGBA before readback for any selected sample; whether store math or prior color/coverage inputs first diverge from Skia-over-white | `False` |
| `f16-readback-and-png-encode` | `kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt`, `kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt` | PNG evidence samples are downstream of an F16 premul to 8-bit unpremul readback boundary; the color-space tag is not preserved in the checked PNG output | whether the residual first appears at readback/encode rather than earlier color conversion or store | `False` |

## Decision

The static audit narrows the residual to the color-space / premul / coverage /
F16 store / PNG-readback cluster, but it does not prove a single boundary. The
stable decision is therefore
`CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION` unless future runtime values prove
one boundary and switch the artifact to `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_BOUNDARY_IDENTIFIED`.

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

- `rtk python3 scripts/validate_for332_circular_arcs_stroke_butt_selected_cell_cpu_color_pipeline_trace.py`
- `rtk python3 scripts/validate_for331_circular_arcs_stroke_butt_selected_cell_normalized_stroke_trace.py`
- `rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py`
- `rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py`
- `rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py`
- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-for332/circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-for332.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
