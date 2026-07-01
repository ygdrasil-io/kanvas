package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/bug530095.cpp::bug591993` (40 x 140).
 *
 * One drawn line `(20, 20) -> (120, 20)` strokes 10 px wide with round
 * caps and dash `[100, 100]` phase 100 -- the dasher should produce a
 * single fully-painted stroke segment topped by round caps at each end.
 * Tests that the dasher's caps are honoured even when the dash phase
 * lands the start exactly on an "off" interval.
 * @see https://github.com/google/skia/blob/main/gm/bug530095.cpp
 */
class Bug591993Gm : SkiaGm {
    override val name = "bug591993"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 40
    override val height = 140

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeCap = StrokeCap.ROUND,
            strokeWidth = 10f,
            pathEffect = PathEffect.Dash(floatArrayOf(100f, 100f), 100f),
        )
        canvas.drawLine(20f, 20f, 120f, 20f, paint)
    }
}
