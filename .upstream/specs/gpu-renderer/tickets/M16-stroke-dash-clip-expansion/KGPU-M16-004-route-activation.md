---
id: KGPU-M16-004
title: "Activate M16 routes: Stroke + Dash + bounded clips default ON with rollback"
status: proposed
milestone: M16
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M16-001, KGPU-M16-002, KGPU-M16-003]
legacy_gate: legacy drawPath stroke
---

# KGPU-M16-004 - Activate M16 routes: Stroke + Dash + bounded clips default ON with rollback

## PM Note

Avec M16 activé, le renderer GPU couvre le stroke, le dash et les clips complexes. C'est la fin de la vague 1 pour la géométrie vectorielle.

## Problem

Stroke, dash, and bounded clip routes need controlled product activation with rollback and parity evidence. M16 completes the Wave 1 geometry coverage alongside M15 path fill.

## Scope

- Add controlled product flags for Stroke, Dash, and BoundedClip routes
- Implement rollback path per route
- Prove parity: GPU output == legacy output for each route
- Set flag defaults to ON after parity review

## Non-Goals

- No activation for paths exceeding stroke edge budget
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawPath stroke; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M16RouteFlags { var stroke: Boolean = false; var dash: Boolean = false; var boundedClip: Boolean = false }
```

## Acceptance Criteria

- [ ] Each M16 route has independent feature flag with rollback
- [ ] Parity evidence: flag ON pixels == flag OFF pixels for stroke, dash, clips
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Per-route before/after pixel comparison dumps
- Rollback validation transcript for each route
- Edge budget refusal diagnostic still active after activation

## Fallback / Refusal Behavior

Any parity failure keeps the affected route flag OFF; edge budget refusals remain.

## Dashboard Impact

- Expected row: `gpu-renderer.m16.route-activation`
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
- `milestone:M16`
- `area:product-validation`
