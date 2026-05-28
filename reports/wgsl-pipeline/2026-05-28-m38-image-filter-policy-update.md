# M38 Image-Filter Smoke And Inventory Policy Update

Date: 2026-05-28
Ticket: GRA-182

## Scope

Update GPU smoke and inventory policy after GRA-181 promoted the selected
`Crop(kDecal, input = Offset(null))` SimpleOffset image-filter child pre-pass.
This is a policy/evidence update only; it does not widen the image-filter DAG
implementation beyond the selected M38 shape.

## Before And After

| Signal | Before GRA-181 | After GRA-181/GRA-182 |
|---|---:|---:|
| Full inventory total classified rows | 100 | 98 |
| `unsupported-image-filter` | 2 | 0 |
| `image-filter.crop-input-nonnull-prepass-required` | 2 | 0 for selected SimpleOffset rows |
| `coverage.edge-count-exceeded` | 46 | 46 |
| `coverage.stroke-outline-edge-count-exceeded` | 4 | 4 |
| Adapter skips | 48 | 48 |
| Similarity regressions | 0 | 0 |
| Unexpected exceptions | 0 | 0 |

The selected rows removed from the old unsupported bucket are:

| Fixture | New policy |
|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest` | Promoted to required `gpuSmokeTest` because it has adapter-backed WebGPU evidence. |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest` | Retained in full inventory as raster/GPU parity evidence; not promoted to required smoke to avoid expanding smoke cost. |

## Stable Remaining Policy

`image-filter.crop-input-nonnull-prepass-required` remains a valid inventory-only
reason for out-of-scope `SkImageFilters.Crop(input = nonNull)` graph shapes that
are not covered by the selected M38 pre-pass. The expected selected SimpleOffset
rows must not appear under this reason after GRA-181.

No similarity floor was changed.

## Files Updated

| File | Change |
|---|---|
| `gpu-raster/build.gradle.kts` | Adds `SimpleOffsetImageFilterWebGpuTest` to required GPU smoke and removes the obsolete M34 smoke block for selected SimpleOffset rows. |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReport.kt` | Updates unsupported image-filter catalog wording so the reason is reserved for out-of-scope Crop(input nonNull) graphs. |
| `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReportTest.kt` | Keeps classifier coverage using synthetic out-of-scope Crop rows instead of implying current SimpleOffset rows still fail. |
| `README.md` | Updates active roadmap status to reflect M38 selected SimpleOffset promotion. |
| `.upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md` | Adds M38 policy update and evidence links. |
| `.upstream/specs/wgsl-pipeline/README.md` | Updates spec index wording for the M38 extension. |
| `build.gradle.kts` | Updates generated conformance report wording for current smoke and inventory policy. |

## Validation

Validation commands:

```text
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest --tests '*SimpleOffsetImageFilterWebGpuTest'
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
rtk git diff --check
```

Results:

| Command | Result |
|---|---|
| `rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy` | Pass |
| `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest` | Pass; `SimpleOffsetImageFilterWebGpuTest` included and passed |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :gpu-raster:test --tests org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest --tests '*SimpleOffsetImageFilterWebGpuTest'` | Pass |
| `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` | Expected non-zero full inventory: `684 tests completed, 50 failed, 48 skipped`; failure classification emitted |
| `rtk git diff --check` | Pass |

Full-inventory classification after the patch remains non-zero because Path AA
edge-budget and adapter-skip rows are intentionally inventory-only:

```text
total=98
expected-unsupported-diagnostic=50
unsupported-image-filter=0
adapter-skip=48
adapter-missing=0
similarity-regression=0
unexpected-exception=0
coverage.edge-count-exceeded=46
coverage.stroke-outline-edge-count-exceeded=4
```
