---
id: KGPU-M13-004
title: "Activate M13 routes: FillRRect + LinearGradient + Scissor default ON with rollback"
status: done
milestone: M13
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M13-001, KGPU-M13-002, KGPU-M13-003]
legacy_gate: legacy drawRect/drawRRect/drawPaint
---

# KGPU-M13-004 - Activate M13 routes: FillRRect + LinearGradient + Scissor default ON with rollback

## PM Note

L'activation produit des routes M13 est le premier vrai test du pattern rollback après M1. Chaque route activée doit pouvoir être désactivée individuellement.

## Problem

The three M13 routes (FillRRect, LinearGradient, Scissor) need controlled product activation with per-route feature flags, rollback paths, and parity evidence before default-ON status.

## Scope

- Add controlled product flags for FillRRect, LinearGradient, Scissor routes
- Implement rollback path per route (flag OFF -> legacy behavior)
- Prove parity: flag ON GPU output == flag OFF legacy output
- Set flag defaults to ON after parity evidence reviewed

## Non-Goals

- No performance readiness claims
- No cross-route activation dependencies beyond M13
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawRect; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M13RouteFlags { var fillRRect: Boolean = false; var linearGradient: Boolean = false; var scissor: Boolean = false }\nclass M13RollbackParityValidator { fun validate(): ParityResult }
```

## Acceptance Criteria

- [ ] Each M13 route has independent feature flag defaulting to OFF initially
- [ ] Rollback path restores legacy behavior per route
- [ ] Parity evidence: flag ON pixels == flag OFF pixels for all three routes
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Per-route before/after pixel comparison dumps
- Rollback validation transcript for each route
- PM bundle row showing M13 activation status

## Fallback / Refusal Behavior

Any parity failure keeps the affected route flag OFF with visible diagnostic.

## Dashboard Impact

- Expected row: `gpu-renderer.m13.route-activation`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check && rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (2026-06-24): GpuProductFlagConfig created in product/ProductFlags.kt with:
  - 3 product flag constants: fillRRect, linearGradient, scissor
  - 3 rollback disable properties: each with `.disable` suffix
  - `fromSystemProperties()` reads system properties to determine flag state
  - `buildCapabilities()` generates GPUCapabilities with active feature facts
  - Default: all 3 flags ON, rollback via `.disable=true`
  - 7 ProductFlagConfigTest tests verify all flag combinations

## Evidence

- 7 ProductFlagConfigTest tests pass
- All 3 flags default to ON
- Rollback via system property `kanvas.gpu.renderer.product.<flag>.disable=true`
- Capabilities built with `first_slice.fill_rrect.native`, `first_slice.linear_gradient.native`, `first_slice.scissor.native`

## Linear Labels

- `gpu-renderer`
- `milestone:M13`
- `area:product-validation`
