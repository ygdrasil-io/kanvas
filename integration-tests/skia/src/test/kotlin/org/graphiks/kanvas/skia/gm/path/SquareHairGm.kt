package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/hairlines.cpp::squarehair`.
 * Hairline / thin-stroke regression matrix. For each combination of
 * 4 stroke widths (0, 0.999, 1, 1.001) × 3 caps (Butt, Square, Round)
 * × 2 AA modes draws a small set of primitives.
 * @see https://github.com/google/skia/blob/main/gm/hairlines.cpp
 */
class SquareHairGm : SkiaGm {
    override val name = "squarehair"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 240
    override val height = 360

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val widths = floatArrayOf(0f, 0.999f, 1f, 1.001f)
        val caps = arrayOf(StrokeCap.BUTT, StrokeCap.SQUARE, StrokeCap.ROUND)
        for (alias in booleanArrayOf(false, true)) {
            canvas.save()
            for (w in widths) {
                for (cap in caps) {
                    drawTests(canvas, w, cap, alias)
                }
            }
            canvas.restore()
            canvas.translate(120f, 0f)
        }
    }

    private fun drawTests(canvas: GmCanvas, width: Float, cap: StrokeCap, aa: Boolean) {
        val paint = Paint(
            strokeCap = cap,
            strokeWidth = width,
            antiAlias = aa,
            style = PaintStyle.STROKE,
        )
        canvas.drawLine(10f, 10f, 20f, 10f, paint)
        canvas.drawLine(30f, 10f, 30f, 20f, paint)
        canvas.drawLine(40f, 10f, 50f, 20f, paint)

        val pathA = Path {
            moveTo(60f, 10f)
            quadTo(60f, 20f, 70f, 20f)
            arcTo(10f, 10f, 0f, false, true, 80f, 10f)
        }
        canvas.drawPath(pathA, paint)

        val pathB = Path {
            moveTo(90f, 10f)
            cubicTo(90f, 20f, 100f, 20f, 100f, 10f)
            lineTo(110f, 10f)
        }
        canvas.drawPath(pathB, paint)

        canvas.translate(0f, 30f)
    }
}
