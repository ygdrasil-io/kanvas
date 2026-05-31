# M55 Official Performance Baseline Payloads

Result: pass.

M55 attaches official candidate decisions to seven selected rows. Four rows use
existing measured CPU and GPU/cache `performanceTrend` payloads; three rows are
explicitly deferred with stable reasons. No estimated metric is relabelled as
measured.

## Payload Counters

| Signal | Count |
|---|---:|
| Selected rows | 7 |
| Rows with measured candidate payloads | 4 |
| Rows with deferred payload decisions | 3 |
| CPU measured candidate lanes | 4 |
| GPU/cache measured candidate lanes | 4 |
| CPU deferred lanes | 3 |
| GPU/cache deferred lanes | 3 |
| Estimated metrics promoted to measured | 0 |

## Measured Rows

| Scene | CPU baseline | GPU/cache baseline | Gate mode |
|---|---|---|---|
| `src-over-stack` | `m50-cpu-warning-local` | `m50-gpu-cache-warning-local` | reporting-only |
| `bitmap-shader-local-matrix` | `m50-cpu-warning-local` | `m50-gpu-cache-warning-local` | reporting-only |
| `m54-src-over-composition-depth` | `m50-cpu-warning-local` | `m50-gpu-cache-warning-local` | reporting-only |
| `m54-local-matrix-blend-composition` | `m50-cpu-warning-local` | `m50-gpu-cache-warning-local` | reporting-only |

These payloads retain host, OS, JDK, backend, sample count, timing, baseline,
baseline owner, regression label, counters, raw metrics paths, and non-blocking
gate metadata through the dashboard `performanceTrend` schema.

## Deferred Rows

| Scene | Decision |
|---|---|
| `solid-rect` | Deferred until approved measured CPU and WebGPU/cache payloads exist. |
| `linear-gradient-rect` | Deferred because existing trend data is estimated only. |
| `m54-simple-aa-clip` | Deferred until a dedicated clip/coverage benchmark and adapter baseline exist. |

## Non-Claims

- M55 does not fabricate or estimate measured data.
- M55 does not turn performance into a release-blocking gate.
- M55 does not expand rendering support or dashboard support claims.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings pipelineSceneDashboard pipelineSceneDashboardGate`
