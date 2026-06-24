---
id: KGPU-M21-002
title: "Register LinearGradientRT + SpiralRT: same pattern, validated WGSL"
status: done
milestone: M21
priority: P0
owner_area: runtime-effects
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M21-001]
legacy_gate: null
---

# KGPU-M21-002 - Register LinearGradientRT + SpiralRT: same pattern, validated WGSL

## PM Note

LinearGradientRT et SpiralRT étendent le registre à trois effets. Le pattern est maintenant validé: oracle Kotlin + WGSL parser-validé + uniformes reflétés.

## Problem

After establishing the SimpleRT registration pattern, the runtime effect registry must be expanded with LinearGradientRT and SpiralRT to prove the pattern scales beyond a single effect.

## Scope

- Register LinearGradientRT with Kotlin CPU oracle + validated WGSL
- Register SpiralRT with Kotlin CPU oracle + validated WGSL
- Validate that all three registered effects follow identical registration pattern
- Produce LinearGradientRT and SpiralRT rendering fixture dumps

## Non-Goals

- No arbitrary SkSL compilation
- No user-defined effect registration

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RUNTIME_EFFECT_KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - source src/gpu/graphite/KeyHelpers.cpp runtimeEffect; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// Same pattern as SimpleRT:\nregisterRuntimeEffect(LinearGradientRT) { oracle, validatedWGSL, reflectedUniforms }\nregisterRuntimeEffect(SpiralRT) { oracle, validatedWGSL, reflectedUniforms }
```

## Acceptance Criteria

- [ ] LinearGradientRT and SpiralRT are registered with validated WGSL
- [ ] CPU oracle output matches GPU output for both effects
- [ ] Registration pattern is identical across all three effects

## Required Evidence

- LinearGradientRT GPU rendering fixture dump
- SpiralRT GPU rendering fixture dump
- CPU oracle vs GPU comparison for both effects
- Registration pattern consistency validation

## Fallback / Refusal Behavior

Unregistered effects emit stable diagnostic; effect registry unchanged.

## Dashboard Impact

- Expected row: `gpu-renderer.m21.linear-spiral-rt-registration`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*RuntimeEffect*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- SimpleRT/LinearGradientRT/SpiralRT descriptors, GPURuntimeEffectExecutor
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M21`
- `area:runtime-effects`
