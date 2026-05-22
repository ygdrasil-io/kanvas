package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/dashing.cpp::Dashing3GM` (640 × 480).
 *
 * Tiles 9 blocks of dashed line strips under a variety of
 * `(dashLength, phase, strokeWidth, circles)` combinations,
 * plus a 4-phase row of rect-fast-path probes at the bottom.
 *
 * Each block draws 100×100 worth of horizontal BW dashed lines and
 * vertical AA dashed lines stacked at `10 * strokeWidth` spacing.
 * Exercises the rasterizer's dash fast paths (points / rects) and the
 * fallback through stroker (round caps + rotation).
 */
public class Dashing3GM : GM() {

    override fun getName(): String = "dashing3"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    private fun drawDashedLines(
        canvas: SkCanvas,
        lineLength: Float,
        phase: Float,
        dashLength: Float,
        strokeWidth: Int,
        circles: Boolean,
    ) {
        val p = SkPaint().apply {
            color = SK_ColorBLACK
            style = SkPaint.Style.kStroke_Style
            this.strokeWidth = strokeWidth.toFloat()
            if (circles) strokeCap = SkPaint.Cap.kRound_Cap
        }
        val intervals = floatArrayOf(dashLength, dashLength)
        p.pathEffect = SkDashPathEffect.Make(intervals, phase)

        var y = 0
        while (y < 100) {
            canvas.drawPoints(
                SkCanvas.PointMode.kLines,
                arrayOf(SkPoint.Make(0f, y.toFloat()), SkPoint.Make(lineLength, y.toFloat())),
                p,
            )
            y += 10 * strokeWidth
        }
        p.isAntiAlias = true
        var x = 0
        while (x < 100) {
            canvas.drawPoints(
                SkCanvas.PointMode.kLines,
                arrayOf(SkPoint.Make(x.toFloat(), 0f), SkPoint.Make(x.toFloat(), lineLength)),
                p,
            )
            x += 14 * strokeWidth
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.save(); c.translate(2f, 0f);    drawDashedLines(c, 100f, 0f, 1f, 1, false); c.restore()
        c.save(); c.translate(112f, 0f);  drawDashedLines(c, 100f, 0.5f, 1f, 1, false); c.restore()
        c.save(); c.translate(222f, 0f);  drawDashedLines(c, 100f, 1f, 1f, 1, false); c.restore()
        c.save(); c.translate(332f, 0f);  drawDashedLines(c, 99.5f, 0.5f, 1f, 1, false); c.restore()
        c.save(); c.translate(446f, 0f);  drawDashedLines(c, 100f, 0f, 255f, 1, false); c.restore()

        c.save(); c.translate(2f, 110f);  drawDashedLines(c, 100f, 0f, 3f, 3, false); c.restore()
        c.save(); c.translate(112f, 110f); drawDashedLines(c, 100f, 1.5f, 3f, 3, false); c.restore()

        c.save(); c.translate(2f, 220f);  drawDashedLines(c, 100f, 1f, 1f, 1, true); c.restore()
        c.save(); c.translate(112f, 220f); drawDashedLines(c, 100f, 0f, 3f, 3, true); c.restore()

        val r2 = sqrt(2f) / 2f
        c.save()
        c.translate(332f + r2 * 100f, 110f + r2 * 100f)
        c.rotate(45f)
        c.translate(-50f, -50f)
        drawDashedLines(c, 100f, 1f, 1f, 1, false)
        c.restore()

        for (phase in 0..3) {
            c.save()
            c.translate((phase * 110 + 2).toFloat(), 330f)
            drawDashedLines(c, 100f, phase.toFloat(), 3f, 1, false)
            c.restore()
        }
    }
}
