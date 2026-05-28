# M43-C GPU/cache measured dashboard metrics

GRA-208 adds a Gradle report path for measured GPU/cache `performanceTrend`
payloads on two stable GPU pass rows.

## Producer

```bash
rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance
```

The task writes measured JSON when a named adapter is supplied. If no adapter is
provided via `-Pkanvas.gpu.performance.adapter` or `KANVAS_GPU_PERFORMANCE_ADAPTER`,
it writes `status=unavailable` with `reason=gpu.adapter-missing` instead of
claiming measured GPU performance.

## Rows

| Scene | Route | Adapter | Samples | Median ms | P95 ms | Raw JSON |
| --- | --- | --- | ---: | ---: | ---: | --- |
| `src-over-stack` | `webgpu.blend.src-over.fixed-function` | `Apple M2 Max` | 30 | 0.024375 | 0.038625 | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-performance.json` |
| `bitmap-shader-local-matrix` | `webgpu.shader.bitmap.local-matrix` | `Apple M2 Max` | 30 | 0.005666 | 0.009875 | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-performance.json` |

## Environment

- Host: `Omega`
- OS: `Mac OS X 26.5 aarch64`
- JDK: `25.0.1+8-LTS (Eclipse Adoptium)`
- Backend: `WebGPU cache/timing dashboard benchmark`
- Adapter: `Apple M2 Max`
- Baseline: `m43-gpu-cache-measured-local` at `2da0d497dba3b908b6a7b517157c583a963a1b52`

## Validation

```bash
rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
