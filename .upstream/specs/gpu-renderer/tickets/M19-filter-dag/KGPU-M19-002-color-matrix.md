---
id: KGPU-M19-002
title: "Add ColorMatrix filter: 4x5 matrix + vector multiply in WGSL"
status: done
milestone: M19
priority: P0
owner_area: filters-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M18-001]
legacy_gate: null
---

# KGPU-M19-002 - Add ColorMatrix filter: 4x5 matrix + vector multiply in WGSL

## PM Note

Le ColorMatrix est le filtre le plus polyvalent: luminosité, contraste, saturation, teinte, niveaux de gris. Une seule multiplication matricielle en WGSL couvre tous ces cas.

## Problem

ColorMatrix filter needs a 4x5 matrix + vector multiply in WGSL to support brightness, contrast, saturation, hue rotation, and grayscale effects. Without this, common image filters must fall back to CPU.

## Scope

- Add ColorMatrix WGSL snippet: 4x5 matrix * RGBA + translation vector
- Add matrix uniform layout with validated packing
- Add standard matrix presets (grayscale, sepia, invert)
- Produce ColorMatrix rendering fixture dumps

## Non-Goals

- No arbitrary shader-based color filters beyond matrix
- No per-channel lookup tables
- No color space conversion in filter

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_BLEND_KEYING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blend-keying) - source src/shaders/graphite/ColorFilterShader.cpp matrix; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct ColorMatrixUniforms { matrix: mat4x4f, translation: vec4f }\nfn applyColorMatrix(color: vec4f, m: mat4x4f, t: vec4f) -> vec4f { return m * color + t; }
```

## Acceptance Criteria

- [ ] ColorMatrix WGSL compiles and produces correct color transformation
- [ ] Standard presets (grayscale, sepia, invert) produce expected results
- [ ] Matrix uniform packing passes ABI validation

## Required Evidence

- ColorMatrix GPU rendering fixture dumps for grayscale, sepia, invert presets
- Custom matrix test: brightness=1.5, contrast=2.0
- Matrix uniform packing validation report

## Fallback / Refusal Behavior

ColorMatrix WGSL compilation failure emits stable diagnostic; filter DAG node refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m19.color-matrix`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ColorMatrix*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- GaussianBlurFilter, ColorMatrixFilter, FilterDAGExecutor
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M19`
- `area:filters-passes`
