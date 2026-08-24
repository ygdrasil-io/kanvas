package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/gradients.cpp::rgbw_sweep_gradient` (DEF_SIMPLE_GM, 100 × 100).
 * Full-revolution sweep with hardstops at each quarter: white, blue, red, green.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class RgbwSweepGradientGm : SkiaGm {
    override val name = "rgbw_sweep_gradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 20.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val stops = listOf(
            GradientStop(0f, ColorARGB.White),
            GradientStop(0.25f, ColorARGB.White),
            GradientStop(0.25f, ColorARGB.Blue),
            GradientStop(0.5f, ColorARGB.Blue),
            GradientStop(0.5f, ColorARGB.Red),
            GradientStop(0.75f, ColorARGB.Red),
            GradientStop(0.75f, ColorARGB.Green),
            GradientStop(1f, ColorARGB.Green),
        )
        val shader = Shader.SweepGradient(
            center = Point2F32(50f, 50f),
            stops = stops,
            tileMode = TileMode.CLAMP,
        )
        val paint = Paint(shader = shader)
        canvas.drawRect(RectF32.ofOriginSize(0f, 0f, 100f, 100f), paint)
    }
}
