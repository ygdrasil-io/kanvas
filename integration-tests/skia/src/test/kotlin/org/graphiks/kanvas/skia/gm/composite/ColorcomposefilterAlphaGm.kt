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
 * Port of Skia's `gm/composecolorfilter.cpp`.
 * Tests color filter compose with alpha-aware blend modes.
 * @see https://github.com/google/skia/blob/main/gm/composecolorfilter.cpp
 */
class ColorcomposefilterAlphaGm : SkiaGm {
    override val name = "colorcomposefilter_alpha"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 790
    override val height = 790

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)

        val colors = listOf(
            Color.fromRGBA(0x80 / 255f, 0f, 0f, 0x80 / 255f),
            Color.fromRGBA(0f, 0x80 / 255f, 0f, 0x80 / 255f),
            Color.fromRGBA(0f, 0f, 0x80 / 255f, 0x80 / 255f),
        )
        val modes = listOf(
            BlendMode.SRC_OVER, BlendMode.XOR, BlendMode.DST_OUT, BlendMode.SRC_ATOP,
        )

        val filters = modes.flatMap { mode -> colors.map { color -> ColorFilter.Blend(color, mode) } }
        val r = Rect(0f, 0f, 50f, 50f)
        val spacer = 10f

        canvas.translate(spacer, spacer)
        val paint = Paint(color = Color.WHITE)

        canvas.drawRect(r, paint)

        for (i in filters.indices) {
            canvas.save()
            canvas.translate((i + 1) * (r.width + spacer), 0f)
            canvas.drawRect(r, paint.copy(colorFilter = filters[i]))
            canvas.restore()

            canvas.save()
            canvas.translate(0f, (i + 1) * (r.width + spacer))
            canvas.drawRect(r, paint.copy(colorFilter = filters[i]))
            canvas.restore()
        }

        canvas.translate(r.width + spacer, r.width + spacer)

        for (y in filters.indices) {
            canvas.save()
            for (x in filters.indices) {
                val composed = ColorFilter.Compose(filters[y], filters[x])
                canvas.drawRect(r, paint.copy(colorFilter = composed))
                canvas.translate(r.width + spacer, 0f)
            }
            canvas.restore()
            canvas.translate(0f, r.height + spacer)
        }
    }
}
