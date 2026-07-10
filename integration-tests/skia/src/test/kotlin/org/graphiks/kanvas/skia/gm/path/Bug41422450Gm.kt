package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Port of Skia's `gm/bug12866.cpp::bug41422450`.
 * Regression test for conics hitting the stroker recursion limit.
 * @see https://github.com/google/skia/blob/main/gm/bug12866.cpp
 */
class Bug41422450Gm : SkiaGm {
    override val name = "bug41422450"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 863
    override val height = 473

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val mat = Matrix33.makeAll(
            1f, 0.000113059919f, -2321738f,
            -0.00000139566271f, 0.0123444516f, -353f,
        )
        canvas.concat(mat)

        val circle = Rect.fromLTRB(-3299135.5f, -12312541.0f, 9897407.0f, 884000.812f)
        val cx = circle.center.x
        val cy = circle.center.y
        val rx = circle.width / 2f
        val ry = circle.height / 2f
        val startAngle = 59.9999962f * PI / 180f
        val endAngle = (59.9999962f + 59.9999962f) * PI / 180f
        val startX = cx + rx * cos(startAngle).toFloat()
        val startY = cy + ry * sin(startAngle).toFloat()
        val endX = cx + rx * cos(endAngle).toFloat()
        val endY = cy + ry * sin(endAngle).toFloat()

        val strokePath = Path {
            moveTo(startX, startY)
            arcTo(rx, ry, 0f, false, false, endX, endY)
        }

        val strokePaint = Paint(style = PaintStyle.STROKE, strokeWidth = 2f)
        canvas.drawPath(strokePath, strokePaint)
    }
}
