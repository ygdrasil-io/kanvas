package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/drawlines_with_local_matrix.cpp`.
 *  Tests drawLines with local matrix — draws lines and points with
 *  gradient shaders and local matrix transforms.
 *  @see https://github.com/google/skia/blob/main/gm/drawlines_with_local_matrix.cpp
 */
class DrawLinesWithLocalMatrixGm : SkiaGm {
    override val name = "drawlines_with_local_matrix"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 500f, 500f))

        val indigo = Color.fromRGBA(0x4b / 255f, 0f, 0x82 / 255f, 1f)
        val violet = Color.fromRGBA(0xee / 255f, 0x82 / 255f, 0xee / 255f, 1f)
        val stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(2f / 6f, Color(0xFFFFFF00u)),
            GradientStop(3f / 6f, Color.GREEN),
            GradientStop(4f / 6f, Color.BLUE),
            GradientStop(5f / 6f, indigo),
            GradientStop(1f, violet),
        )
        val radial = Shader.RadialGradient(
            center = Point(250f, 250f),
            radius = 280f,
            stops = stops,
            tileMode = TileMode.CLAMP,
        )

        val grad = Paint(
            antiAlias = true,
            strokeCap = StrokeCap.SQUARE,
            shader = radial,
        )
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 500f, 500f), grad)

        val white = Paint(
            antiAlias = true,
            strokeCap = StrokeCap.SQUARE,
            color = Color.WHITE,
        )

        fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
            val pts = listOf(Point(x0, y0), Point(x1, y1))
            canvas.drawPoints(PointMode.LINES, pts, white.copy(
                style = PaintStyle.STROKE,
                strokeWidth = w,
            ))
            canvas.drawPoints(PointMode.LINES, pts, grad.copy(
                style = PaintStyle.STROKE,
                strokeWidth = w - 4f,
            ))
        }

        drawLine(20f, 20f, 200f, 120f, 20f)
        drawLine(20f, 200f, 20f, 100f, 20f)
        drawLine(480f, 20f, 400f, 400f, 20f)
        drawLine(50f, 480f, 260f, 100f, 20f)
        drawLine(270f, 20f, 380f, 210f, 20f)
        drawLine(280f, 280f, 400f, 480f, 20f)
        drawLine(160f, 375f, 280f, 375f, 20f)
        drawLine(220f, 410f, 220f, 470f, 20f)
        drawLine(250f, 250f, 250f, 250f, 20f)
    }
}
