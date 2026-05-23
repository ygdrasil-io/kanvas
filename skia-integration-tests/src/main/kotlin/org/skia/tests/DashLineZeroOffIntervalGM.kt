package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(dash_line_zero_off_interval, …)` (160 × 330).
 *
 * Tests that lines drawn with a dash pattern containing zero-length off-intervals
 * (`{5, 0, 2, 0}`) render correctly for all combinations of:
 *   - line direction: horizontal, vertical, point (degenerate), diagonal
 *   - cap: kButt, kSquare, kRound
 *   - anti-alias: off, on
 *
 * Zero-length off-intervals are special-cased in `SkDashPathEffect`: a zero
 * `off` means the dash segments are adjacent with no gap. Previously this
 * triggered an infinite loop in the decomposer.
 *
 * Reference image: `dash_line_zero_off_interval.png`, 160 × 330, default white BG.
 */
public class DashLineZeroOffIntervalGM : GM() {

    override fun getName(): String = "dash_line_zero_off_interval"
    override fun getISize(): SkISize = SkISize.Make(160, 330)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val dashPaint = SkPaint().apply {
            pathEffect = SkDashPathEffect.Make(floatArrayOf(5f, 0f, 2f, 0f), 0f)
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
        }

        data class Line(val a: SkPoint, val b: SkPoint)

        val lines = listOf(
            Line(SkPoint.Make(0.5f, 0.5f), SkPoint.Make(30.5f, 0.5f)),   // horizontal
            Line(SkPoint.Make(0.5f, 0.5f), SkPoint.Make(0.5f, 30.5f)),   // vertical
            Line(SkPoint.Make(0.5f, 0.5f), SkPoint.Make(0.5f, 0.5f)),    // point (degenerate)
            Line(SkPoint.Make(0.5f, 0.5f), SkPoint.Make(25.5f, 25.5f)),  // diagonal
        )

        val pad = 5f + dashPaint.strokeWidth

        c.translate(pad / 2f, pad / 2f)
        c.save()

        // Compute maximum height across all line types.
        var h = 0f
        for (line in lines) {
            h = max(h, abs(line.a.fY - line.b.fY))
        }

        for (line in lines) {
            val w = abs(line.a.fX - line.b.fX)
            for (cap in arrayOf(SkPaint.Cap.kButt_Cap, SkPaint.Cap.kSquare_Cap, SkPaint.Cap.kRound_Cap)) {
                dashPaint.strokeCap = cap
                for (aa in arrayOf(false, true)) {
                    dashPaint.isAntiAlias = aa
                    c.drawLine(line.a.fX, line.a.fY, line.b.fX, line.b.fY, dashPaint)
                    c.translate(0f, pad + h)
                }
            }
            c.restore()
            c.translate(pad + w, 0f)
            c.save()
        }
    }
}
