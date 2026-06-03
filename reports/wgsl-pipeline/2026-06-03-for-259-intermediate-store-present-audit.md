# FOR-259 Intermediate Store Present Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-259 audits the `shader-consumption/blend-store-before-present` boundary
left by FOR-258 for the two RGB byte residual cases:

- `legacy-source-color-uniform.simple-offset-row1-col0`
- `bitmap-texel-upload-sample.bitmap-rect-nearest`

The audit is test-only. It reuses the opt-in FOR-255 raw sentinels, FOR-256
output/readback samples, and FOR-258 shader-side diagnostic compute probe. It
adds no renderer property, no normal shader, no threshold change, no Crop
correction, no fallback-policy change, and does not globally enable
`targetColorSpaceBlend`.

The existing `image-filter.crop-input-nonnull-prepass-required` diagnostic
remains preserved.

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/intermediate-store-present-audit-for259/intermediate-store-present-audit-for259.json`
- `reports/wgsl-pipeline/scenes/artifacts/intermediate-store-present-audit-for259/intermediate-store-present-audit-for259.json`

Intermediate format:

- Normal WebGPU intermediate texture format: `RGBA16Float`
- Diagnostic alternative: test-side `RGBA8Unorm` store/load simulation before
  applying the same present reconstruction

Legacy source-color uniform:

- FOR-258 shader-side `RGBA16Float` intermediate at `[40, 40]`:
  float `[0.7597656, 0.35961914, 0.5996094, 1.0]`,
  byte reconstruction `[194, 92, 153, 255]`
- Current present reconstruction from the `RGBA16Float` sample:
  `[157, 90, 138, 255]`
- GPU/readback: `[157, 90, 138, 255]`
- Reference/oracle: `[158, 90, 139, 255]`
- Test-side `RGBA8Unorm` store/load simulation before present:
  float `[0.7607843, 0.36078432, 0.6, 1.0]`,
  present reconstruction `[158, 90, 139, 255]`

Bitmap texel upload/sample:

- FOR-258 shader-side `RGBA16Float` intermediate at `[8, 24]`:
  float `[0.47045898, 0.7998047, 0.8388672, 1.0]`,
  byte reconstruction `[120, 204, 214, 255]`
- Current present reconstruction from the `RGBA16Float` sample:
  `[148, 193, 207, 255]`
- GPU/readback: `[148, 193, 207, 255]`
- Reference/oracle: `[149, 193, 207, 255]`
- Test-side `RGBA8Unorm` store/load simulation before present:
  float `[0.47058824, 0.8, 0.8392157, 1.0]`,
  present reconstruction `[149, 193, 207, 255]`

## Finding

The current `RGBA16Float` intermediate texture sample plus the normal present
reconstruction matches the existing GPU/readback bytes for both residual
cases. A bounded test-side `RGBA8Unorm` store/load simulation before the same
present reconstruction matches the higher reference/oracle bytes for both
cases.

This narrows the remaining boundary to:

```text
rgba16float-intermediate-store-to-present-byte-quantization-policy
```

No normal correction is applied. Changing the intermediate format back to
`RGBA8Unorm`, adding present-time byte quantization, or otherwise changing the
normal precision policy would be broader than this ticket and needs explicit
before/after evidence that exact scenes and precision-sensitive scenes do not
regress.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest.FOR-259 intermediate store present audit narrows residual boundary'`
- `rtk python3 scripts/validate_for259_intermediate_store_present_audit.py`
- `rtk python3 scripts/validate_for258_shader_side_diagnostic_probe.py`
- `rtk python3 scripts/validate_for257_reference_byte_expectation_audit.py`
- `rtk python3 scripts/validate_for256_shader_store_reference_boundary.py`
- `rtk python3 scripts/validate_for255_raw_color_sentinels.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
