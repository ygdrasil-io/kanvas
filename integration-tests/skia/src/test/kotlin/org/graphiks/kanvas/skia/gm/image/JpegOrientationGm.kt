package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode.cpp::jpeg_orientation` (1000x1000).
 *
 * Best-effort: Skia loads 8 orientation JPEGs from /skia/orientation/Landscape_{1-8}.jpg.
 * Kanvas draws a grid of colored rectangles approximating the orientation layout.
 * @see https://github.com/google/skia/blob/main/gm/encode.cpp
 */
class JpegOrientationGm : SkiaGm {
    override val name = "jpeg_orientation"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(0.25f, 0.25f)
        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(1f, 1f, 0f),
            Color.fromRGBA(0f, 1f, 1f), Color.fromRGBA(1f, 0f, 1f), Color.fromRGBA(1f, 0.5f, 0f), Color.fromRGBA(0.5f, 0f, 0.5f)
        )
        val size = 250f
        for (i in 0 until 8) {
            val paint = Paint(color = colors[i])
            canvas.drawRect(Rect(0f, 0f, size, size), paint)
            canvas.translate(0f, size)
            if (i == 3) {
                canvas.translate(size, -(4f * size))
            }
        }
    }
}
