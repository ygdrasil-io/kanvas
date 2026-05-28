# M40 CPU Route Performance Metrics

Date: 2026-05-28
Linear: GRA-188
Milestone: M40 Performance And Regression Dashboard

## Scope

GRA-188 populates CPU-side performance trend fields for selected M39 P1 scene
rows using the optional `performanceTrend` schema from GRA-176.

The current values are marked `status=estimated`. They are informational static
seed metrics, not regression gates. They make the dashboard shape, raw metric
links, route identities, sample counts, median/p95 timing fields, counters, and
baseline metadata visible before a native CPU benchmark harness is promoted.

## Populated Scenes

| Scene | CPU route | Samples | Median ms | P95 ms | Regression label | Raw metrics |
|---|---|---:|---:|---:|---|---|
| `linear-gradient-rect` | `cpu.shader.linear-gradient.rect` | 30 | 0.035 | 0.046 | `unknown` | `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/cpu-performance.json` |
| `src-over-stack` | `cpu.blend.src-over-stack` | 30 | 0.028 | 0.039 | `unknown` | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-performance.json` |
| `runtime-effect-simple` | `cpu.runtime-effect.descriptor.simple_rt` | 30 | 0.041 | 0.055 | `unknown` | `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu-performance.json` |
| `clip-rect-difference` | `cpu.coverage.clip-rect-difference` | 30 | 1.420 | 1.870 | `unknown` | `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu-performance.json` |
| `bitmap-shader-local-matrix` | `cpu.shader.bitmap.local-matrix` | 30 | 0.018 | 0.024 | `unknown` | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-performance.json` |

## Policy

- Performance fields are separate from visual correctness fields.
- `status=estimated` prevents these values from becoming a pass/fail gate.
- `regression.label=unknown` is used because no explicit performance baseline
  exists yet.
- GRA-190 should preserve this distinction in the PM closeout.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
