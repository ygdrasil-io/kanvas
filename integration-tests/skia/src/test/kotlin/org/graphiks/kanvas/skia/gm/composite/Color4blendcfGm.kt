package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/color4f.cpp::color4blendcf` (360 × 480).
 * Grid of 100×100 rects with white paint and kModulate color-filter blending.
 * @see https://github.com/google/skia/blob/main/gm/color4f.cpp
 */
class Color4blendcfGm : SkiaGm {
    override val name = "color4blendcf"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
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

        val paint = Paint(color = Color.WHITE)
        val r = Rect.fromXYWH(0f, 0f, 100f, 100f)

        for (c4 in colors) {
            val filters = arrayOf(
                ColorFilter.Blend(c4, BlendMode.MODULATE),
                ColorFilter.Blend(c4, BlendMode.MODULATE),
                ColorFilter.Blend(c4, BlendMode.MODULATE),
            )

            canvas.save()
            for (f in filters) {
                canvas.drawRect(r, paint.copy(colorFilter = f))
                canvas.translate(r.width * 6f / 5f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, r.height * 6f / 5f)
        }
    }
}
