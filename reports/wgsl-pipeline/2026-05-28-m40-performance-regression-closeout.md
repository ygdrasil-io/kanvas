# M40 Performance And Regression Dashboard Closeout

Date: 2026-05-28
Linear: GRA-190
Milestone: M40 Performance And Regression Dashboard

## Summary

M40 closes with the static scene dashboard carrying both visual route evidence
and optional performance/regression fields. The dashboard remains static and
self-contained; native/live benchmark writing is intentionally deferred until a
real benchmark harness is promoted.

Dashboard source: `reports/wgsl-pipeline/scenes/data/scenes.json`
Dashboard export: `build/reports/wgsl-pipeline-scenes/index.html`

## Delivered

| Ticket | Result | Evidence |
|---|---|---|
| GRA-176 | Added optional validated `performanceTrend` schema and dashboard `unavailable` labels. | `reports/wgsl-pipeline/2026-05-28-m40-performance-trend-schema.md`, PR #1211 |
| GRA-188 | Added CPU performance trend fields and raw metric JSON for five selected P1 scenes. | `reports/wgsl-pipeline/2026-05-28-m40-cpu-performance-metrics.md`, PR #1212 |
| GRA-189 | Added GPU timing/cache trend fields and raw metric JSON for the same five selected P1 scenes. | `reports/wgsl-pipeline/2026-05-28-m40-gpu-cache-performance-metrics.md`, PR #1213 |
| GRA-190 | Published PM closeout and M40 dashboard summary. | This report, PR #1214 |

## Dashboard State

| Area | Status |
|---|---|
| Visual scene rows | 11 rows from P0, M37, M38, M39. |
| Visual correctness status | 7 pass, 2 tracked-gap, 2 expected-unsupported, 0 fail. |
| CPU performance rows | 5 selected M39 P1 rows populated with `status=estimated`. |
| GPU performance/cache rows | 5 selected M39 P1 rows populated with `status=estimated`. |
| Regression labels | `unknown` for populated CPU/GPU rows because no explicit performance baseline exists yet. |
| Missing performance fields | Displayed as `unavailable` by the static dashboard. |

## Populated Performance Rows

| Scene | CPU perf | GPU perf/cache | Raw metrics |
|---|---|---|---|
| `linear-gradient-rect` | estimated timing/counters | estimated timing/cache counters | `cpu-performance.json`, `gpu-performance.json` |
| `src-over-stack` | estimated timing/counters | estimated timing/cache counters | `cpu-performance.json`, `gpu-performance.json` |
| `runtime-effect-simple` | estimated timing/counters | estimated timing/cache counters | `cpu-performance.json`, `gpu-performance.json` |
| `clip-rect-difference` | estimated timing/counters | estimated timing/cache counters | `cpu-performance.json`, `gpu-performance.json` |
| `bitmap-shader-local-matrix` | estimated timing/counters | estimated timing/cache counters | `cpu-performance.json`, `gpu-performance.json` |

Raw metric path pattern:

```text
reports/wgsl-pipeline/scenes/artifacts/<scene>/{cpu,gpu}-performance.json
```

## Environment Limits

- Current CPU/GPU performance metrics are static informational seeds with
  `status=estimated`, not benchmark gates.
- Adapter identity is recorded as a local WebGPU-capable validation host note;
  exact adapter serialization remains future native benchmark work.
- Existing adapter-missing/tracked-gap rows and expected-unsupported Path AA rows
  keep their correctness semantics and do not become performance failures.

## Next Backlog Recommendation

Do not create short-lived native/live dashboard writers until a real benchmark
harness is ready to own reproducibility, adapter identity, warm/cold phases, and
baseline comparison. Recommended future ticket when needed:

- Native benchmark exporter: write measured CPU/GPU `performanceTrend` JSON from
  the benchmark harness, including exact host/JDK/adapter metadata and baseline
  comparison.

No follow-up Linear ticket was created in this closeout because the M40 static
PM evidence is complete and the native/live writer is not required to close the
current backlog.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
