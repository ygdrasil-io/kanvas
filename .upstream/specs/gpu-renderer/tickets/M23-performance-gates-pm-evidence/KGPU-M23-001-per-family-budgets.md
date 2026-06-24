---
id: KGPU-M23-001
title: "Add per-family performance budgets: measured FPS/ms for each draw family"
status: done
milestone: M23
priority: P0
owner_area: performance-validation
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: []
legacy_gate: null
---

# KGPU-M23-001 - Add per-family performance budgets: measured FPS/ms for each draw family

## PM Note

Les budgets de performance par famille permettent de détecter les régressions. Chaque famille (rect, path, text, image, etc.) a son propre budget FPS/ms.

## Problem

Performance regression detection needs per-family budgets with measured FPS and ms targets. Without this, GPU route activation could silently degrade frame rates below acceptable thresholds.

## Scope

- Add per-family performance measurement (FPS, frame time ms)
- Add budget targets: 60fps goal, 30fps warning threshold
- Add per-family budget tracking and regression detection
- Produce performance telemetry report

## Non-Goals

- Not a release-blocking gate on non-Apple platforms
- No continuous integration performance testing
- Hardware baseline: Apple M-series only

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RECORDER_SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source src/gpu/graphite/Benchmark.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FamilyBudget(val family: DrawFamily, val targetFps: Int, val targetMs: Float, val measured: MeasuredPerf?)
```

## Acceptance Criteria

- [ ] Each draw family has defined FPS and frame time budget
- [ ] Performance is measured and compared against budget
- [ ] Budget violations produce visible diagnostic

## Required Evidence

- Per-family performance measurement report
- Budget definition table with rationale
- Budget violation diagnostic transcript

## Fallback / Refusal Behavior

Performance measurement failure emits stable diagnostic; budget enforcement disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m23.per-family-budgets`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerformanceBudget*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- PerformanceBudget, pipeline cache telemetry, frame gate policy, PM evidence bundle
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M23`
- `area:performance-validation`
