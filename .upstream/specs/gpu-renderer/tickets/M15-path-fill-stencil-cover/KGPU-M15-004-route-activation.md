---
id: KGPU-M15-004
title: "Activate M15 routes: Path fill native + stencil-cover default ON with rollback"
status: proposed
milestone: M15
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M15-001, KGPU-M15-002, KGPU-M15-003]
legacy_gate: legacy drawPath
---

# KGPU-M15-004 - Activate M15 routes: Path fill native + stencil-cover default ON with rollback

## PM Note

L'activation du remplissage de chemin est le milestone le plus critique pour la parité vectorielle. Les chemins arbitraires sont la raison d'être d'un renderer 2D.

## Problem

Path fill routes (convex fan + stencil-cover) need controlled product activation with rollback paths and parity evidence. Path rendering is the core differentiator of a 2D GPU renderer.

## Scope

- Add controlled product flags for PathFill (convex fan + stencil-cover)
- Implement rollback path (flag OFF -> legacy path rendering)
- Prove parity: GPU path fill output == legacy path fill output
- Set flag default to ON after parity review

## Non-Goals

- No activation for paths exceeding 256-edge budget
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawPath; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M15RouteFlags { var pathFill: Boolean = false }\n// Paths > 256 edges remain refused regardless of flag
```

## Acceptance Criteria

- [ ] PathFill has controlled feature flag with rollback path
- [ ] Parity evidence: GPU path pixel output == legacy path pixel output
- [ ] Paths exceeding edge budget emit stable refusal regardless of flag
- [ ] Flag defaults to ON after parity review acceptance

## Required Evidence

- Path fill GPU vs CPU pixel comparison for circle, star, bezier, polygon shapes
- Rollback validation transcript
- Edge budget refusal diagnostic transcript

## Fallback / Refusal Behavior

Parity failure or edge budget exceeded keeps path fill flag OFF with diagnostic.

## Dashboard Impact

- Expected row: `gpu-renderer.m15.route-activation`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check && rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M15`
- `area:product-validation`
