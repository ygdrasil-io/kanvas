---
id: KGPU-M7-002
title: "Add runtime-effect child and source refusal gates"
status: proposed
milestone: M7
priority: P0
owner_area: runtime-effects-validation
claim_impact: RefuseRequired
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M7-001]
legacy_gate: null
---

# KGPU-M7-002 - Add runtime-effect child and source refusal gates

## PM Note

Ce ticket garde les effets enfants et sources libres comme refus explicites.

## Problem

Children, arbitrary source strings, and unsupported placements can be confused
with descriptor support unless refusal gates are explicit.

## Scope

- Add refusal fixtures for arbitrary SkSL/WGSL source, child slots, and unsupported placements.
- Add dashboard categories for refused runtime-effect shapes.

## Non-Goals

- Do not implement child runtime effects.
- Do not add dynamic source compilation.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`
- `.upstream/specs/gpu-renderer/32-target-authority-taxonomy-diagnostics.md`

## Design Sketch

```kotlin
data class RuntimeEffectRefusal(val shape: String, val diagnostic: String)
```

## Acceptance Criteria

- [ ] Unsupported shapes emit canonical diagnostics.
- [ ] Descriptor support cannot imply arbitrary source support.
- [ ] PM output marks refused shapes as `RefuseRequired`.

## Required Evidence

- Refusal matrix and diagnostic dumps.

## Fallback / Refusal Behavior

Unsupported runtime-effect shapes refuse.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect-refusals`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Refusal gates only.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:runtime-effects`
