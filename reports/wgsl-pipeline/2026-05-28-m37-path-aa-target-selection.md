# M37 Path AA Target Selection

Date: 2026-05-28
Linear: GRA-177
Implementation ticket: GRA-178
Milestone: M37 -- Path AA Breadth Strategy

## Decision

Select the stroke primitive family as the M37 implementation target:

```text
StrokeRectGM
StrokeCircleGM
```

The implementation ticket should target the four current inventory rows for this family:

- `org.skia.gpu.webgpu.StrokeRectWebGpuTest#StrokeRectGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.StrokeCircleWebGpuTest#StrokeCircleGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.crossbackend.StrokeRectCrossBackendTest#StrokeRectGM matches reference on raster and GPU backends()`
- `org.skia.gpu.webgpu.crossbackend.StrokeCircleCrossBackendTest#StrokeCircleGM matches reference on raster and GPU backends()`

This target does not raise the global 256-edge WebGPU AA budget. It should either promote a bounded analytic/stroker route for these primitive strokes or produce a narrower, explicitly documented fallback route if the implementation proves that full promotion is not yet technically sound.

## Why This Target

GRA-173 ranked `StrokeRect` / `StrokeCircle` first because the family is product-visible, constrained, and measurable. It is not a broad conformance pack, a shader/filter composition case, or a stress benchmark.

Expected measurable outcome for GRA-178:

- Baseline: 50 `coverage.edge-count-exceeded` rows in the current GPU inventory.
- Targeted reduction if promoted: 4 rows removed from `coverage.edge-count-exceeded`, reducing the class from 50 to 46.
- If full promotion is not viable: 4 rows must move to a more specific documented fallback reason with stable inventory classification and no `unexpected-exception` rows.

Existing per-GM floors already provide concrete acceptance anchors:

| GM | GPU floor | Cross-backend raster floor | Cross-backend GPU floor |
|---|---:|---:|---:|
| `StrokeRectGM` | 95.91% | 93.50% | 95.91% |
| `StrokeCircleGM` | 91.76% | 90.00% | 91.76% |

## Acceptance Thresholds For GRA-178

Image and diff policy:

- `StrokeRectWebGpuTest` must meet or exceed 95.91% similarity against `original-888/strokerect.png` using the existing GPU tolerance.
- `StrokeCircleWebGpuTest` must meet or exceed 91.76% similarity against its reference using the existing GPU tolerance.
- Cross-backend variants must preserve the existing raster floors and GPU floors listed above.
- Any floor change requires a before/after report with rendered artifacts and must not lower thresholds just to clear inventory.

Route diagnostics:

- The promoted route must emit deterministic route diagnostics that identify a stroke primitive AA path, not a generic broad-path fallback.
- The route must keep `WEBGPU_PATH_AA_EDGE_BUDGET` / `SkWebGpuDevice.MAX_AA_EDGES` semantics intact for unrelated paths.
- The M36 scene dashboard should gain a `path-aa-stroke-primitive` or equivalent row in the M37 evidence ticket so the support claim is visible outside raw test output.

Inventory stability:

- The current 50-row edge-budget inventory is the baseline.
- Passing promotion should reduce `coverage.edge-count-exceeded` by exactly the four selected rows unless implementation evidence justifies fewer rows.
- Unsupported rows outside the selected family must remain expected unsupported with stable `coverage.edge-count-exceeded` diagnostics.
- No selected or rejected case may become `unexpected-exception`.

## Rejected Alternatives

| Alternative | GRA-173 rows | Reason rejected for first M37 implementation |
|---|---:|---|
| Arc stroke/hairline (`AddArc`, `CircularArcs`, `Crbug1472747`) | 9 | Higher product value than many breadth rows, but curve subdivision, caps, and hairline behavior make it a larger first step than primitive strokes. |
| General stroke/dash (`CubicStroke`, `ScaledStrokes`, `Strokes`, dashing, overstroke) | 13 | Too heterogeneous; dash/cap/join expansion should be split after primitive strokes are proven. |
| Fill/convex/path packs (`ConvexPaths`, `FillTypes`, `PathFill`, regression packs) | 19 | Broad conformance packs mix many fill rules and shapes; they remain expected unsupported until a narrower route is selected. |
| Filter/shader over path (`BlurRect`, `TestGradient`) | 4 | Composition should wait until the base path coverage route is reliable. |
| Benchmark stress (`PathHeavyBenchmark`) | 1 | Performance signal only; not a support claim and explicitly remains expected unsupported. |

## Out Of Scope

- Raising the 256-edge budget without a profiling ADR.
- Removing or weakening `coverage.edge-count-exceeded` classification for broad path suites.
- Treating CPU fallback as WebGPU support.
- Porting Ganesh, Graphite, SkSL compiler behavior, or a general path renderer.
- Promoting `PathHeavyBenchmark` or broad fill packs as part of this implementation target.

## Validation

Documentation/spec validation for this selection:

```text
rtk git diff --check
```

The implementation ticket should additionally run targeted Path AA tests and the GPU inventory report command:

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```
