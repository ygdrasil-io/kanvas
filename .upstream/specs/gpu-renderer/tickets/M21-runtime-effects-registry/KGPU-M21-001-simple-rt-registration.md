---
id: KGPU-M21-001
title: "Register SimpleRT: Kotlin CPU oracle + parser-validated WGSL + reflected uniforms"
status: done
milestone: M21
priority: P0
owner_area: runtime-effects
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M21-001 - Register SimpleRT: Kotlin CPU oracle + parser-validated WGSL + reflected uniforms

## PM Note

Le registre d'effets runtime commence avec SimpleRT comme preuve de concept. L'oracle CPU Kotlin + le WGSL validé par parser est le pattern pour tous les effets.

## Problem

Runtime effects need a registration system where each effect has a Kotlin CPU oracle, parser-validated WGSL implementation, and reflected uniform layout. SimpleRT establishes this pattern as the first registered effect.

## Scope

- Register SimpleRT effect with Kotlin CPU oracle implementation
- Add parser-validated WGSL for SimpleRT
- Add reflected uniform layout with ABI validation
- Produce SimpleRT effect rendering fixture dumps

## Non-Goals

- No arbitrary SkSL compilation
- No dynamic effect registration beyond hand-written WGSL
- SimpleRT only in this ticket

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RUNTIME_EFFECT_KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - source src/sksl/SkSLRuntimeEffect.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
registerRuntimeEffect(SimpleRT) {{\n  oracle = SimpleRTCpuOracle\n  wgsl = validatedWGSL('simple_rt.wgsl')\n  uniforms = reflectedUniforms\n}}
```

## Acceptance Criteria

- [ ] SimpleRT is registered in the runtime effect dictionary
- [ ] Kotlin CPU oracle produces correct reference output
- [ ] WGSL implementation produces output matching CPU oracle within tolerance

## Required Evidence

- SimpleRT GPU rendering fixture dump
- CPU oracle vs GPU output pixel comparison
- WGSL reflection and ABI validation report

## Fallback / Refusal Behavior

Unregistered or unvalidated runtime effects emit stable diagnostic; effect refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m21.simple-rt-registration`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SimpleRT*'
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
