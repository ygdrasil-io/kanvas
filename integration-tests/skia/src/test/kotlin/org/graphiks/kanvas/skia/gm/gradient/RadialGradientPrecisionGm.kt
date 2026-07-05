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
 * Port of Skia's `gm/radial_gradient_precision.cpp::radial_gradient_precision`
 * (DEF_SIMPLE_GM, 200 × 200).
 * Single draw with a radial gradient whose centre sits at (1000,1000) with
 * radius 40. Visible canvas lands past the last stop under kRepeat tiling.
 * @see https://github.com/google/skia/blob/main/gm/radial_gradient_precision.cpp
 */
class RadialGradientPrecisionGm : SkiaGm {
    override val name = "radial_gradient_precision"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val center = Point(1000f, 1000f)
        val radius = 40f
        val stops = listOf(
            GradientStop(0f, Color.BLACK),
            GradientStop(1f, Color.GREEN),
        )
        val shader = Shader.RadialGradient(
            center = center, radius = radius,
            stops = stops, tileMode = TileMode.REPEAT,
        )
        val paint = Paint(shader = shader)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 200f, 200f), paint)
    }
}
