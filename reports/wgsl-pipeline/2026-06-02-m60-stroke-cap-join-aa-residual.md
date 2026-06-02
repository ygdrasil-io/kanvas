# M60 Stroke Cap/Join AA Residual - 2026-06-02

Linear: `FOR-233`, `FOR-240`

## Decision

`m60-bounded-stroke-cap-join` remains `expected-unsupported`.

The target-colorspace blend pilot from `FOR-232` removed the dominant neutral
AA darkening, and FOR-240 added general RGBA8 quantization for the target-blend
AA stencil-cover source output. The scene still fails the exact `99.95%`
support threshold. No readiness percentage is increased.

## Residual Classification

After `targetColorSpaceBlend=true`:

| Metric | Value |
|---|---:|
| Exact similarity | `95.91%` |
| Matching pixels | `23572 / 24576` |
| Residual mismatches | `1004` |
| One-unit mismatches | `994` |
| Pixels above tolerance 8 | `10` |
| Pixels above tolerance 32 | `6` |
| Max channel delta | `48` |

The residual is mostly byte-exact target-colour transform drift after RGBA8
source quantization. The remaining high-delta tail is small and localized to
stroke cap/join AA boundary pixels. That is not enough to claim support because
the M60 gate is exact.

## Region Breakdown

| Region | Mismatches | One-unit | `> 8` | `> 32` | Max delta |
|---|---:|---:|---:|---:|---:|
| `butt-bevel` | `394` | `392` | `2` | `0` | `25` |
| `round-round` | `523` | `515` | `8` | `6` | `48` |
| `square-bevel` | `87` | `87` | `0` | `0` | `1` |

The dominant exact mismatch region remains `round-round`.

## High-Delta Samples

The diagnostic JSON now records the complete `> 8` tail as
`highDeltaSamples`. The top six samples are all in the `round-round` band on
the descending cap/join boundary:

| Pixel | Reference RGBA | WebGPU RGBA | Max delta |
|---|---|---|---:|
| `(92, 75)` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `48` |
| `(91, 76)` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `48` |
| `(90, 77)` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `48` |
| `(89, 78)` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `48` |
| `(88, 79)` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `48` |
| `(87, 80)` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `48` |

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
- No coordinate, channel, or region-specific shader workaround is added.
- No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM is introduced.

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
```
