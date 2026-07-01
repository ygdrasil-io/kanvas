package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/circulararcs.cpp::crbug_888453` (480 x 150).
 * Small full-circle arcs at increasing radii in 3 rows: fill, hairline, stroke.
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class Crbug888453Gm : SkiaGm {
    override val name = "crbug_888453"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 480
    override val height = 150

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val fill = Paint(antiAlias = true)
        val hairline = Paint(antiAlias = true, style = PaintStyle.STROKE)
        val stroke = Paint(antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 2f)

        var x = 4f
        val y0 = 25f
        val y1 = 75f
        val y2 = 125f
        for (r in 2..20) {
            val rf = r.toFloat()
            canvas.drawArc(Rect(x - rf, y0 - rf, x - rf + 2f * rf, y0 - rf + 2f * rf), 0f, 360f, false, fill)
            canvas.drawArc(Rect(x - rf, y1 - rf, x - rf + 2f * rf, y1 - rf + 2f * rf), 0f, 360f, false, hairline)
            canvas.drawArc(Rect(x - rf, y2 - rf, x - rf + 2f * rf, y2 - rf + 2f * rf), 0f, 360f, false, stroke)
            x += 2f * rf + 4f
        }
    }
}
