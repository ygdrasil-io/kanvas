---
id: KGPU-M5-001
title: "Add saveLayer isolated target route"
status: done
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

## Graphite Algorithm References

- [`GFX-SPECIAL-IMAGE-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-image-layer) - source [SpecialImage_Graphite.cpp:20](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/SpecialImage_Graphite.cpp:20); Study subset-backed special image wrapping for isolated intermediates.
- [`GFX-SPECIAL-DRAW`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-draw) - source [Device.cpp:2180](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2180); Reference snap/draw special image behavior for saveLayer ownership.
- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Use pass extraction and task insertion as the isolated target lifecycle model.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128); Reference target preparation and pass replay for saveLayer route evidence.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class SaveLayerEvidence(val layerPlan: String, val compositePlan: String)
```

## Acceptance Criteria

- [x] Offscreen target ownership is dumpable.
- [x] Restore composite route is explicit.
- [x] Unsupported layer variants refuse.

## Required Evidence

- Layer plan, target, resource, pass, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported layers refuse; no CPU-rendered full-layer texture.

## Dashboard Impact

- Expected row: `gpu-renderer.savelayer.isolated-target`
- Expected classification: `TargetNative`
- Claim promotion allowed: no; this ticket closes contract-gate evidence only.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Independent review accepted the contract-gate evidence after mandatory
  target-usage enforcement and refusal-matrix coverage were completed.
  `GPUSaveLayerIsolatedTargetPlanner` adds contract-gate evidence for
  the `gpu-renderer.savelayer.isolated-target` row with dumpable target
  descriptor, bounds, clear/load/store, resource ownership, task ordering,
  restore composite, and unsupported-variant refusals. The gate records
  `routeKind=GPUNative`, `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false`; it does not claim
  adapter-backed native saveLayer execution or product activation.
- Evidence: `SaveLayerIsolatedTargetGateTest` plus
  `reports/gpu-renderer/2026-06-17-m5-001-savelayer-isolated-target-gate.md`.
- Non-claim: no native saveLayer support, no adapter-backed offscreen target
  allocation, no CPU-rendered full-layer texture fallback, no arbitrary layer
  stacks, no filters, and no destination-read support.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:layers`
