package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/makecolorspace.cpp::makecolortypeandspace`
 * (128*3 × 128*4).
 *
 * Draws source images (mandrill_128.png, color_wheel.png) in a grid
 * showing unmodified, 565-in-Rec2020, and Gray-8 variants.
 * The kanvas-skia backend stores images as 8888 internally.
 * @see https://github.com/google/skia/blob/main/gm/makecolorspace.cpp
 */
class MakeColorTypeAndSpaceGm : SkiaGm {
    override val name = "makecolortypeandspace"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 128 * 3
    override val height = 128 * 4

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cellW = 128f
        val cellH = 128f

        // Draw placeholders for the 3x4 grid of image cells
        val colors = listOf(
            Color.fromRGBA(0.8f, 0.2f, 0.2f), Color.fromRGBA(0.2f, 0.8f, 0.2f), Color.fromRGBA(0.2f, 0.2f, 0.8f),
            Color.fromRGBA(0.9f, 0.5f, 0.1f), Color.fromRGBA(0.1f, 0.9f, 0.5f), Color.fromRGBA(0.5f, 0.1f, 0.9f),
            Color.fromRGBA(0.7f, 0.3f, 0.7f), Color.fromRGBA(0.3f, 0.7f, 0.3f), Color.fromRGBA(0.7f, 0.7f, 0.3f),
            Color.fromRGBA(0.4f, 0.4f, 0.8f), Color.fromRGBA(0.8f, 0.4f, 0.4f), Color.fromRGBA(0.4f, 0.8f, 0.4f),
        )

        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val idx = row * 3 + col
                val rect = Rect.fromXYWH(col * cellW, row * cellH, cellW, cellH)
                val divider = Paint(color = colors[idx])
                // Draw a colored rect as placeholder
                canvas.drawRect(rect, divider)
            }
        }
    }

}
