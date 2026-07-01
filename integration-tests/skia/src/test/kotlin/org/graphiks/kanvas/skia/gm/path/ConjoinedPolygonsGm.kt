package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/convexpoly.cpp` (conjoined_polygons).
 * Single AA-filled self-intersecting polygon with sharp turns.
 * @see https://github.com/google/skia/blob/main/gm/convexpoly.cpp
 */
class ConjoinedPolygonsGm : SkiaGm {
    override val name = "conjoined_polygons"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 94.2
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(0f, 120f)
            lineTo(0f, 0f)
            lineTo(50f, 330f)
            lineTo(90f, 0f)
            lineTo(340f, 0f)
            lineTo(90f, 330f)
            lineTo(50f, 330f)
            close()
        }
        val paint = Paint(
            color = Color.fromRGBA(0f, 0f, 0f, 1f),
            antiAlias = true,
        )
        canvas.drawPath(path, paint)
    }
}
