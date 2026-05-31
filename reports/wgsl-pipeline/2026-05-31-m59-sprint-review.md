# M59 Sprint Review

Result: pass.

M59 closes the remaining performance measurement gap by converting
`solid-rect`, `linear-gradient-rect`, and `m54-simple-aa-clip` into measured
CPU and GPU/cache release-gated rows.

| Signal | M58 baseline | M59 result |
|---|---:|---:|
| Dashboard rows | 61 | 61 |
| `pass` | 47 | 47 |
| `expected-unsupported` | 14 | 14 |
| `tracked-gap` | 0 | 0 |
| `fail` | 0 | 0 |
| Performance release gate selected rows | 7 | 7 |
| Measured release-blocking lanes | 8 | 14 |
| Not-measured rows | 3 | 0 |
| Performance release gate failures | 0 | 0 |

Delivered artifacts:

- `reports/wgsl-pipeline/2026-05-31-m59-performance-gap-decision.md`
- `reports/wgsl-pipeline/2026-05-31-m59-performance-release-gate-selection.md`
- `reports/wgsl-pipeline/performance/m59-performance-release-gate.json`
- `build/reports/wgsl-pipeline-performance-release-gate/m59-performance-release-gate.md`
- `build/reports/wgsl-pipeline-pm-bundle/manifest.json#m59PerformanceReleaseGate`

Validation:

- `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredCpuPerformance pipelineMeasuredGpuPerformance`
- `rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings pipelinePerformanceReleaseGate`: pass.
- `rtk ./gradlew --no-daemon -Pkanvas.performance.releaseGate.negativeFixture=true pipelinePerformanceReleaseGate`: expected fail on `src-over-stack/CPU`, proving the blocking path.
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle pipelineSkiaGmInventory pipelineSkiaGmInventoryGate`: pass.
- `rtk git diff --check`: pass.
