package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/texelsubset.cpp`.
 * Tests texel subset rendering with colored rects and white circles in an 8×8 grid.
 * @see https://github.com/google/skia/blob/main/gm/texelsubset.cpp
 */
class TexelSubsetGm : SkiaGm {
    override val name = "texelsubset"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.2f, 0.2f, 0.2f, 1f)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val x = 20f + col * 60f
                val y = 20f + row * 60f
                val r = 20f
                val rColor = when {
                    row == col -> Color.fromRGBA(1f, 0f, 0f, 1f)
                    row < col -> Color.fromRGBA(0f, 0f, 1f, 1f)
                    else -> Color.fromRGBA(0f, 1f, 0f, 1f)
                }
                canvas.drawRect(Rect.fromXYWH(x, y, r * 2, r * 2), Paint(color = rColor, antiAlias = true))
                canvas.drawCircle(x + r, y + r, r * 0.6f, Paint(color = Color.WHITE))
            }
        }
    }
}
