---
id: KGPU-M36-003
title: "Mipmap auto-generation"
status: proposed
milestone: M36
priority: P1
owner_area: images
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M36-003 - Mipmap auto-generation

## PM Note

La génération automatique de mipmaps améliore la qualité de minification des
textures.

## Problem

Textures without mipmaps suffer from aliasing and poor minification quality
when rendered at reduced scales. GPU-native mipmap generation is required so
that every uploaded image can participate in high-quality downscaled rendering
without requiring pre-computed mip chains.

## Scope

- `GPUImageMipmapGenerationPlan`: level count computation from base
  dimensions, filter selection (box, tent, Kaiser), execution path
  (blit or compute).
- `GPUImageMipmapBlitPlan`: WGPU blit-based level generation using canvas
  texture blit with linear filtering.
- `GPUImageMipmapComputePlan`: WGSL compute shader path for
  filter-controlled downsampling, workgroup dispatch sizing.
- `GPUImageMipmapCachePlan`: cached per upload artifact, filter type, and
  format to avoid redundant generation.

## Non-Goals

- Do not generate mipmaps for non-power-of-two 3D texture arrays.
- Do not implement anisotropic filtering in this ticket.
- Do not cache mipmaps across upload artifacts with different content but
  same dimensions.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Graphite Algorithm References

- `GFX-TEXTURE-MIPMAP` from `../GRAPHITE-ALGORITHM-REFERENCES.md` — Study
  mipmap level computation and blit-dispatch decisions.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPUImageMipmapGenerationPlan(
    val levels: Int,
    val filter: MipmapFilter,
    val path: MipmapGenerationPath,
)

data class GPUImageMipmapBlitPlan(
    val sourceTexture: TextureId,
    val levels: List<TextureBlitOp>,
)

data class GPUImageMipmapComputePlan(
    val wgslModule: WgslModuleId,
    val dispatchSizes: List<WorkgroupSize>,
)

data class GPUImageMipmapCachePlan(
    val key: MipmapCacheKey,
    val artifact: MipmapArtifactRef,
)
```

## Acceptance Criteria

- [ ] Image with auto-generated mipmaps renders with correct minification
      when sampled with linear mipmap filtering.
- [ ] Mipmap generation does not regress nearest-sampled (no-mipmap) image
      rendering.
- [ ] Mip level budget is enforced and generation is refused when level count
      exceeds adapter limits.
- [ ] Both blit and compute paths are available (at least one with GPU
      evidence).

## Required Evidence

- Side-by-side render comparison: mipmapped vs. non-mipmapped minification
  (diff/stat artifacts).
- Nearest-sampled regression check: mip gen does not alter nearest output.
- Mip level budget refusal diagnostic.
- At least one blit or compute path GPU evidence.

## Fallback / Refusal Behavior

- Mip count exceeds adapter limit: `unsupported.image.mipmap_budget_exceeded`.
- Compute shader unavailable: fallback to blit path if supported, else
  refuse with stable diagnostic.
- No CPU-rendered mipmap fallback for GPU composition.

## Dashboard Impact

- Expected row: `gpu-renderer.image.mipmap`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Mipmap*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M36`
- `area:images`
