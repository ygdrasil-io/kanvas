package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/crbug_946965.cpp`.
 *  Regression test for crbug.com/946965 — draws stroked rounded rects
 *  with varying corner radii.
 *  @see https://github.com/google/skia/blob/main/gm/crbug_946965.cpp
 */
class Crbug946965Gm : SkiaGm {
    override val name = "crbug_946965"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 75
    override val height = 150

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rrect = RRect(Rect.fromLTRB(-20f, -5f, 20f, 5f), 10f)
        canvas.translate(25f, 80f)
        canvas.rotate(90f)
        canvas.scale(1.5f, 1f)
        canvas.drawRRect(rrect, Paint(antiAlias = true))
        canvas.translate(0f, -20f)
        canvas.drawRRect(rrect, Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 3f,
        ))
    }
}
