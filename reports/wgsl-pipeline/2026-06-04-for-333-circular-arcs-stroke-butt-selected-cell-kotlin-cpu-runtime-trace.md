# FOR-333 CircularArcsStrokeButt Selected-Cell Kotlin CPU Runtime Trace

Linear: `FOR-333`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-instrumentation-ticket`

Source finding:
`global/kanvas/findings/for-332-circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-requires-kotlin-instrumentation-finding`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_BOUNDARY_IDENTIFIED`

## Result

FOR-333 adds opt-in Kotlin CPU runtime instrumentation for the bounded
`CircularArcsStrokeButt` selected cell. The trace is inactive by default and is
enabled only by the FOR-333 test command:

`rtk ./gradlew --no-daemon -Dkanvas.for333.runtimeTrace.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellKotlinCpuRuntimeTraceTest`

The runtime trace captures all 13 FOR-331/FOR-332 selected samples. The 8 stroke
samples capture:

- paint source alpha and color;
- post-xform `SkColor4f` and premultiplied F16 source;
- per-sample stroke coverage;
- pre/post `SrcOver` F16 store;
- `SkBitmap.getPixel` readback;
- PNG RGBA-row-equivalent bytes after `SkPngEncoder.Encode`.

The first exported divergent boundary is identified as
`f16-readback-and-png-encode`. Stroke-center coverage is full (`16/16`), AA
edge coverage is partial as expected, and the F16 store values read back to the
same CPU RGBA values recorded by FOR-331. The PNG row values match
`SkBitmap.getPixel`; the residual is therefore downstream of F16 Rec.2020 store
values being exported/read as untagged 8-bit RGBA, not an arc coverage or stroke
geometry correction target.

Recommended next correction ticket: target `SkBitmap.getPixel` /
`SkPngEncoder` export color-space handling for F16 Rec.2020 CPU evidence. Do
not change arc coverage, stroke geometry, GPU, WGSL, fallback, threshold, Kadre,
scene promotion, or fidelity scoring from this trace.

## Artifact

| Field | Value |
|---|---|
| JSON | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333/circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333.json` |
| target samples | `13` |
| trace events | `21` |
| F16 store events | `8` |
| inaccessible boundaries | `[]` |
| decision | `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_BOUNDARY_IDENTIFIED` |

## Runtime Samples

| Sample | Zone | XY | Coverage | Readback RGBA | Skia-over-white RGBA | Delta |
|---|---|---:|---:|---|---|---|
| `top_left_background` | `background` | `0,0` | n/a | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `top_edge_background` | `background` | `40,0` | n/a | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `left_edge_background` | `background` | `0,40` | n/a | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `blue_left_aa_edge` | `stroke-aa-edge` | `12,40` | `8/16` | `[214, 208, 253, 255]` | `[210, 210, 255, 255]` | `[4, 2, 2, 0]` |
| `blue_top_outer_edge` | `stroke-aa-edge` | `40,12` | `8/16` | `[214, 208, 253, 255]` | `[209, 209, 255, 255]` | `[5, 1, 2, 0]` |
| `arc_rect_top_left` | `stroke-aa-edge` | `20,20` | `5/16` | `[224, 220, 253, 255]` | `[215, 215, 255, 255]` | `[9, 5, 2, 0]` |
| `blue_top_stroke_center` | `stroke-center` | `40,20` | `16/16` | `[172, 160, 250, 255]` | `[155, 155, 255, 255]` | `[17, 5, 5, 0]` |
| `red_right_stroke_center` | `stroke-center` | `60,40` | `16/16` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` |
| `red_bottom_stroke_center` | `stroke-center` | `40,60` | `16/16` | `[235, 178, 162, 255]` | `[255, 155, 155, 255]` | `[20, 23, 7, 0]` |
| `red_outer_edge` | `stroke-aa-edge` | `67,40` | `6/16` | `[248, 227, 221, 255]` | `[255, 210, 210, 255]` | `[7, 17, 11, 0]` |
| `red_bottom_outer_edge` | `stroke-aa-edge` | `40,67` | `6/16` | `[248, 227, 221, 255]` | `[255, 209, 209, 255]` | `[7, 18, 12, 0]` |
| `cell_center_hole` | `center-hole` | `40,40` | n/a | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |
| `bottom_right_background` | `background` | `79,79` | n/a | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 0]` |

## Preserved Contracts

FOR-331/FOR-332 metrics unchanged:

- FOR-331 different pixels: `2031/6400`
- FOR-331 cell similarity: `68.265625%`
- FOR-331 different pixels outside expected stroke bbox: `0`
- FOR-332 decision:
  `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION`

CPU renderer fixed: `False`

| Contract | Value |
|---|---|
| selected-cell-only | `True` |
| opt-in default inactive | `True` |
| GPU changed | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre changed | `False` |
| scene promotion changed | `False` |
| fidelity score counted | `False` |

## Validation

- `rtk ./gradlew --no-daemon -Dkanvas.for333.runtimeTrace.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellKotlinCpuRuntimeTraceTest`
- `rtk python3 scripts/validate_for333_circular_arcs_stroke_butt_selected_cell_kotlin_cpu_runtime_trace.py`
- `rtk python3 scripts/validate_for332_circular_arcs_stroke_butt_selected_cell_cpu_color_pipeline_trace.py`
- `rtk python3 scripts/validate_for331_circular_arcs_stroke_butt_selected_cell_normalized_stroke_trace.py`
- `rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py`
- `rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py`
- `rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py`
- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333/circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
