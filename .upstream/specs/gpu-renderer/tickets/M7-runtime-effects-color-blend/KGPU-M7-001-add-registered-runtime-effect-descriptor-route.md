---
id: KGPU-M7-001
title: "Add registered runtime-effect descriptor route"
status: proposed
milestone: M7
priority: P0
owner_area: runtime-effects
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-002]
legacy_gate: "runtime-effect legacy"
---

# KGPU-M7-001 - Add registered runtime-effect descriptor route

## PM Note

Ce ticket autorise seulement les effets enregistrés avec contrat Kotlin/WGSL.

## Problem

Runtime effects must resolve through descriptors, CPU oracle, WGSL plan, and
route contract, not arbitrary SkSL or source strings.

## Scope

- Add one descriptor-backed material route.
- Link uniform schema, CPU oracle, WGSL reflection, route, and refusal evidence.

## Non-Goals

- Do not compile SkSL or arbitrary WGSL.
- Do not support children or blenders yet.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`

## Design Sketch

```kotlin
data class RuntimeEffectRouteEvidence(val descriptorId: String, val wgslPlan: String)
```

## Acceptance Criteria

- [ ] Descriptor ID/version and uniform schema are dumpable.
- [ ] CPU oracle and WGSL evidence are linked.
- [ ] Unregistered descriptors refuse.

## Required Evidence

- Registry snapshot, oracle, WGSL, route, and refusal dumps.

## Fallback / Refusal Behavior

Unregistered or unsupported runtime effects refuse; no source-string support.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect.registered`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Descriptor-backed only.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:runtime-effects`
