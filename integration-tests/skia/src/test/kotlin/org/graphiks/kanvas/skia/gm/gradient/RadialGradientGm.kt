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
 * Port of Skia's `gm/gradients.cpp` (RadialGradientGM).
 * 3-stop radial gradient over opaque black background with dither.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class RadialGradientGm : SkiaGm {
    override val name = "radial_gradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 33.5
    override val width = 1280
    override val height = 1280

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(0f, 0f, 0f, 1f)
        val center = Point(w / 2f, h / 2f)
        val radius = w / 2f
        val stops = listOf(
            GradientStop(0f, Color.fromRGBA(0x7f / 255f, 0x7f / 255f, 0x7f / 255f, 0x7f / 255f)),
            GradientStop(0.35f, Color.fromRGBA(0x7f / 255f, 0x7f / 255f, 0x7f / 255f, 0x7f / 255f)),
            GradientStop(1f, Color.fromRGBA(0f, 0f, 0f, 0xb2 / 255f)),
        )
        val paint = Paint(
            shader = Shader.RadialGradient(
                center = center,
                radius = radius,
                stops = stops,
                tileMode = TileMode.CLAMP,
            ),
        )
        canvas.drawRect(Rect.fromLTRB(0f, 0f, w, h), paint)
    }
}
