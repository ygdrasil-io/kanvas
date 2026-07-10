package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/circle_sizes.cpp`.
 * Sixteen anti-aliased circles in a 4×4 grid with radii 1-16.
 * @see https://github.com/google/skia/blob/main/gm/circle_sizes.cpp
 */
class CircleSizesGm : SkiaGm {
    override val name = "circle_sizes"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 87.3
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)
        for (i in 0 until 16) {
            val cx = 14f + 32f * (i % 4)
            val cy = 14f + 32f * (i / 4)
            canvas.drawCircle(cx, cy, (i + 1).toFloat(), paint)
        }
    }
}
