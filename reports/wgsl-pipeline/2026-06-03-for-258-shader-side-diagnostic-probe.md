# FOR-258 Shader-Side Diagnostic Probe

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-258 adds an opt-in shader-side diagnostic probe for the two residual RGB
cases:

- `legacy-source-color-uniform.simple-offset-row1-col0`
- `bitmap-texel-upload-sample.bitmap-rect-nearest`

The property is `kanvas.webgpu.for258.shaderSideProbe`; it is disabled by
default. The probe uses the diagnostic-only compute shader
`shaders/shader_side_probe_for258_diagnostic_only.wgsl` and an isolated layout
with the intermediate texture plus a storage buffer. Normal render shaders,
normal render pipelines, thresholds, Crop routing, fallback policy, and normal
CPU/readback behavior are unchanged.

The existing `image-filter.crop-input-nonnull-prepass-required` diagnostic
remains preserved.

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/shader-side-diagnostic-probe-for258/shader-side-diagnostic-probe-for258.json`
- `reports/wgsl-pipeline/scenes/artifacts/shader-side-diagnostic-probe-for258/shader-side-diagnostic-probe-for258.json`

Legacy source-color uniform:

- FOR-255 host/write boundary: `[255, 0, 0, 102]`
- FOR-256 output/readback: `[157, 90, 138, 255]`
- FOR-257 reference/oracle: `[158, 90, 139, 255]`
- FOR-258 shader-side intermediate at `[40, 40]`:
  float `[0.7597656, 0.35961914, 0.5996094, 1.0]`,
  byte reconstruction `[194, 92, 153, 255]`
- Reconstructed normal present output from shader-side sample:
  `[157, 90, 138, 255]`

Bitmap texel upload/sample:

- FOR-255 host/upload boundary: `[149, 193, 207, 255]`
- FOR-256 output/readback: `[148, 193, 207, 255]`
- FOR-257 reference/oracle: `[149, 193, 207, 255]`
- FOR-258 shader-side intermediate at `[8, 24]`:
  float `[0.47045898, 0.7998047, 0.8388672, 1.0]`,
  byte reconstruction `[120, 204, 214, 255]`
- Reconstructed normal present output from shader-side sample:
  `[148, 193, 207, 255]`

## Finding

The isolated shader-side samples reconstruct the existing GPU/readback bytes
after the normal present pass. They do not reconstruct the higher reference
bytes preserved by FOR-257.

No bounded correction is proven. The remaining named boundary is:

```text
shader-consumption/blend-store-before-present
```

## Validation

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.for258.shaderSideProbe=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest.FOR-258 shader-side diagnostic probe isolates pre-present boundary'`
- `rtk python3 scripts/validate_for258_shader_side_diagnostic_probe.py`
- `rtk python3 scripts/validate_for257_reference_byte_expectation_audit.py`
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
