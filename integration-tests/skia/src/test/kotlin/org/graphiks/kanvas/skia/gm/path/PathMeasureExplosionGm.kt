package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/** Stress test for path measure with a 10K segment path and a zero-interval dash effect. */
class PathMeasureExplosionGm : SkiaGm {
    override val name = "PathMeasure_explosion"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val intervals = floatArrayOf(0f, 10e9f)
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            pathEffect = PathEffect.Dash(intervals, 0f),
        )

        val path = Path {
            moveTo(0f, 0f)
            for (i in 1..10000) {
                when (i) {
                    13, 68, 258, 1053, 1323, 2608 -> quadTo(i.toFloat(), 0f, i.toFloat(), 0f)
                    else -> lineTo(i.toFloat(), 0f)
                }
            }
        }
        canvas.drawPath(path, paint)
    }
}
