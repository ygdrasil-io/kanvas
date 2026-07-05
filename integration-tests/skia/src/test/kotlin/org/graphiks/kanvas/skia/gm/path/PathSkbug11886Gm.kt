package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pathfill.cpp::path_skbug_11886`.
 * AA-filled cubic-Bézier path at large coordinates testing numerical stability.
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class PathSkbug11886Gm : SkiaGm {
    override val name = "path_skbug_11886"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 93.9
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val mx = 0f
        val my = 770f
        val path = Path {
            moveTo(mx, my)
            cubicTo(
                mx + 0f, my + 1f,
                mx + 20f, my - 750f,
                mx + 83f, my - 746f,
            )
        }

        val paint = Paint(antiAlias = true)
        canvas.drawPath(path, paint)
    }
}
