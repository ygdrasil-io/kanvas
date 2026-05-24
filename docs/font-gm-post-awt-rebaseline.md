# Font GM Post-AWT Rebaseline

This document tracks the first post-#948 classification for font-related GM
coverage. It is intentionally limited to status, blockers, and wording cleanup;
feature work belongs in follow-up PRs.

## P0 Classification

### Green / Ratchet

- `BigTextGM`: active textual GM; drift is OpenType-vs-FreeType scaler and AA.
- `BigTextCrbug1370488GM`: active fallback-glyph GM; low similarity is caused
  by the missing `SpiderSymbol.ttf` fixture, with OpenType-vs-FreeType edge
  drift on the fallback glyph.
- `FontCacheGM`: active raster fallback for a GPU-oriented upstream GM; drift is
  OpenType-vs-FreeType scaler plus raster-vs-GPU positioning behavior.
- `ImageFiltersTextIfGM` / `ImageFiltersTextCfGM`: active textual filter GMs;
  drift is portable OpenType/Liberation vs upstream embedded font metrics.
- `MixedTextBlobsGM`: active text-blob GM with emoji run skipped until color
  emoji table dispatch exists.
- `TypefaceStylingGM`: active GM; still a ratchet for embolden/style synthesis.
- `TypefaceStylesKerningGM`: active kerning/style ratchet.

### P1 OpenType Backend Guards

- `TypefaceStylingGM`
- `UserFontGM`
- `FontationsTest` / `typeface_fontations_*`
- `FontScalerDistortableGM`
- `BigTextCrbug1370488GM`
- `GlyphPosGM`
- `LcdTextSizeGM`

These should be revalidated first because they cover style matching, custom
typefaces, large glyphs, glyph positioning, text blobs, and variable-font
plumbing.

### P2 Color-Font Split

- COLRv0/CPAL behavior belongs in active OpenType tests and should stay green.
- `ColrV1GM`, `FontPaletteGM`, `PaletteGM`, `FontPaletteTest`, and
  `ColrV1Test` remain gated by COLRv1 rendering and/or missing fixture fonts.
- Bitmap/SVG color font formats should remain separate follow-ups.

### P3 Textual Tolerance Ratchets

- `GammatextGM`
- `GradTextGM`
- `MixedTextBlobsGM`
- `ImageFiltersTextGM`
- `SurfacePropsGM`
- `BadAppleGM`

Ratchet these only after collecting post-AWT measurements from the pure Kotlin
OpenType path.

### P4 Shaping / Emoji / SDF Blockers

- Complex shaping, bidi, ligatures, script shaping, and multi-font fallback are
  tracked by #927.
- Emoji table dispatch remains separate from the simple OpenType glyph path.
- Distance-field text GMs stay gated by the raster SDF text path.

### P5 Fixture / Format Blockers

- `TypefaceRenderingPfaGM` and `TypefaceRenderingPfbGM` remain blocked by
  Type1/PFA/PFB fixture and format support.
- Missing Skia fixture fonts should be classified separately from OpenType
  backend regressions.

## Wording Rule

Active GM comments should not say that font rendering uses AWT when the code now
uses the pure Kotlin OpenType path. Prefer one of:

- "OpenType-vs-FreeType scaler drift" for raster differences.
- "COLRv1 renderer missing" for COLRv1 color glyphs.
- "emoji table dispatch missing" for color emoji.
- "shaping tracked by #927" for bidi, ligatures, and complex scripts.
- "fixture / format blocker" for missing fonts or unsupported Type1 formats.
