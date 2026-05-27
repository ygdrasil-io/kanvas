# M32 Image-Rect Smoke Promotion Guard

Date: 2026-05-27
Linear: GRA-97
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation

## Goal

Prevent bitmap/image-rect fixtures from entering the required
`:gpu-raster:gpuSmokeTest` gate while they still have unresolved
`similarity-regression` inventory evidence.

## Change

`gpu-raster/build.gradle.kts` now derives `gpuSmokePatterns` from typed
`GpuSmokePatternSpec` entries and runs `validateGpuSmokePromotionPolicy` before
`:gpu-raster:gpuSmokeTest`.

The validation task fails closed when:

- a smoke candidate is explicitly marked with `unresolvedSimilarityRegression`;
- a smoke candidate is missing PM/reviewer evidence for promotion;
- a known M32 bitmap/image-rect regression class is included in smoke while its
  resolution evidence report is missing.

Known M32 image-rect guards:

| Test class | Resolution issue | Required evidence |
|---|---|---|
| `org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest` | GRA-95 | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md` |
| `org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest` | GRA-95 | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md` |
| `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest` | GRA-96 | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md` |
| `org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest` | GRA-96 | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md` |

## Policy Decision

Unresolved `similarity-regression` means `not smoke-eligible`.

GRA-97 does not promote an image-rect fixture and does not remove any existing
smoke fixture. It adds the guard that future promotion must pass before the
fixture can join the required macOS smoke lane.

## Validation

Focused guard validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
```

Required smoke validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
```

JUnit XML smoke result on host `Omega`:

- `org.skia.gpu.webgpu.PipelineKeyTelemetryTest`: `8` tests, `0` skipped, `0` failures, `0` errors.
- `org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`: `24` tests, `0` skipped, `0` failures, `0` errors.

## Reviewer Notes

The guard is intentionally static and evidence-file based. Fresh PR smoke runs
should not depend on a previously generated full inventory report existing in
`build/`. The non-blocking full inventory remains the source of broad failure
classification, while required smoke uses explicit promotion metadata and
versioned evidence.
