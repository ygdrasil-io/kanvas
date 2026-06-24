---
id: KGPU-M17-002
title: "Add BitmapRect execution: image rect draw with texture binding + sampler"
status: proposed
milestone: M17
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M17-001]
legacy_gate: null
---

# KGPU-M17-002 - Add BitmapRect execution: image rect draw with texture binding + sampler

## PM Note

BitmapRect est le draw call fondamental pour les images. Il combine la géométrie rectangle avec le binding de texture et le sampler.

## Problem

Image rectangle draws need a dedicated execution path that binds the image texture, configures the sampler, and emits the correct geometry with UV coordinates. Without this, images cannot be drawn at all.

## Scope

- Add BitmapRect draw execution with texture binding and sampler
- Add UV coordinate generation from source/dest rect mapping
- Add image rect bounds clipping
- Produce BitmapRect rendering fixture dumps

## Non-Goals

- No image transform beyond axis-aligned rects
- No sub-pixel image positioning

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawImageRect; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class BitmapRectDraw(val texture: GpuTexture, val sampler: GpuSampler, val srcRect: Rect, val dstRect: Rect)
```

## Acceptance Criteria

- [ ] BitmapRect draws correctly with texture binding and UV mapping
- [ ] Source/dest rect mapping produces correct image scaling
- [ ] Image rect is correctly clipped to device bounds

## Required Evidence

- BitmapRect GPU rendering fixture dump with various src/dst rects
- UV coordinate validation transcript
- Image bounds clipping test dumps

## Fallback / Refusal Behavior

BitmapRect rendering failure emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m17.bitmap-rect-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*BitmapRect*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M17`
- `area:geometry-passes`
