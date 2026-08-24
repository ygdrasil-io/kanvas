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
 * Port of Skia's `gm/shallowgradient.cpp` (sweep dither).
 * 800×800 sweep gradient 0xFF555555 → 0xFF444444 with dither enabled.
 * @see https://github.com/google/skia/blob/main/gm/shallowgradient.cpp
 */
class ShallowGradientSweepGm : SkiaGm {
    override val name = "shallow_gradient_sweep"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stops = listOf(
            GradientStop(0f, ColorARGB.fromRGBA(0x55 / 255f, 0x55 / 255f, 0x55 / 255f, 1f)),
            GradientStop(1f, ColorARGB.fromRGBA(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f)),
        )
        val paint = Paint(
            shader = Shader.SweepGradient(
                center = Point2F32(w / 2f, h / 2f),
                stops = stops, tileMode = TileMode.CLAMP,
            ),
        )
        canvas.drawRect(RectF32(0f, 0f, w, h), paint)
    }
}
