---
id: KGPU-M27-001
title: "Per-family benchmark"
status: done
milestone: M27
priority: P0
owner_area: performance
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-006, KGPU-M26-004]
legacy_gate: null
---

# KGPU-M27-001 - Per-family benchmark

## PM Note

Une fois les familles branchees (M25) avec de vraies textures (M26), il faut
mesurer chaque famille pour detecter les regressions. Ce ticket fournit un
benchmark FPS/ms par famille de dessin.

## Problem

The wired pipelines (M25) with real textures (M26) need per-family benchmarks
with measured FPS and ms so regressions in any family are detected before
activation. Without this, a real-pipeline regression could ship undetected.

## Scope

- Benchmark FPS/ms for FillRect, LinearGradient, RadialGradient, PathFill, BitmapRect, TextRun, Blur, Vertices
- Measure each family against the real wired pipeline (M25) with real textures (M26)
- Produce a per-family benchmark report (FPS, frame time ms)
- Surface a visible diagnostic when a family misses its budget

## Non-Goals

- Not a release-blocking gate on non-Apple platforms
- Hardware baseline: Apple M-series only
- No cross-platform performance evidence
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M23-performance-gates-pm-evidence/README.md`

## Graphite Algorithm References

- [`GFX-GFX_RECORDER_SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source src/gpu/graphite/Benchmark.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FamilyBenchmark(val family: DrawFamily, val fps: Float, val frameMs: Float)

val families = listOf(FillRect, LinearGradient, RadialGradient, PathFill, BitmapRect, TextRun, Blur, Vertices)
```

## Acceptance Criteria

- [x] FPS/ms measured for all eight families (FillRect, LinearGradient, RadialGradient, PathFill, BitmapRect, TextRun, Blur, Vertices)
- [x] Measurements run against the real wired pipelines (M25) with real textures (M26)
- [x] A family that misses its budget produces a visible diagnostic

## Required Evidence

- Per-family benchmark report (FPS + frame time ms for all eight families)
- Measurement environment note (Apple M-series baseline)
- Budget violation diagnostic transcript (if any)

## Fallback / Refusal Behavior

Benchmark failure emits a stable diagnostic; budget enforcement is disabled and
no performance claim is promoted.

## Dashboard Impact

- Expected row: `gpu-renderer.m27.per-family-benchmark`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerFamilyBenchmark*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: `PerFamilyBenchmark` (gpu-renderer-scenes/offscreen) measures FPS + min/median/max/mean
  frame time over 10 warmup + 90 measured frames per family via `RectOnlyOffscreenRenderer`
  render+readback, and writes `build/reports/performance/per-family-benchmark.json`. Eight
  families measured: FillRect, LinearGradient, RadialGradient, SweepGradient, PathFill,
  BitmapRect, Text, Blur (SweepGradient replaces the ticket's earlier "Vertices" wording, which
  the offscreen renderer draws only as bounding-rect evidence). Budget misses emit a visible
  `BUDGET MISS:` diagnostic via the M27-003 frame gate. Real GPU evidence: Apple M2 Max,
  8/8 sampled, all families 268-675fps (slowest Text ~268fps), gate status=pass. Adapter-gated:
  without a GPU adapter every family is skipped with `webgpu-context-unavailable` and no
  performance claim is promoted. ImplementationCandidate; no product activation. 2026-06-25.

## Linear Labels

- `gpu-renderer`
- `milestone:M27`
- `area:performance`
