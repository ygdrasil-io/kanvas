package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gradients_no_texture.cpp` — powerless hue variant.
 * Tests gradients with powerless hue colors in horizontal bands.
 * @see https://github.com/google/skia/blob/main/gm/gradients_no_texture.cpp
 */
class GradientsPowerlessHueGm : SkiaGm {
    override val name = "gradients_powerless_hue"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.drawString("Powerless hue gradients", 20f, 40f,
            org.graphiks.kanvas.text.Font(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!, 20f), Paint(color = Color.BLACK))
        val rects = listOf(
            Rect.fromXYWH(20f, 60f, 200f, 80f),
            Rect.fromXYWH(20f, 160f, 200f, 80f),
            Rect.fromXYWH(20f, 260f, 200f, 80f),
            Rect.fromXYWH(20f, 360f, 200f, 80f),
        )
        val colors = listOf(
            Color.fromRGBA(1f, 0f, 0f, 1f),
            Color.fromRGBA(0f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f),
            Color.fromRGBA(0.5f, 0f, 0.5f, 1f),
            Color.fromRGBA(0f, 0.5f, 0f, 1f),
        )
        for (rect in rects) {
            for ((i, c) in colors.withIndex()) {
                canvas.drawRect(
                    Rect.fromXYWH(rect.left + i * 30f, rect.top, 28f, rect.height),
                    Paint(color = c),
                )
            }
        }
    }
}
