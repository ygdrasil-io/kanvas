# FOR-252 Color/Reference Bias Audit Outside Image Filters

Linear: FOR-252

## Scope

FOR-252 audits whether the RGB-only 1-byte residual characterized by FOR-251 is
limited to the image-filter Crop path or also appears outside image-filter
routing.

The probe compares three non-image-filter samples:

| Sample | Route | Pixels | Residual pixels | Max channel delta | Alpha deltas |
|---|---:|---:|---:|---:|---:|
| `simple-offsetimagefilter.row1-col0-no-filter` | `webgpu.canvas.draw-rect.src-over` | 6400 | 1600 | 1 | 0 |
| `bitmap-rect-nearest.whole-scene` | `webgpu.image-rect.strict-nearest` | 4096 | 304 | 1 | 0 |
| `linear-gradient-rect.whole-scene` | `webgpu.generated.linear-gradient.rect` | 4096 | 0 | 0 | 0 |

## Evidence

Generated artifact:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/color-reference-bias-audit-for252/color-reference-bias-audit-for252.json
```

Static mirror:

```text
reports/wgsl-pipeline/scenes/artifacts/color-reference-bias-audit-for252/color-reference-bias-audit-for252.json
```

Validator:

```text
scripts/validate_for252_color_reference_bias_audit.py
```

Test:

```text
gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt
```

## Delta Distribution

The signed channel delta is `GPU - reference`.

Global residual population across the three non-image-filter samples:

| Channel | Distribution |
|---|---|
| R | `-1: 1696`, `0: 208` |
| G | `-1: 112`, `0: 1792` |
| B | `-1: 1696`, `0: 208` |
| A | `0: 1904` |

Dominant color pairs:

| Sample | Reference -> GPU | Signed delta | Count |
|---|---|---:|---:|
| `simple-offsetimagefilter.row1-col0-no-filter` | `[158,90,139,255] -> [157,90,138,255]` | `[-1,0,-1,0]` | 1600 |
| `bitmap-rect-nearest.whole-scene` | `[149,193,207,255] -> [148,193,207,255]` | `[-1,0,0,0]` | 64 |
| `bitmap-rect-nearest.whole-scene` | `[69,20,208,255] -> [69,20,207,255]` | `[0,0,-1,0]` | 64 |

## Interpretation

The RGB-only 1-byte tail is not limited to the Crop image-filter renderer. It
reproduces outside image-filter routing in:

- the SimpleOffset source-only cell `row1-col0-no-filter`;
- the generated `bitmap-rect-nearest` scene.

`linear-gradient-rect` remains byte-exact in this audit, so the finding is not
a blanket WebGPU failure. The observed pattern is still consistent with a
bounded color/reference/rounding bias in selected bitmap/source-color paths,
not with a proven Crop renderer defect.

Decision: `KEEP_DIAGNOSTIC`.

No threshold was changed, no CPU/readback fallback was added, and no Crop
correction was applied.

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
