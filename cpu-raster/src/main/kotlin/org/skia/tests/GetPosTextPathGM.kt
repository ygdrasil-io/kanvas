package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/getpostextpath.cpp` (`getpostextpath`).
 *
 * Two stacked text renderings of `"Ham bur ge fons"` :
 *
 *  1. Top : `drawSimpleText` at origin `(0, 0)` followed by a red
 *     stroked outline of the *same* glyphs reconstructed via
 *     `ToolUtils::get_text_path(font, text, len, encoding, nullptr)`.
 *     The stroked outline should overlay the filled text exactly —
 *     the test guards a historical bug where space characters were
 *     dropped from the reconstructed path's per-glyph advances.
 *
 *  2. Bottom : a [SkTextBlob] built with per-glyph positions (random
 *     y-jitter via [SkRandom.nextSScalar1]) rendered with
 *     `drawTextBlob`, followed by the corresponding red stroked
 *     reconstruction via `get_text_path(..., &pos[0])`. Same overlay
 *     invariant.
 *
 * **Helper port** : `ToolUtils::get_text_path` does not exist in
 * `:kanvas-skia`'s [ToolUtils], so we inline a local helper —
 * [getTextPath] — built from the available [SkFont] glyph-walk
 * primitives ([SkFont.unicharsToGlyphs], [SkFont.getPath],
 * [SkFont.getWidth]). This matches the C++ helper's behaviour for
 * the ASCII-only input used by this GM.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(getpostextpath, canvas, 480, 780) {
 *     const char* text = "Ham bur ge fons";
 *     size_t len = strlen(text);
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setSize(48);
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     canvas->translate(10, 64);
 *     canvas->drawSimpleText(text, len, SkTextEncoding::kUTF8, 0, 0, font, paint);
 *     SkPath path = ToolUtils::get_text_path(font, text, len, SkTextEncoding::kUTF8, nullptr);
 *     strokePath(canvas, path);
 *
 *     SkAutoToGlyphs atg(font, text, len, SkTextEncoding::kUTF8);
 *     const size_t count = atg.count();
 *     AutoTArray<SkPoint>  pos(count);
 *     AutoTArray<SkScalar> widths(count);
 *     font.getWidths(atg.glyphs(), widths);
 *
 *     SkRandom rand;
 *     SkScalar x = 20, y = 100;
 *     for (size_t i = 0; i < count; ++i) {
 *         pos[i].set(x, y + rand.nextSScalar1() * 24);
 *         x += widths[i];
 *     }
 *
 *     canvas->translate(0, 64);
 *     canvas->drawTextBlob(SkTextBlob::MakeFromPosText(text, len, pos, font), 0, 0, paint);
 *     path = ToolUtils::get_text_path(font, text, len, SkTextEncoding::kUTF8, &pos[0]);
 *     strokePath(canvas, path);
 * }
 * ```
 */
public class GetPosTextPathGM : GM() {
    override fun getName(): String = "getpostextpath"
    override fun getISize(): SkISize = SkISize.Make(480, 780)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val text = "Ham bur ge fons"
        val font = ToolUtils.DefaultPortableFont().apply { size = 48f }

        val paint = SkPaint().apply { isAntiAlias = true }

        c.translate(10f, 64f)

        // 1) Stroke + fill at default advance positions.
        c.drawSimpleText(text, text.length, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
        val path = getTextPath(font, text, positions = null)
        strokePath(c, path)

        // 2) Compute per-glyph positions with random y-jitter and the
        //    font's natural advance widths.
        val glyphs = textToGlyphs(font, text)
        val count = glyphs.size
        val widths = FloatArray(count) { i -> font.getWidth(glyphs[i]) }
        val pos = Array(count) { SkPoint.Make(0f, 0f) }

        val rand = SkRandom()
        var x = 20f
        val y = 100f
        for (i in 0 until count) {
            pos[i] = SkPoint.Make(x, y + rand.nextSScalar1() * 24f)
            x += widths[i]
        }

        c.translate(0f, 64f)

        val blob = run {
            val builder = SkTextBlobBuilder()
            val rec = builder.allocRunPos(font, count)
            for (i in 0 until count) {
                rec.glyphs[i] = glyphs[i]
                rec.pos[i * 2] = pos[i].fX
                rec.pos[i * 2 + 1] = pos[i].fY
            }
            builder.make()
        } ?: return
        c.drawTextBlob(blob, 0f, 0f, paint)

        val pathPos = getTextPath(font, text, positions = pos)
        strokePath(c, pathPos)
    }

    private fun strokePath(canvas: SkCanvas, path: SkPath) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Inline port of `ToolUtils::get_text_path(font, text, len,
     * SkTextEncoding::kUTF8, positions)`.
     *
     * Walks every glyph of [text] (treated as one code point per
     * Kotlin Char — the upstream GM uses ASCII only), fetches each
     * glyph's per-em path from [SkFont.getPath], offsets it by the
     * cumulative advance (when [positions] is `null`) or by the
     * caller-supplied `(x, y)` (otherwise), and appends to a single
     * [SkPathBuilder].
     */
    private fun getTextPath(
        font: SkFont,
        text: String,
        positions: Array<SkPoint>?,
    ): SkPath {
        val count = text.length
        val unichars = IntArray(count) { text[it].code }
        val glyphs = ShortArray(count)
        font.unicharsToGlyphs(unichars, count, glyphs)

        val builder = SkPathBuilder()
        var advance = 0f
        for (i in 0 until count) {
            val gid = glyphs[i].toInt() and 0xFFFF
            val glyphPath = font.getPath(gid)
            if (glyphPath != null && !glyphPath.isEmpty()) {
                val dx: Float
                val dy: Float
                if (positions != null) {
                    dx = positions[i].fX
                    dy = positions[i].fY
                } else {
                    dx = advance
                    dy = 0f
                }
                builder.addPath(glyphPath, dx, dy)
            }
            if (positions == null) {
                advance += font.getWidth(gid)
            }
        }
        return builder.detach()
    }

    private fun textToGlyphs(font: SkFont, text: String): IntArray {
        val count = text.length
        val unichars = IntArray(count) { text[it].code }
        val glyphs16 = ShortArray(count)
        font.unicharsToGlyphs(unichars, count, glyphs16)
        return IntArray(count) { glyphs16[it].toInt() and 0xFFFF }
    }
}
