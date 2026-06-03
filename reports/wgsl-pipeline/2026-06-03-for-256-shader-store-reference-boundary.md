# FOR-256 Shader Store Reference Boundary

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-256 adds an opt-in output/readback boundary probe for the residual RGB
byte-tail investigation. The property is
`kanvas.webgpu.for256.outputReadbackBoundary`; it is disabled by default and
only records selected bytes already returned by the normal `flush()` readback.

No WGSL storage buffer, shader-side layout change, Crop correction, threshold
change, normal CPU/readback fallback, or render-route promotion was added. The
existing `image-filter.crop-input-nonnull-prepass-required` diagnostic remains
preserved.

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/shader-store-reference-boundary-for256/shader-store-reference-boundary-for256.json`
- `reports/wgsl-pipeline/scenes/artifacts/shader-store-reference-boundary-for256/shader-store-reference-boundary-for256.json`

Legacy source-color uniform:

- Case: `legacy-source-color-uniform.simple-offset-row1-col0`
- FOR-255 host/write boundary: `[255, 0, 0, 102]`
- Output/reference sample at `[40, 40]`: reference `[158, 90, 139, 255]`,
  GPU comparison `[157, 90, 138, 255]`
- FOR-256 final readback sample: `[157, 90, 138, 255]`
- Finding: final readback bytes match the GPU bytes used by the reference
  comparison.

Bitmap texel upload/sample:

- Case: `bitmap-texel-upload-sample.bitmap-rect-nearest`
- FOR-255 host/upload boundary: `[149, 193, 207, 255]`
- Output/reference sample at `[8, 24]`: reference `[149, 193, 207, 255]`,
  GPU comparison `[148, 193, 207, 255]`
- FOR-256 final readback sample: `[148, 193, 207, 255]`
- Finding: final readback bytes match the GPU bytes used by the reference
  comparison.

## Finding

FOR-255 proved that the host source color packing and RGBA8 texture upload
bytes are correct for the two residual cases. FOR-256 proves that the final
readback bytes are exactly the bytes entering the reference/GPU comparison.

The remaining named boundary is therefore:

```text
shader-consumption/blend-store/present-quantization-or-reference-byte-expectation
```

This probe does not isolate a bounded correction. Applying a Crop fix, changing
thresholds, or adding a normal-path readback fallback would overfit the
diagnostic evidence.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest.FOR-256 output readback boundary isolates remaining rgb residual'`
- `rtk python3 scripts/validate_for256_shader_store_reference_boundary.py`
- `rtk python3 scripts/validate_for255_raw_color_sentinels.py`
- `rtk python3 scripts/validate_for254_source_texel_rounding_audit.py`
- `rtk python3 scripts/validate_for253_bitmap_source_rounding_audit.py`
- `rtk python3 scripts/validate_for252_color_reference_bias_audit.py`
- `rtk python3 scripts/validate_for251_color_premul_audit.py`
- `rtk python3 scripts/validate_for250_high_delta_scan.py`
- `rtk python3 scripts/validate_for249_reference_gpu_residual_probe.py`
- `rtk python3 scripts/validate_for248_final_crop_composite_probe.py`
- `rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py`
- `rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
