package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/lcdtext.cpp::LcdTextGM` (640 × 480).
 *
 * Renders the four `Subpixel{True,False} LCDRender{True,False}` text
 * permutations stacked vertically (36 px line height) on a white
 * background, using black antialiased glyphs.
 *
 * **kanvas-skia adaptation** :
 *  - `SkFont::setEdging(kSubpixelAntiAlias)` is silently downgraded to
 *    `kAntiAlias` by the kanvas-skia [SkFont] (cf. `SkFont.kt`). The
 *    "LCDRenderTrue" rows therefore render with plain AA instead of
 *    subpixel RGB-strip coverage — the structure (text + spacing) is
 *    preserved.
 *  - `SkFont::setSubpixel(true)` is honoured in the X-axis snap policy
 *    inside [SkCanvas.drawTextBlob] / `drawString` (see SkCanvas.kt).
 *
 * C++ source : see `gm/lcdtext.cpp::LcdTextGM`. Reference: `lcdtext.png`.
 */
public class LcdTextGM : GM() {

    private var fY: SkScalar = kTextHeight

    override fun getName(): String = "lcdtext"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        fY = kTextHeight
        drawText(c, "TEXT: SubpixelTrue LCDRenderTrue",   subpixel = true,  lcdRender = true)
        drawText(c, "TEXT: SubpixelTrue LCDRenderFalse",  subpixel = true,  lcdRender = false)
        drawText(c, "TEXT: SubpixelFalse LCDRenderTrue",  subpixel = false, lcdRender = true)
        drawText(c, "TEXT: SubpixelFalse LCDRenderFalse", subpixel = false, lcdRender = false)
    }

    private fun drawText(canvas: SkCanvas, string: String, subpixel: Boolean, lcdRender: Boolean) {
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            isDither = true
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), kTextHeight)
        if (subpixel) {
            font.isSubpixel = true
        }
        if (lcdRender) {
            // kanvas-skia downgrades this to kAntiAlias internally (cf. SkFont docstring).
            font.edging = SkFont.Edging.kSubpixelAntiAlias
        }
        canvas.drawString(string, 0f, fY, font, paint)
        fY += kTextHeight
    }

    private companion object {
        const val kTextHeight: SkScalar = 36f
    }
}
