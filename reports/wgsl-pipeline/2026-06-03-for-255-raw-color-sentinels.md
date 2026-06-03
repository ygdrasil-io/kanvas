# FOR-255 Raw Color Sentinels

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-255 adds opt-in diagnostic sentinels for the SimpleOffset RGB byte-tail investigation. The property is `kanvas.webgpu.rawColorSentinels`; it is disabled by default and only records values already computed by the normal WebGPU path.

No Crop correction, threshold change, normal CPU/readback fallback, or shader promotion was added. The existing `image-filter.crop-input-nonnull-prepass-required` diagnostic remains preserved.

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/raw-color-sentinel-audit-for255/raw-color-sentinel-audit-for255.json`
- `reports/wgsl-pipeline/scenes/artifacts/raw-color-sentinel-audit-for255/raw-color-sentinel-audit-for255.json`

Uniform sentinel:

- Case: `legacy-source-color-uniform.simple-offset-row1-col0`
- Host RGBA8: `[255, 0, 0, 102]`
- Raw uniform words: `["3f800000", "00000000", "00000000", "3ecccccd"]`
- Raw write RGBA8 reconstruction: `[255, 0, 0, 102]`
- Output sample remains reference `[158, 90, 139, 255]` versus GPU `[157, 90, 138, 255]`

Texture upload sentinel:

- Case: `bitmap-texel-upload-sample.bitmap-rect-nearest`
- Host representative texel RGBA8: `[149, 193, 207, 255]`
- Raw uploaded RGBA8 bytes: `[149, 193, 207, 255]`
- Output sample remains reference `[149, 193, 207, 255]` versus GPU `[148, 193, 207, 255]`

Shader-observed source/sample values before blend/store are not captured in this ticket. Capturing them would require a diagnostic WGSL/storage-buffer or alternate render target path, changing shader or pipeline layout. FOR-255 intentionally stops at already-computed host/write/upload sentinels.

## Finding

The raw uniform-buffer color words and raw uploaded RGBA8 texel bytes match the host-observed inputs. The remaining RGB-only 1-byte residual is therefore downstream of host color packing and texture byte upload, or in reference/output comparison. No bounded correction is proven.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest.FOR-255 raw color sentinels capture uniform and texel input boundary'`
- `rtk ./gradlew --no-daemon :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'`
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
