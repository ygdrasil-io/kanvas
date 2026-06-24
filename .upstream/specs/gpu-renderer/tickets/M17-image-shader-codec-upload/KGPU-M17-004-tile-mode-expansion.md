---
id: KGPU-M17-004
title: "Add tile mode expansion: Repeat + Mirror + Decal via WGSL math"
status: proposed
milestone: M17
priority: P0
owner_area: materials-wgsl
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M17-001]
legacy_gate: null
---

# KGPU-M17-004 - Add tile mode expansion: Repeat + Mirror + Decal via WGSL math

## PM Note

Les modes de tuilage Repeat, Mirror et Decal sont essentiels pour les fonds d'écran, les textures répétées et les bordures d'image. L'implémentation WGSL doit être précise aux bords.

## Problem

BitmapShader currently only supports Clamp tile mode. Repeat, Mirror, and Decal tile modes must be implemented in WGSL math (fract, mirror, step) for full image tiling support.

## Scope

- Add Repeat tile mode via fract() in WGSL
- Add Mirror tile mode via mirrored fract() in WGSL
- Add Decal tile mode via step() boundary check in WGSL
- Produce tiled image rendering fixture dumps

## Non-Goals

- No hardware sampler tile mode (all via WGSL math for consistency)
- No mipmap interaction with tile modes

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_IMAGE_SAMPLER_KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-sampler-key) - source src/gpu/graphite/KeyHelpers.cpp tile; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
fn applyTileMode(uv: vec2f, mode: u32) -> vec2f {{\n  switch mode {{\n    case REPEAT: return fract(uv);\n    case MIRROR: /* mirrored fract */;\n    case DECAL: /* step boundary check */;\n    default: return clamp(uv, 0.0, 1.0);\n  }}\n}}
```

## Acceptance Criteria

- [ ] Repeat tile mode produces seamless tiling on GPU
- [ ] Mirror tile mode produces correct reflection at tile boundaries
- [ ] Decal tile mode produces transparent black outside [0,1] UV range

## Required Evidence

- Repeat tile mode GPU rendering fixture dump
- Mirror tile mode GPU rendering fixture dump
- Decal tile mode GPU rendering fixture dump
- Tile boundary precision analysis

## Fallback / Refusal Behavior

Tile mode rendering artifacts emit stable diagnostic; affected tile mode disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m17.tile-mode-expansion`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TileMode*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M17`
- `area:materials-wgsl`
