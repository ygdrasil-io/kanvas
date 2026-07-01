package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/circulararcs.cpp::CircularArcsHairlineGM`.
 * 4-quadrant grid of hairline drawArc cells.
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class CircularArcsHairlineGm : SkiaGm {
    override val name = "circular_arcs_hairline"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawGrid(canvas, 0f, 0f, useCenter = false, aa = false, style = PaintStyle.STROKE, strokeWidth = 0f)
        drawGrid(canvas, kW / 2f, 0f, useCenter = true, aa = false, style = PaintStyle.STROKE, strokeWidth = 0f)
        drawGrid(canvas, 0f, kH / 2f, useCenter = false, aa = true, style = PaintStyle.STROKE, strokeWidth = 0f)
        drawGrid(canvas, kW / 2f, kH / 2f, useCenter = true, aa = true, style = PaintStyle.STROKE, strokeWidth = 0f)

        val linePaint = Paint(antiAlias = true, color = Color.BLACK)
        canvas.drawLine(kW / 2f, 0f, kW / 2f, kH.toFloat(), linePaint)
        canvas.drawLine(0f, kH / 2f, kW.toFloat(), kH / 2f, linePaint)
    }

    private fun drawGrid(c: GmCanvas, x: Float, y: Float, useCenter: Boolean, aa: Boolean, style: PaintStyle, strokeWidth: Float) {
        val pad = 20f
        val p0 = Paint(color = Color.RED, antiAlias = aa, style = style, strokeWidth = strokeWidth)
        val p1 = Paint(color = Color.BLUE, antiAlias = aa, style = style, strokeWidth = strokeWidth)
        val alphaRed = Color.fromRGBA(1f, 0f, 0f, 100f / 255f)
        val alphaBlue = Color.fromRGBA(0f, 0f, 1f, 100f / 255f)

        c.save()
        c.translate(pad + x, pad + y)
        for (start in kStarts) {
            c.save()
            for (sweep in kSweeps) {
                c.drawArc(kRect, start, sweep, useCenter, p0.copy(color = alphaRed))
                c.drawArc(kRect, start, -(360f - sweep), useCenter, p1.copy(color = alphaBlue))
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
