package org.skia.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkRect

/**
 * End-to-end tests for `SkBitmapDevice.drawPath` with `kStroke_Style`.
 * Verifies that the stroker plumbing inside [SkBitmapDevice] produces the
 * expected stroked-band coverage on a raster canvas.
 */
class SkBitmapDeviceStrokeTest {

    private fun render(width: Int, height: Int, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).apply(draw)
        return bitmap
    }

    private fun isBlack(bitmap: SkBitmap, x: Int, y: Int, alphaMin: Int = 200): Boolean {
        val px = bitmap.getPixel(x, y)
        return SkColorGetR(px) < 64 && SkColorGetG(px) < 64 && SkColorGetB(px) < 64 &&
            SkColorGetA(px) >= alphaMin
    }

    @Test
    fun `stroked horizontal line lights up a thin band`() {
        val src = SkPathBuilder().moveTo(10f, 30f).lineTo(60f, 30f).detach()
        val bitmap = render(80, 60) {
            drawPath(src, SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 6f
            })
        }
        // Centre of the stroked band is filled.
        assertTrue(isBlack(bitmap, 35, 30), "centre of stroke band")
        // Strip ~3 px above and below centre is filled (halfWidth=3).
        assertTrue(isBlack(bitmap, 35, 28))
        assertTrue(isBlack(bitmap, 35, 32))
        // Outside the stroke band (5 px above) is empty.
        assertTrue(!isBlack(bitmap, 35, 25))
        // Beyond the line ends (x > 60) is empty.
        assertTrue(!isBlack(bitmap, 70, 30))
    }

    @Test
    fun `stroked closed rect lights up a frame with empty interior`() {
        val src = SkPath.Rect(SkRect.MakeLTRB(20f, 20f, 60f, 60f))
        val bitmap = render(80, 80) {
            drawPath(src, SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 4f
                isAntiAlias = true
            })
        }
        // On the frame band: filled.
        assertTrue(isBlack(bitmap, 20, 40), "left frame edge")
        assertTrue(isBlack(bitmap, 60, 40), "right frame edge")
        assertTrue(isBlack(bitmap, 40, 20), "top frame edge")
        assertTrue(isBlack(bitmap, 40, 60), "bottom frame edge")
        // Inside the rect (centre): empty (winding-rule cancels outer + inner rings).
        assertTrue(!isBlack(bitmap, 40, 40), "frame interior should be empty")
    }

    @Test
    fun `stroked L-shape produces a band with mitered corner`() {
        val src = SkPathBuilder()
            .moveTo(20f, 20f)
            .lineTo(60f, 20f)
            .lineTo(60f, 60f)
            .detach()
        val bitmap = render(80, 80) {
            drawPath(src, SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 6f
                strokeMiter = 4f
                isAntiAlias = true
            })
        }
        // Both legs of the L are stroked.
        assertTrue(isBlack(bitmap, 30, 20), "horizontal leg")
        assertTrue(isBlack(bitmap, 60, 50), "vertical leg")
        // Outer corner (miter point) lit up — miter extends out beyond the geometric corner.
        assertTrue(isBlack(bitmap, 62, 18), "miter point near outer corner")
    }

    @Test
    fun `kStrokeAndFill renders both fill and stroke`() {
        val src = SkPath.Rect(SkRect.MakeLTRB(20f, 20f, 60f, 60f))
        val bitmap = render(80, 80) {
            drawPath(src, SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStrokeAndFill_Style
                strokeWidth = 4f
                isAntiAlias = true
            })
        }
        // Frame band: filled (stroke contribution).
        assertTrue(isBlack(bitmap, 20, 40))
        assertTrue(isBlack(bitmap, 60, 40))
        // Interior: filled (fill contribution).
        assertTrue(isBlack(bitmap, 40, 40), "kStrokeAndFill should fill the interior")
    }

    @Test
    fun `stroked path under translation is offset correctly`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(20f, 0f).detach()
        val bitmap = render(60, 60) {
            translate(20f, 30f)
            drawPath(src, SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 4f
            })
        }
        // Stroked band lives at y≈30 (after translate), x in [20, 40].
        assertTrue(isBlack(bitmap, 30, 30), "stroke under translation")
        assertTrue(!isBlack(bitmap, 30, 10), "outside translated band")
    }
}
