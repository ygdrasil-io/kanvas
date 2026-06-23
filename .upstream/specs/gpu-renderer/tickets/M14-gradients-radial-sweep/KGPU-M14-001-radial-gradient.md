---
id: KGPU-M14-001
title: "Add RadialGradient WGSL: distance-from-center math + tile mode"
status: proposed
milestone: M14
priority: P0
owner_area: materials-wgsl
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M13-002]
legacy_gate: null
---

# KGPU-M14-001 - Add RadialGradient WGSL: distance-from-center math + tile mode

## PM Note

Le dégradé radial est plus complexe que le linéaire à cause du calcul de distance. La précision mathématique en WGSL doit être vérifiée pour tous les tile modes.

## Problem

Radial gradients require distance-from-center computation in WGSL with correct tile mode wrapping. Numerical precision issues at gradient boundaries can cause visible banding or discontinuities.

## Scope

- Add RadialGradient WGSL snippet with distance-from-center math
- Add radial-specific uniform layout (center, radius, stops)
- Add tile mode handling for radial space (Clamp, Repeat, Mirror)
- Produce radial gradient rendering fixture dumps

## Non-Goals

- No TwoPointConical gradient
- No gradient dithering

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_GRADIENT_STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source src/shaders/graphite/GradientShader.cpp radial; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct RadialGradientUniforms { center: vec2f, radius: f32, numStops: u32, stops: array<vec4f, 8>, tileMode: u32 }
```

## Acceptance Criteria

- [ ] RadialGradient WGSL compiles and produces correct radial color interpolation
- [ ] All three tile modes render correctly for radial gradients
- [ ] Gradient boundary precision matches CPU reference within tolerance

## Required Evidence

- RadialGradient GPU rendering fixture dump for each tile mode
- Uniform layout validation report
- Radial precision comparison (CPU reference vs GPU)

## Fallback / Refusal Behavior

Radial gradient rendering failure emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m14.radial-gradient`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*RadialGradient*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M14`
- `area:materials-wgsl`
