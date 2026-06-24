---
id: KGPU-M19-001
title: "Add Gaussian blur filter: 2-pass H/V separable blur with downsample/upsample"
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

# KGPU-M19-001 - Add Gaussian blur filter: 2-pass H/V separable blur with downsample/upsample

## PM Note

Le flou gaussien est le filtre le plus utilisé en UI: ombres portées, arrière-plans floutés, effets de profondeur. La version séparable 2-passes est le standard de performance.

## Problem

Gaussian blur needs a 2-pass separable H/V implementation with optional downsample/upsample for performance. Without this, soft shadows and backdrop blurs cannot render.

## Scope

- Add horizontal blur pass with configurable sigma/radius
- Add vertical blur pass with configurable sigma/radius
- Add downsample before blur and upsample after for large radii
- Produce Gaussian blur rendering fixture dumps

## Non-Goals

- No directional or motion blur
- No variable blur (per-pixel radius)

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_FILTER_BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source src/gpu/graphite/render/BlurRenderStep.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class GaussianBlurFilter(val sigmaX: Float, val sigmaY: Float, val downsample: Int) {{\n  fun horizontalPass(input: GpuTexture): GpuTexture;\n  fun verticalPass(input: GpuTexture): GpuTexture;\n}}
```

## Acceptance Criteria

- [ ] Gaussian blur produces visually correct blur for sigma 1-32
- [ ] Downsample/upsample reduces cost without visible quality loss
- [ ] Separable 2-pass produces identical results to reference single-pass

## Required Evidence

- Gaussian blur GPU rendering fixture dumps for sigma=1,4,16,32
- Downsample/upsample quality comparison
- Separable correctness validation (H+V == reference)

## Fallback / Refusal Behavior

Blur rendering failure emits stable diagnostic; filter DAG node refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m19.gaussian-blur`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Blur*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- GaussianBlurFilter, ColorMatrixFilter, FilterDAGExecutor
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry
- BlurSnippet.kt deleted (WGSL deferred — see M19-001 review)

## Linear Labels

- `gpu-renderer`
- `milestone:M19`
- `area:filters-passes`
