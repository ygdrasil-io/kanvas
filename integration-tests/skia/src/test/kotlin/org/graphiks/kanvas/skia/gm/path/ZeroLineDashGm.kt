package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/strokes.cpp` (zerolinedash).
 * Tests rendering of a zero-length dashed line with round caps and bevel joins.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

class ZeroLineDashGm : SkiaGm {
    override val name = "zerolinedash"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 80.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 11f,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.BEVEL,
            pathEffect = PathEffect.Dash(floatArrayOf(1f, 5f), 0f),
        )

        canvas.drawLine(100f, 100f, 100f, 100f, paint)
    }
}
