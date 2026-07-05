package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's gm/runtimeshader.cpp::LinearGradientRT.
 * Draws two horizontal colour-gradient strips comparing sRGB vs linear interpolation.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class LinearGradientRTGm : SkiaGm {
    override val name = "linear_gradient_rt"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 266
    override val height = 143

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val color1 = Color.fromRGBA(0.75f, 0.25f, 0.0f, 1f)
        val color2 = Color.fromRGBA(0.0f, 0.75f, 0.25f, 1f)

        val stops = listOf(
            org.graphiks.kanvas.paint.GradientStop(0f, color1),
            org.graphiks.kanvas.paint.GradientStop(1f, color2),
        )

        val paint = Paint(shader = Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(256f, 0f),
            stops = stops, tileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
        ))

        canvas.save()
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.WHITE)
        )
        canvas.translate(5f, 5f)

        for (i in 0..1) {
            canvas.save()
            canvas.drawRect(Rect(0f, 0f, 256f, 64f), paint)
            canvas.restore()
            canvas.translate(0f, 64f + 5f)
        }

        canvas.restore()
    }
}
