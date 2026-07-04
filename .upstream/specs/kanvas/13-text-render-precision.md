# Text Render Precision — Glyph Quad Sizing + Multi-Size TextBlob

**Date:** 2026-07-04
**Status:** Design approved

## Goal

Fix two root causes of low text GM similarity scores:
1. Hardcoded 10×10 glyph quads in `drawTextAtlasPass` → per-glyph metric-based sizing
2. Single `fontSize` in `TextBlob` → per-run `fontSize` in `KanvasGlyphRun`

## Scope

### Section 1: Per-glyph quad sizing

`GpuTextBlob` gains `glyphRects: List<Rect>` — actual device-pixel dimensions per glyph. `TextBridge.rasterizeViaFont()` fills them during atlas packing. `drawTextAtlasPass()` uses them instead of `w=10f, h=10f`.

Affects 5 fixable GMs: bigtext, overdraw_text_xform, blob_rsxform, blob_rsxform_distortable, textfilter_image.

### Section 2: Multi-size TextBlob

`KanvasGlyphRun` gains `fontSize: Float` property (default `12f`). `TextBlob.fontSize` becomes the fallback default. `TextBridge.rasterizeViaFont()` scales each run's glyphs at its own fontSize.

Affects 3 blocked GMs: textblobtransforms, textblobcolortrans, mixedtextblobs.

## Non-scope

- CTM-aware glyph resolution selection (bigtext_crbug_1370488 stays blocked)
- Shader support in text atlas pass (shadertext3, gammatext_color_shader stay blocked)
- BlendMode.SRC_IN in atlas pass (textblobblockreordering stays blocked)
- ColorFilter.Overdraw (overdrawcolorfilter stays blocked)
- Emoji font integration (emoji GMs stay at ~0%)

## Architecture

### File changes

| File | Change |
|------|--------|
| `kanvas/text/TextBlob.kt` | `KanvasGlyphRun` +`fontSize: Float = 12f` |
| `kanvas/text/TextBridge.kt` | `rasterizeViaFont()` uses `run.fontSize`, fills `glyphRects` |
| `kanvas/text/GpuTextBlob.kt` (if separate) or TextBridge.kt | `GpuTextBlob` +`glyphRects: List<Rect>` |
| `kanvas/surface/gpu/GPURenderer.kt` | `drawTextAtlasPass()` uses `gpuBlob.glyphRects` for quad sizing |
| `kanvas/text/Font.kt` | `toTextBlob()` passes `size` to each run's fontSize |
| `integration-tests/skia/*/gm/text/BlobRSXformGm.kt` | Set `run.fontSize` explicitly |
| `integration-tests/skia/*/gm/text/BlobRSXformDistortableGm.kt` | Set `run.fontSize` explicitly |
| `integration-tests/skia/*/gm/text/TextBlobTransformsGm.kt` | Set per-run fontSize |
| `integration-tests/skia/*/gm/text/TextBlobColorTransGm.kt` | Set per-run fontSize |
| `integration-tests/skia/*/gm/text/MixedTextBlobsGm.kt` | Set per-run fontSize |

### Data flow — Section 1 (quad sizing)

```
TextBridge.rasterizeViaFont()
  → GlyphAtlasUploadPlanner.plan(entries) → Accepted(placements)
  → for each placement: region(r.x, r.y, r.width, r.height)
  → glyphRects[i] = Rect(0, 0, r.width.toFloat(), r.height.toFloat())
  → GpuTextBlob(..., glyphRects)

GPURenderer.drawTextAtlasPass(gpuBlob, ...)
  → for each glyph index i:
  → val rect = gpuBlob.glyphRects[i]
  → val w = rect.width   // was: 10f
  → val h = rect.height  // was: 10f
```

### Data flow — Section 2 (multi-size)

```
Font.toTextBlob("ABC", 0, 0)
  → KanvasGlyphRun(glyphs, positions, fontSize = size)  // per-run

Font.toTextBlob("A" at 256pt, "B" at 72pt)  
  → Run 0: glyphs=["A"], positions=[(0,0)], fontSize=256f
  → Run 1: glyphs=["B"], positions=[(adv,0)], fontSize=72f

TextBridge.rasterizeViaFont(blob)
  → for each run:
  → for each glyph in run:
  → scaler.scaleGlyph(gid, run.fontSize)  // was: blob.fontSize
```

## Implementation

### Phase 1: Glyph quad sizing (1 file + GpuTextBlob)

1. Add `glyphRects: List<Rect>` to `GpuTextBlob`
2. In `TextBridge.rasterizeViaFont()`, fill `glyphRects` from atlas placements
3. In `GPURenderer.drawTextAtlasPass()`, use `gpuBlob.glyphRects[i].width/height`
4. Regenerate renders, update minSimilarity for the 5 fixed GMs

### Phase 2: Multi-size TextBlob (4 files)

1. Add `fontSize: Float = 12f` to `KanvasGlyphRun`
2. Update `Font.toTextBlob()` to pass `size` to runs
3. Update `TextBridge.rasterizeViaFont()` to use `run.fontSize`
4. Update 3 GM source files with explicit per-run fontSize
5. Regenerate renders, update minSimilarity for the 3 unblocked GMs

## Verification

- `./gradlew :kanvas:compileKotlin :integration-tests:skia:compileTestKotlin` passes
- `./gradlew :integration-tests:skia:generateSkiaRenders` produces valid PNGs for all text GMs
- `./gradlew :integration-tests:skia:test --tests "*SkiaGmRunner*"` shows improved scores for 8 GMs
- minSimilarity updated to actual scores for the 5+3 fixed GMs
