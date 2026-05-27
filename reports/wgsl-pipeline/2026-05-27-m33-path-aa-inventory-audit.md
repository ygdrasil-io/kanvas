# M33 Path AA Inventory Audit

Date: 2026-05-27
Linear: GRA-105
Parent epic: GRA-101
Milestone: M33 -- Path AA MVP Boundary

## Goal

Produce a fresh post-M32/GRA-100 GPU inventory audit for Path AA, coverage,
clip, and edge-budget rows so M33 acts on current evidence.

## Command

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Result: expected non-blocking inventory failure (`:gpu-raster:test` failed
because the inventory intentionally includes expected unsupported and
adapter-skip rows). The inventory report task completed and wrote:

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`

## Overall Inventory Classification

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 0 |

## Path AA / Coverage Scope Summary

The Path AA/coverage scope is represented by the 50
`coverage.edge-count-exceeded` records. They are all classified as
`expected-unsupported-diagnostic`.

| Slice | Count | Classification | Owner / follow-up |
|---|---:|---|---|
| WebGPU GM rows | 21 | Expected unsupported Path AA breadth | GRA-106 hardening; implementation breadth remains GRA-70/post-MVP. |
| Cross-backend rows | 28 | Expected unsupported Path AA breadth | GRA-106 hardening; implementation breadth remains GRA-70/post-MVP. |
| Benchmark methodology row | 1 | Expected unsupported Path AA breadth | GRA-106 hardening; benchmark remains inventory-only until the route is supported. |
| Rendered-but-below-floor AA rows | 0 | None observed | No rebaseline or floor decision required. |
| Unclassified AA failures | 0 | None observed | No new blocker ticket required from this audit. |

## Non-Path Categories Observed

| Category | Rows | Classification | Owner / follow-up |
|---|---:|---|---|
| `image-filter.crop-input-nonnull-prepass-required` | 2 | Dependency-gated image-filter pre-pass limitation | M34: GRA-109 through GRA-113. |
| `adapter-skip` | 48 | Adapter-risk placeholders, excluded from required smoke unless promoted later | Existing smoke policy; no M33 Path AA action. |
| `similarity-regression` | 0 | No rendered below-floor inventory rows | No floor/rebaseline action. |
| `unexpected-exception` | 0 | GRA-100 signal remains resolved | No action. |

## M33 Smoke Recommendation

M33 should not promote a Path AA fixture directly from this audit.

Reasoning:

- The remaining Path AA/coverage failures are refusals, not rendered-but-low
  similarity cases.
- The refusal reason is stable: `coverage.edge-count-exceeded`.
- Required smoke must not include fixtures that rely on expected unsupported
  diagnostics.
- A promotion can be reconsidered in GRA-107 only if a small AA fixture is
  adapter-backed, passes the smoke policy, and is not part of the edge-budget
  refusal set.

## Path AA / Coverage Failure Rows

Every row below is classified as expected unsupported Path AA breadth with
reason `coverage.edge-count-exceeded`. Proposed owner: GRA-106 for classifier
hardening and GRA-107 for the smoke decision. Broader implementation ownership
remains GRA-70/post-MVP unless M33 creates a narrower follow-up.

| # | Test | Proposed classification | Owner / follow-up |
|---:|---|---|---|
| 1 | `org.skia.gpu.webgpu.AddArcWebGpuTest#AddArcGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 2 | `org.skia.gpu.webgpu.BlurRectWebGpuTest#BlurRectGM matches raster()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 3 | `org.skia.gpu.webgpu.Bug12866WebGpuTest#Bug12866GM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 4 | `org.skia.gpu.webgpu.CircularArcsHairlineWebGpuTest#CircularArcsHairlineGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 5 | `org.skia.gpu.webgpu.CircularArcsStrokeButtWebGpuTest#CircularArcsStrokeButtGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 6 | `org.skia.gpu.webgpu.CircularArcsStrokeRoundWebGpuTest#CircularArcsStrokeRoundGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 7 | `org.skia.gpu.webgpu.CircularArcsStrokeSquareWebGpuTest#CircularArcsStrokeSquareGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 8 | `org.skia.gpu.webgpu.ConvexPathsWebGpuTest#ConvexPathsGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 9 | `org.skia.gpu.webgpu.Crbug1472747WebGpuTest#Crbug1472747GM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 10 | `org.skia.gpu.webgpu.CubicStrokeWebGpuTest#CubicStrokeGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 11 | `org.skia.gpu.webgpu.FillTypeWebGpuTest#FillTypeGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 12 | `org.skia.gpu.webgpu.FillTypesWebGpuTest#FillTypesGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 13 | `org.skia.gpu.webgpu.PathInteriorWebGpuTest#PathInteriorGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 14 | `org.skia.gpu.webgpu.ScaledStrokesWebGpuTest#ScaledStrokesGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 15 | `org.skia.gpu.webgpu.SliverPathsWebGpuTest#SliverPathsGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 16 | `org.skia.gpu.webgpu.StrokeCircleWebGpuTest#StrokeCircleGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 17 | `org.skia.gpu.webgpu.StrokeRectWebGpuTest#StrokeRectGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 18 | `org.skia.gpu.webgpu.Strokes3WebGpuTest#Strokes3GM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 19 | `org.skia.gpu.webgpu.StrokesWebGpuTest#StrokesGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 20 | `org.skia.gpu.webgpu.TestGradientWebGpuTest#TestGradientGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 21 | `org.skia.gpu.webgpu.ZeroLenStrokesWebGpuTest#ZeroLenStrokesGM renders close to reference PNG on the GPU backend()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 22 | `org.skia.gpu.webgpu.benchmarks.PathHeavyBenchmark#bench - jmh-style methodology with phase decomposition()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 23 | `org.skia.gpu.webgpu.crossbackend.AddArcCrossBackendTest#AddArcGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 24 | `org.skia.gpu.webgpu.crossbackend.BlurRectCrossBackendTest#BlurRectGM matches raster and reference()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 25 | `org.skia.gpu.webgpu.crossbackend.Bug12866CrossBackendTest#Bug12866GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 26 | `org.skia.gpu.webgpu.crossbackend.Bug530095CrossBackendTest#Bug530095GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 27 | `org.skia.gpu.webgpu.crossbackend.CircularArcsHairlineCrossBackendTest#CircularArcsHairlineGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 28 | `org.skia.gpu.webgpu.crossbackend.ConicPathsCrossBackendTest#ConicPathsGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 29 | `org.skia.gpu.webgpu.crossbackend.ConvexPathsCrossBackendTest#ConvexPathsGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 30 | `org.skia.gpu.webgpu.crossbackend.Crbug1086705CrossBackendTest#Crbug1086705GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 31 | `org.skia.gpu.webgpu.crossbackend.Crbug1139750CrossBackendTest#Crbug1139750GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 32 | `org.skia.gpu.webgpu.crossbackend.Crbug1472747CrossBackendTest#Crbug1472747GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 33 | `org.skia.gpu.webgpu.crossbackend.Crbug888453CrossBackendTest#Crbug888453GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 34 | `org.skia.gpu.webgpu.crossbackend.CubicStrokeCrossBackendTest#CubicStrokeGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 35 | `org.skia.gpu.webgpu.crossbackend.DRRectSmallInnerCrossBackendTest#DRRectSmallInnerGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 36 | `org.skia.gpu.webgpu.crossbackend.DashCubicsCrossBackendTest#DashCubicsGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 37 | `org.skia.gpu.webgpu.crossbackend.DashingCrossBackendTest#DashingGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 38 | `org.skia.gpu.webgpu.crossbackend.FillTypesCrossBackendTest#FillTypesGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 39 | `org.skia.gpu.webgpu.crossbackend.OverStrokeCrossBackendTest#OverStrokeGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 40 | `org.skia.gpu.webgpu.crossbackend.PathFillCrossBackendTest#PathFillGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 41 | `org.skia.gpu.webgpu.crossbackend.PathInteriorCrossBackendTest#PathInteriorGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 42 | `org.skia.gpu.webgpu.crossbackend.RRectCrossBackendTest#RRectGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 43 | `org.skia.gpu.webgpu.crossbackend.ScaledStrokesCrossBackendTest#ScaledStrokesGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 44 | `org.skia.gpu.webgpu.crossbackend.SliverPathsCrossBackendTest#SliverPathsGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 45 | `org.skia.gpu.webgpu.crossbackend.StrokeCircleCrossBackendTest#StrokeCircleGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 46 | `org.skia.gpu.webgpu.crossbackend.StrokeRectCrossBackendTest#StrokeRectGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 47 | `org.skia.gpu.webgpu.crossbackend.Strokes3CrossBackendTest#Strokes3GM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 48 | `org.skia.gpu.webgpu.crossbackend.StrokesCrossBackendTest#StrokesGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 49 | `org.skia.gpu.webgpu.crossbackend.TestGradientCrossBackendTest#TestGradientGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |
| 50 | `org.skia.gpu.webgpu.crossbackend.ZeroLenStrokesCrossBackendTest#ZeroLenStrokesGM matches reference on raster and GPU backends()` | Expected unsupported: `coverage.edge-count-exceeded` | GRA-106; GRA-107; GRA-70/post-MVP breadth |

## Image-Filter Rows For M34 Handoff

These rows are outside M33 Path AA scope, but they remain in the same full
inventory and must be consumed by M34.

| Test | Classification | Owner / follow-up |
|---|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()` | Dependency-gated: `image-filter.crop-input-nonnull-prepass-required` | M34: GRA-109/GRA-110/GRA-111 |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()` | Dependency-gated: `image-filter.crop-input-nonnull-prepass-required` | M34: GRA-109/GRA-110/GRA-111 |

## Conclusion

No unclassified Path AA failure remains in the post-M32 inventory. M33 should
first harden the expected unsupported classification (`GRA-106`) and then make
a conservative smoke decision (`GRA-107`). The current audit does not justify
promoting any edge-budget refusal fixture into required GPU smoke.
