package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/addarc.cpp::DEF_SIMPLE_GM(manyarcs, …)`. Lifted
 * upstream from `canvas-arc-circumference-fill-diffs.html`.
 *
 * Grid of `4 × 20 + 4 × 20 = 160` html-canvas-style arc paths
 * (`moveTo(0,2)` → `arcTo(circle, start, sweep)` → `lineTo(0,28)`),
 * stroked with the default `kButt_Cap` + `kMiter_Join`. The 20 sweep
 * angles deliberately include several near-singular cases:
 *  - exact `0` and `±0.000001 × 180°` — degenerate sweep, just a line
 *    to the start point;
 *  - `±0.3 … ±2 × 180°` — short arcs spanning < or > 180°;
 *  - `4.3 × 180°` and `3934723942837.3 × 180°` — sweeps that wind
 *    multiple turns around the circle (the latter so large that
 *    `start + sweep` overflows scalar precision).
 * The 4 start angles `{-1, -0.5, 0, 0.5} × 180°` are crossed with
 * `anticlockwise ∈ {false, true}` (sign flips the sweep).
 *
 * Stresses the [SkPathBuilder.arcTo] sweep-mod-360 + multi-quadrant
 * cubic emitter and the stroker's behaviour on near-zero sweeps.
 *
 * Reference image: `manyarcs.png`, `620 × 330`, default white BG.
 */
public class ManyArcsGM : GM() {

    override fun getName(): String = "manyarcs"
    override fun getISize(): SkISize = SkISize.Make(620, 330)

    /**
     * Mirror of upstream `html_canvas_arc(path, x, y, r, start, end, ccw, callArcTo)`.
     * Always called with `callArcTo = true` from this GM.
     */
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

        c.translate(10f, 10f)

        // 20 angles.
        val sweepAngles = floatArrayOf(
            -123.7f, -2.3f, -2f, -1f, -0.3f, -0.000001f, 0f, 0.000001f, 0.3f, 0.7f,
            1f, 1.3f, 1.5f, 1.7f, 1.99999f, 2f, 2.00001f, 2.3f, 4.3f, 3934723942837.3f,
        )
        for (i in sweepAngles.indices) {
            sweepAngles[i] *= 180f
        }

        val startAngles = floatArrayOf(-1f, -0.5f, 0f, 0.5f)
        for (i in startAngles.indices) {
            startAngles[i] *= 180f
        }

        var anticlockwise = false
        var sign = 1f
        val n = startAngles.size
        for (i in 0 until n * 2) {
            if (i == n) {
                anticlockwise = true
                sign = -1f
            }
            val startAngle = startAngles[i % n] * sign
            c.save()
            for (j in sweepAngles.indices) {
                val path = SkPathBuilder()
                path.moveTo(0f, 2f)
                htmlCanvasArc(
                    path, 18f, 15f, 10f,
                    startAngle, startAngle + (sweepAngles[j] * sign),
                    anticlockwise, true,
                )
                path.lineTo(0f, 28f)
                c.drawPath(path.detach(), paint)
                c.translate(30f, 0f)
            }
            c.restore()
            c.translate(0f, 40f)
        }
    }
}
