# FOR-339 CircularArcsStrokeButt Adjacent F16 Runtime Trace

Linear: `FOR-339`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_PARTIAL_REQUIRES_REFERENCE_SOURCE`

FOR-339 captures real Kanvas Kotlin CPU F16 runtime samples for the two
requested adjacent `CircularArcsStrokeButt` cells. It does not change renderer
behavior, geometry, coverage policy, GPU, WGSL, thresholds, fallback policy,
Kadre, promotion, or score.

## Result

The runtime side is captured for `2` /
`2` adjacent target cells. The decision remains
partial because `0` isolated upstream
Skia reference cells are available in checked-in evidence.

The inaccessible boundary is:

- `isolated-upstream-skia-reference-source`

No selected-cell values are reused as measured adjacent-cell proof.

## Target Cells

| group | column | sweep | samples | captured stroke samples | isolated reference |
|---|---:|---:|---:|---:|---|
| adjacent_arc_stroke_start0_sweep45_target | 1 | 45 | 6 | 5 | no |
| adjacent_arc_stroke_start0_sweep130_target | 3 | 130 | 6 | 5 | no |

## Runtime Capture

- Target samples: `12`
- Trace events: `22`
- F16 store events: `10`
- PNG byte size: `1868`

Each captured stroke sample includes paint source, transformed/premul color,
coverage, F16 SrcOver store data, `SkBitmap.getPixel` readback,
`SkBitmap.getPixelAsSrgb` export readback, PNG-row-equivalent bytes, and the
straight-sRGB quantized-alpha candidate value when coverage is present.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- Historical artifacts FOR-329 through FOR-338 are not rewritten.

## Remaining Risk

The runtime data is now real for both adjacent cells, but residual computation
and any renderer color-policy decision remain blocked until isolated upstream
Skia references exist for these exact cells.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339/circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339.json`
- Validator: `scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-339-circular-arcs-stroke-butt-adjacent-f16-runtime-trace.md`

## Validation

- `rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py`
- `rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py`
- `rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py`
- `rtk ./gradlew --no-daemon -Dkanvas.for339.runtimeTrace.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtAdjacentF16RuntimeTraceTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
