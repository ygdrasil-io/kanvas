# FOR-257 Reference Byte Expectation Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-257 audits the reference byte expectation for the two residual RGB cases:

- `legacy-source-color-uniform.simple-offset-row1-col0`
- `bitmap-texel-upload-sample.bitmap-rect-nearest`

The audit reuses the opt-in FOR-255/FOR-256 snapshots and adds only test-side
reference/oracle reconstruction. It does not add a new renderer property, WGSL
storage, shader-side layout, pipeline layout, threshold change, Crop correction,
or normal CPU/readback fallback. The existing
`image-filter.crop-input-nonnull-prepass-required` diagnostic remains preserved.

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/reference-byte-expectation-audit-for257/reference-byte-expectation-audit-for257.json`
- `reports/wgsl-pipeline/scenes/artifacts/reference-byte-expectation-audit-for257/reference-byte-expectation-audit-for257.json`

Legacy source-color uniform:

- FOR-255 host/write boundary: `[255, 0, 0, 102]`
- FOR-256 output sample at `[40, 40]`: reference `[158, 90, 139, 255]`,
  GPU/readback `[157, 90, 138, 255]`
- Reconstructed upstream PNG oracle at `[40, 40]`: `[158, 90, 139, 255]`
- Reference byte round-trip quantization: `[158, 90, 139, 255]`
- Local source-over/present formula candidate: `[220, 153, 145, 255]`;
  it matches neither the upstream reference nor GPU/readback, so it is not an
  admissible correction oracle.

Bitmap texel upload/sample:

- FOR-255 host/upload boundary: `[149, 193, 207, 255]`
- FOR-256 output sample at `[8, 24]`: reference `[149, 193, 207, 255]`,
  GPU/readback `[148, 193, 207, 255]`
- Reconstructed generated Skia artifact oracle at `[8, 24]`:
  `[149, 193, 207, 255]`
- Strict-nearest uploaded texel expectation: `[149, 193, 207, 255]`
- Reference byte round-trip quantization: `[149, 193, 207, 255]`

## Finding

The reference/oracle bytes are stable and reconstructable for both residual
cases, but every admissible reference reconstruction matches the reference side,
not the lower GPU/readback bytes. Therefore the `-1` RGB tail is not proven to
be a bounded reference-byte expectation or quantization correction.

No correction is applied. Rewriting the reference expectation now would overfit
stable Skia/oracle bytes without shader-side proof.

The remaining named boundary is:

```text
shader-consumption/blend-store/present-quantization
```

A separate shader-side diagnostic probe with an isolated layout becomes the
next bounded step if this residual must be root-caused further.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest.FOR-257 reference byte expectation audit preserves shader-side boundary'`
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
