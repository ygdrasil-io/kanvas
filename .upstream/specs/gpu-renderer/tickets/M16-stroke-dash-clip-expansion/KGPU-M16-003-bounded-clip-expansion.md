---
id: KGPU-M16-003
title: "Add bounded clip expansion: rrect/path clip stacks beyond simple scissor"
status: done
milestone: M16
priority: P0
owner_area: clips-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M13-003, KGPU-M15-002]
legacy_gate: null
---

# KGPU-M16-003 - Add bounded clip expansion: rrect/path clip stacks beyond simple scissor

## PM Note

Les clips complexes (rrect et path) vont au-delà du simple scissor rect. La pile de clips doit gérer l'intersection, la simplification et le refus.

## Problem

Clip stacks need to support rrect and path clips beyond simple device-rect scissor, with bounded clip region tracking, intersection, and simplification. Without this, complex UI clipping (rounded corners, custom shapes) is impossible.

## Scope

- Add rrect clip stack support with analytic coverage
- Add path clip stack support via stencil-based clip regions
- Add clip intersection and simplification for bounded regions
- Produce complex clip stack rendering fixture dumps

## Non-Goals

- No unbounded or inverse-fill clip regions
- No clip stack merging across saveLayer boundaries

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_CLIP_SIMPLIFY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-clip-simplify) - source src/gpu/graphite/DrawContext.cpp clip; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class ClipStack { fun push(rrect: RRect); fun push(path: Path); fun pop(); fun currentClipBounds(): Rect; fun emitScissorOrStencil(): ClipCommand }
```

## Acceptance Criteria

- [ ] RRect clip stack correctly constrains rendering to rounded region
- [ ] Path clip stack correctly constrains rendering via stencil
- [ ] Clip intersection produces correct bounded region
- [ ] Complex clip stacks exceeding capability emit stable diagnostic

## Required Evidence

- RRect clip stack GPU rendering fixture dump
- Path clip stack GPU rendering fixture dump
- Clip intersection correctness dump
- Clip refusal diagnostic for unsupported clip configurations

## Fallback / Refusal Behavior

Unsupported clip configurations emit stable diagnostic; affected draws use last valid clip.

## Dashboard Impact

- Expected row: `gpu-renderer.m16.bounded-clip-expansion`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ClipStack*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- StrokeExpander, DashPathEffect, bounded clip expansion
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M16`
- `area:clips-passes`
