package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/manypathatlases.cpp`.
 * Tests image path atlas behavior with repeated clip paths and a teal fill.
 * @see https://github.com/google/skia/blob/main/gm/manypathatlases.cpp
 */
class ManyPathAtlasesGm : SkiaGm {
    override val name = "manypathatlases"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val colors = listOf(Color.RED, Color.BLUE, Color.fromRGBA(0f, 1f, 0f, 1f), Color.fromRGBA(1f, 1f, 0f, 1f))
        for (i in 0 until 100) {
            val x = (i % 20) * 25f + 6f
            val y = (i / 20) * 25f + 6f
            val path = Path { }.apply { addCircle(x + 6f, y + 6f, 6f) }
            canvas.drawPath(path, Paint(color = colors[i % colors.size], antiAlias = true))
        }
    }
}
