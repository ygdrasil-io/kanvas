package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/skbug_4868.cpp::skbug_4868` (DEF_SIMPLE_GM, 32 × 32).
 *
 * Tiny regression test : `clipRect` and `drawLine` should align exactly
 * when both consume the same point, even after a large translate that
 * pushes the user-space coordinates near the float-precision limit.
 * @see https://github.com/google/skia/blob/main/gm/skbug_4868.cpp
 */
class Skbug4868Gm : SkiaGm {
    override val name = "skbug_4868"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 94.2
    override val width = 32
    override val height = 32

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(-68f, -3378f)
        val paint = Paint(antiAlias = true, style = PaintStyle.STROKE)
        canvas.scale(0.56692914f, 0.56692914f)
        val rc = Rect(158f, 5994.80273f, 165f, 5998.80225f)
        canvas.clipRect(rc)
        canvas.drawColor(0xCE / 255f, 0xCF / 255f, 0xCE / 255f)
        canvas.drawLine(rc.left, rc.top, rc.right, rc.bottom, paint)
        canvas.drawLine(rc.right, rc.top, rc.left, rc.bottom, paint)
    }
}
