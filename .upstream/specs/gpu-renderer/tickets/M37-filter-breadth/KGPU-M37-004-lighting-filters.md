---
id: KGPU-M37-004
title: "Lighting filters"
<<<<<<< HEAD
status: done
=======
status: proposed
>>>>>>> master
milestone: M37
priority: P1
owner_area: filters
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M37-004 - Lighting filters

## PM Note

Les filtres d'eclairage (directional, specular) avec bump map et normal map
permettent les effets de relief et de materiau dans les compositions de calques.
Ils completent l'ombre portee (M37-003) pour un pipeline d'eclairage complet.

## Problem

Current GPU renderer has no lighting filter route. Lighting filters (directional
diffuse, specular highlights with Phong/Blinn-Phong model) require normal
surface information — either derived from an alpha bump map (height-field
gradient approximation) or directly sampled from a normal map texture. Without
lighting, layer effects such as bevel, emboss, and material shading cannot be
promoted to TargetNative.

## Scope

- `GPULightingPlan` — light type (directional, point, spot, specular),
  direction vector or position, surface scale (height-field amplitude), light
  color, ambient color, specular exponent (shininess), light attenuation
  parameters (constant, linear, quadratic for point/spot).
- `GPULightingNormalMapPlan` — normal source: bump map (alpha height-field →
  gradient-based normal estimation via Sobel or central differences) or
  dedicated normal map texture (RGB = XYZ normal in tangent space).
- `GPULightingWGSL` — Phong or Blinn-Phong lighting model WGSL implementation:
  ambient + diffuse (N·L) + specular (Blinn-Phong half-vector H·N raised to
  exponent).

## Non-Goals

- Do not implement spot light attenuation cones in initial acceptance (deferred
  with stable refusal).
- Do not implement multiple simultaneous light sources.
- Do not implement environment map or image-based lighting.
- Do not implement shadow mapping for lights.
- Do not activate product routing for lighting.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Provide scratch-device, special-image, cached-bitmap proxy, and blur-device hooks used by image filters.
- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Decide when a filter result must resolve to texture versus remain deferred as shader logic.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPULightingPlan(
    val type: GPULightType, // Directional, Point, Spot, Specular
    val direction: GPUVec3?, // directional light direction (normalized)
    val position: GPUVec3?, // point/spot light position in surface space
    val surfaceScale: Float, // height-field amplitude for bump map
    val lightColor: GPUColor,
    val ambientColor: GPUColor,
    val specularExponent: Float, // shininess (1-128)
    val attenuation: GPUAttenuation?, // constant, linear, quadratic for point/spot
)

data class GPULightingNormalMapPlan(
    val normalSource: GPULightingNormalSource, // BumpAlpha, NormalMap
    val sourceBinding: GPUTextureBinding,
    val normalMapBinding: GPUTextureBinding?, // null for bump alpha
)

enum class GPULightType { Directional, Point, Spot, Specular }

enum class GPULightingNormalSource { BumpAlpha, NormalMap }

data class GPUAttenuation(val constant: Float, val linear: Float, val quadratic: Float)
```

### Route

```
Lighting(type, normalSource, params) → lighting plan
    → normalSource == BumpAlpha:
        → sample source alpha, compute gradient (Sobel 3x3), derive normal
    → normalSource == NormalMap:
        → sample normal map texture (RGB → XYZ), transform to surface space
    → type == Directional:
        → ambient + diffuse(N·L) * lightColor + specular((H·N)^exponent) * lightColor
    → type == Specular:
        → same as directional with explicit specular highlight control
    → type == Point || type == Spot:
        → initial refusal with stable diagnostic
    → result (lit surface, replacing source pixels)

Normal source missing → refusal (unsupported.filter.lighting_normal_source_missing)
```

## Acceptance Criteria

- [ ] Directional light with bump map (surfaceScale=5.0) produces correct
      lighting: bright pixels where normal faces light direction, dark pixels
      where normal faces away, matching CPU oracle.
- [ ] Specular highlight is visible with specularExponent=16; highlight size
      decreases as exponent increases (exponent=64 produces tighter highlight).
- [ ] Directional light with normal map texture produces correct lighting
      matching CPU oracle; normals are correctly sampled from RGB channels.
- [ ] Ambient color contributes to all pixels regardless of surface orientation.
- [ ] Light color modulates diffuse and specular contributions; ambient color is
      additive.
- [ ] Point and Spot light types produce stable refusal with diagnostic code
      (not a crash or silent pass-through).
- [ ] Bump map with surfaceScale=0 produces flat surface (all normals point up);
      diffuse lighting is uniform across the surface.

## Required Evidence

- Contract tests for directional light with bump map at surface scales [0, 2, 5, 10].
- Contract tests for directional light with normal map fixture.
- Contract tests for specular with exponents [1, 16, 64, 128].
- CPU oracle parity dumps: per-pixel MSE < 5e-3 for 8-bit sRGB (lighting is
  more tolerant due to floating-point normal computation).
- Refusal fixtures for point light, spot light, and missing normal source.
- WGSL validation for Phong/Blinn-Phong lighting shader module.
- Intermediate artifact dumps: estimated normals (visualized as RGB) for bump
  map path, sampled normals for normal map path.

## Fallback / Refusal Behavior

- Normal source (bump map alpha or normal map texture) is missing or incompatible:
  `unsupported.filter.lighting_normal_source_missing` diagnostic.
- Point or spot light type requested (not yet accepted):
  `unsupported.filter.lighting_type_unsupported` diagnostic.
- Source texture is not compatible with lighting pipeline:
  `unsupported.filter.lighting_source_format` diagnostic.
- No silent CPU-rendered complete lighting-to-texture fallback is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.lighting`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Lighting*'
```

## Status Notes

- `proposed`: Initial ticket.
<<<<<<< HEAD
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted to done after independent review accepted linked evidence; no hidden product activation.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M37`
- `area:filters`
