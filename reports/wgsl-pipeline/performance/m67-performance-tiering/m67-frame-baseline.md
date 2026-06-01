# M67 Performance Baseline

Baseline source: `reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json`.

| Scene | Lane | Samples | Median ms | P95 ms | Baseline |
|---|---|---:|---:|---:|---|
| `p0-live-baseline` | `frame.headless-webgpu` | 120 | 8.9599 | 9.3046 | `m65-headless-offscreen-baseline` |
| `p0-live-m63` | `frame.headless-webgpu` | 120 | 12.9778 | 13.249 | `m65-headless-offscreen-baseline` |
| `p0-live-m64` | `frame.headless-webgpu` | 120 | 12.4671 | 12.702 | `m65-headless-offscreen-baseline` |

This baseline is useful for trend visibility. It is not a native Kadre FPS baseline.
