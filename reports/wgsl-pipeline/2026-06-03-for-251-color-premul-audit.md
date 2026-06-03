# FOR-251 Color/Premultiplication SimpleOffset Residual Audit

Linear: FOR-251

Parent: FOR-241

Scene: `crop-image-filter-nonnull-prepass`

## Scope

FOR-251 adds a test-side audit for the diffuse `SimpleOffsetImageFilterGM`
residual left by FOR-250. The audit compares the upstream Skia reference PNG
with the normal WebGPU render, groups signed channel deltas by color and by the
dominant SimpleOffset cells, and writes stable evidence.

It does not modify the WebGPU renderer, does not add a renderer diagnostic
property, does not add a CPU/readback fallback to the render path, and does not
change thresholds.

## Evidence

Generated:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/color-premul-audit-for251.json
```

Static mirror:

```text
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/color-premul-audit-for251.json
```

Route metadata names the proof without over-claiming support:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
```

## Result

The residual remains an RGB-only byte tail.

| Metric | Value |
|---|---:|
| Non-identical pixels | 6622 |
| Global maxChannelDelta | 1 |
| `alphaDeltaNonZeroPixels` | 0 |
| `rgbOnlyResidualPixels` | 6622 |
| GPU/reference score | 98.44% |
| Strict target | 99.95% |

Signed channel delta means `GPU - reference`.

| Channel | Signed deltas |
|---|---|
| R | `-1`: 5727, `0`: 895 |
| G | `0`: 6583, `1`: 39 |
| B | `-1`: 3227, `0`: 3044, `1`: 351 |
| A | `0`: 6622 |

Dominant cells:

| Cell | Pixels | Max delta | Alpha deltas | Main color pair |
|---|---:|---:|---:|---|
| `row1-col0-no-filter` | 1600 | 1 | 0 | `[158,90,139,255] -> [157,90,138,255]`, delta `[-1,0,-1,0]` |
| `row1-col1-offset-no-crop` | 1600 | 1 | 0 | `[221,153,145,255] -> [220,153,145,255]`, delta `[-1,0,0,0]` |
| `row2-col3-crop-clip-dst` | 1444 | 1 | 0 | `[221,153,145,255] -> [220,153,145,255]`, delta `[-1,0,0,0]` |

## Interpretation

FOR-247 proved the selected scratch pixel was correct. FOR-248 proved the
forced final Crop composite could read that scratch pixel and apply alpha.
FOR-249 showed the selected final fragment window was only a 1-byte residual.
FOR-250 showed the whole scene has no `maxChannelDelta > 8` pixel.

FOR-251 characterizes the remaining score loss as RGB-only byte rounding:
every residual pixel has `maxChannelDelta=1`, and alpha never differs. The
dominant cells include `row1-col0-no-filter`, which does not exercise the
image-filter Crop route. That means the evidence points away from a bounded
Crop/composite correction and toward a broader color/reference rounding audit.

Decision: `KEEP_DIAGNOSTIC`.

No renderer correction is justified by this ticket. A threshold change would
hide the remaining fidelity gap rather than explain it.

The out-of-scope refusal row remains:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk python3 scripts/validate_for251_color_premul_audit.py
rtk python3 scripts/validate_for250_high_delta_scan.py
rtk python3 scripts/validate_for249_reference_gpu_residual_probe.py
rtk python3 scripts/validate_for248_final_crop_composite_probe.py
rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

## Remaining Risk

The scene remains below the strict target: GPU/reference is still 98.44%
versus 99.95%. FOR-251 does not prove which color conversion or reference
generation step accounts for the 1-byte rounding. The next correction should
only happen after a broader color pipeline audit proves the same signed delta
pattern across non-image-filter scenes or isolates a bounded pack/store issue.
