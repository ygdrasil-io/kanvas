package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashing.cpp` (Dashing4GM).
 * Cartesian product of stroke widths, intervals, AA and caps.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class Dashing4Gm : SkiaGm {
    override val name = "dashing4"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 78.2
    override val width = 640
    override val height = 1100

    private fun drawLine(
        canvas: GmCanvas,
        on: Int, off: Int,
        paint: Paint,
        finalX: Float = 600f, finalY: Float = 0f,
        phase: Float = 0f,
        startX: Float = 0f, startY: Float = 0f,
    ) {
        val p = paint.copy(pathEffect = PathEffect.Dash(floatArrayOf(on.toFloat(), off.toFloat()), phase))
        canvas.drawLine(startX, startY, finalX, finalY, p)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint(style = PaintStyle.STROKE)

        canvas.translate(20f, 20f)
        canvas.translate(0.5f, 0.5f)

        val intervals = arrayOf(intArrayOf(1, 1), intArrayOf(4, 2), intArrayOf(0, 4))
        for (width0 in 0..2) {
            for (data in intervals) {
                for (aa in 0..1) {
                    for (cap in arrayOf(StrokeCap.ROUND, StrokeCap.SQUARE)) {
                        val w = width0 * width0 * width0
                        paint = paint.copy(
                            antiAlias = aa != 0,
                            strokeWidth = w.toFloat(),
                            strokeCap = cap,
                        )
                        val scale = if (w != 0) w else 1
                        drawLine(canvas, data[0] * scale, data[1] * scale, paint)
                        canvas.translate(0f, 20f)
                    }
                }
            }
        }

        for (aa in 0..1) {
            paint = paint.copy(
                antiAlias = aa != 0,
                strokeWidth = 8f,
                strokeCap = StrokeCap.SQUARE,
            )
            drawLine(canvas, 32, 16, paint, finalX = 20f, phase = 5f)
            canvas.translate(0f, 20f)
            drawLine(canvas, 32, 16, paint, finalX = 56f, phase = 5f)
            canvas.translate(0f, 20f)
            drawLine(canvas, 32, 16, paint, finalX = 584f, phase = 5f)
            canvas.translate(0f, 20f)
            drawLine(canvas, 32, 16, paint, finalX = 600f, finalY = 30f)
            canvas.translate(0f, 20f)
            drawLine(canvas, 32, 16, paint, finalX = 8f, phase = 40f)
            canvas.translate(0f, 20f)
        }

        canvas.translate(5f, 20f)
        paint = paint.copy(
            antiAlias = true,
            strokeCap = StrokeCap.ROUND,
            color = Color.fromRGBA(0f, 0f, 0f, 0x44 / 255f),
            strokeWidth = 40f,
        )
        drawLine(canvas, 0, 30, paint)

        canvas.translate(0f, 50f)
        paint = paint.copy(strokeCap = StrokeCap.SQUARE)
        drawLine(canvas, 0, 30, paint)

        canvas.translate(0f, 50f)
        paint = paint.copy(
            strokeCap = StrokeCap.ROUND,
            color = Color.BLACK,
            strokeWidth = 11f,
        )
        drawLine(canvas, 0, 30, paint, finalX = 0f)

        canvas.translate(100f, 0f)
        drawLine(canvas, 1, 30, paint, finalX = 0f)
    }
}
