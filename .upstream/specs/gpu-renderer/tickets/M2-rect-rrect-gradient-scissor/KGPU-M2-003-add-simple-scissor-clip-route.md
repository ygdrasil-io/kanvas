---
id: KGPU-M2-003
title: "Add simple scissor clip route"
status: proposed
milestone: M2
priority: P0
owner_area: clips-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-001]
legacy_gate: null
---

# KGPU-M2-003 - Add simple scissor clip route

## PM Note

Ce ticket borne le premier support clip à un scissor device-rect explicite.

## Problem

The first slice accepts wide-open, empty, and device-rect clips, but needs
dedicated `GPUClipPlan` evidence and complex-clip refusals.

## Scope

- Add scissor clip planning and pass integration for rect/rrect.
- Add empty clip cull/discard evidence.
- Refuse complex, non-device, stencil, mask, and shader clip cases.

## Non-Goals

- Do not add rrect/path clips.
- Do not add clip atlas or stencil routes.

## Spec Sources

- `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`
- `.upstream/specs/gpu-renderer/30-coordinate-transform-bounds-policy.md`

## Design Sketch

```kotlin
data class GPUScissorClipEvidence(val clipPlanDump: String, val passDump: String)
```

## Acceptance Criteria

- [ ] Wide-open, empty, and device-rect scissor cases are covered.
- [ ] Complex clips refuse with stable diagnostics.
- [ ] Clip facts do not enter material identity.

## Required Evidence

- Clip plan, bounds, pass, and telemetry dumps.
- Refusal fixtures for unsupported clip stacks.

## Fallback / Refusal Behavior

Unsupported clips refuse or remain on legacy policy after explicit activation
decision; no hidden CPU-rendered clipped texture is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.scissor-clip`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Simple scissor only.

## Linear Labels

- `gpu-renderer`
- `milestone:M2`
- `area:clips`
