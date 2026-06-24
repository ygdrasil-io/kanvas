---
id: KGPU-M18-002
title: "Add SaveLayer restore: composite child texture into parent with blend"
status: proposed
milestone: M18
priority: P0
owner_area: layers-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M18-001]
legacy_gate: null
---

# KGPU-M18-002 - Add SaveLayer restore: composite child texture into parent with blend

## PM Note

Le restore de SaveLayer doit composer la texture enfant dans le parent avec le bon mode de fusion. Sans cela, les groupes d'opacité et les effets de calque sont brisés.

## Problem

After drawing into a SaveLayer, the offscreen texture must be composited back into the parent surface with the correct blend mode and opacity. Without restore compositing, SaveLayer is a black hole.

## Scope

- Add layer composite render step: child texture -> parent surface
- Add blend mode application during composite
- Add opacity modulation during composite
- Produce SaveLayer restore rendering fixture dumps

## Non-Goals

- No backdrop filter support
- No advanced blend modes beyond srcOver at this stage

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWLIST_LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-layer) - source src/gpu/graphite/render/LayerCompositeRenderStep.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class LayerComposite(val childTexture: GpuTexture, val blendMode: BlendMode, val opacity: Float)
```

## Acceptance Criteria

- [ ] SaveLayer restore composites child texture correctly into parent
- [ ] Opacity is correctly applied during composite
- [ ] SrcOver blend produces visually correct transparency

## Required Evidence

- SaveLayer restore GPU rendering fixture dump
- Opacity composite test (0%, 50%, 100% opacity layers)
- Blend mode composite test dumps

## Fallback / Refusal Behavior

Layer composite failure emits stable diagnostic; parent surface left unchanged.

## Dashboard Impact

- Expected row: `gpu-renderer.m18.savelayer-restore`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LayerComposite*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M18`
- `area:layers-passes`
