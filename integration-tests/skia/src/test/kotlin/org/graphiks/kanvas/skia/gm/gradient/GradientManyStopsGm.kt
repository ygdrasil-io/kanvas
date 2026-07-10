package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients.cpp` (gradient_many_stops).
 * 200-stop gradient stressing texture-based and analytic evaluators.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientManyStopsGm : SkiaGm {
    override val name = "gradient_many_stops"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kStopCount = 200

        val stops = List(kStopCount) { i ->
            val (r, g, b) = when (i % 5) {
                0 -> Triple(1f, 0f, 0f)
                1, 2 -> Triple(0f, 1f, 0f)
                3 -> Triple(0f, 0f, 1f)
                else -> Triple(1f, 0f, 0f)
            }
            GradientStop(i.toFloat() / (kStopCount - 1), Color.fromRGBA(r, g, b, 1f))
        }

        val paint = Paint(shader = Shader.LinearGradient(
            start = Point(50f, 50f), end = Point(450f, 450f),
            stops = stops, tileMode = TileMode.CLAMP,
        ))
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 500f, 500f), paint)
    }
}
