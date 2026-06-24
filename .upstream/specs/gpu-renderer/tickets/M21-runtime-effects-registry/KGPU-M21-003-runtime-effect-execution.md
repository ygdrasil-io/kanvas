---
id: KGPU-M21-003
title: "Add RuntimeEffect execution lane: descriptor lookup -> WGSL snippet -> GPU submit"
status: done
milestone: M21
priority: P0
owner_area: runtime-effects
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M21-001, KGPU-M21-002]
legacy_gate: null
---

# KGPU-M21-003 - Add RuntimeEffect execution lane: descriptor lookup -> WGSL snippet -> GPU submit

## PM Note

La lane d'exécution des effets runtime est le chemin critique: lookup du descripteur, injection du snippet WGSL, soumission GPU. Chaque étape doit être diagnostiquée.

## Problem

Registered runtime effects need an execution lane that performs descriptor lookup, injects the WGSL snippet into the shader, binds uniforms, and submits the draw to GPU. Without this, registered effects cannot be rendered.

## Scope

- Add runtime effect execution lane: descriptor lookup by effect ID
- Add WGSL snippet injection into pipeline shader
- Add uniform binding from reflected layout
- Produce runtime effect execution trace

## Non-Goals

- No effect compilation or code generation at runtime
- No unregistered effect execution

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RUNTIME_EFFECT_KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - source src/gpu/graphite/DrawContext.cpp runtimeEffect; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class RuntimeEffectExecutor {{\n  fun execute(effectId: String, uniforms: UniformData): DrawCommand?;\n}}\n// Lookup descriptor -> validate uniforms -> inject WGSL -> submit
```

## Acceptance Criteria

- [ ] Registered effects are found by descriptor lookup during execution
- [ ] WGSL snippet is correctly injected into pipeline shader
- [ ] Uniforms are correctly bound from reflected layout
- [ ] Unregistered effect IDs emit stable diagnostic

## Required Evidence

- Runtime effect execution trace for SimpleRT, LinearGradientRT, SpiralRT
- Descriptor lookup success/failure diagnostic transcript
- WGSL injection and uniform binding validation

## Fallback / Refusal Behavior

Execution lane failures emit stable diagnostic; effect draw refused with clear error.

## Dashboard Impact

- Expected row: `gpu-renderer.m21.runtime-effect-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*RuntimeEffectExec*'
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
