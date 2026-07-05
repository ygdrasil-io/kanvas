package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/tinyanglearcs.cpp`.
 * Tests arcTo with tiny sweep angles on very large radii.
 * @see https://github.com/google/skia/blob/main/gm/tinyanglearcs.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TinyAngleArcsGm : SkiaGm {
    override val name = "tinyanglearcs"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 94.9
    override val width = 620
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
        )

        canvas.translate(50f, 50f)

        val outerRadius = 100000.0f
        val innerRadius = outerRadius - 20.0f
        val centerX = 50f
        val centerY = outerRadius
        val startAngles = floatArrayOf(1.5f * PI.toFloat(), 1.501f * PI.toFloat())
        val sweepAngle = 10.0f / outerRadius

        for (i in startAngles.indices) {
            val path = Path { }
            val endAngle = startAngles[i] + sweepAngle
            path.moveTo(
                centerX + innerRadius * cos(startAngles[i]),
                centerY + innerRadius * sin(startAngles[i]),
            )
            path.lineTo(
                centerX + outerRadius * cos(startAngles[i]),
                centerY + outerRadius * sin(startAngles[i]),
            )
            arcTo(path,
                centerX, centerY, outerRadius,
                startAngles[i] * 180f / PI.toFloat(), endAngle * 180f / PI.toFloat(),
                true, true,
            )
            path.lineTo(
                centerX + innerRadius * cos(endAngle),
                centerY + innerRadius * sin(endAngle),
            )
            arcTo(path,
                centerX, centerY, innerRadius,
                endAngle * 180f / PI.toFloat(), startAngles[i] * 180f / PI.toFloat(),
                true, false,
            )
            canvas.drawPath(path, paint)
            canvas.translate(20f, 0f)
        }
    }

    private fun arcTo(
        path: Path,
        x: Float, y: Float, r: Float,
        startDeg: Float, endDeg: Float,
        ccw: Boolean,
        callArcTo: Boolean,
    ) {
        val rx = r
        val ry = r
        val startRad = startDeg * PI.toFloat() / 180f
        val endRad = endDeg * PI.toFloat() / 180f
        val sweep = if (ccw) endRad - startRad else startRad - endRad
        val sweepDeg = if (ccw) endDeg - startDeg else startDeg - endDeg

        val x1 = x + rx * cos(startRad)
        val y1 = y + ry * sin(startRad)
        val x2 = x + rx * cos(endRad)
        val y2 = y + ry * sin(endRad)
        val largeArc = abs(sweepDeg) > 180f
        val sweepFlag = sweepDeg > 0f

        if (!callArcTo) {
            path.moveTo(x1, y1)
        }
        path.arcTo(rx, ry, 0f, largeArc, sweepFlag, x2, y2)
    }
}
