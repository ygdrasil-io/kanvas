package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/bug615686.cpp::bug615686` (DEF_SIMPLE_GM, 250 x 250).
 *
 * Single AA-stroked self-intersecting cubic with `strokeWidth = 20`.
 * Originally exposed a stroker bug in (SkPathStroker::cubicPerpRay) --
 * rays computed at high curvature points would produce inverted
 * outlines, leaving a sliver of un-filled pixels at the loop's
 * crossover. Pure path / stroker stress.
 * @see https://github.com/google/skia/blob/main/gm/bug615686.cpp
 */
class Bug615686Gm : SkiaGm {
    override val name = "bug615686"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 79.8
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 20f,
        )
        val path = Path {
            moveTo(0f, 0f)
            cubicTo(200f, 200f, 0f, 200f, 200f, 0f)
        }
        canvas.drawPath(path, paint)
    }
}
