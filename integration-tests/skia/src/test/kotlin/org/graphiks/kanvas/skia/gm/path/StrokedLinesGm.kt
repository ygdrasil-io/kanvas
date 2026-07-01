package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StrokedLinesGm : SkiaGm {
    override val name = "strokedlines"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 540
    override val height = 720

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.1f, 0.4f, 0.84f, 1f)

        canvas.translate(0f, (kRadius + kPad).toFloat())

        val whitePaint = Paint(color = Color.WHITE, style = PaintStyle.STROKE, strokeWidth = kStrokeWidth)

        val colors = listOf(
            whitePaint,
            whitePaint.copy(color = Color.fromRGBA(1f, 0f, 0f, 1f)),
        )

        for (paint in colors) {
            canvas.save()
            drawRow(canvas, paint)
            canvas.restore()
            canvas.translate(0f, 2f * (kRadius + kPad))
        }
    }

    private fun drawRow(canvas: GmCanvas, src: Paint) {
        canvas.translate((kRadius + kPad).toFloat(), 0f)

        val caps = listOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)
        for (cap in caps) {
            for (isAA in listOf(true, false)) {
                val paint = src.copy(strokeCap = cap, antiAlias = isAA)
                canvas.save()
                canvas.translate(2f * (kRadius + kPad), 0f)
                drawSnowflake(canvas, paint)
                canvas.restore()
            }
        }
    }

    private fun drawSnowflake(canvas: GmCanvas, paint: Paint) {
        var angle = 0f
        val step = PI.toFloat() / (kNumSpokes / 2f)
        for (i in 0 until kNumSpokes / 2) {
            val s = sin(angle) * kRadius
            val cs = cos(angle) * kRadius
            canvas.save()
            canvas.translate(0f, 0f)
            drawLine(canvas, -cs, -s, cs, s, paint)
            drawFins(canvas, 0.5f * cs, 0.5f * s, angle, paint)
            drawFins(canvas, -0.5f * cs, -0.5f * s, angle + PI.toFloat(), paint)
            canvas.restore()
            angle += step
        }
    }

    private fun drawFins(canvas: GmCanvas, ox: Float, oy: Float, angle: Float, paint: Paint) {
        val half = kRadius / 2f
        val piF = PI.toFloat()
        var s = sin(angle + piF / 4f) * half
        var cs = cos(angle + piF / 4f) * half
        drawLine(canvas, ox, oy, ox + cs, oy + s, paint)
        s = sin(angle - piF / 4f) * half
        cs = cos(angle - piF / 4f) * half
        drawLine(canvas, ox, oy, ox + cs, oy + s, paint)
    }

    private fun drawLine(canvas: GmCanvas, x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
        canvas.drawPath(Path { moveTo(x0, y0); lineTo(x1, y1) }, paint)
    }

    private companion object {
        const val kRadius: Int = 40
        const val kPad: Int = 5
        const val kNumSpokes: Int = 6
        const val kStrokeWidth: Float = 5.0f
    }
}
