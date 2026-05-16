package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/thinrects.cpp` (`ThinRectsGM`).
 *
 * Phase 2 implements the **non-round** variant only. The round variant
 * (`fRound = true`, GM name `thinroundrects`) requires `SkRRect`, deferred
 * to Phase 4.
 *
 * Draws thin axis-aligned rects at 1/8-pixel translation increments, with
 * widths/heights down to 1/8 of a pixel — every cell exercises a different
 * fractional sub-pixel boundary, which is the whole point of the analytic
 * AA path in [SkBitmapDevice.fillRectAA].
 */
public class ThinRectsGM(private val fRound: Boolean = false) : GM() {
    init {
        require(!fRound) {
            "ThinRectsGM(fRound=true) draws via drawRRect — deferred to Phase 4 (SkRRect)"
        }
        setBGColor(SK_ColorBLACK)
    }

    override fun getName(): String = if (fRound) "thinroundrects" else "thinrects"
    override fun getISize(): SkISize = SkISize.Make(240, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val white = SkPaint().apply { color = SK_ColorWHITE; isAntiAlias = true }
        val green = SkPaint().apply { color = SK_ColorGREEN; isAntiAlias = true }

        for (i in 0 until 8) {
            c.save()
            c.translate(i * 0.125f, i * 40.0f)
            drawVertRects(c, white)
            c.translate(40.0f, 0.0f)
            drawVertRects(c, green)
            c.restore()

            c.save()
            c.translate(80.0f, i * 40.0f + i * 0.125f)
            drawHorizRects(c, white)
            c.translate(40.0f, 0.0f)
            drawHorizRects(c, green)
            c.restore()

            c.save()
            c.translate(160.0f + i * 0.125f, i * 40.0f + i * 0.125f)
            drawSquares(c, white)
            c.translate(40.0f, 0.0f)
            drawSquares(c, green)
            c.restore()
        }
    }

    private fun drawVertRects(c: SkCanvas, p: SkPaint) {
        val rects = arrayOf(
            SkRect.MakeLTRB(1f, 1f,    5.0f, 21f),    // 4 pix wide
            SkRect.MakeLTRB(8f, 1f,   10.0f, 21f),    // 2 pix wide
            SkRect.MakeLTRB(13f, 1f,  14.0f, 21f),    // 1 pix wide
            SkRect.MakeLTRB(17f, 1f,  17.5f, 21f),    // 1/2 pix wide
            SkRect.MakeLTRB(21f, 1f,  21.25f, 21f),   // 1/4 pix wide
            SkRect.MakeLTRB(25f, 1f,  25.125f, 21f),  // 1/8 pix wide
            SkRect.MakeLTRB(29f, 1f,  29.0f, 21f),    // 0 pix wide
        )
        for (r in rects) c.drawRect(r, p)
    }

    private fun drawHorizRects(c: SkCanvas, p: SkPaint) {
        val rects = arrayOf(
            SkRect.MakeLTRB(1f, 1f,  21f,  5.0f),    // 4 pix high
            SkRect.MakeLTRB(1f, 8f,  21f, 10.0f),    // 2 pix high
            SkRect.MakeLTRB(1f, 13f, 21f, 14.0f),    // 1 pix high
            SkRect.MakeLTRB(1f, 17f, 21f, 17.5f),    // 1/2 pix high
            SkRect.MakeLTRB(1f, 21f, 21f, 21.25f),   // 1/4 pix high
            SkRect.MakeLTRB(1f, 25f, 21f, 25.125f),  // 1/8 pix high
            SkRect.MakeLTRB(1f, 29f, 21f, 29.0f),    // 0 pix high
        )
        for (r in rects) c.drawRect(r, p)
    }

    private fun drawSquares(c: SkCanvas, p: SkPaint) {
        val rects = arrayOf(
            SkRect.MakeLTRB(1f, 1f,    5.0f,   5.0f),     // 4 pix
            SkRect.MakeLTRB(8f, 8f,   10.0f,  10.0f),     // 2 pix
            SkRect.MakeLTRB(13f, 13f, 14.0f,  14.0f),     // 1 pix
            SkRect.MakeLTRB(17f, 17f, 17.5f,  17.5f),     // 1/2 pix
            SkRect.MakeLTRB(21f, 21f, 21.25f, 21.25f),    // 1/4 pix
            SkRect.MakeLTRB(25f, 25f, 25.125f, 25.125f),  // 1/8 pix
            SkRect.MakeLTRB(29f, 29f, 29.0f,  29.0f),     // 0 pix
        )
        for (r in rects) c.drawRect(r, p)
    }
}
