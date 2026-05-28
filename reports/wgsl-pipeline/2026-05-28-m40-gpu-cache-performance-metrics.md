# M40 GPU Route Cache Performance Metrics

Date: 2026-05-28
Linear: GRA-189
Milestone: M40 Performance And Regression Dashboard

## Scope

GRA-189 populates GPU-side `performanceTrend` fields for selected M39 P1 scene
rows using the schema from GRA-176. The metrics include timing fields, route
identity, pipeline key, cache hit/miss counters, bind/resource counters, adapter
notes, and raw metric JSON links.

The current values are marked `status=estimated`. They are informational static
seed metrics and do not replace adapter-backed benchmark evidence. Adapter
missing and expected-unsupported rows remain valid and are not converted into
performance failures.

## Populated Scenes

| Scene | GPU route | Samples | Median ms | P95 ms | Cache hits | Cache misses | Raw metrics |
|---|---|---:|---:|---:|---:|---:|---|
| `linear-gradient-rect` | `webgpu.generated.linear-gradient.rect` | 30 | 0.090 | 0.128 | 29 | 1 | `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/gpu-performance.json` |
| `src-over-stack` | `webgpu.blend.src-over.fixed-function` | 30 | 0.075 | 0.111 | 58 | 2 | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-performance.json` |
| `runtime-effect-simple` | `webgpu.runtime-effect.descriptor.simple_rt` | 30 | 0.105 | 0.150 | 29 | 1 | `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu-performance.json` |
| `clip-rect-difference` | `webgpu.coverage.clip-difference.analytic-rrect-mask` | 30 | 1.720 | 2.240 | 29 | 1 | `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu-performance.json` |
| `bitmap-shader-local-matrix` | `webgpu.shader.bitmap.local-matrix` | 30 | 0.066 | 0.092 | 29 | 1 | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-performance.json` |

## Policy

- GPU performance fields are separate from visual correctness and route support.
- `status=estimated` means these rows are not regression gates.
- Adapter-missing/tracked-gap rows keep their existing route/fallback semantics.
- GRA-190 should present the metrics as a dashboard capability and defer native
  live benchmarking to future backlog.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
