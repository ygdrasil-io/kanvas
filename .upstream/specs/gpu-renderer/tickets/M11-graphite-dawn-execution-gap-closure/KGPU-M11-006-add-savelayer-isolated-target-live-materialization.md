---
id: KGPU-M11-006
title: "Add saveLayer isolated target live materialization"
status: done
milestone: M11
priority: P1
owner_area: layers-resources
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-001, KGPU-M11-003]
legacy_gate: "saveLayer legacy"
---

# KGPU-M11-006 - Add saveLayer isolated target live materialization

## PM Note

Ce ticket transforme le plan saveLayer isolé en cible offscreen vivante et
composite contrôlé.

## Problem

KGPU-M5-001 closes saveLayer isolated-target contract evidence, but it remains
non-promoted and `materialized=false`. The missing execution work is allocating
the offscreen target, initializing it, rendering children into it, and
compositing it back to the parent with explicit resources and ordering.

## Scope

- Materialize `GPULayerTargetPlan`, initialization, source, and composite
  resources through `GPUResourceProvider`.
- Emit pass commands for target allocation/clear/load/store, child pass
  boundaries, and restore composite where the bounded route is accepted.
- Validate target usage flags, target generation, bounds, layer budget, and
  active parent/destination-read constraints.
- Keep filters, arbitrary layer stacks, and destination-read variants behind
  their own accepted plans.

## Non-Goals

- Do not add arbitrary saveLayer/filter DAG support.
- Do not CPU-render a full layer into a texture.
- Do not activate the legacy saveLayer route or root PM support.

## Spec Sources

- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-SPECIAL-IMAGE-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-image-layer) - Reference isolated intermediate wrapping.
- [`GFX-SPECIAL-DRAW`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-draw) - Study drawing snapped special images.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - Reference target preparation and pass replay.
- Boundary: Kanvas does not copy Skia layer internals or Graphite task classes.

## Design Sketch

```kotlin
data class GPUSaveLayerMaterializationPlan(
    val layerTargetRef: String,
    val initializationCommand: String,
    val childPassScope: String,
    val compositeCommand: String,
)
```

## Acceptance Criteria

- [x] Accepted isolated-layer plans allocate or reuse a provider-owned offscreen
      target with render-attachment, texture-binding, copy, and store facts as
      required by the plan.
- [x] Initialization, child rendering, optional source sampling, and restore
      composite appear as ordered pass-command evidence.
- [x] Layer target lifetime, generation, bounds, format, sample count, and
      budget are dumpable.
- [x] Unsupported variants, missing target usage, read/write aliasing,
      over-budget targets, and illegal parent sampling refuse stably.
- [x] Adapter-backed evidence includes at least one isolated target render and
      composite trace or an explicit skipped-readback reason.

## Required Evidence

- Layer target materialization and lifetime dump.
- Ordered command stream for initialize, child render, and restore composite.
- Refusal fixtures for unsupported variants, missing usage, and budget overflow.
- Non-claim note preserving no product activation and no CPU layer fallback.

## Fallback / Refusal Behavior

Unsupported layers refuse. The renderer must not draw child content directly to
the parent unless an accepted elision proof exists, and must not CPU-render the
layer as a compatibility texture.

## Dashboard Impact

- Expected row: `gpu-renderer.savelayer.live-materialization`
- Expected classification: `TargetNative`
- Claim promotion allowed: no without adapter-backed evidence and review.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerLiveMaterializationTest
rtk git diff --check
```

## Status Notes

- `done` (2026-06-17): Added contract live materialization for accepted
  saveLayer isolated-target lanes. Evidence includes provider-owned layer
  target texture, render-target, texture-view, and sampler operands; ordered
  target prepare, clear, child render, and restore composite command dumps;
  stale generation, usage, budget, bounds, format, sample count, allocation,
  parent aliasing, active-attachment, and gate-refusal diagnostics; and explicit
  skipped readback evidence. Product activation remains false.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:layers`
