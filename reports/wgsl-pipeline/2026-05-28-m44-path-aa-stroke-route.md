# M44-B Path AA primitive-stroke GPU route

GRA-212 promotes the selected M44 primitive-stroke family to a bounded WebGPU
coverage route:

```text
webgpu.coverage.path-aa-stroke-primitive
```

The route is selected only when the device marks the draw as a selected stroke
outline overflow fallback. It does not raise `WEBGPU_PATH_AA_EDGE_BUDGET` /
`SkWebGpuDevice.MAX_AA_EDGES` and does not change the generic
`coverage.edge-count-exceeded` refusal for broad Path AA rows.

## Implementation

- Added `WebGpuCoverageStrategy.PathAaStrokePrimitive`.
- Added strategy inventory row `path-aa-stroke-primitive` with adapter-lane
  status handling.
- Changed `strokeOutlineFallbackEnabled` overflow selection from refuse to
  `webgpu.coverage.path-aa-stroke-primitive`.
- Kept generic AA edge overflow refusing with `coverage.edge-count-exceeded`.
- Kept mask/atlas fallback behavior unchanged.

## Adapter-backed evidence

Targeted Stroke tests on `Apple M2 Max`:

| Test | Result | Similarity |
| --- | --- | ---: |
| `StrokeRectWebGpuTest` | pass | 95.96% |
| `StrokeCircleWebGpuTest` | pass | 91.81% |
| `StrokeRectCrossBackendTest` GPU | pass | 95.96% |
| `StrokeRectCrossBackendTest` raster | pass | 93.64% |
| `StrokeCircleCrossBackendTest` GPU | pass | 91.81% |
| `StrokeCircleCrossBackendTest` raster | pass | 90.21% |

Acceptance floors remain those selected by GRA-211:

| GM | GPU floor | Raster floor |
| --- | ---: | ---: |
| `StrokeRectGM` | 95.91% | 93.50% |
| `StrokeCircleGM` | 91.76% | 90.00% |

## Inventory effect

`rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` still exits non-zero
because out-of-scope inventory rows intentionally fail. The generated inventory
summary after this change reports:

| Category | Count |
| --- | ---: |
| `expected-unsupported-diagnostic` | 46 |
| `adapter-skip` | 48 |
| `unexpected-exception` | 0 |
| `similarity-regression` | 0 |

This is the expected M44-B effect: the selected four StrokeRect/StrokeCircle
rows no longer appear in `coverage.edge-count-exceeded`; broad Path AA suites
remain expected unsupported.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

The inventory command is expected to fail overall while producing the PM-readable
classification artifacts, because the remaining 46 Path AA breadth rows stay
expected unsupported.
