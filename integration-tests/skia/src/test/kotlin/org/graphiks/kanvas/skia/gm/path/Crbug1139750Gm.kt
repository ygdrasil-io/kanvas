package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/crbug_1139750.cpp`.
 *  Regression test for crbug.com/1139750 — draws stroked rounded rects
 *  with extreme corner radii.
 *  @see https://github.com/google/skia/blob/main/gm/crbug_1139750.cpp
 */
class Crbug1139750Gm : SkiaGm {
    override val name = "crbug_1139750"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 50
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
        )
        val r = Rect.fromXYWH(1f, 1f, 19f, 19f)
        val rr = RRect(r, 1f)
        canvas.translate(10f, 10f)
        canvas.scale(1.47619f, 1.52381f)
        canvas.drawRRect(rr, paint)
    }
}
