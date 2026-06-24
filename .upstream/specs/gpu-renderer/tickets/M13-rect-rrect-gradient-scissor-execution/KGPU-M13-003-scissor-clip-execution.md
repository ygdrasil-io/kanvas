---
id: KGPU-M13-003
title: "Add scissor clip execution: device-rect clip -> WebGPU setScissor + uniform"
status: review
milestone: M13
priority: P0
owner_area: clips-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M13-003 - Add scissor clip execution: device-rect clip -> WebGPU setScissor + uniform

## PM Note

Le scissor clip est le mécanisme de clipping le plus performant sur GPU. Sans lui, chaque draw call doit utiliser le stencil ou le shader discard, ce qui tue les performances.

## Problem

Device-rectangle scissor clips need to be translated into WebGPU setScissor calls with proper rect snapping and uniform updates. Without scissor execution, all clips would require expensive stencil or discard paths.

## Scope

- Add scissor clip execution with WebGPU setScissor calls
- Add device-rect snapping to pixel boundaries
- Add scissor uniform update for shader-based clipping awareness
- Produce scissor clip rendering fixture dumps

## Non-Goals

- No complex clip stacks beyond single device-rect
- No clip simplification or merging at this stage

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWCONTEXT_RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-record) - source src/gpu/graphite/DrawContext.cpp:155; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class ScissorClipExecution(val deviceRect: Rect, val setScissor: WebGPUCommand, val uniformUpdate: UniformUpdate)
```

## Acceptance Criteria

- [ ] Scissor rect clipping produces correct visual results on GPU
- [ ] Scissor snapping does not introduce visible artifacts
- [ ] Scissor uniform is correctly updated for downstream shaders

## Required Evidence

- Scissor clip GPU rendering fixture dump
- Scissor rect snapping transcript
- Performance comparison: scissor vs no-scissor draw calls

## Fallback / Refusal Behavior

Scissor clip failure falls back to full-rect draw with stable diagnostic; route remains disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m13.scissor-clip-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ScissorClip*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M13`
- `area:clips-passes`
