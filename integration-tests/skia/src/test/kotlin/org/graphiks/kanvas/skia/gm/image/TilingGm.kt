package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests image tiling with a repeating grid of colored squares. */
class TilingGm : SkiaGm {
    override val name = "tiling"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val tileColors = listOf(
            Color.fromRGBA(1f, 0f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f),
        )
        val tileSize = 40f
        for (row in 0 until (height / tileSize.toInt())) {
            for (col in 0 until (width / tileSize.toInt())) {
                val ci = (row + col) % tileColors.size
                canvas.drawRect(
                    Rect.fromXYWH(col * tileSize, row * tileSize, tileSize, tileSize),
                    Paint(color = tileColors[ci]),
                )
            }
        }
    }
}
