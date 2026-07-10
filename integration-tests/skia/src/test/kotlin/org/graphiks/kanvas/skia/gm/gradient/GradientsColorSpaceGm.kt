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
 * Port of Skia's gm/gradients.cpp (gradients_color_space).
 * Tests various color space interpolations for gradients.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsColorSpaceGm : SkiaGm {
    override val name = "gradients_color_space"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 265
    override val height = 355

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(0x80 / 255f, 0x80 / 255f, 0x80 / 255f, 1f))
        )
        
        val blue = Color.BLUE
        val yellow = Color.fromRGBA(1f, 1f, 0f, 1f)
        
        val stops = listOf(
            GradientStop(0f, blue),
            GradientStop(1f, yellow),
        )
        
        for (i in 0..9) {
            val paint = Paint(shader = Shader.LinearGradient(
                start = Point(5f, 0f), end = Point(260f, 0f),
                stops = stops, tileMode = TileMode.CLAMP,
            ))
            canvas.drawRect(
                Rect(5f, 5f + i * 30f, 255f, 20f),
                paint
            )
        }
    }
}
