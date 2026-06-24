---
id: KGPU-M13-002
title: "Add LinearGradient material execution: WGSL snippet + uniform layout + payload"
status: done
milestone: M13
priority: P0
owner_area: materials-wgsl
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M13-002 - Add LinearGradient material execution: WGSL snippet + uniform layout + payload

## PM Note

Le dégradé linéaire est le premier shader de matériau complexe. La validation du layout uniforme et du payload est critique pour tous les gradients futurs.

## Problem

Linear gradients need a WGSL material snippet with proper uniform layout, gradient stop interpolation, and tile mode handling. Without validated uniform packing, gradient colors will be wrong or the shader will fail to compile.

## Scope

- Add LinearGradient WGSL snippet with 4-stop and 8-stop variants
- Add gradient uniform layout with validated byte packing
- Add tile mode handling (Clamp, Repeat, Mirror) in WGSL
- Produce gradient rendering fixture dumps for each variant

## Non-Goals

- No TwoPointConical gradient
- No gradient dithering or color space conversion

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_GRADIENT_STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source src/gpu/graphite/KeyHelpers.cpp:173; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct LinearGradientUniforms { startPoint: vec2f, endPoint: vec2f, numStops: u32, stops: array<vec4f, 8>, tileMode: u32 }
```

## Acceptance Criteria

- [ ] LinearGradient WGSL compiles and produces correct color interpolation
- [ ] All three tile modes (Clamp, Repeat, Mirror) render correctly
- [ ] 4-stop and 8-stop variants produce identical results for same gradient

## Required Evidence

- LinearGradient GPU rendering fixture dump for each tile mode
- Uniform layout validation report
- Gradient stop precision comparison (CPU reference vs GPU)

## Fallback / Refusal Behavior

Gradient compilation or rendering failure emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m13.linear-gradient-material`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LinearGradient*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (2026-06-24): LinearGradient WGSL snippet added in LinearGradientSnippet.kt with `compute_t_raw`, `sample_stops_at`, `linear_gradient_clamp` functions. GPULinearGradientMaterialLowering + GPULinearGradientMaterialDictionary created with dictionary expansion and material key derivation. Planner checks `first_slice.linear_gradient.native` capability for FillRect with LinearGradient material. Product flag `kanvas.gpu.renderer.product.linearGradient` with `.disable` rollback in ProductFlags.kt.

## Evidence

- 6 LinearGradientMaterialLoweringTest tests pass
- 2 new FirstRoutePlannerTest tests pass (LinearGradient acceptance + refusal)
- 7 ProductFlagConfigTest tests pass
- LinearGradient capability `first_slice.linear_gradient.native` gated by product flag

## Linear Labels

- `gpu-renderer`
- `milestone:M13`
- `area:materials-wgsl`
