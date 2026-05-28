# M38 Image-Filter Child Pre-Pass Dashboard Scene

Date: 2026-05-28
Ticket: GRA-183
Scene: `crop-image-filter-nonnull-prepass`

## Scope

Add the selected M38 `Crop(kDecal, input = Offset(null))` image-filter child
pre-pass case to the static scene evidence dashboard. This row represents the
promoted SimpleOffset target from GRA-181/GRA-182 and keeps the remaining
out-of-scope `Crop(input = nonNull)` diagnostic separate from render failures.

## Dashboard Row

| Field | Value |
|---|---|
| Scene id | `crop-image-filter-nonnull-prepass` |
| Status | `pass` |
| Reference kind | `skia-upstream` |
| CPU route | `cpu.image-filter.crop-nonnull.offset-oracle` |
| GPU route | `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite` |
| Pre-pass diagnostic | `LayerCompositeDraw.materializeToIntermediate` into `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch` |
| Removed unsupported reason | `image-filter.crop-input-nonnull-prepass-required` |

## Artifacts

The row links CPU, GPU, diff/reference, route diagnostics, pre-pass diagnostics,
and stats under:

```text
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/
```

Files:

- `skia.png`
- `cpu.png`
- `gpu.png`
- `cpu-diff.png`
- `gpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `route-prepass.json`
- `stats.json`

## Stats

| Metric | CPU | GPU |
|---|---:|---:|
| Similarity | 84.88% | 98.13% |
| Matching pixels | 108,644 / 128,000 | 125,600 / 128,000 |
| Max channel delta | A=0, R=60, G=107, B=110 | A=0, R=38, G=107, B=110 |
| Threshold | 50.0% | 50.0% |

The threshold is inherited from the existing SimpleOffset image-filter fixture;
this ticket does not lower similarity floors.

## Unsupported Category Boundary

Current full inventory after GRA-181/GRA-182 reports:

```text
unsupported-image-filter=0
image-filter.crop-input-nonnull-prepass-required=0 for selected SimpleOffset rows
```

If future unsupported `Crop(input = nonNull)` graph shapes appear, they should
remain classified with stable diagnostics and should be represented as
`expected-unsupported`, not as adapter-missing and not as render failures.

## Validation

```text
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk git diff --check
```
