package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/hairlines.cpp::hairline_subdiv`.
 * 4 increasingly-large quadratic-Bézier hairlines at progressively-shifted origins.
 * @see https://github.com/google/skia/blob/main/gm/hairlines.cpp
 */
class HairlineSubdivGm : SkiaGm {
    override val name = "hairline_subdiv"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 43.6
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(45f, -25f)
        drawSubdividedQuad(canvas, 334, 334, 467, 267, Color.BLACK)

        canvas.translate(-185f, -150f)
        drawSubdividedQuad(canvas, 472, 472, 660, 378, Color.RED)

        canvas.translate(-275f, -200f)
        drawSubdividedQuad(canvas, 668, 668, 934, 535, Color.GREEN)

        canvas.translate(-385f, -260f)
        drawSubdividedQuad(canvas, 944, 944, 1320, 756, Color.BLUE)
    }

    private fun drawSubdividedQuad(canvas: GmCanvas, x0: Int, y0: Int, x1: Int, y1: Int, color: Color) {
        val paint = Paint(
            color = color,
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            antiAlias = true,
        )
        val path = Path {
            moveTo(0f, 0f)
            quadTo(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
        }
        canvas.drawPath(path, paint)
    }
}
