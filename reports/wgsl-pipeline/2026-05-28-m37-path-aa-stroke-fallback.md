# M37 Path AA Stroke Fallback Implementation

Date: 2026-05-28
Linear: GRA-178
Selection note: `reports/wgsl-pipeline/2026-05-28-m37-path-aa-target-selection.md`

## Outcome

GRA-178 implements the bounded fallback strategy selected by GRA-177. It does not claim WebGPU rendered support for `StrokeRectGM` or `StrokeCircleGM`; instead it replaces their generic edge-budget refusal with a narrower stable diagnostic:

```text
coverage.stroke-outline-edge-count-exceeded
```

The global WebGPU AA edge budget remains unchanged at 256. Broad Path AA suites still use `coverage.edge-count-exceeded`.

## Changed Route Behavior

The new fallback applies only when an over-budget AA path was produced from one of these bounded stroke-outline cases:

- source oval stroke with a device stroke width of at least 8 px, covering `StrokeCircleGM` while excluding the smaller `ScaledStrokesGM` circle cells;
- hairline stroke over a two-contour rectangular stroke outline, covering the red outline overlay in `StrokeRectGM`.

The selector records this through `WebGpuPathCoverageFacts.strokeOutlineFallbackEnabled` and emits pipeline-key coverage kind `pathStrokeOutlineOverflow`.

## Before / After Inventory

Baseline from GRA-173 / GRA-177:

- `coverage.edge-count-exceeded`: 50 rows
- `coverage.stroke-outline-edge-count-exceeded`: 0 rows

After GRA-178 full inventory:

- `coverage.edge-count-exceeded`: 46 rows
- `coverage.stroke-outline-edge-count-exceeded`: 4 rows
- total expected unsupported diagnostics: 50 rows
- unsupported image-filter diagnostics: 2 rows
- adapter skips: 48 rows
- unexpected exceptions: 0 rows

Rows moved to the new diagnostic:

- `org.skia.gpu.webgpu.StrokeCircleWebGpuTest#StrokeCircleGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.StrokeRectWebGpuTest#StrokeRectGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.crossbackend.StrokeCircleCrossBackendTest#StrokeCircleGM matches reference on raster and GPU backends()`
- `org.skia.gpu.webgpu.crossbackend.StrokeRectCrossBackendTest#StrokeRectGM matches reference on raster and GPU backends()`

## Validation

Unit validation passed:

```text
rtk ./gradlew --no-daemon :render-pipeline:test --tests org.skia.pipeline.GeometryCoverageContractsTest :gpu-raster:test --tests org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest
```

Targeted selected-family validation generated the intended expected-unsupported inventory:

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
```

Expected result: task exits non-zero because the four selected rendered tests remain unsupported, but `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json` classifies all four as `coverage.stroke-outline-edge-count-exceeded`.

Full inventory validation:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Expected result: task exits non-zero because inventory failures are intentionally present, but the generated JSON reports 46 `coverage.edge-count-exceeded` rows and exactly 4 `coverage.stroke-outline-edge-count-exceeded` rows.
