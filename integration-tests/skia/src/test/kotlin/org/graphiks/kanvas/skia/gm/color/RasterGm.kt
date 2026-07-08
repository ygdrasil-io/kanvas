package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests raster color rendering with a 4×4 grid of distinct color swatches. */
class RasterGm : SkiaGm {
    override val name = "raster"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cellW = width / 4f
        val cellH = height / 4f
        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f), Color.fromRGBA(1f, 0f, 1f, 1f), Color.WHITE, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.fromRGBA(1f, 0.5f, 0f, 1f), Color.fromRGBA(0f, 1f, 0.5f, 1f), Color.fromRGBA(0.5f, 0f, 1f, 1f), Color.fromRGBA(1f, 0f, 0.5f, 1f),
            Color.fromRGBA(0.5f, 1f, 0f, 1f), Color.fromRGBA(0f, 0.5f, 1f, 1f), Color.fromRGBA(1f, 1f, 0f, 1f), Color.BLACK,
        )
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val idx = row * 4 + col
                canvas.drawRect(Rect(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH), Paint(color = colors[idx]))
            }
        }
    }
}
