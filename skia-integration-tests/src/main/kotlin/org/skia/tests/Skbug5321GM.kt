package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/skbug_5321.cpp`
 * ([`DEF_SIMPLE_GM(skbug_5321, canvas, 128, 128)`](https://github.com/google/skia/blob/main/gm/skbug_5321.cpp)).
 *
 * Two ways of laying out the UTF-8 string `"x̀y"` (an `x` followed
 * by a combining grave accent and a `y`) should produce identical
 * rasters — one via [SkCanvas.drawSimpleText] (Skia computes glyph
 * positions internally), the other via [SkTextBlobBuilder.allocRunPosH]
 * with per-glyph X computed from the font's per-glyph advance widths.
 * Upstream's bug was specific to PDF, but the GM is a useful
 * cross-pipeline rasteriser sanity check.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(skbug_5321, canvas, 128, 128) {
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setEdging(SkFont::Edging::kAlias);
 *     font.setSize(30);
 *
 *     const char text[] = "x\314\200y";  // utf8(u"x̀y")
 *     SkScalar x = 20, y = 45;
 *
 *     size_t byteLength = strlen(text);
 *     canvas->drawSimpleText(text, byteLength, SkTextEncoding::kUTF8, x, y, font, SkPaint());
 *
 *     y += font.getMetrics(nullptr);
 *     size_t glyph_count = font.countText(text, byteLength, SkTextEncoding::kUTF8);
 *     SkTextBlobBuilder builder;
 *
 *     auto rec = builder.allocRunPosH(font, glyph_count, y);
 *     font.textToGlyphs(text, byteLength, SkTextEncoding::kUTF8, {rec.glyphs, glyph_count});
 *
 *     font.getWidths({rec.glyphs, glyph_count}, {rec.pos, glyph_count});
 *     for (size_t i = 0; i < glyph_count; ++i) {
 *         SkScalar w = rec.pos[i];
 *         rec.pos[i] = x;
 *         x += w;
 *     }
 *
 *     canvas->drawTextBlob(builder.make(), 0, 0, SkPaint());
 * }
 * ```
 *
 * `:kanvas-skia` doesn't carry the `SkFont::countText`,
 * `SkFont::textToGlyphs(SkSpan, SkSpan)` and `SkFont::getWidths(SkSpan,
 * SkSpan)` shortcuts ; we reproduce them via [SkFont.unicharsToGlyphs]
 * (codepoint glyph mapping) and per-glyph [SkFont.getWidth] (advance
 * width) — the same data the upstream span-based overloads marshal.
 */
public class Skbug5321GM : GM() {
    override fun getName(): String = "skbug_5321"
    override fun getISize(): SkISize = SkISize.Make(128, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font = ToolUtils.DefaultPortableFont().apply {
            edging = SkFont.Edging.kAlias
            size = 30f
        }

        // utf8(u"x̀y") — `x`, U+0300 combining grave accent, `y`.
        val text = "x̀y"
        var x = 20f
        var y = 45f

        // First draw — drawSimpleText (Skia computes positions internally).
        c.drawSimpleText(text, text.length, SkTextEncoding.kUTF8, x, y, font, SkPaint())

        // Bump baseline by one line spacing (font.getMetrics(nullptr) returns
        // the recommended spacing). We have getSpacing() for the same value.
        y += font.getSpacing()

        // Resolve codepoints → glyph IDs (mirrors `font.textToGlyphs(text, …)`).
        val codepoints = text.codePoints().toArray()
        val glyphCount = codepoints.size
        val glyphsShort = ShortArray(glyphCount)
        font.unicharsToGlyphs(codepoints, glyphCount, glyphsShort)

        // allocRunPosH gives us per-glyph X with shared baseline Y.
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPosH(font, glyphCount, y)
        for (i in 0 until glyphCount) {
            rec.glyphs[i] = glyphsShort[i].toInt() and 0xFFFF
        }

        // `font.getWidths(glyphs, pos)` — fill the position array with each
        // glyph's advance width, then convert advances → cumulative X just
        // like the upstream loop.
        for (i in 0 until glyphCount) {
            rec.pos[i] = font.getWidth(rec.glyphs[i])
        }
        for (i in 0 until glyphCount) {
            val w = rec.pos[i]
            rec.pos[i] = x
            x += w
        }

        val blob = builder.make() ?: return
        c.drawTextBlob(blob, 0f, 0f, SkPaint())
    }
}
