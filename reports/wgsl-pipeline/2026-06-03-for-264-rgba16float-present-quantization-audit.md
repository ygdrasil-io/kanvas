# FOR-264 RGBA16Float Present Quantization Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

FOR-264 compares the normal `RGBA16Float` intermediate store boundary
against the presented byte output for residual FOR-261 cases, an exact
control, and a `targetColorSpaceBlend`-sensitive control. It does not
change production defaults, shaders, thresholds, Crop policy, fallback
policy, or global renderer properties.

Preserved fallback reason:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Artifacts

- `reports/wgsl-pipeline/scenes/generated/artifacts/rgba16float-present-quantization-audit-for264/rgba16float-present-quantization-audit-for264.json`
- `reports/wgsl-pipeline/scenes/artifacts/rgba16float-present-quantization-audit-for264/rgba16float-present-quantization-audit-for264.json`

## Cases

| Case | Exact similarity | Max delta | Matching pixels | Format | Quantization policy | Route diagnostics | Classification |
|---|---:|---:|---:|---|---|---|---|
| `residual-route.simple-offsetimagefilter` | 94.826563 | 1 | 121378/128000 | `RGBA16Float` | `RGBA16Float textureLoad in present pass followed by byte output quantization` | `webgpu.image-filter.offset-crop-prepass-and-src-over` | `rgba16float-present-byte-quantization-residual` |
| `residual-route.bitmap-rect-nearest` | 92.578125 | 1 | 3792/4096 | `RGBA16Float` | `RGBA16Float textureLoad in present pass followed by byte output quantization` | `webgpu.image-rect.strict-nearest` | `rgba16float-present-byte-quantization-residual` |
| `exact-control.black-white-rect` | 100 | 0 | 64/64 | `RGBA16Float` | `RGBA16Float normal path presents byte-exact output` | `webgpu.coverage.analytic-rect` | `already-exact-no-quantization-correction-needed` |
| `target-blend-sensitive.m60-target-colorspace-neutral-aa` | 50 | 13 | 2/4 | `RGBA16Float` | `normal targetColorSpaceBlend=false present pass converts sRGB-coded intermediate after blending` | `webgpu.present-pass.srgb-to-rec2020-after-blend` | `targetColorSpaceBlend-required-not-quantization` |
| `target-blend-sensitive.m60-target-colorspace-neutral-aa.targetBlendControl` | 100 | 0 | 4/4 | `RGBA16Float` | `targetColorSpaceBlend=true control` | `webgpu.target-colorspace-blend.solid-coverage` | `targetColorSpaceBlend-not-present-quantization` |

## Conclusion

none_applied: RGBA16Float present-byte quantization remains diagnostic because the residual representatives are bounded samples, the exact control needs no correction, and the targetColorSpaceBlend-sensitive fixture is corrected only by targetColorSpaceBlend=true

Missing condition: `missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend`.

The remaining boundary stays `rgba16float-intermediate-store-to-present-byte-quantization-policy`.

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-264*'
rtk python3 scripts/validate_for264_rgba16float_present_quantization_audit.py
rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py
rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
