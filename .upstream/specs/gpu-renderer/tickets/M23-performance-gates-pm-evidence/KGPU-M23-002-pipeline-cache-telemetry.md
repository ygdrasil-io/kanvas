---
id: KGPU-M23-002
title: "Add pipeline cache telemetry: hit rate, eviction, module count per scene"
status: done
milestone: M23
priority: P0
owner_area: performance-validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M23-002 - Add pipeline cache telemetry: hit rate, eviction, module count per scene

## PM Note

La télémétrie du cache de pipelines mesure l'efficacité du cache et guide les optimisations de taille.

## Problem

Pipeline cache performance needs telemetry on hit rate, eviction count, and module count per scene. Without this, cache inefficiency goes undetected.

## Scope

- Add pipeline cache hit rate telemetry
- Add eviction count and reason tracking
- Add module count per scene telemetry
- Produce cache telemetry report

## Non-Goals

- No cache size tuning or optimization
- No cross-scene cache analysis

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_PIPELINE_MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source src/gpu/graphite/PipelineCache.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class PipelineCacheTelemetry(val hitRate: Float, val evictionCount: Int, val moduleCount: Int)
```

## Acceptance Criteria

- [ ] Hit rate telemetry recorded per scene
- [ ] Eviction events include reason and timestamp
- [ ] Module count tracked accurately

## Required Evidence

- Pipeline cache telemetry report for representative scenes
- Hit rate trend analysis
- Eviction reason distribution

## Fallback / Refusal Behavior

Telemetry collection failure emits stable diagnostic; cache operates without telemetry.

## Dashboard Impact

- Expected row: `gpu-renderer.m23.pipeline-cache-telemetry`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PipelineCacheTelemetry*'
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
