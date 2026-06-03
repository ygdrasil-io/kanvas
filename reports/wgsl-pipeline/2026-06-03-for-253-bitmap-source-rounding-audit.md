# FOR-253 Bitmap/Source Rounding Stage Audit

Linear: FOR-253

## Scope

FOR-253 isolates the stage that produces the RGB-only 1-byte residual found by
FOR-251 and FOR-252. The probe compares three bounded micro-cases:

| Micro-case | Route | Pixels | Residual pixels | Max channel delta | Alpha deltas | Stage signal |
|---|---:|---:|---:|---:|---:|---|
| `source-color-constant.simple-offset-row1-col0` | `webgpu.canvas.draw-rect.src-over` | 6400 | 1600 | 1 | 0 | Appears before bitmap sampling |
| `bitmap-nearest.generated-whole-scene` | `webgpu.image-rect.strict-nearest` | 4096 | 304 | 1 | 0 | Appears in bitmap-nearest source path |
| `linear-gradient.generated-whole-scene` | `webgpu.generated.linear-gradient.rect` | 4096 | 0 | 0 | 0 | Exact final-store control |

## Evidence

Generated artifact:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-source-rounding-audit-for253/bitmap-source-rounding-audit-for253.json
```

Static mirror:

```text
reports/wgsl-pipeline/scenes/artifacts/bitmap-source-rounding-audit-for253/bitmap-source-rounding-audit-for253.json
```

Validator:

```text
scripts/validate_for253_bitmap_source_rounding_audit.py
```

Test:

```text
gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt
```

## Delta Distribution

The signed channel delta is `GPU - reference`.

Global residual population across the three micro-cases:

| Channel | Distribution |
|---|---|
| R | `-1: 1696`, `0: 208` |
| G | `-1: 112`, `0: 1792` |
| B | `-1: 1696`, `0: 208` |
| A | `0: 1904` |

Representative color pairs:

| Micro-case | Reference -> GPU | Signed delta | Count |
|---|---|---:|---:|
| `source-color-constant.simple-offset-row1-col0` | `[158,90,139,255] -> [157,90,138,255]` | `[-1,0,-1,0]` | 1600 |
| `bitmap-nearest.generated-whole-scene` | `[149,193,207,255] -> [148,193,207,255]` | `[-1,0,0,0]` | 64 |
| `linear-gradient.generated-whole-scene` | no residual | `[]` | 0 |

## Interpretation

The residual appears in a source-color constant path before any bitmap sampling
and also appears in the bitmap-nearest texture path. The generated
linear-gradient control remains byte-exact, so the evidence rules out a global
final pack/store-only defect.

The bounded producer is therefore the input color normalization/rounding area:
legacy source-color uniform packing and bitmap texel upload/sample rounding are
the next sub-problem. The evidence does not prove a Crop renderer bug, a
threshold change, or a bounded correction.

Decision: `KEEP_DIAGNOSTIC`.

No CPU/readback fallback was added, no threshold was changed, and no Crop
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
