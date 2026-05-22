package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/lcdtext.cpp::LcdTextSizeGM` (320 × 120).
 *
 * Skia auto-disables LCD requests when the text size exceeds an internal
 * limit (hard-coded here to 48 px). The GM places four "LCD"/"GRAY" labels
 * at corners formed by combining a text size and a canvas scale that
 * straddles the LCD / non-LCD boundary :
 *
 *  - top-left : size 47 (LCD ok)            → label "LCD"
 *  - top-right: size 49 (above limit)       → label "GRAY"
 *  - bottom-left: size 24 × scale 1.99      → label "LCD"
 *  - bottom-right: size 24 × scale 2.01     → label "GRAY"
 *
 * **kanvas-skia adaptation** : like [LcdTextGM], the subpixel-AA edging
 * is internally downgraded to plain AA (see `SkFont.kt` docstring), so
 * the visual structure (text size + position) is preserved but the
 * LCD-stripe shading is not visible.
 */
public class LcdTextSizeGM : GM() {

    override fun getName(): String = "lcdtextsize"
    override fun getISize(): SkISize = SkISize.Make(320, 120)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val lcdText = "LCD"
        val grayText = "GRAY"
        val kLCDTextSizeLimit = 48f

        data class Rec(val loc: SkPoint, val textSize: SkScalar, val scale: SkScalar, val text: String)
        val rec = listOf(
            Rec(SkPoint(10f, 50f),  kLCDTextSizeLimit - 1f, 1f,    lcdText),
            Rec(SkPoint(160f, 50f), kLCDTextSizeLimit + 1f, 1f,    grayText),
            Rec(SkPoint(10f, 100f), kLCDTextSizeLimit / 2f, 1.99f, lcdText),
            Rec(SkPoint(160f, 100f),kLCDTextSizeLimit / 2f, 2.01f, grayText),
        )

        for (r in rec) {
            val saveCount = c.save()
            val font = SkFont(ToolUtils.DefaultPortableTypeface(), r.textSize)
            font.edging = SkFont.Edging.kSubpixelAntiAlias
            scaleAbout(c, r.scale, r.scale, r.loc.fX, r.loc.fY)
            c.drawString(r.text, r.loc.fX, r.loc.fY, font, SkPaint())
            c.restoreToCount(saveCount)
        }
    }

    private fun scaleAbout(canvas: SkCanvas, sx: SkScalar, sy: SkScalar, px: SkScalar, py: SkScalar) {
        // SkMatrix::setScale(sx, sy, px, py) = T(px, py) · S(sx, sy) · T(-px, -py)
        canvas.translate(px, py)
        canvas.scale(sx, sy)
        canvas.translate(-px, -py)
    }
}
