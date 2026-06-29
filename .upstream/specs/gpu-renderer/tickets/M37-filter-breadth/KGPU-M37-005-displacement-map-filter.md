---
id: KGPU-M37-005
title: "Displacement map filter"
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

# KGPU-M37-005 - Displacement map filter

## PM Note

Le displacement map deforme une texture source en fonction d'une autre texture
(carte de deplacement). C'est le fondement des effets de distorsion, de verre,
de rideau et de refraction utilises dans les compositions graphiques avancees.

## Problem

Current GPU renderer has no displacement map filter route. Displacement mapping
samples the source texture at coordinates offset by per-pixel displacement values
read from a displacement map texture. Each output pixel's source coordinate is:
`sourceCoord + displacement * scale`, where displacement is a scalar read from a
selected channel (R, G, B, or A) of the displacement map. Without this filter,
distortion, glass, ripple, and refraction effects cannot be promoted to
TargetNative.

## Scope

- `GPUDisplacementMapPlan` — channel select (R/G/B/A for X displacement, R/G/B/A
  for Y displacement, default R=X G=Y), scale factors (scaleX, scaleY), tile
  mode for out-of-bounds displaced coordinates.
- `GPUDisplacementSamplingPlan` — per-pixel coordinate remapping: sample
  displacement map at current fragment coordinate, extract displacement values
  from selected channels, compute `dstCoord = srcCoord + displacement * scale`,
  sample source texture at dstCoord with specified tile mode.

## Non-Goals

- Do not implement subpixel displacement (displacement map at different
  resolution from source).
- Do not implement displacement with mipmap LOD selection based on displacement
  magnitude.
- Do not implement multi-octave displacement or fractal noise displacement.
- Do not activate product routing for displacement.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Provide scratch-device, special-image, cached-bitmap proxy, and blur-device hooks used by image filters.
- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Decide when a filter result must resolve to texture versus remain deferred as shader logic.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUDisplacementMapPlan(
    val channelX: GPUColorChannel, // R, G, B, A — source channel for X displacement
    val channelY: GPUColorChannel, // R, G, B, A — source channel for Y displacement
    val scaleX: Float,
    val scaleY: Float,
    val tileMode: GPUTileMode, // Clamp, Repeat, Mirror, Decal
)

data class GPUDisplacementSamplingPlan(
    val sourceBinding: GPUTextureBinding,
    val displacementBinding: GPUTextureBinding,
    val targetBinding: GPUTextureBinding,
    val plan: GPUDisplacementMapPlan,
)

enum class GPUColorChannel { R, G, B, A }
```

### Route

```
DisplacementMap(source, displacementMap, channelX, channelY, scaleX, scaleY, tileMode)
    → displacement sampling plan
    → for each output pixel at coord (x, y):
        → displacement = sample(displacementMap, (x, y))
        → dx = displacement[channelX] * scaleX
        → dy = displacement[channelY] * scaleY
        → dstCoord = (x + dx, y + dy)
        → output = sample(source, dstCoord, tileMode)
    → result

Displacement map texture missing → refusal
    (unsupported.filter.displacement_missing_texture)
Scale == 0 → elision (identity pass)
```

## Acceptance Criteria

- [ ] Channel R=X, G=Y with scaleX=10, scaleY=10 produces correct pixel shift
      matching CPU oracle: pixels displaced by (R*10, G*10) from displacement
      map.
- [ ] Clamp tile mode for out-of-bounds coordinates: displaced coordinates outside
      source bounds clamp to edge pixels.
- [ ] Repeat tile mode for out-of-bounds coordinates: displaced coordinates wrap
      around source boundaries.
- [ ] Mirror tile mode for out-of-bounds coordinates: displaced coordinates are
      reflected at source boundaries.
- [ ] Channel A=X, A=Y (same channel for both axes) produces symmetric
      displacement along both axes matching CPU oracle.
- [ ] ScaleX=0, ScaleY=0 produces identity pass (no displacement, source
      unchanged).
- [ ] ScaleX≠ScaleY (e.g., scaleX=5, scaleY=0) produces anisotropic displacement
      (X-only shift) matching CPU oracle.

## Required Evidence

- Contract tests for displacement with channel pairs [(R,G), (A,A), (B,G)] and
  scales [(0,0), (5,5), (10,0), (20,10)].
- Contract tests for all four tile modes (Clamp, Repeat, Mirror, Decal) with
  scale=10 and a displacement map that pushes coordinates out of bounds.
- CPU oracle parity dumps: per-pixel exact match for bilinear or nearest
  sampling at 8-bit integer precision.
- Refusal fixture for missing displacement map texture.
- Elision fixture for scale=(0,0).
- WGSL validation for displacement sampling shader module.
- Intermediate artifact dumps: displacement map read-back showing correct
  channel extraction.

## Fallback / Refusal Behavior

- Displacement map texture is missing or not bound:
  `unsupported.filter.displacement_missing_texture` diagnostic.
- Source texture is not compatible with displacement sampling:
  `unsupported.filter.displacement_source_format` diagnostic.
- Tile mode not supported by adapter (Decal may require `textureSample` with
  specific capabilities):
  `unsupported.filter.displacement_tile_mode_unsupported` diagnostic.
- No silent CPU-rendered complete displacement-to-texture fallback is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.displacement`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Displacement*'
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
