---
id: KGPU-M14-003
title: "Activate M14 routes: Radial + Sweep gradients default ON with rollback"
status: done
milestone: M14
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-001, KGPU-M14-002]
legacy_gate: legacy drawPaint
---

# KGPU-M14-003 - Activate M14 routes: Radial + Sweep gradients default ON with rollback

## PM Note

Avec M13 et M14 activés, les trois types de gradients (linéaire, radial, sweep) couvrent la majorité des besoins UI. TwoPointConical reste refusé.

## Problem

Radial and sweep gradient routes need controlled product activation with rollback paths and parity evidence, completing the gradient family coverage alongside the already-activated linear gradient from M13.

## Scope

- Add controlled product flags for RadialGradient and SweepGradient routes
- Implement rollback path per gradient type
- Prove parity: GPU gradient output == legacy gradient output
- Set flag defaults to ON after parity review

## Non-Goals

- No TwoPointConical gradient activation (deferred)
- No performance readiness claims
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawPaint; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M14RouteFlags { var radialGradient: Boolean = false; var sweepGradient: Boolean = false }\n// TwoPointConical remains refused with stable diagnostic
```

## Acceptance Criteria

- [ ] RadialGradient and SweepGradient have independent feature flags
- [ ] Rollback path restores legacy gradient rendering per type
- [ ] Parity evidence: flag ON pixels == flag OFF pixels for both gradient types
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Per-gradient-type before/after pixel comparison dumps
- Rollback validation transcript for each gradient type
- TwoPointConical refusal diagnostic still active after activation

## Fallback / Refusal Behavior

Any parity failure keeps the affected gradient type flag OFF. TwoPointConical remains refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m14.route-activation`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check && rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: Implemented in KGPU-M14. Added GpuProductFlagConfig.radialGradientEnabled and sweepGradientEnabled (default ON) with system property overrides. Capability facts first_slice.radial_gradient.native and first_slice.sweep_gradient.native emitted in buildCapabilities(). 11 unit tests in ProductFlagConfigTest.

## Linear Labels

- `gpu-renderer`
- `milestone:M14`
- `area:product-validation`
