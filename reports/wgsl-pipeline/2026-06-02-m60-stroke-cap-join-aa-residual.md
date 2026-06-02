# M60 Stroke Cap/Join AA Residual - 2026-06-02

Linear: `FOR-233`

## Decision

`m60-bounded-stroke-cap-join` remains `expected-unsupported`.

The target-colorspace blend pilot from `FOR-232` removed the dominant neutral
AA darkening, but the scene still fails the exact `99.95%` support threshold.
No readiness percentage is increased.

## Residual Classification

After `targetColorSpaceBlend=true`:

| Metric | Value |
|---|---:|
| Exact similarity | `95.37%` |
| Matching pixels | `23437 / 24576` |
| Residual mismatches | `1139` |
| One-unit mismatches | `1129` |
| Pixels above tolerance 8 | `10` |
| Pixels above tolerance 32 | `6` |
| Max channel delta | `49` |

The residual is mostly byte-exact target-colour quantization drift. The
remaining high-delta tail is small and localized to stroke cap/join AA boundary
pixels. That is not enough to claim support because the M60 gate is exact.

## Region Breakdown

| Region | Mismatches | One-unit | `> 8` | `> 32` | Max delta |
|---|---:|---:|---:|---:|---:|
| `butt-bevel` | `455` | `453` | `2` | `0` | `24` |
| `round-round` | `512` | `504` | `8` | `6` | `49` |
| `square-bevel` | `172` | `172` | `0` | `0` | `1` |

The dominant exact mismatch region remains `round-round`.

## Route Policy

The normal WebGPU route remains:

```text
webgpu.coverage.refuse
```

with:

```text
coverage.stroke-cap-join-visual-parity-below-threshold
```

The refined root-cause accounting is:

- resolved: `color-space.target-blend-required`;
- remaining: `coverage.stroke-cap-join-aa-residual`.

## Artifacts

- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/experimental-gpu-diagnostic.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json`

## Non-Claims

- No M60 support promotion is claimed.
- No threshold is lowered.
- No broad target-colorspace mode rollout is claimed.
- No shader workaround is added for one scene's exact-byte drift.
- No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM is introduced.

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
```
