package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/**
 * Port of Skia's `gm/dashcircle.cpp`.
 * Reference outline vs dashed circle comparison across dash patterns.
 * @see https://github.com/google/skia/blob/main/gm/dashcircle.cpp
 */
class DashCircleGm : SkiaGm {
    override val name = "dashcircle"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 36.5
    override val width = 900
    override val height = 1200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val refPaint = Paint(
            color = Color.fromRGBA(0xbf / 255f, 0x3f / 255f, 0x7f / 255f, 1f),
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            antiAlias = true,
        )

        val radius = 125f
        val rx = radius + 20f
        val ry = radius + 20f
        val circumference = radius * 2f * PI.toFloat()
        val circle = Path { }.also { it.addCircle(0f, 0f, radius) }

        val wedges = intArrayOf(6, 12, 36)
        canvas.translate(rx, ry)

        for (wedge in wedges) {
            val arcLength = 360f / wedge
            canvas.save()
            for (dashExample in dashExamples) {
                var dashUnits = 0
                for (v in dashExample) dashUnits += v
                val unitLength = arcLength / dashUnits
                val refPath = Path { }
                var angle = 0f
                for (i in 0 until wedge) {
                    var i2 = 0
                    while (i2 < dashExample.size) {
                        val span = dashExample[i2] * unitLength
                        val startRad = angle * PI.toFloat() / 180f
                        val endRad = (angle + span) * PI.toFloat() / 180f
                        val x1 = rx * cos(startRad)
                        val y1 = ry * sin(startRad)
                        val x2 = rx * cos(endRad)
                        val y2 = ry * sin(endRad)
                        refPath.moveTo(0f, 0f)
                        refPath.lineTo(x1, y1)
                        refPath.arcTo(rx, ry, 0f, abs(span) > 180f, span > 0f, x2, y2)
                        refPath.close()
                        angle += span + dashExample[i2 + 1] * unitLength
                        i2 += 2
                    }
                }
                canvas.save()
                canvas.drawPath(refPath, refPaint)
                canvas.restore()

                val dashLength = circumference / wedge / dashUnits
                val intervals = FloatArray(dashExample.size) { i -> dashExample[i] * dashLength }
                val p = Paint(
                    antiAlias = true,
                    style = PaintStyle.STROKE,
                    strokeWidth = 10f,
                    pathEffect = PathEffect.Dash(intervals, 0f),
                )
                canvas.save()
                canvas.drawPath(circle, p)
                canvas.restore()
                canvas.translate(0f, radius * 2f + 50f)
            }
            canvas.restore()
            canvas.translate(radius * 2f + 50f, 0f)
        }
    }

    private companion object {
        private val dashExamples: Array<IntArray> = arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, 3),
            intArrayOf(1, 1, 3, 3),
            intArrayOf(1, 3, 2, 4),
        )
    }
}
