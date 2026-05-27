# M34 Crop(input = NonNull) Limitation Hardening

Date: 2026-05-27
Linear: GRA-111
Branch: gra-111-crop-nonnull-limitation

## Selected Path

GRA-110 selected the accepted MVP limitation path. GRA-111 implements that decision by hardening required GPU smoke policy rather than adding a render-to-texture child pre-pass.

## Guard Added

`gpu-raster/build.gradle.kts` now defines `unsupportedImageFilterSmokeGuards` for the two inventory-only crop pre-pass fixtures:

| Test class | Reason | Issue |
|---|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest` | `image-filter.crop-input-nonnull-prepass-required` | GRA-111 |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest` | `image-filter.crop-input-nonnull-prepass-required` | GRA-111 |

`rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy` now fails if any required smoke pattern may include either class. This keeps the fixtures in full inventory only until a future render-to-texture pre-pass implementation lands with adapter-backed evidence.

## Existing Classification Evidence

`GpuInventoryFailureReportTest` already asserts the exact two `Crop(input = nonNull)` rows and the stable reason code. GRA-111 re-ran that test to prove the inventory classifier still names the limitation consistently.

## Remaining Limitation

The WebGPU backend still refuses `SkImageFilters.Crop(input = nonNull)` with:

```text
image-filter.crop-input-nonnull-prepass-required
```

That is intentional for MVP. The post-MVP implementation must materialise the child image-filter output into a scratch texture, apply the crop/tile sampling semantics against that scratch, and then revisit smoke eligibility with a new evidence report.

## Direct Fixture Check

The two direct fixture runs still fail, as expected for the limitation path:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test \
  --tests 'org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest' \
  --tests 'org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest'
```

Their generated JUnit XML carries the stable refusal diagnostic in both failure messages:

```text
diagnostic=backend=GPU,reason=image-filter.crop-input-nonnull-prepass-required,action=RefuseDiagnostic(image-filter.crop-input-nonnull-prepass-required)
```

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest'
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
```

The two direct `SimpleOffsetImageFilter*` inventory tests are intentionally not expected to pass before the pre-pass exists; they remain full-inventory expected unsupported rows, not smoke candidates.
