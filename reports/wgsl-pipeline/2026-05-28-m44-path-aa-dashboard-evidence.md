# M44-C Path AA dashboard evidence

GRA-213 adds a dashboard row for the M44 promoted Path AA primitive-stroke
family.

## Dashboard row

| Field | Value |
| --- | --- |
| Scene id | `path-aa-stroke-primitive` |
| Status | `pass` |
| Representative artifact | `StrokeCircleGM` |
| Promoted family | `StrokeRectGM` + `StrokeCircleGM` |
| GPU route | `webgpu.coverage.path-aa-stroke-primitive` |
| Adapter | `Apple M2 Max` |
| Inventory effect | `coverage.edge-count-exceeded` 50 -> 46 |

## Artifacts

- Reference: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/skia.png`
- CPU: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu.png`
- GPU: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu-diff.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu-diff.png`
- CPU route: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-cpu.json`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/stats.json`

## Evidence

Targeted validation from GRA-212:

| Test | Result | Similarity |
| --- | --- | ---: |
| `StrokeRectWebGpuTest` | pass | 95.96% |
| `StrokeCircleWebGpuTest` | pass | 91.81% |
| `StrokeRectCrossBackendTest` GPU | pass | 95.96% |
| `StrokeRectCrossBackendTest` raster | pass | 93.64% |
| `StrokeCircleCrossBackendTest` GPU | pass | 91.81% |
| `StrokeCircleCrossBackendTest` raster | pass | 90.21% |

The dashboard row uses `StrokeCircleGM` as the representative visual artifact and
links both StrokeRect and StrokeCircle tests as source evidence. Remaining broad
Path AA rows stay visible in the inventory as `coverage.edge-count-exceeded`.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
```
