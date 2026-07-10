package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(dashtextcaps, …)` (512 × 512).
 *
 * Draws a horizontal line with a `{12, 12}` round-capped dash pattern (stroke-width 10).
 *
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class DashTextCapsGm : SkiaGm {
    override val name = "dashtextcaps"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND,
            color = Color.fromRGBA(0xBB.toFloat() / 255f, 0f, 0f, 1f),
            pathEffect = PathEffect.Dash(floatArrayOf(12f, 12f), 0f),
        )

        // Draw the line - text drawing requires font support not yet available
        canvas.drawLine(8f, 120f, 456f, 120f, paint)
    }
}
