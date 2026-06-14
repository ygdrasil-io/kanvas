# M67 Performance Baseline

Baseline source: `reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json`.

| Scene | Lane | Samples | Median ms | P95 ms | Baseline |
|---|---|---:|---:|---:|---|
| `p0-live-baseline` | `frame.headless-webgpu` | 120 | 9.2756 | 12.2369 | `m65-headless-offscreen-baseline` |
| `p0-live-m63` | `frame.headless-webgpu` | 120 | 13.202 | 26.0364 | `m65-headless-offscreen-baseline` |
| `p0-live-m64` | `frame.headless-webgpu` | 120 | 12.8265 | 17.2254 | `m65-headless-offscreen-baseline` |

This baseline is useful for trend visibility. It is not a native Kadre FPS baseline.
