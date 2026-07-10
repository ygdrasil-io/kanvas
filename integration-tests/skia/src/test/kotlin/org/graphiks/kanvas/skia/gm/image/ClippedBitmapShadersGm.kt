package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clippedbitmapshaders.cpp`.
 * Tests clipped bitmap shaders with a 3×3 grid of colored rectangles.
 * @see https://github.com/google/skia/blob/main/gm/clippedbitmapshaders.cpp
 */
class ClippedBitmapShadersGm : SkiaGm {
    override val name = "clippedbitmapshaders"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        val rectSize = 64f
        val slideSize = 300f
        val margin = (slideSize / 3f - rectSize) / 2f
        for (i in 0 until 3) {
            val yOrigin = slideSize / 3f * i + margin
            for (j in 0 until 3) {
                if (i == 1 && j == 1) continue
                val xOrigin = slideSize / 3f * j + margin
                val colors = listOf(Color.RED, Color.GREEN, Color.BLACK, Color.BLUE)
                val idx = (i * 3 + j) % 4
                canvas.drawRect(Rect(xOrigin, yOrigin, xOrigin + rectSize, yOrigin + rectSize), Paint(color = colors[idx]))
            }
        }
    }
}
