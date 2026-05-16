package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkStroker
import org.skia.foundation.colorToRGB565
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/fatpathfill.cpp::fatpathfill` (288 × 480).
 *
 * Renders a 9×3 alpha bitmap zoomed 32× to make individual
 * pixel-coverage values visible. For each of 5 lines from `(1, 2)` to
 * `(4 + i, 1)`, we :
 *  1. Stroke-1-fill the line into the small surface (no AA — the
 *     stroker's coverage drops the `1/2`-pixel-wide line onto each
 *     touched pixel).
 *  2. Composite the stroked alpha into the main canvas.
 *  3. Overlay the unmodified line as a hairline (red AA stroke).
 *  4. Mark every pixel centre with a small filled circle so the
 *     coverage and pixel grid are visible side-by-side.
 *
 * Substitutes Skia's `skpathutils::FillPathWithPaint` with our
 * `SkStroker.fromPaint(paint).stroke(line)`.
 */
public class FatPathFillGM : GM() {

    private val zoom = 32
    private val smallW = 9
    private val smallH = 3
    private val repeatLoop = 5

    override fun getName(): String = "fatpathfill"
    override fun getISize(): SkISize = SkISize.Make(smallW * zoom, smallH * zoom * repeatLoop)

    private fun newSurface(width: Int, height: Int): SkSurface =
        SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(width, height))

    private fun drawPixelCenters(canvas: SkCanvas) {
        val paint = SkPaint().apply {
            color = colorToRGB565(0xFF0088FFu.toInt())
            isAntiAlias = true
        }
        for (y in 0 until smallH) {
            for (x in 0 until smallW) {
                canvas.drawCircle(x + 0.5f, y + 0.5f, 1.5f / zoom, paint)
            }
        }
    }

    private fun drawFatpath(canvas: SkCanvas, surface: SkSurface, path: SkPath) {
        val paint = SkPaint()
        surface.canvas.clear(SK_ColorTRANSPARENT)
        surface.canvas.drawPath(path, paint)
        surface.draw(canvas, 0f, 0f)

        paint.isAntiAlias = true
        paint.color = SK_ColorRED
        paint.style = SkPaint.Style.kStroke_Style
        canvas.drawPath(path, paint)

        drawPixelCenters(canvas)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val surface = newSurface(smallW, smallH)
        c.scale(zoom.toFloat(), zoom.toFloat())

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
        }

        for (i in 0 until repeatLoop) {
            val line = SkPath.Line(1f to 2f, (4f + i) to 1f)
            // Substitute for `skpathutils::FillPathWithPaint(line, paint, &builder)`.
            val fatPath = SkStroker.fromPaint(paint).stroke(line)
            drawFatpath(c, surface, fatPath)
            c.translate(0f, smallH.toFloat())
        }
    }
}
