# M27 - Performance

## Goal

Deliver per-family benchmarks, pipeline cache telemetry, and a frame gate policy
for the families now rendered with real GPU pipelines (M25 wiring) and real
textures (M26). M23 defined the budget framework; this milestone measures the
real wired pipelines end-to-end so regressions in any family are detected before
activation.

## Dependencies

Depends on M25 wiring and M26 real-texture rendering. Hardware baseline is
Apple M-series only.

## Exit Criteria

- [x] Per-family benchmark measures FPS/ms for FillRect, LinearGradient, RadialGradient, PathFill, BitmapRect, TextRun, Blur, Vertices
- [x] Pipeline cache telemetry reports hit rate, eviction count, and module count per scene
- [x] Frame gate policy enforces 60fps target, 30fps warning, and quarantine on regression
- [x] All measurements use the real wired pipelines (M25) with real textures (M26)

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M27-001 - Per-family benchmark](KGPU-M27-001-per-family-benchmark.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `performance` | [KGPU-M25-006, KGPU-M26-004] | null |
| [KGPU-M27-002 - Pipeline cache telemetry](KGPU-M27-002-pipeline-cache-telemetry.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `performance` | [KGPU-M25-006] | null |
| [KGPU-M27-003 - Frame gate policy](KGPU-M27-003-frame-gate-policy.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `performance` | [KGPU-M27-001] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerFamilyBenchmark*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PipelineCacheTelemetry*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FrameGate*'
```

## Non-Claims

- Not a release-blocking gate on non-Apple platforms
- Hardware baseline: Apple M-series only
- No cross-platform performance evidence
- No product activation: these tickets measure and gate, they do not flip routes ON

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
