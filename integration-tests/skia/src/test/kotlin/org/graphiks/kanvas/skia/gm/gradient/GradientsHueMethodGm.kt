package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/gradients.cpp::gradients_hue_method` (285 x 155, gray BG).
 * Renders HSL gradients with HSL interpolation.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsHueMethodGm : SkiaGm {
    override val name = "gradients_hue_method"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 285
    override val height = 155

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val gray = ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f)
        canvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = gray))
        canvas.translate(5f, 5f)

        val pts = Point2F32(0f, 0f) to Point2F32(200f, 0f)
        val rect = RectF32.ofOriginSize(0f, 0f, 200f, 20f)
        val font = Font(typeface, size = 12f)

        val repeatedColors = listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Red, ColorARGB.Red)
        val stops = repeatedColors.mapIndexed { i, c ->
            GradientStop(i.toFloat() / (repeatedColors.size - 1).toFloat(), c)
        }
        val shader = Shader.LinearGradient(
            start = pts.first, end = pts.second,
            stops = stops, tileMode = TileMode.CLAMP,
            interpolation = ColorSpaceInterpolation.HSL,
        )
        canvas.drawRect(rect, Paint(shader = shader))
        canvas.drawString("Shorter", 210f, 15f, font, Paint())
        canvas.translate(0f, 25f)

        // Explicit-position endpoints
        val stops2 = listOf(
            GradientStop(0.3f, ColorARGB.Red),
            GradientStop(0.7f, ColorARGB.Green),
        )
        val shader2 = Shader.LinearGradient(
            start = pts.first, end = pts.second,
            stops = stops2, tileMode = TileMode.CLAMP,
            interpolation = ColorSpaceInterpolation.HSL,
        )
        canvas.drawRect(rect, Paint(shader = shader2))
        canvas.translate(0f, 25f)

        val stops3 = listOf(
            GradientStop(0.0f, ColorARGB.Red),
            GradientStop(0.3f, ColorARGB.Red),
            GradientStop(0.7f, ColorARGB.Green),
            GradientStop(1.0f, ColorARGB.Green),
        )
        val shader3 = Shader.LinearGradient(
            start = pts.first, end = pts.second,
            stops = stops3, tileMode = TileMode.CLAMP,
            interpolation = ColorSpaceInterpolation.HSL,
        )
        canvas.drawRect(rect, Paint(shader = shader3))
    }
}
