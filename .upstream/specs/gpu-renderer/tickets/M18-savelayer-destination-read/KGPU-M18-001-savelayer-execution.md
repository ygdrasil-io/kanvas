---
id: KGPU-M18-001
title: "Add SaveLayer execution: offscreen target allocation + clear/load/store"
status: proposed
milestone: M18
priority: P0
owner_area: layers-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M18-001 - Add SaveLayer execution: offscreen target allocation + clear/load/store

## PM Note

SaveLayer est la brique de base pour la composition: ombres, flous, groupes d'opacité. Sans allocation offscreen fiable, tous ces effets sont impossibles.

## Problem

SaveLayer needs offscreen render target allocation with proper clear, load, and store actions. Without this, layer compositing (opacity groups, backdrop filters, blend modes) cannot function.

## Scope

- Add offscreen render target allocation for SaveLayer
- Add clear/load/store actions for layer texture lifecycle
- Add layer bounds tracking and scissor
- Produce SaveLayer execution trace

## Non-Goals

- No layer elision or optimization
- No framebuffer-fetch
- No f16 or HDR layer formats

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RENDERPASS_TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source src/gpu/graphite/render/RenderPassTask.cpp:128; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class SaveLayerExecution(val offscreenTarget: GpuTexture, val clearAction: LoadOp, val storeAction: StoreOp)
```

## Acceptance Criteria

- [ ] SaveLayer allocates offscreen target with correct dimensions
- [ ] Clear action initializes layer to transparent correctly
- [ ] Store action preserves layer content for restore

## Required Evidence

- SaveLayer offscreen target allocation trace
- Layer clear/load/store action transcript
- Layer bounds scissor validation

## Fallback / Refusal Behavior

SaveLayer allocation failure emits stable diagnostic; layer compositing refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m18.savelayer-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SaveLayer*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M18`
- `area:layers-passes`
