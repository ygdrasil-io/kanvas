---
id: KGPU-M2-002
title: "Add linear-gradient rect and rrect material route"
status: proposed
milestone: M2
priority: P0
owner_area: materials-wgsl
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-001]
legacy_gate: null
---

# KGPU-M2-002 - Add linear-gradient rect and rrect material route

## PM Note

Ce ticket ajoute le premier matériau non solide tout en gardant les payloads
hors des clés durables.

## Problem

Linear gradients are in the first-slice contract, but require material-source
lowering, stop normalization, payload packing, WGSL ABI, and tile-mode refusals.

## Scope

- Add linear-gradient material source plans for rect/rrect routes.
- Validate complete WGSL modules and payload packing.
- Refuse unsupported tile modes, stop storage, or color interpolation cases.

## Non-Goals

- Do not add radial, sweep, conical, image shader, or runtime-effect materials.
- Do not claim broad color management.

## Spec Sources

- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md`
- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`
- `.upstream/specs/gpu-renderer/14-first-slice-contract.md`

## Design Sketch

```kotlin
data class GPULinearGradientRoute(val stops: Int, val tileMode: String, val wgslModuleHash: String)
```

## Acceptance Criteria

- [ ] Equivalent gradient descriptors produce deterministic material identity.
- [ ] Payload values do not alter durable keys.
- [ ] Unsupported gradient variants refuse with canonical diagnostics.

## Required Evidence

- MaterialKey and WGSL reflection dumps.
- Payload packing and slot dumps.
- Accepted and refused route fixtures.

## Fallback / Refusal Behavior

Unsupported gradients refuse; they must not be approximated silently or rendered
through CPU textures.

## Dashboard Impact

- Expected row: `gpu-renderer.linear-gradient.rect-rrect`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Depends on rrect/rect route fixture stability.

## Linear Labels

- `gpu-renderer`
- `milestone:M2`
- `area:materials`
