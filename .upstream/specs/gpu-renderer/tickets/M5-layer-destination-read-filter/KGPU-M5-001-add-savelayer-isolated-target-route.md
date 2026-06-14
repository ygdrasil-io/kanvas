---
id: KGPU-M5-001
title: "Add saveLayer isolated target route"
status: proposed
milestone: M5
priority: P0
owner_area: layers-resources
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-003, KGPU-M4-002]
legacy_gate: "saveLayer legacy"
---

# KGPU-M5-001 - Add saveLayer isolated target route

## PM Note

Ce ticket introduit un layer GPU isolé sans fallback CPU pleine couche.

## Problem

saveLayer requires explicit offscreen target ownership, initialization, source
rendering, and restore composite evidence.

## Scope

- Add `GPULayerPlan` and isolated target route evidence.
- Add target descriptor, load/store, bounds, and composite dumps.

## Non-Goals

- Do not add arbitrary layer stacks or filters.
- Do not add destination reads beyond accepted strategy.

## Spec Sources

- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`

## Design Sketch

```kotlin
data class SaveLayerEvidence(val layerPlan: String, val compositePlan: String)
```

## Acceptance Criteria

- [ ] Offscreen target ownership is dumpable.
- [ ] Restore composite route is explicit.
- [ ] Unsupported layer variants refuse.

## Required Evidence

- Layer plan, target, resource, pass, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported layers refuse; no CPU-rendered full-layer texture.

## Dashboard Impact

- Expected row: `gpu-renderer.savelayer.isolated-target`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: First isolated layer route.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:layers`
