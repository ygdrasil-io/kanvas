---
id: KGPU-M38-001
title: "Live parameter editing V2"
status: proposed
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

# KGPU-M38-001 - Live parameter editing V2

## PM Note

L'édition live des paramètres d'effets runtime sans recompilation de shader.

## Problem

Runtime effects today require a full pipeline recompilation when uniform
parameters change. Dynamic preview, animation, and tooling workflows need
live parameter mutation that updates only the changed bytes in the GPU
uniform buffer without tearing down or rebuilding the pipeline.

## Scope

- `GPURuntimeEffectLiveParameterSchema`: parameter ID, display name, type
  (float/float2/float3/float4/int/color), default value, min/max, step.
- `GPURuntimeEffectLiveParameterBinding`: maps each parameter to a uniform
  byte offset within the effect's uniform block.
- `GPURuntimeEffectLiveState`: current parameter values, dirty flags per
  parameter, generation counter for change detection.
- `GPURuntimeEffectLiveControlPlan`: set parameter by ID, animate with
  keyframes, reset to defaults, serialize/deserialize preset.
- Descriptor extension: `GPURuntimeEffectDescriptor` gains optional
  `liveEdit: GPURuntimeEffectLiveParameterSchema?` field.
- Validate that all live-editable parameters have matching WGSL uniform
  declarations in the effect's shader module.

## Non-Goals

- Do not support live editing of struct fields or array elements.
- Do not support live addition or removal of parameters after registration.
- Do not provide a UI widget binding layer (only data contracts).
- Do not support live editing for effects without live-edit metadata.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`

## Graphite Algorithm References

- `GFX-RUNTIMEEFFECT-UNIFORM` — Study how SkRuntimeEffect uniform data is
  copied into SkSL uniforms for parameter mutation patterns.
- `GFX-RUNTIMEEFFECT-MAKE` — Study SkRuntimeEffect::makeShader with uniform
  data for parameter binding and offset layout.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPURuntimeEffectLiveParameterSchema(
    val parameters: List<GPURuntimeEffectLiveParameter>,
) {
    data class GPURuntimeEffectLiveParameter(
        val id: String,
        val displayName: String,
        val type: GPURuntimeEffectLiveParameterType,
        val default: GPURuntimeEffectLiveValue,
        val min: GPURuntimeEffectLiveValue?,
        val max: GPURuntimeEffectLiveValue?,
        val step: GPURuntimeEffectLiveValue?,
    )
}

data class GPURuntimeEffectLiveParameterBinding(
    val parameterId: String,
    val uniformOffsetBytes: Int,
)

data class GPURuntimeEffectLiveState(
    val values: Map<String, GPURuntimeEffectLiveValue>,
    val dirtyFlags: Set<String>,
    val generationCounter: ULong,
)
```

## Acceptance Criteria

- [ ] Register 3+ live parameters with correct GPU output without pipeline
      recompilation.
- [ ] Dirty-tracking ensures only changed bytes are re-uploaded to the GPU.
- [ ] Preset round-trip (serialize → deserialize → apply) produces identical
      GPU output.
- [ ] Effect without live-edit metadata → refusal with stable diagnostic.

## Required Evidence

- GPU dump showing uniform buffer contents before and after live parameter
  mutation without pipeline rebuild.
- Diagnostic dump for unregistered parameter refusal.
- Preset round-trip fixture with checksum-matched GPU output.

## Fallback / Refusal Behavior

- Unregistered parameter → `unsupported.runtime_effect.live_parameter_unregistered`.
- Type mismatch → `unsupported.runtime_effect.live_parameter_type_mismatch`.
- Missing live-edit metadata → effect registered but live-editing refused.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect.live-editing`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LiveEdit*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M38`
- `area:runtimeeffects`
