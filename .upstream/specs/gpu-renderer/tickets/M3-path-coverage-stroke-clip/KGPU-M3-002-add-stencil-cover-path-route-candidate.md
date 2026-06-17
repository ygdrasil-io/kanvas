---
id: KGPU-M3-002
title: "Add stencil-cover path route candidate"
status: done
milestone: M3
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-001]
legacy_gate: "path fill legacy"
---

# KGPU-M3-002 - Add stencil-cover path route candidate

## PM Note

Ce ticket explore un chemin path natif via stencil-cover, avec preuves et refus.

## Problem

Stencil-cover can be native, but must prove ordering, pass state, coverage, and
failure diagnostics before promotion.

## Scope

- Add `GPUStencilCoverPlan` evidence for one bounded path class.
- Add pass/resource/telemetry dumps and unsupported-case refusals.

## Non-Goals

- No general tessellation or compute path pipeline.
- No stroke support.

## Spec Sources

- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`

## Graphite Algorithm References

- [`GFX-MSAA-PATH-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-msaa-path-heuristics) - source [Device.cpp:2040](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2040); Study convex/wedge/curve path routing for a stencil-cover candidate.
- [`GFX-TESSELLATE-WEDGES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-wedges) - source [TessellateWedgesRenderStep.cpp:82](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateWedgesRenderStep.cpp:82); Reference wedge patch emission for small or convex path fills.
- [`GFX-TESSELLATE-CURVES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-curves) - source [TessellateCurvesRenderStep.cpp:80](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateCurvesRenderStep.cpp:80); Reference curve patch stencil tessellation for bounded complex paths.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Use disjoint stencil and painter-order constraints as acceptance evidence.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class StencilCoverEvidence(val stencilPlan: String, val coverPass: String)
```

## Acceptance Criteria

- [x] Stencil and cover ordering is dumpable.
- [x] Unsupported fill/clip/target cases refuse.
- [x] GPU evidence or explicit skipped reason is linked.
- [x] Any prepared-path continuation remains owned by `KGPU-M3-001` and is not
      promoted by this `GPUNative` candidate ticket.

## Required Evidence

- Stencil-cover route dumps.
- Pass/resource/readback evidence.
- Refusal diagnostics.

## Fallback / Refusal Behavior

If stencil-cover cannot preserve semantics, this `GPUNative` candidate refuses.
Any prepared-path continuation remains under `KGPU-M3-001` and is not promoted
by this ticket.

## Dashboard Impact

- Expected row: `gpu-renderer.path.stencil-cover`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Closed as a contract-gate candidate without product promotion after
  independent review `019ed2a0-44e5-77d1-bae8-3b8e9926ffca`.
  `GPUStencilCoverGatePlanner` records a dumpable `GPUStencilCoverPlan` only
  when adapter, depth/stencil, sample-count, target, clip, stencil-state,
  producer-before-cover ordering, pass/resource, and readback evidence labels
  are all explicit. Missing native facts refuse with stable skipped-lane
  diagnostics:
  `unsupported.geometry.stencil_cover_unavailable`,
  `unsupported.geometry.stencil_cover_target`,
  `unsupported.clip.stencil_cover`,
  `unsupported.geometry.stencil_cover_ordering_illegal`,
  `unsupported.geometry.stencil_cover_pass_resources_missing`, or
  `unsupported.execution.readback_unavailable`.
- Evidence: `StencilCoverGatePlannerTest` plus
  `reports/gpu-renderer/2026-06-17-m3-002-stencil-cover-gate-contract.md`.
  The path-stencil-cover scene now reports
  `pathStencilCoverTicketStatus=done` and
  `pathStencilCoverClosure=contract-gate-complete-no-product-promotion`.
- Non-claim: no native stencil-cover support, no product activation, no
  release-blocking gate, and no prepared-path continuation support promotion.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:geometry`
