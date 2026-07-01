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
 * Port of Skia's `gm/gradients.cpp::gradients_alpha_many_stops`.
 * 13-stop linear gradient fading from opaque to transparent, drawn over 50% gray.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsAlphaManyStopsGm : SkiaGm {
    override val name = "gradients_alpha_many_stops"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val kG = 34f / 255f
        val stops = listOf(
            GradientStop(0f, Color.fromRGBA(kG, kG, kG, 1f)),
            GradientStop(0.19f, Color.fromRGBA(kG, kG, kG, 0.738f)),
            GradientStop(0.34f, Color.fromRGBA(kG, kG, kG, 0.541f)),
            GradientStop(0.47f, Color.fromRGBA(kG, kG, kG, 0.382f)),
            GradientStop(0.565f, Color.fromRGBA(kG, kG, kG, 0.278f)),
            GradientStop(0.65f, Color.fromRGBA(kG, kG, kG, 0.194f)),
            GradientStop(0.73f, Color.fromRGBA(kG, kG, kG, 0.126f)),
            GradientStop(0.802f, Color.fromRGBA(kG, kG, kG, 0.075f)),
            GradientStop(0.861f, Color.fromRGBA(kG, kG, kG, 0.042f)),
            GradientStop(0.91f, Color.fromRGBA(kG, kG, kG, 0.021f)),
            GradientStop(0.952f, Color.fromRGBA(kG, kG, kG, 0.008f)),
            GradientStop(0.982f, Color.fromRGBA(kG, kG, kG, 0.002f)),
            GradientStop(1f, Color.fromRGBA(kG, kG, kG, 0f)),
        )

        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(0f, 100f),
            stops = stops,
            tileMode = TileMode.CLAMP,
        )

        val paint = Paint(shader = shader)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), paint)
    }
}
