package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/manyarcs.cpp`.
 * Tests arcTo with many combinations of sweep/start angles and direction flags.
 * @see https://github.com/google/skia/blob/main/gm/manyarcs.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

class ManyArcsGm : SkiaGm {
    override val name = "manyarcs"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 620
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            antiAlias = true,
        )

        canvas.translate(10f, 10f)

        val sweepAngles = floatArrayOf(
            -123.7f, -2.3f, -2f, -1f, -0.3f, -0.000001f, 0f, 0.000001f, 0.3f, 0.7f,
            1f, 1.3f, 1.5f, 1.7f, 1.99999f, 2f, 2.00001f, 2.3f, 4.3f, 3934723942837.3f,
        )
        for (i in sweepAngles.indices) {
            sweepAngles[i] *= 180f
        }

        val startAngles = floatArrayOf(-1f, -0.5f, 0f, 0.5f)
        for (i in startAngles.indices) {
            startAngles[i] *= 180f
        }

        var anticlockwise = false
        var sign = 1f
        val n = startAngles.size
        for (i in 0 until n * 2) {
            if (i == n) {
                anticlockwise = true
                sign = -1f
            }
            val startAngle = startAngles[i % n] * sign
            canvas.save()
            for (j in sweepAngles.indices) {
                val path = Path { moveTo(0f, 2f) }
                htmlCanvasArc(path, 18f, 15f, 10f, startAngle, startAngle + sweepAngles[j] * sign, anticlockwise)
                path.lineTo(0f, 28f)
                canvas.drawPath(path, paint)
                canvas.translate(30f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 40f)
        }
    }

    private fun htmlCanvasArc(
        path: Path,
        x: Float, y: Float, r: Float,
        start: Float, end: Float,
        ccw: Boolean,
    ) {
        val sweep = if (ccw) end - start else start - end
        val startRad = Math.toRadians(start.toDouble())
        val endRad = Math.toRadians((start + sweep).toDouble())
        val x1 = x + r * cos(startRad).toFloat()
        val y1 = y + r * sin(startRad).toFloat()
        val x2 = x + r * cos(endRad).toFloat()
        val y2 = y + r * sin(endRad).toFloat()
        val largeArc = abs(sweep) > 180f
        val sweepFlag = sweep > 0f
        path.arcTo(r, r, 0f, largeArc, sweepFlag, x2, y2)
    }
}
