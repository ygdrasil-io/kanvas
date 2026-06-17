---
id: KGPU-M11-008
title: "Add registered runtime-effect execution lane"
status: proposed
milestone: M11
priority: P1
owner_area: runtime-effects
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M7-001, KGPU-M11-001, KGPU-M11-002]
legacy_gate: "runtime-effect legacy"
---

# KGPU-M11-008 - Add registered runtime-effect execution lane

## PM Note

Ce ticket exécute seulement des runtime effects enregistrés, jamais du SkSL ou
du WGSL arbitraire.

## Problem

KGPU-M7-001 accepts descriptor-route evidence for a registered runtime effect,
but it explicitly does not claim adapter-backed execution, readback, children,
blenders, filters, arbitrary source, or live editing. The gap is an execution
lane that consumes registered descriptors, parser-validated WGSL, CPU oracle
evidence, payload materialization, and pipeline/cache resources.

## Scope

- Execute one registered material runtime-effect descriptor through the
  material dictionary, WGSL module cache, payload upload, and bind group lane.
- Require descriptor ID/version, registry generation, uniform schema, WGSL
  reflection, CPU oracle, and route placement to match before materialization.
- Preserve refusals for unregistered descriptors, arbitrary SkSL/WGSL source,
  children, blenders, filters, and live editing unless a future ticket owns
  those lanes.

## Non-Goals

- Do not compile SkSL, rebuild SkSL IR/VM, or accept arbitrary source strings.
- Do not add runtime-effect children, filters, blenders, primitive blenders, or
  clip shaders.
- Do not treat CPU oracle success as GPU support without readback evidence.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`
- `.upstream/specs/gpu-renderer/16-material-dictionary-and-snippet-registry.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`
- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`
- `.upstream/specs/wgsl4k-evolution/01-validation-reflection-contract.md`

## Graphite Algorithm References

- [`GFX-RUNTIME-EFFECT-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - Reference registered runtime-effect snippet identity and uniform gathering.
- [`GFX-RUNTIME-EFFECT-PREAMBLE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-preamble) - Study callback-based lowering vocabulary without SkSL support.
- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - Reference key-tree validation boundaries.
- Boundary: references do not authorize arbitrary runtime-effect source support.

## Design Sketch

```kotlin
data class GPURuntimeEffectExecutionPlan(
    val descriptorId: String,
    val registryGeneration: String,
    val wgslModuleKey: String,
    val payloadSlot: String,
    val pipelineKey: String,
)
```

## Acceptance Criteria

- [ ] Registered descriptor execution validates descriptor ID/version, registry
      generation, route placement, WGSL reflection, and uniform schema before
      pipeline materialization.
- [ ] Runtime-effect uniform values materialize through payload upload and bind
      group creation without entering material or pipeline keys.
- [ ] WGSL module and pipeline cache entries include descriptor/WGSL identity and
      reject stale registry generations.
- [ ] GPU readback/reference diff evidence compares against the registered CPU
      oracle before any support promotion.
- [ ] Unknown descriptors, arbitrary source, child slots, filters, blenders, and
      reflection/schema mismatches refuse stably.

## Required Evidence

- Runtime-effect execution plan dump.
- Registry, WGSL reflection, payload, pipeline, and bind group dumps.
- CPU oracle and GPU readback diff evidence or explicit skipped-readback reason.
- Refusal fixtures for unregistered, arbitrary source, child, and mismatch
  cases.

## Fallback / Refusal Behavior

Unsupported runtime effects refuse. The renderer must not compile SkSL,
execute arbitrary WGSL strings, or use CPU oracle output as product rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect.registered.execution`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no until descriptor execution, readback, and review
  evidence are accepted.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Planning-only continuation of KGPU-M7-001 from descriptor gate to
  registered execution lane.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:runtime-effects`
