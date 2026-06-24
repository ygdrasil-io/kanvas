---
id: KGPU-M22-004
title: "Activate M22 routes: DrawVertices + mesh default ON with rollback"
status: proposed
milestone: M22
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M22-001, KGPU-M22-002, KGPU-M22-003]
legacy_gate: legacy drawVertices
---

# KGPU-M22-004 - Activate M22 routes: DrawVertices + mesh default ON with rollback

## PM Note

L'activation DrawVertices ferme la vague 3. Le GPU renderer supporte maintenant toutes les familles de dessin: formes, texte, images, filtres, effets, et meshes.

## Problem

DrawVertices and mesh batching routes need controlled product activation. Vertex rendering is the last draw family needed for complete 2D GPU rendering coverage.

## Scope

- Add controlled product flag for DrawVertices route
- Implement rollback path (flag OFF -> legacy vertex rendering)
- Prove parity: GPU vertex output == legacy vertex output
- Set flag default to ON after parity review

## Non-Goals

- No index buffer or custom vertex layout activation
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawVertices; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M22RouteFlags { var drawVertices: Boolean = false }\n// Index buffers and custom layouts remain refused
```

## Acceptance Criteria

- [ ] DrawVertices has controlled feature flag with rollback path
- [ ] Parity evidence: GPU vertex output == legacy vertex output
- [ ] Flag defaults to ON after parity review acceptance

## Required Evidence

- DrawVertices GPU vs CPU pixel comparison
- Rollback validation transcript
- Mesh batching telemetry under activated route

## Fallback / Refusal Behavior

Any parity failure keeps the route flag OFF; unsupported vertex features remain refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m22.route-activation`
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
- `milestone:M22`
- `area:product-validation`
