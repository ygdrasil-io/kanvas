package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashing.cpp` (DashingGM).
 * Grid of dashed drawLine calls with varying stroke widths, intervals and AA.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class DashingGm : SkiaGm {
    override val name = "dashing"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 340

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint(style = PaintStyle.STROKE)

        canvas.translate(20f, 20f)
        canvas.translate(0f, 0.5f)

        for (width0 in 0..2) {
            val w = width0 * width0 * width0
            for (pattern in arrayOf(intArrayOf(1, 1), intArrayOf(4, 1))) {
                for (aa in 0..1) {
                    paint = paint.copy(antiAlias = aa != 0, strokeWidth = w.toFloat())
                    val scale = if (w > 0) w else 1
                    drawLine(canvas, pattern[0] * scale, pattern[1] * scale, paint)
                    canvas.translate(0f, 20f)
                }
            }
        }

        showGiantDash(canvas)
        canvas.translate(0f, 20f)

        showZeroLenDash(canvas)
        canvas.translate(0f, 20f)

        paint = paint.copy(strokeWidth = 8f, antiAlias = false)
        drawLine(canvas, 0, 0, paint)
    }

    private fun drawLine(
        canvas: GmCanvas,
        on: Int, off: Int,
        paint: Paint,
        finalX: Float = 600f,
        finalY: Float = 0f,
        phase: Float = 0f,
        startX: Float = 0f,
        startY: Float = 0f,
    ) {
        val p = paint.copy(pathEffect = PathEffect.Dash(floatArrayOf(on.toFloat(), off.toFloat()), phase))
        canvas.drawLine(startX, startY, finalX, finalY, p)
    }

    private fun showGiantDash(canvas: GmCanvas) {
        val paint = Paint(style = PaintStyle.STROKE)
        drawLine(canvas, 1, 1, paint, finalX = 20_000f)
    }

    private fun showZeroLenDash(canvas: GmCanvas) {
        var paint = Paint(style = PaintStyle.STROKE)
        drawLine(canvas, 2, 2, paint, finalX = 0f)
        paint = paint.copy(strokeWidth = 2f)
        canvas.translate(0f, 20f)
        drawLine(canvas, 4, 4, paint, finalX = 0f)
    }
}
