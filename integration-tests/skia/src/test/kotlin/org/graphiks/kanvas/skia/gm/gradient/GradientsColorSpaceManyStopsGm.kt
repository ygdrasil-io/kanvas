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
 * Port of Skia's gm/gradients.cpp (gradients_color_space_many_stops).
 * Many-stop gradient slice.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsColorSpaceManyStopsGm : SkiaGm {
    override val name = "gradients_color_space_many_stops"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(0x80 / 255f, 0x80 / 255f, 0x80 / 255f, 1f))
        )
        
        val stopCount = 200
        val stops = mutableListOf<GradientStop>()
        for (i in 0 until stopCount) {
            val t = i.toFloat() / (stopCount - 1).toFloat()
            val lerp = if (t < 0.5f) t * 2f else (1f - t) * 2f
            val r = (0 + (255 - 0) * lerp + 0.5f).toInt().coerceIn(0, 255)
            val g = (0 + (255 - 0) * lerp + 0.5f).toInt().coerceIn(0, 255)
            val b = (255 + (0 - 255) * t + 0.5f).toInt().coerceIn(0, 255)
            stops.add(GradientStop(
                i.toFloat() / (stopCount - 1).toFloat(),
                Color.fromRGBA(r / 255f, g / 255f, b / 255f, 1f)
            ))
        }
        
        val paint = Paint(shader = Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(500f, 500f),
            stops = stops, tileMode = TileMode.CLAMP,
        ))
        canvas.drawRect(Rect(0f, 0f, 500f, 500f), paint)
    }
}
