---
id: KGPU-M37-002
title: "Morphology filter"
status: ready
milestone: M37
priority: P0
owner_area: filters
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M37-002 - Morphology filter

## PM Note

La morphologie (dilate/erode) est le deuxieme filtre de base apres le blur.
Elle est necessaire pour les pipelines d'ombre, de glow, et de masque dans les
futures compositions de calques. Sans elle, les effets de dilatation/erosion
restent non supportes.

## Problem

Current GPU renderer has no morphology filter route. Dilate and erode are
fundamental image processing operations used in shadow expansion, glow effects,
mask refinement, and stencil-cover pipelines. They require configurable kernel
shapes (rect, circle, ellipse) and anisotropic radii. A naive full-resolution
morphology pass with large kernels is expensive; separable decomposition for
rectangular kernels reduces cost from O(K^2) to O(2K) per pixel.

## Scope

- `GPUMorphologyPlan` — operation type (dilate/erode), kernel radius (X and Y,
  independently configurable for anisotropic support), kernel shape (rect,
  circle, ellipse), tile mode for sampling.
- `GPUMorphologyPassPlan` — single-pass 2D gather for circle/ellipse shapes, or
  separable two-pass (horizontal then vertical) for rect shapes using min/max
  reduce.
- `GPUMorphologySamplingPlan` — gather N samples from source texture within
  kernel footprint, apply min (erode) or max (dilate) reduction per output
  pixel.

## Non-Goals

- Do not implement color-channel-independent morphology (per-channel dilate/erode
  with different radii per R/G/B/A).
- Do not implement iterative morphology (N iterations of dilate/erode).
- Do not implement gradient morphology or distance-field morphology.
- Do not activate product routing for morphology.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Provide scratch-device, special-image, cached-bitmap proxy, and blur-device hooks used by image filters.
- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Decide when a filter result must resolve to texture versus remain deferred as shader logic.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUMorphologyPlan(
    val operation: GPUMorphologyOperation, // Dilate, Erode
    val radiusX: Int,
    val radiusY: Int,
    val kernelShape: GPUMorphologyKernelShape, // Rect, Circle, Ellipse
    val tileMode: GPUTileMode,
)

data class GPUMorphologyPassPlan(
    val direction: GPUMorphologyPassDirection?, // Horizontal, Vertical (null for single-pass)
    val sampleOffsets: IntArray,
    val reduceOp: GPUMorphologyReduceOp, // Min, Max
)

enum class GPUMorphologyOperation { Dilate, Erode }

enum class GPUMorphologyKernelShape { Rect, Circle, Ellipse }

enum class GPUMorphologyPassDirection { Horizontal, Vertical }

enum class GPUMorphologyReduceOp { Min, Max }

data class GPUMorphologySamplingPlan(
    val sourceBinding: GPUTextureBinding,
    val targetBinding: GPUTextureBinding,
    val pass: GPUMorphologyPassPlan,
)
```

### Route

```
Morphology(operation, radiusX, radiusY, shape) → morphology plan
    → radiusX == 0 && radiusY == 0 → elision (identity pass)
    → shape == Rect && radiusX > 0 && radiusY > 0 → separable two-pass
        → horizontal pass (gather radiusX*2+1 samples along X, min/max reduce)
        → vertical pass (gather radiusY*2+1 samples along Y, min/max reduce)
    → shape == Circle || shape == Ellipse → single-pass 2D gather
    → result
```

## Acceptance Criteria

- [ ] Dilate radius 3 (9×9 rect kernel) produces correct output matching CPU
      oracle at full precision.
- [ ] Erode radius 3 (9×9 rect kernel) produces correct output matching CPU
      oracle at full precision.
- [ ] Dilate followed by erode with same radius produces approximately original
      image (closing operation); maximum pixel divergence ≤ 1/255 for 8-bit sRGB.
- [ ] Anisotropic radii (radiusX=3, radiusY=1) produce correct output: horizontal
      dilation covers ±3 pixels, vertical dilation covers ±1 pixel.
- [ ] Circle kernel radius 2 produces correct output in single-pass mode; no
      corner artifacts from square-kernel assumption.
- [ ] Radius = 0 (both X and Y) produces elision with no sampling and identity
      output.
- [ ] Separable two-pass output matches single-pass 2D gather for rect shape
      (within precision) at radius 2.

## Required Evidence

- Contract tests for dilate and erode at radii [0, 1, 2, 3, 5] for rect shape.
- Contract tests for circle shape at radii [1, 2, 3].
- WGSL validation for generated dilate/erode shader modules (single-pass and
  separable).
- CPU oracle parity dumps: per-pixel exact match for 8-bit integer input (no
  floating-point divergence since min/max are exact).
- Refusal fixtures for radius exceeding kernel budget.
- Intermediate texture dump for separable path showing correct partial reduction.

## Fallback / Refusal Behavior

- Kernel radius exceeds adapter or budget limits:
  `unsupported.filter.morphology_radius_budget` diagnostic.
- Kernel shape not supported by adapter:
  `unsupported.filter.morphology_shape_unsupported` diagnostic.
- No silent CPU-rendered complete morphology-to-texture fallback is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.morphology`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Morphology*'
```

## Status Notes

- `proposed`: Initial ticket.
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.

## Linear Labels

- `gpu-renderer`
- `milestone:M37`
- `area:filters`
