---
id: KGPU-M0-003
title: "Review R2 WGSL module and ABI evidence"
status: review
milestone: M0
priority: P0
owner_area: wgsl-materials
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M0-002]
legacy_gate: null
---

# KGPU-M0-003 - Review R2 WGSL module and ABI evidence

## PM Note

Ce ticket vérifie que le shader WGSL est validé comme module complet, pas comme
fragment isolé.

## Problem

R2 is reported as merged, but `WGSLModule`, reflection, binding layout, and
packing evidence need independent review before acceptance.

## Scope

- Review solid-source snippet metadata and module assembly.
- Review parser/reflection and Kotlin packing ABI evidence.
- Confirm unsupported parser, reflection, layout, and facade-limit cases refuse.

## Non-Goals

- Do not accept arbitrary WGSL strings.
- Do not claim route support from fragment-only validation.

## Spec Sources

- `.upstream/specs/gpu-renderer/03-material-key-wgsl.md`
- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Design Sketch

```kotlin
data class WGSLAbiReview(val moduleHash: String, val reflectionDump: String, val packingDump: String)
```

## Acceptance Criteria

- [ ] Module hash and reflection dump are linked.
- [ ] Packing layout and byte-size evidence are linked.
- [ ] Rejected module fixtures are linked.

## Required Evidence

- `WGSLModuleAbiTest` output.
- WGSL reflection and packing dumps.
- R2 progress row.

## Fallback / Refusal Behavior

Parser ambiguity or surprising `wgsl4k` behavior must stop promotion and be
captured as evidence instead of hidden by workaround code.

## Dashboard Impact

- Expected row: `gpu-renderer.r2.wgsl-abi-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAbiTest
rtk git diff --check
```

## Status Notes

- `review`: R2 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:wgsl`
