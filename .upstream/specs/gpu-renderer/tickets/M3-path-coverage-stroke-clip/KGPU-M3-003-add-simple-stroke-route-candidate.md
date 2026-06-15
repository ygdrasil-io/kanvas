---
id: KGPU-M3-003
title: "Add simple stroke route candidate"
status: done
milestone: M3
priority: P0
owner_area: geometry-stroke
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-001]
legacy_gate: "stroke legacy"
---

# KGPU-M3-003 - Add simple stroke route candidate

## PM Note

Ce ticket rend les strokes simples auditables avant toute promesse de parité.

## Problem

Stroke support requires cap, join, miter, dash, transform, and bounds evidence.

## Scope

- Add `GPUStrokeDescriptor`, `GPUStrokeExpansionPlan`, and route/refusal dumps.
- Cover one simple bounded stroke and nearby unsupported variants.

## Non-Goals

- No broad cap/join parity, hairline, dash, or path-effect support.

## Spec Sources

- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`

## Graphite Algorithm References

- [`GFX-TESSELLATE-STROKES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-strokes) - source [TessellateStrokesRenderStep.cpp:91](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateStrokesRenderStep.cpp:91); Study stroke cap/join/cusp tessellation and transform-scale uniforms.
- [`GFX-MSAA-PATH-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-msaa-path-heuristics) - source [Device.cpp:2040](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2040); Reference how Graphite routes strokes and hairlines before fill renderers.
- [`GFX-DRAWGEOMETRY-ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source [Device.cpp:1512](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1512); Use draw splitting and order updates as route evidence vocabulary.
- [`GFX-RENDERSTEP-MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83); Compare stroke support to multi-step renderer decomposition.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class StrokeRouteEvidence(val strokePlan: String, val refusalDumps: List<String>)
```

## Acceptance Criteria

- [ ] Simple stroke route has deterministic descriptor and bounds dumps.
- [ ] Unsupported stroke styles refuse with stable diagnostics.
- [ ] Prepared/native route kind is explicit.

## Required Evidence

- Stroke descriptor and expansion dumps.
- CPU/GPU evidence or explicit refusal.

## Fallback / Refusal Behavior

Unsupported strokes refuse; they are not converted to CPU-rendered textures.

## Dashboard Impact

- Expected row: `gpu-renderer.stroke.simple`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GPUSimpleStrokePreparedPlanner` contract evidence for one
  bounded simple stroke as `CPUPreparedGPU`, deterministic stroke artifact keys
  including accepted miter values, descriptor dumps, `GPUStrokeExpansionPlan`
  output bounds, stable refusal diagnostics for unsupported width, hairline,
  cap, join, miter, dash, path-effect, transform, expansion budget, path key,
  and path bounds cases, plus explicit non-claims for product activation,
  adapter-backed execution, hidden CPU texture fallback, broad stroke parity,
  hairline, dash, path-effect, and round cap/join. Independent review
  `019ec7dd-5430-7551-8720-f602d65a4415` found miter key collision and missing
  path-effect evidence; both were remediated with targeted RED/GREEN coverage.
  Post-remediation independent review `019ec7e4-77c7-7ec3-ae53-571b6086fbcd`
  accepted the evidence and confirmed no broad stroke support, product route
  activation, hidden CPU texture fallback, or adapter-backed execution is
  implied. Remaining gate: future stroke promotion still needs real execution
  and visual/reference evidence before any broader support claim.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:stroke`
