package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/strokes4.cpp`.
 * Tests very thin strokes rendered under 1000x scale.
 * @see https://github.com/google/skia/blob/main/gm/strokes4.cpp
 */

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class Strokes4Gm : SkiaGm {
    override val name = "strokes_zoomed"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0.055f,
        )
        canvas.scale(1000f, 1000f)
        canvas.drawCircle(0f, 2f, 1.97f, paint)
    }
}
