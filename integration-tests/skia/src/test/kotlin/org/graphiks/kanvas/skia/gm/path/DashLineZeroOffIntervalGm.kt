package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(dash_line_zero_off_interval, …)` (160 × 330).
 *
 * Tests that lines drawn with a dash pattern containing zero-length off-intervals
 * (`{5, 0, 2, 0}`) render correctly for all combinations of:
 *   - line direction: horizontal, vertical, point (degenerate), diagonal
 *   - cap: Butt, Square, Round
 *   - anti-alias: off, on
 *
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class DashLineZeroOffIntervalGm : SkiaGm {
    override val name = "dash_line_zero_off_interval"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 160
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val dashPaint = Paint(
            pathEffect = PathEffect.Dash(floatArrayOf(5f, 0f, 2f, 0f), 0f),
            style = PaintStyle.STROKE,
            strokeWidth = 20f,
        )

        data class Line(val a: Point, val b: Point)

        val lines = listOf(
            Line(Point(0.5f, 0.5f), Point(30.5f, 0.5f)),   // horizontal
            Line(Point(0.5f, 0.5f), Point(0.5f, 30.5f)),   // vertical
            Line(Point(0.5f, 0.5f), Point(0.5f, 0.5f)),    // point (degenerate)
            Line(Point(0.5f, 0.5f), Point(25.5f, 25.5f)),  // diagonal
        )

        val pad = 5f + dashPaint.strokeWidth

        canvas.translate(pad / 2f, pad / 2f)
        canvas.save()

        // Compute maximum height across all line types.
        var h = 0f
        for (line in lines) {
            h = max(h, abs(line.a.y - line.b.y))
        }

        for (line in lines) {
            val w = abs(line.a.x - line.b.x)
            for (cap in listOf(StrokeCap.BUTT, StrokeCap.SQUARE, StrokeCap.ROUND)) {
                for (aa in listOf(false, true)) {
                    val currentPaint = dashPaint.copy(strokeCap = cap, antiAlias = aa)
                    canvas.drawLine(line.a.x, line.a.y, line.b.x, line.b.y, currentPaint)
                    canvas.translate(0f, pad + h)
                }
            }
            canvas.restore()
            canvas.translate(pad + w, 0f)
            canvas.save()
        }
    }
}
