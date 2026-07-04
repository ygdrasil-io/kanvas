# Emoji Kanvas — Native Color Glyph Pipeline

**Date:** 2026-07-03
**Status:** Design approved

## Goal

Add native color glyph support to the Kanvas `GlyphScaler` / `FontTypeface` pipeline and migrate the 5 remaining emoji GMs from `skia-integration-tests/` to `integration-tests/skia/gm/text/`.

## Scope

1. Glyph representation type system — `Outline`, `Bitmap`, `ColorLayers`, `SvgDocument`
2. OpenType table parsing in `GlyphScaler` — CPAL, COLRv0, CBDT/CBLC, sbix, COLRv1, SVG
3. `Font` API extensions — `getMetrics()`, `isEmbolden`
4. `GmCanvas` extension — `drawGlyphs(glyphIds, positions, font, paint)`
5. Native Kanvas `EmojiTypeface` — replaces `STUB.EMOJI_TABLES`
6. Color glyph rendering in `Canvas.drawString()`
7. Migration of 5 remaining emoji GMs

## Non-scope

- COLRv1 variable axes (ItemVariationStore deltas) — tracked in #1020
- SVG glyph rendering in Canvas — SVG table parsed but not rendered
- Emoji ZWJ sequence shaping — codepoint-to-glyph remains 1:1 via cmap
- Strike selection for bitmap emoji — single-strike fonts only initially

## Modules affected

| Module | Change |
|--------|--------|
| `font/scaler` (GlyphScaler) | New table parsers, `GlyphRepresentation` type, `scaleGlyph()` extended |
| `kanvas/text` (Font, Typeface, FontTypeface) | `getMetrics()`, `isEmbolden`, `EmojiTypeface`, `GlyphRepresentation` |
| `integration-tests/skia` (GmCanvas) | `drawGlyphs()` method |
| `codec/png` | Unchanged — used as-is for CBDT/sbix PNG decode |

## Design

### 1. Glyph representation type system

```kotlin
// kanvas/text/GlyphRepresentation.kt
sealed interface GlyphRepresentation {
    data class Outline(val commands: List<OutlineCommand>) : GlyphRepresentation
    data class Bitmap(
        val pngData: ByteArray,
        val originX: Float, val originY: Float,
        val pixelWidth: Int, val pixelHeight: Int,
    ) : GlyphRepresentation
    data class ColorLayers(
        val layers: List<ColorLayerEntry>,
    ) : GlyphRepresentation
    data class SvgDocument(
        val svgData: ByteArray,
        val docWidth: Float, val docHeight: Float,
    ) : GlyphRepresentation
}

data class ColorLayerEntry(val glyphId: Int, val paletteColor: Color)
```

`ScaledGlyph` gains `representation: GlyphRepresentation?`. Existing `Outline` path unchanged — no breaking change.

### 2. OpenType tables to parse

**Already parsed:**
- `cmap` (formats 0, 4, 6, 12) — codepoint → glyphId
- `head` — unitsPerEm, indexToLocFormat
- `hhea` — numHMetrics, ascent/descent/lineGap
- `hmtx` — advance widths
- `loca` + `glyf` — simple glyph contours, composite glyphs (depth 8)
- `maxp` — numGlyphs

**New tables:**

| Table | Output | Parsing order |
|-------|--------|---------------|
| **CPAL** | Palette colors (ARGB array) | 1 (dependency) |
| **COLRv0** | `ColorLayers` — BaseGlyphRecord → LayerRecord stack | 2 |
| **CBDT/CBLC** | `Bitmap` — PNG strikes via CBLC index | 3 |
| **sbix** | `Bitmap` — per-glyph PNG + origin offsets | 3 |
| **COLRv1** | Extended `ColorLayers` — paint graph (Solid/Glyph/Gradient/Transform/Composite) | 4 |
| **SVG** | `SvgDocument` — document index parsed, rendering deferred | 5 (bonus) |

All new tables are optional — if absent, `scaleGlyph()` falls back to `Outline` with no regression.

### 3. Color rendering in Canvas.drawString()

```
drawString(str, x, y, font, paint)
  for each codepoint:
    glyphId = typeface.glyphIdForCodepoint(cp)
    result = scaler.scaleGlyph(glyphId, fontSize)
    advance = scaler.getAdvance(glyphId, fontSize)
    when (result.representation):
      Outline(cmds)    → drawPath(buildPath(cmds), paint)        // existing
      Bitmap(png,...)  → decode PNG → drawImage(image, rect)     // new
      ColorLayers(l)   → for each layer: drawPath(layerGlyph,    // new
                           paint.copy(color = layer.color))
      SvgDocument(svg) → skipped (no SVG renderer yet)           // deferred
      null             → skip (no glyph)                          // existing
    cursorX += advance
```

### 4. Font API extensions

