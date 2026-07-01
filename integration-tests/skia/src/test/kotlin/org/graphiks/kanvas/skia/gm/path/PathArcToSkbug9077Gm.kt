package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pathfill.cpp` `DEF_SIMPLE_GM(path_arcto_skbug_9077, …)`.
 *
 * Regression test: 3 lineTo + close, then a tangent-`arcTo` (radius=60)
 * landing at `(180, 160)`. Stroke width 2, AA red.
 *
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class PathArcToSkbug9077Gm : SkiaGm {
    override val name = "path_arcto_skbug_9077"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
        )
        val radius = 60f
        val path = Path {
            moveTo(20f, 20f)
            lineTo(100f, 20f)
            lineTo(100f, 60f)
            close()
            // arcTo with radius - using arcTo with explicit parameters
            // arcTo(rx, ry, xAxisRotation, largeArc, sweep, x, y)
            arcTo(radius, radius, 0f, false, true, 180f, 160f)
        }
        canvas.drawPath(path, p)
    }
}
