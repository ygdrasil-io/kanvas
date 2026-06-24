---
id: KGPU-M14-002
title: "Add SweepGradient WGSL: atan2 angle interpolation + tile mode"
status: done
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

# KGPU-M14-002 - Add SweepGradient WGSL: atan2 angle interpolation + tile mode

## PM Note

Le dégradé conique (sweep) utilise atan2, une fonction coûteuse en GPU. La validation de performance et de précision est essentielle avant activation.

## Problem

Sweep gradients need atan2-based angle interpolation in WGSL, which is computationally expensive and numerically sensitive. Incorrect angle normalization causes visible seams at the 0/360-degree boundary.

## Scope

- Add SweepGradient WGSL snippet with atan2 angle interpolation
- Add sweep-specific uniform layout (center, startAngle, stops)
- Add tile mode handling for angular space
- Produce sweep gradient rendering fixture dumps

## Non-Goals

- No TwoPointConical gradient
- No gradient dithering

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_GRADIENT_STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source src/shaders/graphite/GradientShader.cpp sweep; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct SweepGradientUniforms { center: vec2f, startAngle: f32, endAngle: f32, numStops: u32, stops: array<vec4f, 8>, tileMode: u32 }
```

## Acceptance Criteria

- [ ] SweepGradient WGSL compiles and produces correct angular color interpolation
- [ ] No visible seam at 0/360-degree boundary
- [ ] All tile modes render correctly for sweep gradients

## Required Evidence

- SweepGradient GPU rendering fixture dump for each tile mode
- Angle boundary seam analysis (magnified pixel inspection)
- atan2 precision comparison (CPU reference vs GPU)

## Fallback / Refusal Behavior

Sweep gradient rendering failure or seam artifact emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m14.sweep-gradient`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SweepGradient*'
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: Implemented in KGPU-M14. SweepGradientSnippet.kt with compute_t_sweep (atan2), sample_stops_at, clamp-only entry point. Material lowering with dictionary, expandOrRefuse, planSweepGradient (clamp-only, ≤16 stops). Product flag sweepGradientEnabled=true.

## Linear Labels

- `gpu-renderer`
- `milestone:M14`
- `area:materials-wgsl`