```kotlin
// kanvas/text/FontMetrics.kt
data class FontMetrics(
    val ascent: Float,
    val descent: Float,
    val leading: Float,
    val xHeight: Float = 0f,
    val capHeight: Float = 0f,
)

// kanvas/text/Font.kt — additions
data class Font(...,
    val isEmbolden: Boolean = false,  // new
) {
    fun getMetrics(): FontMetrics     // new — from hhea/OS2 tables
    fun measureText(str: String): Float  // existing
    fun toTextBlob(str: String, x: Float, y: Float): TextBlob  // existing
}
```

`isEmbolden` behavior: when `true`, `getAdvance()` adds `fontSize * 0.02f` and the scaler applies a slight horizontal offset to outline commands.

### 5. GmCanvas.drawGlyphs()

```kotlin
// GmCanvas — new method
fun drawGlyphs(glyphIds: List<Int>, positions: List<Point>, font: Font, paint: Paint) {
    // Iterates glyphIds at explicit positions.
    // Delegates to inner Canvas via per-glyph drawString scaling.
}
```

Required by `ScaledEmojiPosGM` which tests per-glyph-position vs auto-advance agreement.

### 6. Native Kanvas EmojiTypeface

```kotlin
// kanvas/text/EmojiTypeface.kt
object EmojiTypeface {
    enum class Format { Sbix, CBDT, COLRv0, SVG }

    fun create(format: Format, fontData: ByteArray): Typeface {
        return FontTypeface(fontData, "emoji-${format.name.lowercase()}")
    }

    fun createOrFallback(format: Format, fontData: ByteArray): Typeface {
        return try {
            create(format, fontData)
        } catch (_: Exception) {
            Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")
                ?: error("No fallback typeface available")
        }
    }
}
```

No per-format dispatch — `GlyphScaler` inspects available tables and adapts. `Format` is retained for debug naming only. Replaces the `cpu-raster` `STUB.EMOJI_TABLES`.

### 7. GM migration plan

| Order | GM | Unblocked by | Notes |
|-------|----|-------------|-------|
| 1 | **ScaledemojiGM** | COLRv0 + `getMetrics` | 4 sizes, drawSimpleText via emoji typeface |
| 2 | **ColoremojiBlendmodesGM** | COLRv0 + `getMetrics` | 29 blend modes × 1 emoji glyph |
| 3 | **ScaledEmojiPosGM** | `drawGlyphs` | Per-glyph-pos vs auto-advance |
| 4 | **ScaledEmojiPerspectiveGM** | `concat(perspectiveMatrix)` | Perspective on emoji glyph |
| 5 | **ColrV1GM** | COLRv1 paint graph | Categories + "ABCD" fallback |

All GMs use `EmojiTypeface.createOrFallback()` — LiberationSans fallback when emoji font data is absent.

**Reference images:** 19 emoji PNGs + 62 ColrV1 PNGs in `original-888/`. Moved to `reference/` during migration. `minSimilarity = 0.0` until emoji font resources are integrated.

### 8. Implementation phases

**Phase 1 — Foundation (CPAL, COLRv0, Font features)**
- Parse CPAL + COLRv0 in GlyphScaler
- `GlyphRepresentation` + `ColorLayers`
- `Font.getMetrics()`, `Font.isEmbolden`
- Canvas COLRv0 rendering
- `EmojiTypeface` native
- Migrate: ScaledemojiGM

**Phase 2 — Bitmap emoji (CBDT/CBLC, sbix)**
- Parse CBDT/CBLC + sbix PNG strikes
- `GlyphRepresentation.Bitmap`
- Canvas bitmap rendering (PNG decode + drawImage)
- Migrate: ColoremojiBlendmodesGM

**Phase 3 — drawGlyphs + transforms**
- `GmCanvas.drawGlyphs()`
- Migrate: ScaledEmojiPosGM, ScaledEmojiPerspectiveGM

**Phase 4 — COLRv1**
- Parse COLRv1 paint graph (Solid, Glyph, Gradient, Transform, Composite)
- Canvas COLRv1 rendering
- Migrate: ColrV1GM

## Risks and mitigations

| Risk | Mitigation |
|------|-----------|
| COLRv1 paint graph recursion unbounded | Depth limit + cycle detection in graph traversal |
| CBDT raw bitmap formats (non-PNG) | Only support PNG formats 17, 18, 19; skip others |
| Memory pressure from bitmap glyphs in large strikes | Glyph cache eviction at atlas level (existing infra) |
| emoji font resources missing in CI | Fallback LiberationSans + empty generated-renders |
| COLRv0 layer stacking breaks existing monochrome | New code path gated on `hasColorTable` flag; existing monochrome path untouched |

## Verification

- Existing monochrome GM tests continue to pass (no regression)
- New emoji GMs produce deterministic output (LiberationSans fallback or emoji font)
- `./gradlew :integration-tests:skia:compileTestKotlin` passes after each migration
- Reference images match when emoji font resources are present
