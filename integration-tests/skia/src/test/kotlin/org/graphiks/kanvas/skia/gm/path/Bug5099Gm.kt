package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/cubicpaths.cpp::bug5099` (DEF_SIMPLE_GM, 50 x 50).
 *
 * Single AA-stroked cubic at width 10 -- the cubic has near-coincident
 * control points making the stroker emit a degenerate normal at the
 * highly-curved tip. Originally exposed a stroker bug where the
 * outline self-intersected at that tip and left a crescent of un-filled
 * pixels.
 * @see https://github.com/google/skia/blob/main/gm/cubicpaths.cpp
 */
class Bug5099Gm : SkiaGm {
    override val name = "bug5099"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 76.8
    override val width = 50
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
        )
        val path = Path {
            moveTo(6f, 27f)
            cubicTo(31.5f, 1.5f, 3.5f, 4.5f, 29f, 29f)
        }
        canvas.drawPath(path, paint)
    }
}
