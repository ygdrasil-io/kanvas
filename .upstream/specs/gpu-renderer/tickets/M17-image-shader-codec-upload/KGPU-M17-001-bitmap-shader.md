---
id: KGPU-M17-001
title: "Add BitmapShader WGSL: texture sample + nearest/linear filter + clamp tile"
status: done
milestone: M17
priority: P0
owner_area: materials-wgsl
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-007]
legacy_gate: null
---

# KGPU-M17-001 - Add BitmapShader WGSL: texture sample + nearest/linear filter + clamp tile

## PM Note

Le BitmapShader est le premier shader qui échantillonne une texture. La configuration du sampler (filtre, tile mode) doit être validée pour chaque combinaison.

## Problem

Bitmap-based fills need a WGSL shader that samples a texture with configurable filter mode (nearest/linear) and clamp tile mode. Without this, images cannot be used as fill paints.

## Scope

- Add BitmapShader WGSL snippet with textureSample
- Add nearest and linear filter mode support via sampler config
- Add clamp tile mode in sampler descriptor
- Produce BitmapShader rendering fixture dumps

## Non-Goals

- No mipmap filtering
- No anisotropic filtering
- No Repeat/Mirror/Decal tile modes at this stage (see KGPU-M17-004)

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_IMAGE_SAMPLER_KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-sampler-key) - source src/shaders/graphite/ImageShader.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct BitmapShaderUniforms { textureIndex: u32, samplerIndex: u32 }\n@group(X) @binding(Y) var bitmapTexture: texture_2d<f32>;\n@group(X) @binding(Y) var bitmapSampler: sampler;
```

## Acceptance Criteria

- [ ] BitmapShader WGSL compiles and samples texture correctly
- [ ] Nearest filter produces pixel-exact sampling
- [ ] Linear filter produces smooth interpolation between texels

## Required Evidence

- BitmapShader GPU rendering fixture dump for nearest filter
- BitmapShader GPU rendering fixture dump for linear filter
- Sampler configuration validation report

## Fallback / Refusal Behavior

BitmapShader compilation or sampling failure emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m17.bitmap-shader`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*BitmapShader*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- BitmapShader WGSL + material lowering, BitmapRect execution, image upload materialization, tile modes
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M17`
- `area:materials-wgsl`
