package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/pathfill.cpp::path_skbug_11859`.
 * Two-subpath red fill under scale(2, 2) - tests clipping regression at bitmap edge.
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class PathSkbug11859Gm : SkiaGm {
    override val name = "path_skbug_11859"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 40.3
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f), antiAlias = true)
        val path = Path {
            moveTo(258f, -2f)
            lineTo(258f, 258f)
            lineTo(237f, 258f)
            lineTo(240f, -2f)
            lineTo(258f, -2f)
            moveTo(-2f, -2f)
            lineTo(240f, -2f)
            lineTo(238f, 131f)
            lineTo(-2f, 131f)
            lineTo(-2f, -2f)
        }

        canvas.scale(2f, 2f)
        canvas.drawPath(path, paint)
    }
}
