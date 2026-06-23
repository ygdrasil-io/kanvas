---
id: KGPU-M21-004
title: "Activate M21 routes: registered effects default ON, unregistered -> refusal"
status: proposed
milestone: M21
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M21-001, KGPU-M21-002, KGPU-M21-003]
legacy_gate: legacy drawRuntimeEffect
---

# KGPU-M21-004 - Activate M21 routes: registered effects default ON, unregistered -> refusal

## PM Note

L'activation des effets runtime avec refus des effets non-enregistrés est la politique de sécurité: seuls les effets validés passent, le reste est refusé avec diagnostic.

## Problem

Registered runtime effects need controlled product activation where only registered effects execute on GPU and unregistered effects are refused with stable diagnostics. This enforces the security boundary between validated and arbitrary effects.

## Scope

- Add controlled product flag for runtime effect execution
- Implement rollback path (flag OFF -> legacy effect rendering)
- Prove parity: registered effect GPU output == CPU oracle output
- Set flag default to ON after parity review; unregistered effects always refused

## Non-Goals

- No arbitrary SkSL effect activation
- No user-defined effect registration at runtime
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawRuntimeEffect; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M21RouteFlags { var registeredEffects: Boolean = false }\n// Unregistered effects always refused regardless of flag\n// Three effects registered: SimpleRT, LinearGradientRT, SpiralRT
```

## Acceptance Criteria

- [ ] Registered effects execute on GPU when flag is ON
- [ ] Unregistered effects are always refused with stable diagnostic
- [ ] Rollback path restores legacy effect rendering
- [ ] Flag defaults to ON after parity review acceptance

## Required Evidence

- Registered effect GPU vs CPU oracle pixel comparison
- Unregistered effect refusal diagnostic transcript
- Rollback validation transcript

## Fallback / Refusal Behavior

Flag OFF restores legacy rendering; unregistered effects always refused regardless of flag.

## Dashboard Impact

- Expected row: `gpu-renderer.m21.route-activation`
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
- `milestone:M21`
- `area:product-validation`
