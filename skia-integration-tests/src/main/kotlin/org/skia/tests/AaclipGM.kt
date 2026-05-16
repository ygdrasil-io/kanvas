package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetRGB
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/aaclip.cpp::aaclip` (240 × 120).
 *
 * Stress test for AA-clipping at sub-pixel offsets. For each of 5
 * horizontal positions the GM offsets the canvas by an additional
 * `0.2 × col` px, then draws three "rect tests" (square / column /
 * bar). Each test :
 *  - draws a 2-px-wide green border around the target,
 *  - draws a red rect filling the target,
 *  - clipRect-AA-clips to the target,
 *  - draws a slightly-larger blue rect over the same area.
 * Expected output : every test cell shows blue inside, surrounded
 * by a 2-px green border, no red leakage.
 */
public class AaclipGM : GM() {

    override fun getName(): String = "aaclip"
    override fun getISize(): SkISize = SkISize.Make(240, 120)

    private fun draw(canvas: SkCanvas, target: SkRect, x: Int, y: Int) {
        val borderPaint = SkPaint().apply {
            color = SkColorSetRGB(0x00, 0xDD, 0x00)
            isAntiAlias = true
        }
        val backgroundPaint = SkPaint().apply {
            color = SkColorSetRGB(0xDD, 0x00, 0x00)
            isAntiAlias = true
        }
        val foregroundPaint = SkPaint().apply {
            color = SkColorSetRGB(0x00, 0x00, 0xDD)
            isAntiAlias = true
        }

        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        var t = target.makeOutset(2f, 2f)
        canvas.drawRect(t, borderPaint)
        canvas.drawRect(target, backgroundPaint)
        canvas.clipRect(target, doAntiAlias = true)
        t = target.makeOutset(4f, 4f)
        canvas.drawRect(t, foregroundPaint)
        canvas.restore()
    }

    private fun drawSquare(canvas: SkCanvas, x: Int, y: Int) =
        draw(canvas, SkRect.MakeWH(10f, 10f), x, y)

    private fun drawColumn(canvas: SkCanvas, x: Int, y: Int) =
        draw(canvas, SkRect.MakeWH(1f, 10f), x, y)

    private fun drawBar(canvas: SkCanvas, x: Int, y: Int) =
        draw(canvas, SkRect.MakeWH(10f, 1f), x, y)

    private fun drawRectTests(canvas: SkCanvas) {
        drawSquare(canvas, 10, 10)
        drawColumn(canvas, 30, 10)
        drawBar(canvas, 10, 30)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Initial pixel-boundary-aligned draw.
        drawRectTests(c)
        // Repeat 4× with 0.2 / 0.4 / 0.6 / 0.8 px offsets accumulated.
        for (i in 0 until 4) {
            c.translate(1f / 5f, 1f / 5f)
            c.translate(50f, 0f)
            drawRectTests(c)
        }
    }
}
