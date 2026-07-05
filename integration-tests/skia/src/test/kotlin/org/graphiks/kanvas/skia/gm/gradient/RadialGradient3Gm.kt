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
 * Port of Skia's `gm/gradients.cpp::RadialGradient3GM` dither variant.
 * Single 500×500 drawRect filled with a 2-stop radial gradient (white → black),
 * centre (0,0) radius 3000. Visible canvas occupies ~17% of gradient span.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class RadialGradient3Gm : SkiaGm {
    override val name = "radial_gradient3"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 9.8
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val center = Point(0f, 0f)
        val radius = 3000f
        val stops = listOf(
            GradientStop(0f, Color.fromRGBA(1f, 1f, 1f, 1f)),
            GradientStop(1f, Color.fromRGBA(0f, 0f, 0f, 1f)),
        )
        val shader = Shader.RadialGradient(
            center = center, radius = radius,
            stops = stops, tileMode = TileMode.CLAMP,
        )
        val paint = Paint(shader = shader)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 500f, 500f), paint)
    }
}
