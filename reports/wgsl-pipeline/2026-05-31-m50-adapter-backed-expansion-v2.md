# M50-C Adapter-Backed Scene Expansion V2

Date: 2026-05-31
Milestone: M50 -- MEP Readiness Acceleration Toward 80%

## Result

M50 raises adapter-backed dashboard proof from 7 rows to 17 rows while keeping
0 `tracked-gap` and 0 `fail`.

Newly marked adapter-backed rows:

| Scene | Family | Adapter | Evidence command |
|---|---|---|---|
| `bitmap-shader-repeat-tile` | bitmap | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest` |
| `bitmap-subset-local-matrix-repeat` | bitmap / transform | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.BitmapSubsetShaderCrossBackendTest` |
| `sweep-gradient-path-clamp` | gradient / Path AA promoted subset | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SweepGradientPathTest` |
| `draw-paint-full-clip` | paint / clip | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintFullClipTest` |
| `draw-paint-clipped-rect` | paint / clip | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintClippedRectTest` |
| `scaled-rects-transform-stack` | transform / blend | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ScaledRectsTransformStackTest` |
| `gradient-color-filter-linear-kplus` | gradient / blend | Apple M2 Max | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GradientColorFilterLinearKPlusTest` |

The M50 font pass rows also carry adapter metadata for their selected simple
outline text contracts, but they do not broaden font support beyond the exact
generated scenes.

## Artifact Contract

Each adapter-backed pass row keeps:

- reference, CPU, GPU, CPU diff, GPU diff, route diagnostics, and stats;
- `gpu.route.fallbackReason=none`;
- `gpu.stats.adapter=Apple M2 Max`;
- `gpu.stats.adapterBackend=WebGPU/Metal`;
- report link back to this file or the M50 font/text evidence pack.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```
