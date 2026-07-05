package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/color4f.cpp::color4shader` (360 × 480).
 * Grid of 100×100 rects each filled with a solid-color shader.
 * @see https://github.com/google/skia/blob/main/gm/color4f.cpp
 */
class Color4shaderGm : SkiaGm {
    override val name = "color4shader"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 360
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)

        val colors = listOf(
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
        )

        val r = Rect.fromXYWH(0f, 0f, 100f, 100f)

        for (c4 in colors) {
            val shaders = arrayOf(
                Shader.SolidColor(c4),
                Shader.SolidColor(c4),
                Shader.SolidColor(c4),
            )

            canvas.save()
            for (s in shaders) {
                canvas.drawRect(r, Paint(shader = s))
                canvas.translate(r.width * 6f / 5f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, r.height * 6f / 5f)
        }
    }
}
