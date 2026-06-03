# FOR-254 Source Uniform vs Bitmap Texel Rounding Audit

Linear: FOR-254

## Scope

FOR-254 compares the remaining RGB-only 1-byte residual across the input
normalization paths selected by FOR-253:

| Path | Route | Pixels | Residual pixels | Max channel delta | Alpha deltas | Signal |
|---|---:|---:|---:|---:|---:|---|
| `legacy-source-color-uniform.simple-offset-row1-col0` | `webgpu.canvas.draw-rect.src-over` | 6400 | 1600 | 1 | 0 | Residual after legacy source-color uniform path |
| `generated-solid-control.solid-rect` | `webgpu.generated.solid-rect.src-over` | 64 | 0 | 0 | 0 | Exact generated solid control |
| `generated-gradient-control.linear-gradient-rect` | `webgpu.generated.linear-gradient.rect` | 4096 | 0 | 0 | 0 | Exact generated gradient/final-store control |
| `bitmap-texel-upload-sample.bitmap-rect-nearest` | `webgpu.image-rect.strict-nearest` | 4096 | 304 | 1 | 0 | Residual after bitmap upload/sample path |

## Evidence

Generated artifact:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/source-texel-rounding-audit-for254/source-texel-rounding-audit-for254.json
```

Static mirror:

```text
reports/wgsl-pipeline/scenes/artifacts/source-texel-rounding-audit-for254/source-texel-rounding-audit-for254.json
```

Validator:

```text
scripts/validate_for254_source_texel_rounding_audit.py
```

Test:

```text
gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt
```

## Input Observability

The audit records packed or loaded values when they are observable from tests or
artifacts:

| Path | Input bytes / floats | Representative output | Signed delta |
|---|---|---|---|
| Legacy source uniform | ARGB `[102,255,0,0]`, RGBA `[255,0,0,102]`, normalized `[1.0,0.0,0.0,0.4]`, premul `[0.4,0.0,0.0,0.4]` | `[158,90,139,255] -> [157,90,138,255]` | `[-1,0,-1,0]` |
| Generated solid control | ARGB `[255,23,33,28]`, RGBA `[23,33,28,255]`, normalized/premul `[0.09019608,0.12156863,0.10980392,1.0]` | `[23,33,28,255] -> [23,33,28,255]` | `[0,0,0,0]` |
| Generated gradient control | Gradient stop uniforms are not dumped by this audit | `[125,0,130,255] -> [125,0,130,255]` | `[0,0,0,0]` |
| Bitmap texel path | Representative oracle RGBA `[149,193,207,255]`, normalized/premul `[0.58431375,0.75686276,0.8117647,1.0]` | `[149,193,207,255] -> [148,193,207,255]` | `[-1,0,0,0]` |

The raw uniform-buffer bytes, raw uploaded texture bytes, and shader-observed
sampled texel are not directly captured by this probe.

## Delta Distribution

The signed channel delta is `GPU - reference`.

Global residual population across the four paths:

| Channel | Distribution |
|---|---|
| R | `-1: 1696`, `0: 208` |
| G | `-1: 112`, `0: 1792` |
| B | `-1: 1696`, `0: 208` |
| A | `0: 1904` |

## Interpretation

The residual is present in the legacy source-color uniform path and in the
bitmap texel upload/sample path. The generated solid and generated gradient
controls are byte-exact, so the issue is not a global final pack/store defect
and not a general generated-uniform defect.

This narrows the problem to an input normalization/consumption boundary, but it
does not prove whether the cause is host-side normalization, uniform-buffer
write/consumption, texture upload/sample, shader arithmetic, or reference
conversion. A bounded correction is therefore not justified.

Decision: `KEEP_DIAGNOSTIC`.

Next required instrument: renderer diagnostic sentinels that dump raw
uniform-buffer color words, uploaded RGBA8 texel bytes, and shader-observed
sampled color before blend/store.

No Crop correction was applied, no threshold was changed, and no CPU/readback
fallback was added.

The existing Crop limitation/refusal remains preserved:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest'
```

Required full validation for handoff:

```text
rtk python3 scripts/validate_for254_source_texel_rounding_audit.py
rtk python3 scripts/validate_for253_bitmap_source_rounding_audit.py
rtk python3 scripts/validate_for252_color_reference_bias_audit.py
rtk python3 scripts/validate_for251_color_premul_audit.py
rtk python3 scripts/validate_for250_high_delta_scan.py
rtk python3 scripts/validate_for249_reference_gpu_residual_probe.py
rtk python3 scripts/validate_for248_final_crop_composite_probe.py
rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
