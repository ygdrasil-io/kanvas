---
id: KGPU-M2-003
title: "Add simple scissor clip route"
status: done
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

## Graphite Algorithm References

- [`GFX-SCISSOR-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-scissor-snap) - source [ClipStack.cpp:308](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ClipStack.cpp:308); Reference coarse scissor bounds that trade slight overdraw for fewer state changes.
- [`GFX-RENDERSTEP-SCISSOR`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-scissor) - source [Renderer.cpp:49](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.cpp:49); Study per-step scissor elision and inverse-fill handling.
- [`GFX-CLIP-SIMPLIFY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-clip-simplify) - source [ClipStack.cpp:348](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ClipStack.cpp:348); Use shape-aware clip simplification as the correctness model for bounded clips.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Validate that scissor state changes are visible in batching evidence.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

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

- `done`: `M2SimpleSceneEvidenceTest` adds a deterministic
  `GPUClipPlan`-backed device-rect scissor dump for
  `m2-simple-device-scissor` and stable `unsupported.clip.non_device_rect`
  refusal evidence. Product activation remains false. Independent review
  `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9` accepted the evidence as
  scissor-planning proof only and confirmed no complex clip, stencil, mask,
  shader clip, or hidden CPU fallback support is claimed.

## Linear Labels

- `gpu-renderer`
- `milestone:M2`
- `area:clips`
