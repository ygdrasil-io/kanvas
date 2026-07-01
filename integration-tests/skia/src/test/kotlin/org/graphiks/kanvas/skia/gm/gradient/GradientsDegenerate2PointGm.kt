package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients.cpp::GradientsDegenrate2PointGM`.
 * Tests degenerate 2-point conical gradient (X^2 coefficient=0).
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsDegenerate2PointGm : SkiaGm {
    override val name = "gradients_degenerate_2pt"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 1f)

        val c0 = Point(-80f, 25f)
        val r0 = 70f
        val c1 = Point(0f, 25f)
        val r1 = 150f

        val shader = Shader.ConicalGradient(
            start = c0, startRadius = r0,
            end = c1, endRadius = r1,
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.01f, Color.GREEN),
                GradientStop(0.99f, Color.GREEN),
                GradientStop(1f, Color.RED),
            ),
            tileMode = TileMode.CLAMP,
        )
        val paint = Paint(shader = shader)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), paint)
    }
}
