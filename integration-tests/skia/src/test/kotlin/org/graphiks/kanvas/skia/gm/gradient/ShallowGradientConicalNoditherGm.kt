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
 * Port of Skia's `gm/shallowgradient.cpp` (conical no-dither).
 * 800×800 two-point conical gradient 0xFF555555 → 0xFF444444, no dither.
 * @see https://github.com/google/skia/blob/main/gm/shallowgradient.cpp
 */
class ShallowGradientConicalNoditherGm : SkiaGm {
    override val name = "shallow_gradient_conical_nodither"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val center = Point(w / 2f, h / 2f)
        val stops = listOf(
            GradientStop(0f, Color.fromRGBA(0x55 / 255f, 0x55 / 255f, 0x55 / 255f, 1f)),
            GradientStop(1f, Color.fromRGBA(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f)),
        )
        val paint = Paint(
            shader = Shader.ConicalGradient(
                start = center, startRadius = w / 64f,
                end = center, endRadius = w / 2f,
                stops = stops, tileMode = TileMode.CLAMP,
            ),
        )
        canvas.drawRect(Rect(0f, 0f, w, h), paint)
    }
}
