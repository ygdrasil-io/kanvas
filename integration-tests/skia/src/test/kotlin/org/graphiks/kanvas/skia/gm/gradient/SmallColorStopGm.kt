package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/gradients.cpp::small_color_stop`.
 * Regression test for very-small gradient color stop intervals
 * (`pos = {0, 0.003, 1}`). Renders a 3-stop two-point-conical
 * gradient over a yellow background — the second stop at 0.003
 * stresses the gradient evaluator's interpolation precision.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class SmallColorStopGm : SkiaGm {
    override val name = "small_color_stop"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 150

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rect = RectF32.ofOriginSize(0f, 0f, 100f, 150f)

        canvas.drawRect(rect, Paint(color = Color.fromRGBA(1f, 1f, 0f, 1f)))

        val stops = listOf(
            GradientStop(0f, Color.GREEN),
            GradientStop(0.003f, Color.RED),
            GradientStop(1f, Color.fromRGBA(1f, 1f, 0f, 1f)),
        )
        val shader = Shader.ConicalGradient(
            start = Point2F32(200f, 25f), startRadius = 20f,
            end = Point2F32(200f, 25f), endRadius = 10f,
            stops = stops, tileMode = TileMode.CLAMP,
        )
        canvas.drawRect(rect, Paint(shader = shader))
    }
}
