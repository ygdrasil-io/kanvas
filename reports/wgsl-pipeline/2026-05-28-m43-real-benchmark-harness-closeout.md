# M43 real benchmark harness closeout

M43 replaced selected static performance seeds with reproducible measured CPU
and GPU/cache dashboard payloads. The milestone does not add required CI
performance gates; all measured rows remain `reporting-only` under the GRA-209
policy.

## Ticket summary

| Ticket | PR | Outcome |
| --- | --- | --- |
| GRA-206 | #1225 | Defined measured `performanceTrend` payload shape and adapter-missing semantics. |
| GRA-207 | #1226 | Added measured CPU payloads for two stable rows. |
| GRA-208 | #1227 | Added measured GPU/cache payloads for two stable rows on `Apple M2 Max`. |
| GRA-209 | #1228 | Defined baseline/regression policy and deferred required gates. |

## Measured rows

| Scene | CPU median / p95 ms | CPU raw JSON | GPU median / p95 ms | GPU adapter | GPU raw JSON |
| --- | ---: | --- | ---: | --- | --- |
| `src-over-stack` | 0.023334 / 0.099208 | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-performance.json` | 0.026083 / 0.037041 | `Apple M2 Max` | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-performance.json` |
| `bitmap-shader-local-matrix` | 0.015667 / 0.015958 | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-performance.json` | 0.006417 / 0.007666 | `Apple M2 Max` | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-performance.json` |

## Dashboard status

Static source dashboard rows after M43:

| Signal | Count |
| --- | ---: |
| Scene rows | 8 |
| `pass` | 5 |
| `tracked-gap` | 1 |
| `expected-unsupported` | 2 |
| CPU `performanceTrend.status=measured` | 2 |
| CPU `performanceTrend.status=estimated` | 2 |
| GPU `performanceTrend.status=measured` | 2 |
| GPU `performanceTrend.status=estimated` | 2 |
| GPU performance absent | 2 |

`pipelineSceneDashboard` still merges generated M41 rows at export time; the
source dashboard remains valid and M43 does not hide expected unsupported rows or
tracked gaps.

## Commands

```bash
rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance
rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

## Policy and residual risks

- Baselines are `m43-cpu-measured-local` and `m43-gpu-cache-measured-local`.
- Current regression labels are `unknown` because M43 creates initial local
  baselines rather than approved CI comparison sets.
- GPU/cache measured rows require a named adapter; adapter-missing runs write
  `status=unavailable` with `reason=gpu.adapter-missing`.
- Required performance gates are deferred until a future ticket defines CI
  budget, host/JDK/backend/adapter eligibility, variance thresholds,
  flake/quarantine handling, rollback rules, and a baseline owner.
- `analytic-aa-convex` remains a P0 tracked gap owned by GRA-222; M43 did not
  change that rendering policy gap.

## References

- `.upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md`
- `reports/wgsl-pipeline/2026-05-28-m43-measured-benchmark-payload.md`
- `reports/wgsl-pipeline/2026-05-28-m43-cpu-measured-metrics.md`
- `reports/wgsl-pipeline/2026-05-28-m43-gpu-measured-cache-metrics.md`
- `reports/wgsl-pipeline/2026-05-28-m43-baseline-regression-policy.md`
