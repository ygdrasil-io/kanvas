---
id: KGPU-M34-002
title: "Color font pipeline"
<<<<<<< HEAD
status: blocked
=======
status: proposed
>>>>>>> master
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

<<<<<<< HEAD
Le parsing des polices couleur (COLRv0/CPAL, CBDT/CBLC) et le handoff
`ColorGlyphPlan` existent et sont livrés. La route GPU consomme ce handoff et
refuse de façon stable le rendu couleur (`text.gpu.color-plan-unsupported`,
sans fallback texture CPU). Le rendu GPU des glyphes couleur (COLRv0
rasterisé, COLRv1, SVG, emoji) reste DependencyGated sur l'exécution GPU
(M10/M11).

## Claim Split & Re-Scope (2026-06-29)

Audit `fichier:ligne` : les artefacts text-stack supposés manquants existent en
réalité. Le motif de blocage initial « gated on pure-kotlin-text COLRv0
parsing artifacts » est faux et corrigé.

**Implémenté mais non promu — reste `DependencyGated` (handoff + facts portés + refus stable) :**

- Parsing COLRv0 / CPAL : `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt:361` et `:232`.
- Parsing CBDT/CBLC : `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt:1977`.
- Handoff `ColorGlyphPlan` + enregistrement registre :
  `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt:415`,
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/routing/KanvasPreparedGPUArtifactRegistry.kt:40`.
- Refus GPU stable du rendu couleur, sans fallback texture CPU :
  `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusals.kt:257-267`
  (`text.gpu.color-plan-unsupported`), gate `COLRColorGlyph` = `not-promoted`
  (`gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`).
- Validation : `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/ColorFontHandoffRouteTest.kt` (4 tests PASSED).

**Dependency-Gated (non livré) — rendu GPU :**

- Contrats GPU `GPUColorGlyphLayerPlan`, `GPUColorGlyphCompositePlan`,
  `GPUCBDTCBLCGlyphPlan` : absents (sketch de spec uniquement).
- Rasterisation GPU d'un glyphe COLRv0, COLRv1, SVG OpenType, emoji ; codes
  `unsupported.text.color_font.format_unavailable` / `.layer_count`.
- Gated sur exécution GPU M10/M11. `product_activation` reste `false`.
=======
Les polices couleur (COLRv0/v1, CBDT/CBLC, SVG, emoji) sont DependencyGated
en attendant les artefacts du text stack pure Kotlin.
>>>>>>> master

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

<<<<<<< HEAD
> Scope (2026-06-29) : le sous-scope borné « Claim Split » (handoff + refus
> stable) est implémenté et testé, mais ne promeut pas le ticket. Les critères
> de rendu GPU ci-dessous restent `DependencyGated` (M10/M11) — le ticket reste
> `blocked`.

=======
>>>>>>> master
- [ ] Contracts defined and dumpable (`GPUColorGlyphLayerPlan`,
      `GPUCBDTCBLCGlyphPlan`).
- [ ] COLRv0 layer tree rasterized with GPU evidence (at least one glyph).
- [ ] CBDT/CBLC decoded via `22-image-bitmap-codec-pipeline.md` contract.
- [ ] Unsupported format (COLRv1, SVG, layer count exceeded) → stable
      refusal with diagnostic.

## Required Evidence

<<<<<<< HEAD
> Scope borné couvert par `ColorFontHandoffRouteTest`. Les preuves de rendu GPU
> ci-dessous restent `DependencyGated` (M10/M11).

=======
>>>>>>> master
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
<<<<<<< HEAD
- Expected classification: `DependencyGated` (rendu GPU couleur). Le handoff +
  refus stable est implémenté/testé mais ne promeut pas le ticket.
- Claim promotion allowed: no — aucun claim de rendu GPU couleur tant que
  l'évidence GPU (M10/M11) n'est pas livrée.
=======
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.
>>>>>>> master

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ColorFont*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack COLRv0
  parsing artifacts.
<<<<<<< HEAD
- `proposed → blocked` (2026-06-28): Blocked on pure-kotlin-text COLRv0 parsing artifacts.
- `reste blocked` (2026-06-29): correction du motif. Le motif « pure-kotlin-text
  COLRv0 artifacts » est faux : parsing COLRv0/CPAL/CBDT + handoff `ColorGlyphPlan`
  sont livrés (voir Claim Split). Le ticket **reste `blocked` / `DependencyGated`**
  car l'évidence de rendu GPU couleur est toujours KO (`product_activation: false`,
  contrats GPU absents, refus stable `text.gpu.color-plan-unsupported`). Le
  sous-scope borné handoff + refus est implémenté et testé
  (`ColorFontHandoffRouteTest`) mais ne promeut pas le ticket. Vrai gate :
  exécution GPU M10/M11.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
