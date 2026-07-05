package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/circulararcs.cpp::CircularArcsStrokeRoundGM`.
 * 4-quadrant grid of stroked drawArc cells with kRound_Cap.
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class CircularArcsStrokeRoundGm : SkiaGm {
    override val name = "circular_arcs_stroke_round"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 40.7
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawGrid(canvas, 0f, 0f, useCenter = false, aa = false, style = PaintStyle.STROKE, strokeWidth = 15f, cap = StrokeCap.ROUND)
        drawGrid(canvas, kW / 2f, 0f, useCenter = true, aa = false, style = PaintStyle.STROKE, strokeWidth = 15f, cap = StrokeCap.ROUND)
        drawGrid(canvas, 0f, kH / 2f, useCenter = false, aa = true, style = PaintStyle.STROKE, strokeWidth = 15f, cap = StrokeCap.ROUND)
        drawGrid(canvas, kW / 2f, kH / 2f, useCenter = true, aa = true, style = PaintStyle.STROKE, strokeWidth = 15f, cap = StrokeCap.ROUND)

        val linePaint = Paint(antiAlias = true, color = Color.BLACK)
        canvas.drawLine(kW / 2f, 0f, kW / 2f, kH.toFloat(), linePaint)
        canvas.drawLine(0f, kH / 2f, kW.toFloat(), kH / 2f, linePaint)
    }

    private fun drawGrid(c: GmCanvas, x: Float, y: Float, useCenter: Boolean, aa: Boolean, style: PaintStyle, strokeWidth: Float, cap: StrokeCap) {
        val pad = 20f
        val alphaRed = Color.fromRGBA(1f, 0f, 0f, 100f / 255f)
        val alphaBlue = Color.fromRGBA(0f, 0f, 1f, 100f / 255f)

        c.save()
        c.translate(pad + x, pad + y)
        for (start in kStarts) {
            c.save()
            for (sweep in kSweeps) {
                val p0 = Paint(color = alphaRed, antiAlias = aa, style = style, strokeWidth = strokeWidth, strokeCap = cap)
                val p1 = Paint(color = alphaBlue, antiAlias = aa, style = style, strokeWidth = strokeWidth, strokeCap = cap)
                c.drawArc(kRect, start, sweep, useCenter, p0)
                c.drawArc(kRect, start, -(360f - sweep), useCenter, p1)
                c.translate(kRect.width + pad, 0f)
            }
            c.restore()
            c.translate(0f, kRect.height + pad)
        }
        c.restore()
    }

    private companion object {
        const val kW: Int = 1000
        const val kH: Int = 1000
        val kStarts: FloatArray = floatArrayOf(0f, 10f, 30f, 45f, 90f, 165f, 180f, 270f)
        val kSweeps: FloatArray = floatArrayOf(1f, 45f, 90f, 130f, 180f, 184f, 300f, 355f)
        val kRect: Rect = Rect.fromLTRB(0f, 0f, 40f, 40f)
    }
}
