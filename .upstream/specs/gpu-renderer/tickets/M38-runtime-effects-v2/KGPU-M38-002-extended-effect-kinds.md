---
id: KGPU-M38-002
title: "Extended effect kinds"
status: done
milestone: M38
priority: P0
owner_area: runtimeeffects
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M38-002 - Extended effect kinds

## PM Note

Le registre d'effets runtime s'étend de Shader/ColorFilter à Blender,
ClipShader, et Compute.

## Problem

The current runtime effect registry only covers `Shader` and `ColorFilter`
kinds. Paint blending, clip shaders, and compute dispatch each have distinct
WGSL entry-point signatures and route requirements that demand first-class
kind contracts.

## Scope

- `GPURuntimeEffectKind.Blender`: `fn blend(src: float4, dst: float4) -> float4`
  with premultiplied inputs and premultiplied output.
- `GPURuntimeEffectKind.ClipShader`: `fn clipShader(coords: float2, uniformBlock: U) -> float`
  returning coverage value in [0, 1].
- `GPURuntimeEffectKind.Compute`: compute shader dispatch with storage buffer
  I/O, `@compute @workgroup_size(...)` entry point.
- Kind-specific contracts:
  - Material (existing): `coords → unpremul result`.
  - Blender: `src + dst → premul result`.
  - ClipShader: `coords + uniforms → coverage float`.
  - Compute: `@compute dispatch → storage buffer output`.
- Kind validation: WGSL entry-point name, parameter types, and return type must
  match the registered kind's contract.
- Route placement: Blender rides the blend pipeline stage; ClipShader rides the
  clip-cover route; Compute rides the compute-dispatch route.

## Non-Goals

- Do not add PixelLocal, tile-shading, or subpass compute kinds.
- Do not add multi-entry-point shaders.
- Do not change existing Shader/ColorFilter kind contracts.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`

## Graphite Algorithm References

- [`GFX-RUNTIME-EFFECT-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - source [KeyHelpers.cpp:1387](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:1387); Register or find a runtime-effect snippet, stash user-defined effects in a transient dictionary, gather transformed uniforms.
- [`GFX-BLEND-KEYING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blend-keying) - source [KeyHelpers.cpp:2593](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:2593); Reduce coefficient blends to constants, group HSL advanced blends into a shared snippet, and leave other advanced modes as fixed blend snippets.
- [`GFX-RENDERSTEP-MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83); Decompose one high-level renderer into ordered RenderSteps that can be batched across draws if stencil and painter-order dependencies remain valid.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
enum class GPURuntimeEffectKind {
    Material,
    Blender,
    ClipShader,
    Compute,
}

data class GPURuntimeEffectKindContract(
    val kind: GPURuntimeEffectKind,
    val entryPointSignature: WGSEntryPointSignature,
    val routePlacement: GPURenderRouteStage,
)

interface GPURuntimeEffectKindValidator {
    fun validate(
        effect: GPURuntimeEffectDescriptor,
        wgslModule: WgslModule,
    ): GPURuntimeEffectKindResult
}
```

## Acceptance Criteria

- [ ] Register a Blender effect with GPU evidence (composed src+dst premul
      output).
- [ ] Register a ClipShader effect with correct coverage output (CPU oracle
      parity).
- [ ] Register a Compute effect with storage buffer I/O and GPU evidence.
- [ ] Kind mismatch (e.g., Blender entry point on a Material-registered effect)
      → `unsupported.runtime_effect.kind_mismatch` diagnostic.

## Required Evidence

- GPU dump showing Blender premul output for known src+dst inputs.
- Coverage float output from ClipShader matching CPU oracle within tolerance.
- Compute shader output buffer validated against CPU reference.
- Diagnostic dump for kind-mismatch refusal.

## Fallback / Refusal Behavior

- Unregistered kind → `unsupported.runtime_effect.kind_not_registered`.
- Kind mismatch → `unsupported.runtime_effect.kind_mismatch`.
- WGSL entry-point validation failure → refusal with stable diagnostic.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect.extended-kinds`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*EffectKinds*'
```

## Status Notes

- `proposed`: Initial ticket.
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted — independent review accepted.

## Linear Labels

- `gpu-renderer`
- `milestone:M38`
- `area:runtimeeffects`
