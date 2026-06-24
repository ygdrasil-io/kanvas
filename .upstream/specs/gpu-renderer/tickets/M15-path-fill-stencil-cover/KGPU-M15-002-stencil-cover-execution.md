---
id: KGPU-M15-002
title: "Add stencil-cover execution: two-pass stencil write + cover resolve with WGSL"
status: done
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

# KGPU-M15-002 - Add stencil-cover execution: two-pass stencil write + cover resolve with WGSL

## PM Note

Le stencil-cover en deux passes est la méthode standard pour remplir des polygones non-convexes sur GPU. Sans cela, seuls les éventails convexes simples sont supportés.

## Problem

Non-convex path fills need stencil-cover rendering: a stencil write pass to mark coverage, followed by a cover resolve pass to fill the stenciled region. Without this, only convex fan fills are possible.

## Scope

- Add stencil write pass: triangulated path -> stencil buffer increment/decrement
- Add cover resolve pass: stencil-tested fullscreen quad with WGSL
- Add stencil state management (clear, write, test)
- Produce stencil-cover rendering fixture dumps

## Non-Goals

- No stencil-then-cover path for convex shapes (use convex fan)
- No nested stencil clip interactions at this stage

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_TESSELLATE_CURVES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-curves) - source src/gpu/graphite/render/StencilCoverRenderStep.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class StencilCoverPass { fun stencilWrite(pathPath: PathData); fun coverResolve(fillColor: Color); fun composite(): RenderPass }
```

## Acceptance Criteria

- [ ] Stencil-cover correctly fills non-convex paths on GPU
- [ ] Stencil buffer is properly cleared between passes
- [ ] Cover resolve samples produce correct anti-aliased edges

## Required Evidence

- Stencil-cover GPU rendering fixture dump for star and self-intersecting paths
- Stencil buffer state dump between passes
- Coverage comparison: stencil-cover vs CPU reference

## Fallback / Refusal Behavior

Stencil-cover failure falls back to convex fan path or emits diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m15.stencil-cover-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*StencilCover*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: Implemented StencilCoverExecutor (two-pass stencil-cover with clear, write, resolve), StencilCoverSnippet.kt (WGSL fullscreen vertex + resolve fragment), and 4 unit tests covering star fill, circle fill, buffer diagnostics, and empty refusal.

## Linear Labels

- `gpu-renderer`
- `milestone:M15`
- `area:geometry-passes`
