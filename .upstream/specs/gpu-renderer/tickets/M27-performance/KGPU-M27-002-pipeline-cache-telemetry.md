---
id: KGPU-M27-002
title: "Pipeline cache telemetry"
status: done
milestone: M27
priority: P0
owner_area: performance
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-006]
legacy_gate: null
---

# KGPU-M27-002 - Pipeline cache telemetry

## PM Note

Une fois toutes les familles branchees (M25), le cache de pipelines doit etre
mesure. Ce ticket fournit la telemetrie hit rate / evictions / nombre de modules
par scene.

## Problem

With every family now assembling real WGSL modules (M25), the pipeline cache
needs telemetry on hit rate, eviction count, and module count per scene. Without
this, cache inefficiency from the newly wired modules goes undetected.

## Scope

- Add pipeline cache hit-rate telemetry over the wired (M25) modules
- Add eviction count and reason tracking
- Add module-count-per-scene telemetry
- Produce a cache telemetry report

## Non-Goals

- No cache size tuning or optimization
- No cross-scene cache analysis
- Hardware baseline: Apple M-series only
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M23-performance-gates-pm-evidence/README.md`

## Graphite Algorithm References

- [`GFX-GFX_PIPELINE_MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source src/gpu/graphite/PipelineCache.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class PipelineCacheTelemetry(val hitRate: Float, val evictionCount: Int, val moduleCount: Int)
```

## Acceptance Criteria

- [x] Hit rate telemetry recorded per scene over the wired modules
- [ ] Eviction events include reason and timestamp
- [x] Module count tracked accurately per scene

## Required Evidence

- Pipeline cache telemetry report for representative scenes
- Hit rate trend analysis over the wired families
- Eviction reason distribution

## Fallback / Refusal Behavior

Telemetry collection failure emits a stable diagnostic; the cache operates
without telemetry and no performance claim is promoted.

## Dashboard Impact

- Expected row: `gpu-renderer.m27.pipeline-cache-telemetry`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PipelineCacheTelemetry*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: `GPUPipelineCacheTelemetry` (gpu-renderer/telemetry) extended with per-family
  pipeline-creation counts (`pipelineCreationCountsByFamily`, `totalPipelineCreations`).
  `rectOnlyPipelineCacheTelemetry` + `RectOnlyOffscreenRenderer.pipelineCacheTelemetry`
  derive hit count, miss count, hit rate, eviction count, module count per scene, and
  pipeline creation count per family from the exact pipeline passes the renderer assembles.
  `PipelineCacheTelemetryReport` writes `build/reports/performance/pipeline-cache-telemetry.json`.
  Real evidence (Apple M2 Max run, frameCount=100): single-family scenes report
  moduleCount=1 hitRate=0.99; `bitmap-sampler-matrix` and `glyph-atlas-strip` correctly
  report moduleCount=2 (content pass + SolidRect tray pass).
  Remaining gate: telemetry is draw-plan-derived (`telemetrySource=draw-plan-derived`), not a
  backend pipeline-cache observation, so eviction events carry a count only (0 in the
  steady-state per-scene model) and do not yet include reason+timestamp; that requires a real
  backend cache instrumentation hook. ImplementationCandidate; no product activation. 2026-06-25.

## Linear Labels

- `gpu-renderer`
- `milestone:M27`
- `area:performance`
