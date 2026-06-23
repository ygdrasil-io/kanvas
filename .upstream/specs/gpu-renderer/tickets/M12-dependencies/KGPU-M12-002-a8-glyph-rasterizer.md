---
id: KGPU-M12-002
title: "Add A8 glyph rasterizer with strike key + cache invalidation"
status: done
milestone: M12
priority: P0
owner_area: text-shaper
claim_impact: TargetNative
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M12-001]
legacy_gate: null
---

# KGPU-M12-002 - Add A8 glyph rasterizer with strike key + cache invalidation

## PM Note

Le rasterizer A8 produit les masques de glyphes qui seront uploadés dans l'atlas GPU. Sans cache et invalidation, chaque trame recharge les mêmes glyphes.

## Problem

Glyph masks must be rasterized as A8 bitmaps with a strike-keyed cache before GPU atlas upload can proceed. Without this, text rendering would re-rasterize every glyph per frame.

## Scope

- Add A8 glyph rasterizer with configurable glyph size
- Add strike-keyed glyph cache with LRU eviction
- Add cache invalidation on font or strike change
- Produce A8 bitmap dumps for Latin glyph validation

## Non-Goals

- No subpixel LCD or ARGB glyph rendering
- No SDF generation at this stage

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_TEXT_ATLAS_GLYPH_UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source src/gpu/graphite/GlyphCache.h; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class A8GlyphRasterizer { fun rasterize(scaler: GlyphScaler, glyphId: Int): A8Bitmap }\nclass GlyphCache { fun get(key: StrikeKey, glyphId: Int): A8Bitmap?; fun put(...); fun invalidate() }
```

## Acceptance Criteria

- [ ] A8 rasterizer produces correct bitmap dimensions for Latin glyphs
- [ ] Cache hit rate > 0 for repeated glyph requests within same strike
- [ ] Cache invalidation clears all entries on font/strike change

## Required Evidence

- A8 glyph rasterizer unit test output
- Cache hit/miss telemetry for Latin text scene
- Cache invalidation transcript

## Fallback / Refusal Behavior

Missing or corrupt A8 glyph masks emit stable diagnostic and refuse GPU text route.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.text.a8-rasterizer-cache`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :font:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:text-shaper`
