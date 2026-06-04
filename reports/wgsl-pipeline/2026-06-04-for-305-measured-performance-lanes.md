# FOR-305 Measured Performance Lanes

Linear: `FOR-305`

Related: `FOR-304`, `FOR-241`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-measured-performance-lanes-for-clip-rect-difference-and-simple-rt-ticket`

Decision: `PERFORMANCE_LANES_MEASURED_REPORTING_ONLY`

## Result

FOR-305 replaces the four static M40 `estimated` performance payloads for
`clip-rect-difference` and `runtime-effect-simple` with local measured
dashboard-row payloads. The payloads include 30 samples, host, OS/architecture,
JDK, backend, baseline commit, owner, regression label, raw samples, and the
GPU/cache adapter.

The lanes remain `reporting-only`. This ticket does not introduce a
release-blocking performance gate and does not change renderer support claims.

## Measured Lanes

| Scene | Lane | Status | Median ms | P95 ms | Command |
|---|---|---|---:|---:|---|
| `clip-rect-difference` | CPU | `measured` | 0.332500 | 0.495917 | `rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance` |
| `clip-rect-difference` | GPU/cache | `measured` | 0.002666 | 0.003375 | `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance` |
| `runtime-effect-simple` | CPU | `measured` | 0.058666 | 0.388917 | `rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance` |
| `runtime-effect-simple` | GPU/cache | `measured` | 0.002792 | 0.003208 | `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance` |

## Environment

| Field | Value |
|---|---|
| Host | `Omega` |
| OS | `Mac OS X 26.5 aarch64` |
| JDK | `25.0.1+8-LTS (Eclipse Adoptium)` |
| CPU backend | `CPU scalar Kotlin dashboard benchmark` |
| GPU/cache backend | `WebGPU cache/timing dashboard benchmark` |
| GPU/cache adapter | `Apple M2 Max` |
| Baseline commit | `94ed720b280c85aa8fde0c48be496961fe70c80f` |

## Payload Changes

| File | Previous status | New status | Gate mode |
|---|---|---|---|
| `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu-performance.json` | `estimated` | `measured` | `reporting-only` |
| `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu-performance.json` | `estimated` | `measured` | `reporting-only` |
| `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu-performance.json` | `estimated` | `measured` | `reporting-only` |
| `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu-performance.json` | `estimated` | `measured` | `reporting-only` |

## Gate Evidence

`pipelineGeneratedSceneExport` now refreshes row-local `performanceTrend`
payloads from `rawMetrics` JSON when the payload `sceneId` matches the
dashboard row. This keeps generated scene rows aligned with the measured
payload files while preserving shared base-metric rows that intentionally point
to another scene.

After FOR-305:

- `pipelineSceneDashboardGate` has no `performance.estimated` warnings for
  `clip-rect-difference` or `runtime-effect-simple`;
- `pipelinePerformanceTrendWarnings` reports `11` measured CPU rows, `11`
  measured GPU/cache rows, and `0` warnings.

## Scope Preservation

| Surface | Changed |
|---|---|
| Renderer behavior | No |
| Shader generation | No |
| Scene support status | No |
| Performance thresholds | No |
| Fallback policy | No |
| Release-blocking gate | No |

## Validation

Required:

- `rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance`
- `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk ./gradlew pipelinePerformanceTrendWarnings`
- `rtk ./gradlew pipelinePmBundle`
- `rtk python3 -m json.tool reports/wgsl-pipeline/for-305-measured-performance-lanes.json`
- `rtk git diff --check origin/master...HEAD`
