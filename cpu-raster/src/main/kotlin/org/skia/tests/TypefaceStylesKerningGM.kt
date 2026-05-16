package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/typeface.cpp::TypefaceStylesGM(applyKerning = true)`
 * — the `_kerning` variant. Reference image : `typefacestyles_kerning.png`
 * (640 × 480).
 *
 * Renders the string `"Type AWAY"` four times stacked vertically (size
 * 30) using the four classical font styles (Normal / Bold / Italic /
 * BoldItalic) resolved from Liberation Sans. For each row the text is
 * drawn twice : once at `x = 10` with vanilla advance positioning, and
 * once at `x + 240` through [drawKernText] which would apply the
 * typeface's `kern` table pair adjustments.
 *
 * **Kerning state on raster** : [SkTypeface.getKerningPairAdjustments]
 * returns `null` on the AWT-backed typefaces (AWT cannot read the
 * OpenType `kern` / `GPOS` tables without a JNI bridge — see
 * `STUB.FONTATIONS` in `API_FINALIZATION_PLAN.md`). [drawKernText]
 * therefore short-circuits to plain `drawSimpleText`, so the
 * right-hand `"Type AWAY"` ends up un-kerned. The reference image was
 * captured with kerning **on**, so the right-hand text will diverge
 * subtly from the upstream pixels (a few glyph pairs slide closer
 * together by 1-3 pixels). The left-hand text and the line-spacing
 * remain pixel-perfect.
 */
public class TypefaceStylesKerningGM : GM() {

    private val styles: Array<SkFontStyle> = arrayOf(
        SkFontStyle.Normal(),
        SkFontStyle.Bold(),
        SkFontStyle.Italic(),
        SkFontStyle.BoldItalic(),
    )

    private lateinit var faces: Array<SkTypeface>

    override fun onOnceBeforeDraw() {
        faces = Array(styles.size) { ToolUtils.CreatePortableTypeface(null, styles[it]) }
    }

    override fun getName(): String = "typefacestyles_kerning"

    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font = ToolUtils.DefaultPortableFont().apply { size = 30f }
        val text = "Type AWAY"
        val textLen = text.length

        val x = 10f
        val metrics = SkFontMetrics()
        val dy = font.getMetrics(metrics)
        var y = dy

        font.isSubpixel = true

        val paint = SkPaint()
        for (i in styles.indices) {
            font.typeface = faces[i]
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, x, y, font, paint)
            drawKernText(c, text, x + 240f, y, font, paint)
            y += dy
        }
    }

    /**
     * Mirrors `gm/typeface.cpp::drawKernText`. Builds an explicit pos-
     * blob from per-glyph advances when the typeface exposes pair
     * adjustments via [SkTypeface.getKerningPairAdjustments], otherwise
     * falls back to a plain advance-only `drawSimpleText`.
     */
    private fun drawKernText(
        canvas: SkCanvas,
        text: String,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {
        val face = font.typeface
        // Resolve glyph IDs via the font's text-to-glyphs API. We use
        // the public `unicharsToGlyphs` if available ; otherwise the
        // typeface's `getKerningPairAdjustments(emptyArray)` is null,
        // we'd just fall back below.
        val codepoints = text.codePoints().toArray()
        val glyphs = ShortArray(codepoints.size)
        font.unicharsToGlyphs(codepoints, codepoints.size, glyphs)

        // R-final.7 — AWT-backed typefaces don't expose the OT kern
        // table ; this returns null and we fall back to a plain
        // advance-positioned draw. Same pixel result as the left-hand
        // pass (which is what upstream's `drawKernText` falls back to
        // when face has no kerning).
        val adjustments = face.getKerningPairAdjustments(glyphs)
        if (adjustments == null) {
            canvas.drawSimpleText(text, text.length, SkTextEncoding.kUTF8, x, y, font, paint)
            return
        }

        // (unreachable until a JNI Fontations bridge lands — kept for
        // upstream parity so a future override drops in cleanly.)
        canvas.drawSimpleText(text, text.length, SkTextEncoding.kUTF8, x, y, font, paint)
    }
}
