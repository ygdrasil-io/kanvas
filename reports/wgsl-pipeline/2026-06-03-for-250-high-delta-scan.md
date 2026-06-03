# FOR-250 High-Delta SimpleOffset Residual Scan

Linear: FOR-250

Parent: FOR-241

Scene: `crop-image-filter-nonnull-prepass`

## Scope

FOR-250 adds a whole-scene test-side scan for `SimpleOffsetImageFilterGM`.
The scan compares the upstream Skia reference PNG with the normal WebGPU
render, classifies residual pixels by the explicit SimpleOffset GM cell grid,
and writes stable evidence. It does not modify the WebGPU renderer and does
not add a CPU/readback fallback to the render path.

## Evidence

Generated:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/high-delta-scan-for250.json
```

Static mirror:

```text
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/high-delta-scan-for250.json
```

Route metadata:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
```

## Result

FOR-250 finds no strong high-delta block:

There are 0 pixels above `maxChannelDelta > 8`.

| Metric | Value |
|---|---:|
| Strict high-delta threshold | `maxChannelDelta > 8` |
| Pixels above strict high-delta threshold | 0 |
| Residual scan threshold | `maxChannelDelta > 0` |
| Non-identical pixels | 6622 |
| Global maxChannelDelta | 1 |
| Top pixel limit | 20 |

Dominant residual cells by non-identical pixel count:

| Cell | Pixels | Max delta |
|---|---:|---:|
| `row1-col0-no-filter` | 1600 | 1 |
| `row1-col1-offset-no-crop` | 1600 | 1 |
| `row2-col3-crop-clip-dst` | 1444 | 1 |
| `row1-col2-offset-crop-src` | 517 | 1 |
| `row1-col5-offset-clip-dst` | 517 | 1 |
| `row1-col3-offset-clip-src` | 400 | 1 |
| `row2-col1-crop-src-clip-dst` | 312 | 1 |
| `row2-col2-crop-dst-clip-src` | 156 | 1 |
| `row1-col4-offset-crop-20x20` | 76 | 1 |
| `row2-col0-crop-clip-src` | 0 | 0 |
| `outside-simple-offset-cells` | 0 | 0 |

## Interpretation

FOR-247 proved the selected scratch pixel was correct. FOR-248 proved the
forced final Crop composite could read that scratch pixel and apply alpha.
FOR-249 proved the previously selected `(385,125)` local window only had
`maxChannelDelta=1`.

FOR-250 extends that to the whole scene: there are no pixels above
`maxChannelDelta > 8`. The score gap is a diffuse byte-level residual across
several SimpleOffset cells, not a bounded high-delta Crop/composite failure.

Decision: `KEEP_DIAGNOSTIC`.

No renderer correction is applied by FOR-250. The evidence does not prove a
bounded WebGPU mapping bug, and changing thresholds would hide the remaining
fidelity gap rather than fix it.

The out-of-scope refusal row remains:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk python3 scripts/validate_for250_high_delta_scan.py
rtk python3 scripts/validate_for249_reference_gpu_residual_probe.py
rtk python3 scripts/validate_for248_final_crop_composite_probe.py
rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

## Remaining Risk

The GPU/reference score remains 98.44% and the strict target remains 99.95%.
The next useful work is not another Crop target probe, but a byte-level
color/premultiplication or reference-generation audit across the dominant
cells, especially `row1-col0-no-filter`, `row1-col1-offset-no-crop`, and
`row2-col3-crop-clip-dst`.
