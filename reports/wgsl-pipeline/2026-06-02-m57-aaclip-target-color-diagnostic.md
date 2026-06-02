# M57 aaclip target-color diagnostic - 2026-06-02

Linear: `FOR-235`

## Decision

`m57-aaclip-bounded-grid` keeps its normal WebGPU route
`webgpu.coverage.aaclip-bounded-grid` and remains a local-threshold
`pass`. The `targetColorSpaceBlend=true` render is diagnostic-only and
is not enabled globally.

Exact-score result: targetColorSpaceBlend degrades the exact score.

## Metrics

Adapter: `Apple M2 Max`

| Mode | Target blend | Support similarity | Exact similarity | Matching pixels at exact | Max channel delta |
|---|---:|---:|---:|---:|---:|
| Normal WebGPU | false | 98.83% | 95.88% | 27612/28800 | 178 |
| Diagnostic target-color | true | 94.73% | 94.71% | 27276/28800 | 187 |

Exact delta: `-1.17` percentage points.

## Tolerance profile

| Tolerance | Normal similarity | Target-color similarity |
|---:|---:|---:|
| 0 | 95.88% | 94.71% |
| 8 | 98.83% | 94.73% |
| 16 | 98.83% | 95.25% |
| 32 | 98.83% | 96.54% |

## Non-claims

- No global `targetColorSpaceBlend` enablement.
- No shader, `SkWebGpuDevice`, route, or threshold change.
- No promotion to `99.95%` support.
- No expansion beyond this solid-color AA clip-grid diagnostic.

## Artifacts

- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu-target-color.png`
- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu-target-color-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/target-color-diagnostic.json`
- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/stats.json`

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AaclipSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
