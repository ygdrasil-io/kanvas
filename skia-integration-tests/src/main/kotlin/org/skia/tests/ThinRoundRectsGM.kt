package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector

/**
 * Port of Skia's `gm/thinrects.cpp::ThinRectsGM(round=true)`
 * (`thinroundrects`, 240 × 320).
 *
 * Sister of the existing [ThinRectsGM] — same vert / horiz / square
 * thin-rect matrix at 1/8-pixel translations, but each cell is
 * `drawRRect` instead of `drawRect`. Vert rects use a 4-corner
 * `setRectRadii` ; horiz rects use `setNinePatch` ; squares use a
 * uniform `setRectXY`.
 *
 * The round variant exists as a separate class (rather than reusing
 * `ThinRectsGM(fRound=true)`) because the existing port asserts
 * `!fRound` in its init block.
 */
public class ThinRoundRectsGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "thinroundrects"
    override fun getISize(): SkISize = SkISize.Make(240, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val white = SkPaint().apply { color = SK_ColorWHITE; isAntiAlias = true }
        val green = SkPaint().apply { color = SK_ColorGREEN; isAntiAlias = true }

        for (i in 0 until 8) {
            c.save()
            c.translate(i * 0.125f, i * 40f)
            drawVertRects(c, white)
            c.translate(40f, 0f)
            drawVertRects(c, green)
            c.restore()

            c.save()
            c.translate(80f, i * 40f + i * 0.125f)
            drawHorizRects(c, white)
            c.translate(40f, 0f)
            drawHorizRects(c, green)
            c.restore()

            c.save()
            c.translate(160f + i * 0.125f, i * 40f + i * 0.125f)
            drawSquares(c, white)
            c.translate(40f, 0f)
            drawSquares(c, green)
            c.restore()
        }
    }

    private val vertRadii = arrayOf(
        SkVector(1f / 32f, 2f / 32f),
        SkVector(3f / 32f, 1f / 32f),
        SkVector(2f / 32f, 3f / 32f),
        SkVector(1f / 32f, 3f / 32f),
    )

    private fun drawVertRects(c: SkCanvas, p: SkPaint) {
        val rects = arrayOf(
            SkRect.MakeLTRB(1f, 1f, 5.0f, 21f),
            SkRect.MakeLTRB(8f, 1f, 10.0f, 21f),
            SkRect.MakeLTRB(13f, 1f, 14.0f, 21f),
            SkRect.MakeLTRB(17f, 1f, 17.5f, 21f),
            SkRect.MakeLTRB(21f, 1f, 21.25f, 21f),
            SkRect.MakeLTRB(25f, 1f, 25.125f, 21f),
            SkRect.MakeLTRB(29f, 1f, 29.0f, 21f),
        )
        for (r in rects) {
            val rrect = SkRRect().apply { setRectRadii(r, vertRadii) }
            c.drawRRect(rrect, p)
        }
    }

    private fun drawHorizRects(c: SkCanvas, p: SkPaint) {
        val rects = arrayOf(
            SkRect.MakeLTRB(1f, 1f, 21f, 5.0f),
            SkRect.MakeLTRB(1f, 8f, 21f, 10.0f),
            SkRect.MakeLTRB(1f, 13f, 21f, 14.0f),
            SkRect.MakeLTRB(1f, 17f, 21f, 17.5f),
            SkRect.MakeLTRB(1f, 21f, 21f, 21.25f),
            SkRect.MakeLTRB(1f, 25f, 21f, 25.125f),
            SkRect.MakeLTRB(1f, 29f, 21f, 29.0f),
        )
        for (r in rects) {
            val rrect = SkRRect().apply { setNinePatch(r, 1f / 32f, 2f / 32f, 3f / 32f, 4f / 32f) }
            c.drawRRect(rrect, p)
        }
    }

    private fun drawSquares(c: SkCanvas, p: SkPaint) {
        val rects = arrayOf(
            SkRect.MakeLTRB(1f, 1f, 5.0f, 5.0f),
            SkRect.MakeLTRB(8f, 8f, 10.0f, 10.0f),
            SkRect.MakeLTRB(13f, 13f, 14.0f, 14.0f),
            SkRect.MakeLTRB(17f, 17f, 17.5f, 17.5f),
            SkRect.MakeLTRB(21f, 21f, 21.25f, 21.25f),
            SkRect.MakeLTRB(25f, 25f, 25.125f, 25.125f),
            SkRect.MakeLTRB(29f, 29f, 29.0f, 29.0f),
        )
        for (r in rects) {
            val rrect = SkRRect().apply { setRectXY(r, 1f / 32f, 2f / 32f) }
            c.drawRRect(rrect, p)
        }
    }
}
