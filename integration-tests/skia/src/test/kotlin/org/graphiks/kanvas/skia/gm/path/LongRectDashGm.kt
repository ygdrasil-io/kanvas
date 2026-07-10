package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests dashed-stroke rendering of extremely large/small rects within a clipped area. */
class LongRectDashGm : SkiaGm {
    override val name = "longrect_dash"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val leftVals = floatArrayOf(20f, -100001f)
        val topVals = floatArrayOf(20f, -100001f)
        val rightVals = floatArrayOf(40f, 100001f)
        val bottomVals = floatArrayOf(40f, 100001f)

        val paint = Paint(
            color = Color.BLACK,
            strokeWidth = 5f,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.BEVEL,
            style = PaintStyle.STROKE,
            pathEffect = PathEffect.Dash(floatArrayOf(1f, 5f), 2f),
        )

        for (left in leftVals) {
            for (top in topVals) {
                for (right in rightVals) {
                    for (bottom in bottomVals) {
                        canvas.save()
                        canvas.clipRect(Rect.fromLTRB(10f, 10f, 50f, 50f))
                        canvas.drawRect(Rect.fromLTRB(left, top, right, bottom), paint)
                        canvas.restore()
                        canvas.translate(60f, 0f)
                    }
                }
                canvas.translate(-60f * 4, 60f)
            }
        }
    }
}
