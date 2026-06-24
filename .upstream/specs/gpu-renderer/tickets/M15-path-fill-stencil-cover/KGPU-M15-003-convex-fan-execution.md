---
id: KGPU-M15-003
title: "Add convex fan execution: single-pass analytic AA with triangle list"
status: proposed
milestone: M15
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M15-001]
legacy_gate: null
---

# KGPU-M15-003 - Add convex fan execution: single-pass analytic AA with triangle list

## PM Note

L'éventail convexe en une seule passe est plus performant que le stencil-cover pour les polygones convexes. Le GPU renderer doit choisir la meilleure méthode automatiquement.

## Problem

Convex paths (including tessellated convex polygons) should use a single-pass analytic AA triangle-list draw instead of the two-pass stencil-cover. Without this optimization, all path fills would pay the stencil-cover overhead.

## Scope

- Add convex fan execution path with single-pass analytic AA
- Add triangle list emission from convex fan tessellation output
- Add convexity detection in path fill routing
- Produce convex fan rendering fixture dumps

## Non-Goals

- No concave path support via convex decomposition
- No auto-detection of optimal triangle count

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_ANALYTIC_RRECT_STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-analytic-rrect-step) - source src/gpu/graphite/render/AnalyticRRectRenderStep.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class ConvexFanExecutor { fun execute(vertices: TriangleList, fillColor: Color): RenderPass }\n// Single-pass: vertex shader emits clip-space positions, fragment shader computes edge AA
```

## Acceptance Criteria

- [ ] Convex fan renders with correct analytic AA in single pass
- [ ] Convexity detection correctly routes convex paths to fan execution
- [ ] Convex fan is measurably faster than stencil-cover for same path

## Required Evidence

- Convex fan GPU rendering fixture dump
- Performance comparison: convex fan vs stencil-cover for convex paths
- Convexity detection routing transcript

## Fallback / Refusal Behavior

Convexity detection failure routes path to stencil-cover path; no silent fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.m15.convex-fan-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ConvexFan*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M15`
- `area:geometry-passes`
