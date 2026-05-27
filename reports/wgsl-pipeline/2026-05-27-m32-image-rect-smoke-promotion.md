# M32 Image-Rect Smoke Promotion

Date: 2026-05-27
Linear: GRA-99
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation

## Decision

Promote `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest` into the
required `:gpu-raster:gpuSmokeTest` gate.

Do not promote the cross-backend Skbug4734 test or `DrawBitmapRect3` in this
slice. They remain covered by full GPU inventory and focused validation, while
smoke gets exactly one minimal image-rect fixture.

## Rationale

`DrawBitmapRectSkbug4734WebGpuTest` is the smallest stable M32 image-rect
candidate:

- output size is `64x64` (`4096` pixels), lower smoke cost than the
  `DrawBitmapRect3` `640x480` output;
- it exercises the fixed strict-nearest/fractional-source sampling path;
- GRA-96 proves it now passes at `100.00 >= 99.95` with `4096/4096` matching
  pixels and max diff `(A=0, R=0, G=0, B=0)`;
- GRA-97 added the executable guard that prevents unresolved image-rect
  similarity regressions from entering smoke.

## Promotion Checklist

| Requirement | Status | Evidence |
|---|---|---|
| Adapter lane proof | Passed locally; CI required gate to verify on PR | `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest` |
| No adapter skip | Passed locally, `0` skipped for smoke XML suites | `gpu-raster/build/test-results/gpuSmokeTest/` |
| No expected unsupported diagnostic | Passed; fixture renders through similarity path above floor | GRA-96 report |
| Stable similarity floor | Passed, `100.00 >= 99.95` | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md` |
| PM-readable evidence | Present | this report plus GRA-96/GRA-97 reports |
| Rollback path | Remove this single `GpuSmokePatternSpec` and keep fixture inventory-only | `gpu-raster/build.gradle.kts` |

## Mandatory Image-Rect Coverage After Promotion

Required smoke now includes one bitmap/image-rect fixture:

- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`

Inventory-only image-rect coverage remains:

- `org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest`
- `org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest`
- `org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest`

## Validation

Promotion validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
```

Local smoke XML results on host `Omega`:

- `DrawBitmapRectSkbug4734WebGpuTest`: `1` test, `0` skipped, `0` failures,
  `0` errors; similarity `100.00`, `4096/4096`, max diff
  `(A=0, R=0, G=0, B=0)`.
- `PipelineKeyTelemetryTest`: `8` tests, `0` skipped, `0` failures,
  `0` errors.
- `WebGpuCoveragePlanSelectorTest`: `24` tests, `0` skipped, `0` failures,
  `0` errors.

Full inventory status remains non-blocking. The latest M32 inventory evidence
records `similarity-regression=0` and keeps unrelated unsupported/skipped
families out of required smoke.
