package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/drawbitmaprect.cpp`.
 * Tests drawBitmapRect with a 4×4 grid of colored, anti-aliased rectangles.
 * @see https://github.com/google/skia/blob/main/gm/drawbitmaprect.cpp
 */
class DrawBitmapRect4Gm : SkiaGm {
    override val name = "drawbitmaprect4"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val x = 10f + col * 60f
                val y = 10f + row * 60f
                val ci = (row * 4 + col) % 4
                val c = when (ci) {
                    0 -> Color.RED; 1 -> Color.BLUE
                    2 -> Color.fromRGBA(0f, 1f, 0f, 1f); else -> Color.fromRGBA(1f, 1f, 0f, 1f)
                }
                canvas.drawRect(Rect.fromXYWH(x, y, 50f, 50f), Paint(color = c, antiAlias = true))
            }
        }
    }
}
