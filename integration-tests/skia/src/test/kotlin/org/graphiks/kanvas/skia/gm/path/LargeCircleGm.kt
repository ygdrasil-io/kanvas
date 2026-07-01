package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/conicpaths.cpp` (DEF_SIMPLE_GM largecircle).
 * AA regression test with large stroked circle.
 * @see https://github.com/google/skia/blob/main/gm/conicpaths.cpp
 */
class LargeCircleGm : SkiaGm {
    override val name = "largecircle"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(50f, 100f)
        val paint = Paint(
            style = PaintStyle.STROKE,
            antiAlias = true,
        )
        val cx = 1052.5390625f
        val cy = 506.8760978034711f
        val radius = 1096.702150363923f
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
