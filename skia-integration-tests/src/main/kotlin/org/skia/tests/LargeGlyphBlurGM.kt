package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/largeglyphblur.cpp::largeglyphblur` (1920 × 600).
 *
 * Ensures that glyphs whose point size is **less** than the strike's
 * maximum but carry a **large blur** are still handled correctly —
 * i.e. the blur radius is applied to the glyph's coverage mask in
 * device space rather than getting silently dropped when the
 * pre-blur bbox would clip the glyph.
 *
 * Layout :
 *  - Row 1, baseline y = 200 : "Hamburgefons" via [SkTextBlob] under
 *    a 40-px-radius normal blur mask filter, then the same blob
 *    re-drawn opaque on top (so the un-blurred text "rides" on the
 *    blurred halo).
 *  - Row 2, baseline y = 500 : same content / colour stack, but drawn
 *    via [SkCanvas.drawSimpleText] instead of a pre-built blob.
 *
 * Font is `ToolUtils::DefaultPortableTypeface()` at 256 px ; sigma
 * derives from radius 40 via `SkBlurMask::ConvertRadiusToSigma(r)
 * = 0.57735·r + 0.5` ≈ 23.6.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(largeglyphblur, canvas, 1920, 600) {
 *     const char text[] = "Hamburgefons";
 *
 *     SkFont font(ToolUtils::DefaultPortableTypeface(), 256);
 *     auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
 *
 *     // setup up maskfilter
 *     const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(40));
 *
 *     SkPaint blurPaint;
 *     blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, kSigma));
 *
 *     canvas->drawTextBlob(blob, 10, 200, blurPaint);
 *     canvas->drawTextBlob(blob, 10, 200, SkPaint());
 *
 *     size_t len = strlen(text);
 *     canvas->drawSimpleText(text, len, SkTextEncoding::kUTF8, 10, 500, font, blurPaint);
 *     canvas->drawSimpleText(text, len, SkTextEncoding::kUTF8, 10, 500, font, SkPaint());
 * }
 * ```
 */
public class LargeGlyphBlurGM : GM() {
    override fun getName(): String = "largeglyphblur"
    override fun getISize(): SkISize = SkISize.Make(1920, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val text = "Hamburgefons"

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 256f)
        val blob = makeBlobFromText(text, font) ?: return

        // setup up maskfilter — radius=40 → sigma ≈ 23.594
        val kSigma = convertRadiusToSigma(40f)

        val blurPaint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, kSigma)
        }

        c.drawTextBlob(blob, 10f, 200f, blurPaint)
        c.drawTextBlob(blob, 10f, 200f, SkPaint())

        val len = text.length
        c.drawSimpleText(text, len, SkTextEncoding.kUTF8, 10f, 500f, font, blurPaint)
        c.drawSimpleText(text, len, SkTextEncoding.kUTF8, 10f, 500f, font, SkPaint())
    }

    /**
     * Mirrors the inline `SkTextBlob::MakeFromText` call. Builds a
     * single `HorizontalSpread` run at origin `(0, 0)` — the canvas's
     * `drawTextBlob(blob, x, y, paint)` adds the per-call offset on
     * top so the upstream "blob at (10, 200)" lookup still matches.
     */
    private fun makeBlobFromText(text: String, font: SkFont): SkTextBlob? {
        if (text.isEmpty()) return null
        val codePoints = text.codePoints().toArray()
        val n = codePoints.size
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codePoints, n, glyphsShort)

        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, n, 0f, 0f)
        for (i in 0 until n) {
            rec.glyphs[i] = glyphsShort[i].toInt() and 0xFFFF
        }
        return builder.make()
    }

    /** Mirrors `SkBlurMask::ConvertRadiusToSigma(radius)`. */
    private fun convertRadiusToSigma(radius: Float): Float =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f
}
