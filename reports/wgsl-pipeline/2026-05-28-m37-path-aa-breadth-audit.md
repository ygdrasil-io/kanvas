# M37 Path AA Breadth Audit

Date: 2026-05-28
Linear: GRA-173
Milestone: M37 -- Path AA Breadth Strategy

## Outcome

The current GPU inventory still contains 50 `coverage.edge-count-exceeded` rows. The rows are expected-unsupported diagnostics, not WebGPU support claims. This audit keeps the ADR 0005 256-edge budget intact and turns the breadth list into a ranked implementation strategy for M37 follow-up work.

Representative M36 dashboard evidence is linked so this breadth inventory remains separate from accepted scene rows:

- Dashboard closeout: `reports/wgsl-pipeline/2026-05-28-m36-scene-dashboard-closeout.md`
- Scene data contract: `reports/wgsl-pipeline/scenes/data/scenes.json`
- Passing/visible comparator row: `analytic-aa-convex` is tracked in the dashboard separately from edge-budget refusals.

## Inventory Source

Command used:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

The Gradle task exits non-zero because `:gpu-raster:test` intentionally contains classified inventory failures, but it still generated:

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`

Current classification summary from the generated JSON:

- `expected-unsupported-diagnostic`: 50
- `similarity-regression`: 0
- `unsupported-image-filter`: 2
- `adapter-skip`: 48
- `adapter-missing`: 0
- `unexpected-exception`: 0

## Ranked Strategy

| Rank | Family | Rows | Product value | Implementation risk | Recommendation |
|---:|---|---:|---|---|---|
| 1 | Stroke rectangle/circle | 4 | High UI/product value; constrained primitive geometry. | Low-medium: can scope to rect/circle stroke descriptors without lifting the global budget. | Use as first promotion candidate family. |
| 2 | Arc stroke/hairline | 9 | High drawing value and common GM breadth. | Medium-high: curve subdivision and cap behavior need dedicated acceptance bounds. | Queue after primitive strokes. |
| 3 | General stroke/dash | 13 | High value, but behavior is heterogeneous. | High: dash/cap/join expansion can mask many unrelated failures. | Split into smaller follow-ups before promotion. |
| 4 | Fill/convex/path pack | 19 | Useful breadth signal for path AA coverage. | High: broad fixtures include many shapes and fill rules. | Keep expected unsupported except selected future fixture. |
| 5 | Filter/shader over path | 4 | Composition matters after base coverage works. | High: combines path coverage with shader/filter behavior. | Defer until base path route stabilizes. |
| 6 | Benchmark stress | 1 | Performance signal only. | High and intentionally broad. | Keep expected unsupported. |

Promotion candidate: `StrokeRect` / `StrokeCircle` are the best first targets because they are product-visible primitive strokes and can be scoped without raising `SkWebGpuDevice.MAX_AA_EDGES` or converting the whole broad path pack into a support claim.

Explicit expected-unsupported remainder: `PathHeavyBenchmark`, broad fill packs such as `ConvexPaths`, `FillTypes`, and mixed path fixtures stay expected unsupported until a narrower route is implemented and backed by CPU oracle plus adapter-backed GPU evidence.

## Complete Edge-Budget Inventory

Approximate edge bucket uses the stable diagnostic boundary: every listed row exceeded the 256-edge WebGPU AA budget. The current inventory does not emit exact edge counts per GM, so the bucket records the likely workload source rather than a fabricated count.

| Test | Shape category | Approx edge bucket | Convexity / fill type / AA route expectation | Current CPU route | Current GPU fallback reason | Recommended disposition |
|---|---|---|---|---|---|---|
| `org.skia.gpu.webgpu.AddArcWebGpuTest#AddArcGM renders close to reference PNG on the GPU backend()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.BlurRectWebGpuTest#BlurRectGM matches raster()` | Filter/shader over path | >256 / composed path workload | path coverage with shader/filter composition | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer until base path coverage route is promoted, then re-evaluate composition. |
| `org.skia.gpu.webgpu.Bug12866WebGpuTest#Bug12866GM renders close to reference PNG on the GPU backend()` | Fill/convex/path pack | >256 / broad fill path pack | mixed regression path fixture; fill/stroke behavior unspecified | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.CircularArcsHairlineWebGpuTest#CircularArcsHairlineGM renders close to reference PNG on the GPU backend()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.CircularArcsStrokeButtWebGpuTest#CircularArcsStrokeButtGM renders close to reference PNG on the GPU backend()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.CircularArcsStrokeRoundWebGpuTest#CircularArcsStrokeRoundGM renders close to reference PNG on the GPU backend()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.CircularArcsStrokeSquareWebGpuTest#CircularArcsStrokeSquareGM renders close to reference PNG on the GPU backend()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.ConvexPathsWebGpuTest#ConvexPathsGM renders close to reference PNG on the GPU backend()` | Fill/convex/path pack | >256 / broad fill path pack | mostly convex fill pack; broad fixture | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Leave expected unsupported: broad conformance pack exceeds MVP edge budget. |
| `org.skia.gpu.webgpu.Crbug1472747WebGpuTest#Crbug1472747GM renders close to reference PNG on the GPU backend()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.CubicStrokeWebGpuTest#CubicStrokeGM renders close to reference PNG on the GPU backend()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.FillTypeWebGpuTest#FillTypeGM renders close to reference PNG on the GPU backend()` | Fill/convex/path pack | >256 / broad fill path pack | fill-type coverage; winding/even-odd mix | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Leave expected unsupported: broad conformance pack exceeds MVP edge budget. |
| `org.skia.gpu.webgpu.FillTypesWebGpuTest#FillTypesGM renders close to reference PNG on the GPU backend()` | Fill/convex/path pack | >256 / broad fill path pack | fill-type coverage; winding/even-odd mix | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Leave expected unsupported: broad conformance pack exceeds MVP edge budget. |
| `org.skia.gpu.webgpu.PathInteriorWebGpuTest#PathInteriorGM renders close to reference PNG on the GPU backend()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.ScaledStrokesWebGpuTest#ScaledStrokesGM renders close to reference PNG on the GPU backend()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.SliverPathsWebGpuTest#SliverPathsGM renders close to reference PNG on the GPU backend()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.StrokeCircleWebGpuTest#StrokeCircleGM renders close to reference PNG on the GPU backend()` | Stroke rectangle/circle | >256 / stroke primitive expansion | stroke; primitive shape; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Promote candidate: narrow analytic stroke primitive subset for GRA-177/GRA-178. |
| `org.skia.gpu.webgpu.StrokeRectWebGpuTest#StrokeRectGM renders close to reference PNG on the GPU backend()` | Stroke rectangle/circle | >256 / stroke primitive expansion | stroke; primitive shape; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Promote candidate: narrow analytic stroke primitive subset for GRA-177/GRA-178. |
| `org.skia.gpu.webgpu.Strokes3WebGpuTest#Strokes3GM renders close to reference PNG on the GPU backend()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.StrokesWebGpuTest#StrokesGM renders close to reference PNG on the GPU backend()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.TestGradientWebGpuTest#TestGradientGM renders close to reference PNG on the GPU backend()` | Filter/shader over path | >256 / composed path workload | path coverage with shader/filter composition | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer until base path coverage route is promoted, then re-evaluate composition. |
| `org.skia.gpu.webgpu.ZeroLenStrokesWebGpuTest#ZeroLenStrokesGM renders close to reference PNG on the GPU backend()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | CPU/reference PNG oracle for WebGPU GM | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.benchmarks.PathHeavyBenchmark#bench - jmh-style methodology with phase decomposition()` | Benchmark stress | >256 / stress workload | stress workload; deliberately broad path mix | CPU/reference benchmark phase oracle | `coverage.edge-count-exceeded` | Leave expected unsupported: benchmark/stress row, not a support claim. |
| `org.skia.gpu.webgpu.crossbackend.AddArcCrossBackendTest#AddArcGM matches reference on raster and GPU backends()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.crossbackend.BlurRectCrossBackendTest#BlurRectGM matches raster and reference()` | Filter/shader over path | >256 / composed path workload | path coverage with shader/filter composition | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer until base path coverage route is promoted, then re-evaluate composition. |
| `org.skia.gpu.webgpu.crossbackend.Bug12866CrossBackendTest#Bug12866GM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed regression path fixture; fill/stroke behavior unspecified | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.Bug530095CrossBackendTest#Bug530095GM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.CircularArcsHairlineCrossBackendTest#CircularArcsHairlineGM matches reference on raster and GPU backends()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.crossbackend.ConicPathsCrossBackendTest#ConicPathsGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.ConvexPathsCrossBackendTest#ConvexPathsGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mostly convex fill pack; broad fixture | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Leave expected unsupported: broad conformance pack exceeds MVP edge budget. |
| `org.skia.gpu.webgpu.crossbackend.Crbug1086705CrossBackendTest#Crbug1086705GM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.Crbug1139750CrossBackendTest#Crbug1139750GM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.Crbug1472747CrossBackendTest#Crbug1472747GM matches reference on raster and GPU backends()` | Arc stroke/hairline | >256 / arc subdivision breadth | stroke/hairline arcs; curve subdivision; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Rank next after strokes; keep expected unsupported until arc subdivision route is scoped. |
| `org.skia.gpu.webgpu.crossbackend.Crbug888453CrossBackendTest#Crbug888453GM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.CubicStrokeCrossBackendTest#CubicStrokeGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.DRRectSmallInnerCrossBackendTest#DRRectSmallInnerGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | rounded-rect fill; nested/curve coverage | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.DashCubicsCrossBackendTest#DashCubicsGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.DashingCrossBackendTest#DashingGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.FillTypesCrossBackendTest#FillTypesGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | fill-type coverage; winding/even-odd mix | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Leave expected unsupported: broad conformance pack exceeds MVP edge budget. |
| `org.skia.gpu.webgpu.crossbackend.OverStrokeCrossBackendTest#OverStrokeGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.PathFillCrossBackendTest#PathFillGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | fill-type coverage; winding/even-odd mix | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Leave expected unsupported: broad conformance pack exceeds MVP edge budget. |
| `org.skia.gpu.webgpu.crossbackend.PathInteriorCrossBackendTest#PathInteriorGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.RRectCrossBackendTest#RRectGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | rounded-rect fill; nested/curve coverage | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.ScaledStrokesCrossBackendTest#ScaledStrokesGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.SliverPathsCrossBackendTest#SliverPathsGM matches reference on raster and GPU backends()` | Fill/convex/path pack | >256 / broad fill path pack | mixed fill/convexity path pack | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer; useful breadth signal but too broad for first promotion. |
| `org.skia.gpu.webgpu.crossbackend.StrokeCircleCrossBackendTest#StrokeCircleGM matches reference on raster and GPU backends()` | Stroke rectangle/circle | >256 / stroke primitive expansion | stroke; primitive shape; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Promote candidate: narrow analytic stroke primitive subset for GRA-177/GRA-178. |
| `org.skia.gpu.webgpu.crossbackend.StrokeRectCrossBackendTest#StrokeRectGM matches reference on raster and GPU backends()` | Stroke rectangle/circle | >256 / stroke primitive expansion | stroke; primitive shape; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Promote candidate: narrow analytic stroke primitive subset for GRA-177/GRA-178. |
| `org.skia.gpu.webgpu.crossbackend.Strokes3CrossBackendTest#Strokes3GM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.StrokesCrossBackendTest#StrokesGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |
| `org.skia.gpu.webgpu.crossbackend.TestGradientCrossBackendTest#TestGradientGM matches reference on raster and GPU backends()` | Filter/shader over path | >256 / composed path workload | path coverage with shader/filter composition | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer until base path coverage route is promoted, then re-evaluate composition. |
| `org.skia.gpu.webgpu.crossbackend.ZeroLenStrokesCrossBackendTest#ZeroLenStrokesGM matches reference on raster and GPU backends()` | General stroke/dash | >256 / stroke and dash expansion | stroke/dash expansion; mixed caps/joins; non-fill | Raster CPU backend in cross-backend oracle | `coverage.edge-count-exceeded` | Defer behind primitive strokes; split dash/cap/join behavior before promotion. |

## Validation

- Parsed generated JSON and verified 50 rows with `category=expected-unsupported-diagnostic` and `reason=coverage.edge-count-exceeded`.
- `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` generated the inventory report and failed only because the classified inventory tests remain intentionally failing.
- `rtk git diff --check` should be run before merge.

