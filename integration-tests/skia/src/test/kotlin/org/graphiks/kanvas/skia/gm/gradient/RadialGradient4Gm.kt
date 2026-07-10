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
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gradients.cpp::RadialGradient4GM` dither variant.
 * 500×500 drawRect filled with 5-stop radial gradient (red, red, white, white, red)
 * at positions (0, .4, .4, .8, .8), centre (250,250), radius 250.
 * Repeated-position stops force hard band transitions.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class RadialGradient4Gm : SkiaGm {
    override val name = "radial_gradient4"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 32.7
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val center = Point(250f, 250f)
        val radius = 250f
        val stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(0.4f, Color.RED),
            GradientStop(0.4f, Color.WHITE),
            GradientStop(0.8f, Color.WHITE),
            GradientStop(0.8f, Color.RED),
        )
        val shader = Shader.RadialGradient(
            center = center, radius = radius,
            stops = stops, tileMode = TileMode.CLAMP,
        )
        val paint = Paint(antiAlias = true, shader = shader)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 500f, 500f), paint)
    }
}
