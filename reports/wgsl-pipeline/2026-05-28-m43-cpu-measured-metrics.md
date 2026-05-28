# M43-B CPU measured dashboard metrics

GRA-207 adds a native Gradle report path for measured CPU `performanceTrend`
payloads on two stable M39 P1 pass rows.

## Producer

```bash
rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance
```

The task writes measured JSON to the dashboard artifact tree and keeps Java 25
Vector API out of the benchmark path (`environment.vector=not used`). The M43
gate remains `reporting-only` until CI budget, rollback, and quarantine policy
exist.

## Rows

| Scene | Route | Samples | Median ms | P95 ms | Raw JSON |
| --- | --- | ---: | ---: | ---: | --- |
| `src-over-stack` | `cpu.blend.src-over-stack` | 30 | 0.016708 | 0.100750 | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-performance.json` |
| `bitmap-shader-local-matrix` | `cpu.shader.bitmap.local-matrix` | 30 | 0.015875 | 0.016125 | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-performance.json` |

## Environment

- Host: `Omega`
- OS: `Mac OS X 26.5 aarch64`
- JDK: `25.0.1+8-LTS (Eclipse Adoptium)`
- Backend: `CPU scalar Kotlin dashboard benchmark`
- Baseline: `m43-cpu-measured-local` at `40eed32e0cbc5d3e0fac1f040fd14c4b8770a0b4`

## Validation

```bash
rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
