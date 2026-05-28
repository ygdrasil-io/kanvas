# M44-E Path AA family promotion closeout

M44 closes the first real Path AA rendered family promotion. The selected scope
was deliberately narrow: primitive strokes represented by `StrokeRectGM` and
`StrokeCircleGM`.

## Outcome

| Signal | Result |
| --- | --- |
| Selected family | `StrokeRectGM` + `StrokeCircleGM` primitive strokes |
| Dashboard scene | `path-aa-stroke-primitive` |
| Dashboard status | `pass` |
| GPU route | `webgpu.coverage.path-aa-stroke-primitive` |
| Adapter evidence | `Apple M2 Max` |
| Inventory effect | `coverage.edge-count-exceeded` 50 -> 46 |
| Unexpected failures | 0 |
| Similarity regressions | 0 |

M44 did not raise the global Path AA edge budget and did not claim broad Path AA
support. Remaining broad suites stay expected unsupported through stable
`coverage.edge-count-exceeded` diagnostics.

## Ticket evidence

| Ticket | Evidence | PR |
| --- | --- | --- |
| GRA-211 | Selected primitive stroke family and acceptance floors. | #1230 |
| GRA-212 | Added bounded WebGPU route and targeted rendered smoke evidence. | #1231 |
| GRA-213 | Added dashboard row and visual/route/stats artifacts. | #1232 |
| GRA-214 | Published inventory before/after report. | #1233 |

## Visual and route artifacts

Dashboard row: `reports/wgsl-pipeline/scenes/data/scenes.json`, scene id
`path-aa-stroke-primitive`.

Artifacts:

- Reference: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/skia.png`
- CPU: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu.png`
- GPU: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu-diff.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu-diff.png`
- CPU route: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-cpu.json`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/stats.json`

## Rendered evidence

| Test | Result | Similarity | Floor |
| --- | --- | ---: | ---: |
| `StrokeRectWebGpuTest` | pass | 95.96% | 95.91% |
| `StrokeCircleWebGpuTest` | pass | 91.81% | 91.76% |
| `StrokeRectCrossBackendTest` GPU | pass | 95.96% | 95.91% |
| `StrokeRectCrossBackendTest` raster | pass | 93.64% | 93.50% |
| `StrokeCircleCrossBackendTest` GPU | pass | 91.81% | 91.76% |
| `StrokeCircleCrossBackendTest` raster | pass | 90.21% | 90.00% |

## Inventory before/after

| Signal | Before M44 | After M44 | Delta |
| --- | ---: | ---: | ---: |
| `coverage.edge-count-exceeded` | 50 | 46 | -4 |
| `expected-unsupported-diagnostic` | 50 | 46 | -4 |
| `adapter-skip` | 48 | 48 | 0 |
| `unexpected-exception` | 0 | 0 | 0 |
| `similarity-regression` | 0 | 0 | 0 |

The four removed unsupported rows are the WebGPU and cross-backend rows for
`StrokeRectGM` and `StrokeCircleGM`.

## Remaining Path AA order

| Order | Family | Current status | Rationale |
| ---: | --- | --- | --- |
| 1 | Arc stroke/hairline | Expected unsupported | Closest follow-up to primitive strokes, but needs curve subdivision and cap/hairline behavior separated. |
| 2 | General stroke/dash | Expected unsupported | Requires dash/cap/join expansion and should not be hidden behind primitive-stroke support. |
| 3 | Fill/convex/path packs | Expected unsupported | Broad shape and fill-rule coverage must be split before support claims. |
| 4 | Filter/shader over path | Expected unsupported | Depends on stable base coverage plus composition/layer semantics. |
| 5 | Benchmark stress | Expected unsupported | Performance signal only until a feature ticket owns rendered support. |

## Residual risks

- The promoted route is intentionally bounded to the selected primitive stroke
  family; it is not a general Path AA renderer.
- Broad Path AA diagnostics remain expected unsupported and should continue to
  fail closed in inventory.
- Any later expansion must provide rendered CPU/GPU/reference artifacts, route
  diagnostics, and an inventory delta without raising the global edge budget by
  default.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

The inventory command is expected to exit non-zero while producing the PM-readable
classification artifacts for the remaining expected unsupported rows.
