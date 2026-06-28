---
id: KGPU-M37-001
title: "Multi-pass separable blur"
status: review
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

# KGPU-M37-001 - Multi-pass separable blur

## PM Note

Le blur separable multi-passe est le premier filtre M37 et la brique de base
reutilisee par M37-003 (drop shadow). Sans lui, aucun filtre par convolution ne
peut etre accepte en route native.

## Problem

Current GPU renderer has no separable blur route. A naive 2D convolution blur is
prohibitively expensive (O(N*M*K^2) samples). Separable decomposition reduces
this to O(N*M*2K) via two 1D passes with an intermediate texture, making
real-time blur feasible on GPU. Without separable blur, drop shadow (M37-003)
and any sigma-dependent filter pipeline cannot be promoted to TargetNative.

## Scope

- `GPUSeparableBlurPlan` — kernel size computed from sigma, 2-pass strategy,
  intermediate texture allocation, tile mode for out-of-bounds sampling.
- `GPUBlurPassPlan` — horizontal and vertical 1D passes, precomputed kernel
  weights derived from Gaussian function, sampler binding for source and
  intermediate textures.
- `GPUBlurKernelCachePlan` — cache precomputed normalized kernel weights keyed
  by sigma and quality level, avoiding recomputation per frame.
- `GPUBlurIntermediateArtifact` — typed intermediate texture descriptor
  (dimensions, format, mip-levels=1, usage sampled+render-target).
- `GPUBlurQualityLevel` — three tiers:
  - **Fast** (5-tap box blur, directionally separable, minimum quality).
  - **Normal** (sigma-dependent tap count, ceil(sigma)*2+1, default).
  - **High** (sigma*3 tap count Gaussian, maximum quality).

## Non-Goals

- Do not implement non-separable 2D convolution blur.
- Do not implement progressive downscale blur pyramids.
- Do not implement motion blur or radial blur variants.
- Do not activate product routing for blur.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Provide scratch-device, special-image, cached-bitmap proxy, and blur-device hooks used by image filters.
- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Decide when a filter result must resolve to texture versus remain deferred as shader logic, merging sampling, tiling, color filters, and decal behavior.
- [`GFX-SPECIAL-IMAGE-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-image-layer) - source [SpecialImage_Graphite.cpp:20](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/SpecialImage_Graphite.cpp:20); Wrap Graphite-backed images with subset metadata and convert non-Graphite images through the recorder image provider before filter use.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUSeparableBlurPlan(
    val sigma: Float,
    val quality: GPUBlurQualityLevel,
    val tileMode: GPUTileMode,
    val intermediate: GPUBlurIntermediateArtifact,
)

data class GPUBlurPassPlan(
    val direction: GPUBlurPassDirection, // Horizontal, Vertical
    val kernelWeights: FloatArray,
    val sourceBinding: GPUTextureBinding,
    val targetBinding: GPUTextureBinding,
)

enum class GPUBlurQualityLevel { Fast, Normal, High }

data class GPUBlurKernelCachePlan(
    val entries: Map<BlurKernelKey, FloatArray>,
)

data class GPUBlurIntermediateArtifact(
    val width: Int,
    val height: Int,
    val format: GPUTextureFormat,
)

data class BlurKernelKey(val sigma: Float, val quality: GPUBlurQualityLevel)
```

### Route

```
Blur(sigma) → quality level selection → separable plan (sigma > 0)
    → horizontal pass (source → intermediate, 1D kernel along X)
    → vertical pass (intermediate → target, 1D kernel along Y)
    → result

sigma == 0 → elision (identity pass, no intermediate allocation)
sigma < 0  → refusal (unsupported.filter.blur_sigma_range)
```

## Acceptance Criteria

- [ ] Sigma 2.0 with Normal quality produces correct Gaussian blur output matching
      CPU oracle (full 2D convolution) at 16-bit float precision.
- [ ] Sigma 10.0 with Normal and High quality tiers produce correct output with
      CPU oracle parity; High tier kernel tap count equals ceil(sigma*3)*2+1.
- [ ] Fast tier produces correct 5-tap box blur; acceptable visual divergence
      from Gaussian baseline is documented.
- [ ] Horizontal-only and vertical-only separable passes produce identical output
      to a single-pass non-separable 2D convolution (within precision) for a
      square kernel.
- [ ] Sigma = 0 produces elision with no intermediate allocation and identity
      output.
- [ ] Sigma < 0 produces stable refusal with diagnostic code.
- [ ] Intermediate texture is correctly sized (source width × source height for
      horizontal pass; source height × source width for vertical pass).

## Required Evidence

- Contract tests for `GPUSeparableBlurPlan` across sigma [0, 0.5, 2.0, 5.0, 10.0]
  at all three quality tiers.
- WGSL validation for generated horizontal and vertical blur shader modules.
- CPU oracle parity dumps: per-pixel MSE < 1e-3 at 16-bit float for Normal tier
  at sigma 2.0.
- Refusal fixtures for sigma = -1.0, sigma = 0.0 (elision), and sigma out of
  budget.
- Intermediate texture dump showing correct pass separation (horizontal pass
  output is blurred along X only; vertical pass output is blurred along Y only
  with no residual X blur artifact).

## Fallback / Refusal Behavior

- Sigma out of accepted range (sigma < 0 or sigma > maximum kernel budget):
  `unsupported.filter.blur_sigma_range` diagnostic.
- Intermediate texture allocation exceeds budget:
  `unsupported.filter.blur_intermediate_budget` diagnostic.
- Quality level not supported by adapter capabilities:
  `unsupported.filter.blur_quality_level` diagnostic.
- No silent CPU-rendered complete blur-to-texture fallback is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.blur-multi-pass`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Blur*'
```

## Status Notes

- `proposed`: Initial ticket.
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.

## Linear Labels

- `gpu-renderer`
- `milestone:M37`
- `area:filters`
