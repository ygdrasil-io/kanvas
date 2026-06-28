---
id: KGPU-M34-002
title: "Color font pipeline"
status: proposed
milestone: M34
priority: P0
owner_area: text
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-002 - Color font pipeline

## PM Note

Les polices couleur (COLRv0/v1, CBDT/CBLC, SVG, emoji) sont DependencyGated
en attendant les artefacts du text stack pure Kotlin.

## Problem

Color fonts (COLRv0/v1, CBDT/CBLC, SVG OpenType, emoji sequences) require
layered glyph compositing, embedded bitmap decoding, and per-glyph color
rasterization. The GPU renderer must define contracts for consuming these
artifacts from the pure Kotlin text stack, but cannot implement COLR/CBDT/SVG
parsing within `:gpu-renderer`.

## Scope

- `GPUColorGlyphLayerPlan` — COLRv0/v1 layers (solid fill, linear/radial
  gradient, glyph reference), layer tree composition.
- `GPUColorGlyphCompositePlan` — composite layered glyph into atlas or direct
  render target.
- `GPUCBDTCBLCGlyphPlan` — embedded bitmap decode + color + GPU upload.
- `GPUSVGOpenTypeGlyphPlan` — SVG per-glyph CPU raster → GPU upload.
- `GPUEmojiFallbackPlan` — emoji sequence fallback contract.

## Non-Goals

- No COLR/CBDT/SVG parsing in `:gpu-renderer`. All parsing lives in the pure
  Kotlin text stack.
- No COLRv1 or SVG OpenType rendering until text stack delivers parsing
  artifacts.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md` (CBDT/CBLC
  codec path)
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-BITMAP-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-bitmap-text-step) - source [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59); Choose A8/LCD/color variants, append per-glyph instance data, bind up to four atlas textures, and produce coverage or primitive color from indexed atlas samples.
- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Carry a subspan of an atlas subrun, mask bounds, mask-to-device matrix, glyph range, SDF/LCD metadata, and renderer data as geometry.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Resolve mask format, normalize glyph pixels with padding, add glyphs to a DrawAtlas, and record pending atlas uploads.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
enum class GPUColorFontVersion {
    COLRv0,
    COLRv1,
    Unsupported,
}

data class GPUColorGlyphLayerPlan(
    val layers: List<GPUColorGlyphLayer>,
    val composite: GPUColorGlyphCompositePlan,
)

sealed class GPUColorGlyphLayer {
    data class SolidFill(val color: GPURGBA) : GPUColorGlyphLayer()
    data class Gradient(val gradient: GPUGradientFill) : GPUColorGlyphLayer()
    data class GlyphRef(val glyphId: Int, val fontKey: GPUFontKey) : GPUColorGlyphLayer()
}

data class GPUCBDTCBLCGlyphPlan(
    val glyphId: Int,
    val bitmapData: ByteArray,
    val format: GPUBitmapFormat,
    val colorSpace: GPUColorSpace,
)

data class GPUSVGOpenTypeGlyphPlan(
    val glyphId: Int,
    val svgDocument: GPUSVGDocumentRef,
    val rasterSize: GPUSize,
)
```

## Acceptance Criteria

- [ ] Contracts defined and dumpable (`GPUColorGlyphLayerPlan`,
      `GPUCBDTCBLCGlyphPlan`).
- [ ] COLRv0 layer tree rasterized with GPU evidence (at least one glyph).
- [ ] CBDT/CBLC decoded via `22-image-bitmap-codec-pipeline.md` contract.
- [ ] Unsupported format (COLRv1, SVG, layer count exceeded) → stable
      refusal with diagnostic.

## Required Evidence

- `GPUColorGlyphLayerPlan` dump (COLRv0 glyph with ≥2 layers).
- `GPUCBDTCBLCGlyphPlan` dump.
- Refusal fixtures:
  - COLRv1 format.
  - SVG OpenType format.
  - Layer count exceeds maximum.
- GPU evidence: at least one COLRv0 glyph rendered to target.

## Fallback / Refusal Behavior

- Unsupported color font format →
  `unsupported.text.color_font.format_unavailable`.
- Layer count exceeds maximum →
  `unsupported.text.color_font.layer_count`.
- Silent fallback to CPU-rendered color glyph texture is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.text.color-font`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ColorFont*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack COLRv0
  parsing artifacts.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
