package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode_srgb.cpp`.
 * Tests sRGB encoding by rendering a row of saturated color swatches.
 * @see https://github.com/google/skia/blob/main/gm/encode_srgb.cpp
 */
class EncodeSrgbGm : SkiaGm {
    override val name = "encodesrgb"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val srgbColors = listOf(
            Color.fromRGBA(1f, 0f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f),
            Color.fromRGBA(1f, 0f, 1f, 1f),
            Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.fromRGBA(1f, 0.5f, 0f, 1f),
            Color.fromRGBA(0.5f, 0f, 1f, 1f),
            Color.fromRGBA(0f, 0.5f, 0.8f, 1f),
        )
        for ((i, c) in srgbColors.withIndex()) {
            canvas.drawRect(Rect.fromXYWH(20f + i * 48f, 30f, 40f, 180f), Paint(color = c))
        }
    }
}
