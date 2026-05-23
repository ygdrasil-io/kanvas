package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SK_ScalarPI
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/addarc.cpp::DEF_SIMPLE_GM(tinyanglearcs, …)`.
 *
 * Lifted from <https://bugs.chromium.org/p/chromium/issues/detail?id=640031>.
 *
 * Two stroked annular wedges built from arcs of *huge* radius
 * (`outerRadius = 100000`, `innerRadius = 99980`) and *tiny* sweep
 * (`sweep = 10 / outerRadius = 1e-4 rad ≈ 0.0057°`). The combination
 * is the canonical chromium repro for arc rasterisation breaking down
 * on the degenerate `huge-r ∧ tiny-sweep` limit — Skia is expected to
 * fall back to a chord-line.
 *
 * Each wedge is built as:
 *  1. `moveTo(inner-start)`
 *  2. `lineTo(outer-start)` — radial out
 *  3. `arcTo(outer-circle, start, sweep, true)` — outer arc, CCW
 *  4. `lineTo(inner-end)` — radial in
 *  5. `addArc(inner-circle, end→start, -sweep)` — inner arc, reverse
 *
 * The second wedge starts `0.001π` rad later and is translated by
 * `(20, 0)` so the pair appears side-by-side near the top of the
 * surface.
 *
 * Reference image: `tinyanglearcs.png`, `620 × 330`, default white BG.
 */
public class TinyAngleArcsGM : GM() {

    override fun getName(): String = "tinyanglearcs"
    override fun getISize(): SkISize = SkISize.Make(620, 330)

    /** Mirror of upstream `html_canvas_arc(path, x, y, r, start, end, ccw, callArcTo)`. */
    private fun htmlCanvasArc(
        path: SkPathBuilder,
        x: Float, y: Float, r: Float,
        start: Float, end: Float,
        ccw: Boolean,
        callArcTo: Boolean,
    ) {
        val bounds = SkRect.MakeLTRB(x - r, y - r, x + r, y + r)
        val sweep = if (ccw) end - start else start - end
        if (callArcTo) {
            path.arcTo(bounds, start, sweep, false)
        } else {
            path.addArc(bounds, start, sweep)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }

        c.translate(50f, 50f)

        val outerRadius = 100000.0f
        val innerRadius = outerRadius - 20.0f
        val centerX = 50f
        val centerY = outerRadius
        val startAngles = floatArrayOf(1.5f * SK_ScalarPI, 1.501f * SK_ScalarPI)
        val sweepAngle = 10.0f / outerRadius

        for (i in startAngles.indices) {
            val path = SkPathBuilder()
            val endAngle = startAngles[i] + sweepAngle
            path.moveTo(
                centerX + innerRadius * cos(startAngles[i]),
                centerY + innerRadius * sin(startAngles[i]),
            )
            path.lineTo(
                centerX + outerRadius * cos(startAngles[i]),
                centerY + outerRadius * sin(startAngles[i]),
            )
            // A combination of tiny sweepAngle + large radius, we should draw a line.
            htmlCanvasArc(
                path, centerX, outerRadius, outerRadius,
                startAngles[i] * 180f / SK_ScalarPI, endAngle * 180f / SK_ScalarPI,
                true, true,
            )
            path.lineTo(
                centerX + innerRadius * cos(endAngle),
                centerY + innerRadius * sin(endAngle),
            )
            htmlCanvasArc(
                path, centerX, outerRadius, innerRadius,
                endAngle * 180f / SK_ScalarPI, startAngles[i] * 180f / SK_ScalarPI,
                true, false,
            )
            c.drawPath(path.detach(), paint)
            c.translate(20f, 0f)
        }
    }
}
