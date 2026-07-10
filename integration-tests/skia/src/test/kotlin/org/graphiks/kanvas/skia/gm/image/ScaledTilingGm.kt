package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests scaled tiling with a 2× scaled grid of semi-transparent colored tiles. */
class ScaledTilingGm : SkiaGm {
    override val name = "scaledtiling"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val tileColors = listOf(
            Color.fromRGBA(1f, 0f, 0f, 0.8f),
            Color.fromRGBA(0f, 1f, 0f, 0.8f),
            Color.fromRGBA(0f, 0f, 1f, 0.8f),
            Color.fromRGBA(1f, 1f, 0f, 0.8f),
        )
        val tileSize = 30f
        canvas.save()
        canvas.scale(2f, 2f)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val ci = (row + col) % tileColors.size
                canvas.drawRect(
                    Rect.fromXYWH(col * tileSize, row * tileSize, tileSize, tileSize),
                    Paint(color = tileColors[ci]),
                )
            }
        }
        canvas.restore()
    }
}
