# M59 Performance Gap Decision

Result: pass.

M59 keeps all three M58 `not-measured` rows in the final 100% performance
target and resolves them with measured CPU plus GPU/cache payloads. No row is
removed from target scope, and no estimated or missing metric is promoted to
measured evidence.

| Row | M59 decision | CPU payload | GPU/cache payload | Final target |
|---|---|---|---|---|
| `solid-rect` | Measure | `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu-performance.json` | `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu-performance.json` | Kept |
| `linear-gradient-rect` | Replace estimates with measured payloads | `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/cpu-performance.json` | `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/gpu-performance.json` | Kept |
| `m54-simple-aa-clip` | Measure without broadening Path AA support | `reports/wgsl-pipeline/scenes/artifacts/m54-simple-aa-clip/cpu-performance.json` | `reports/wgsl-pipeline/scenes/artifacts/m54-simple-aa-clip/gpu-performance.json` | Kept |

The three rows are eligible for the M59 release gate only when both lanes have
`status=measured`, sample count, timing, counters, baseline metadata,
environment metadata, owner metadata, and GPU adapter metadata for GPU/cache
lanes.

M59 can claim 100% readiness because `pipelinePerformanceReleaseGate` now
reports 7 selected rows, 7 pass rows, 14 measured release-blocking lanes,
0 `notMeasuredRows`, 0 `notMeasuredLanes`, and 0 blocking failures.
