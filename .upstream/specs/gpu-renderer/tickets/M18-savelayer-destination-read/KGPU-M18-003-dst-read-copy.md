---
id: KGPU-M18-003
title: "Add destination-read copy strategy: split pass + copy target texture"
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

# KGPU-M18-003 - Add destination-read copy strategy: split pass + copy target texture

## PM Note

La lecture de destination (destination-read) est nécessaire pour les modes de fusion avancés. La stratégie de copie explicite est plus portable que framebuffer-fetch.

## Problem

Blend modes that read destination pixels (e.g., srcIn, dstOver, multiply) need a destination-read strategy. Without this, advanced blend modes cannot function on WebGPU.

## Scope

- Add destination-read copy strategy: split render pass + copy target
- Add copy region tracking for touched pixel bounds
- Add copy command insertion before render pass execution
- Produce destination-read rendering fixture dumps

## Non-Goals

- No framebuffer-fetch strategy
- No destination-read for stencil-only passes

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DST_READ_COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-read-copy) - source src/gpu/graphite/DrawContext.cpp dstRead; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class DstReadCopyStrategy(val copyRegion: Rect, val sourceTexture: GpuTexture, val copyCommand: CopyCommand)
```

## Acceptance Criteria

- [ ] Destination-read copy produces correct pixel data for blend modes
- [ ] Copy region is correctly bounded to touched pixels only
- [ ] Copy command is ordered before render pass that consumes it

## Required Evidence

- Destination-read copy GPU rendering fixture dump for multiply blend
- Copy region bounds validation transcript
- Command ordering validation transcript

## Fallback / Refusal Behavior

Destination-read copy failure emits stable diagnostic; advanced blend mode draws refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m18.dst-read-copy`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DstRead*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M18`
- `area:layers-passes`
