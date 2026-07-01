package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gradients.cpp::rgbw_sweep_gradient` (DEF_SIMPLE_GM, 100 × 100).
 * Full-revolution sweep with hardstops at each quarter: white, blue, red, green.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class RgbwSweepGradientGm : SkiaGm {
    override val name = "rgbw_sweep_gradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 20.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val stops = listOf(
            GradientStop(0f, Color.WHITE),
            GradientStop(0.25f, Color.WHITE),
            GradientStop(0.25f, Color.BLUE),
            GradientStop(0.5f, Color.BLUE),
            GradientStop(0.5f, Color.RED),
            GradientStop(0.75f, Color.RED),
            GradientStop(0.75f, Color.GREEN),
            GradientStop(1f, Color.GREEN),
        )
        val shader = Shader.SweepGradient(
            center = Point(50f, 50f),
            stops = stops,
            tileMode = TileMode.CLAMP,
        )
        val paint = Paint(shader = shader)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 100f, 100f), paint)
    }
}
