package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(thin_aa_dash_lines, …)` (330 × 110).
 *
 * Draws a grid of sub-pixel-width (`0.25 / 100 = 0.0025 px`) dashed lines
 * for three cap types (kButt, kSquare, kRound), scaled up by `kScale = 100`
 * so the canvas operates at 1/100-unit precision.
 *
 * Lines are horizontal and vertical, spaced one dash-cycle apart
 * (`kStep = kIntervals[0] + kIntervals[1]`), with a tiny subpixel shift
 * (`kSubstep`) each iteration to sample different sub-pixel offsets.
 *
 * The test probes the AA rasterizer for thin-line dropout and the dash
 * decomposer for correctness at very small stroke widths.
 *
 * Reference image: `thin_aa_dash_lines.png`, 330 × 110, default white BG.
 */
public class ThinAaDashLinesGM : GM() {

    override fun getName(): String = "thin_aa_dash_lines"
    override fun getISize(): SkISize = SkISize.Make(330, 110)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kScale = 100f
        val intervals = floatArrayOf(10f / kScale, 5f / kScale)
        val paint = SkPaint().apply {
            pathEffect = SkDashPathEffect.Make(intervals, 0f)
            isAntiAlias = true
            strokeWidth = 0.25f / kScale
        }
        val kSubstep = 0.05f / kScale
        val kStep = intervals[0] + intervals[1]

        c.scale(kScale, kScale)
        c.translate(intervals[1], intervals[1])

        for (cap in arrayOf(SkPaint.Cap.kButt_Cap, SkPaint.Cap.kSquare_Cap, SkPaint.Cap.kRound_Cap)) {
            paint.strokeCap = cap
            var x = -0.5f * intervals[1]
            while (x < 105f / kScale) {
                c.drawLine(x, 0f, x, 100f / kScale, paint)
                c.drawLine(0f, x, 100f / kScale, x, paint)
                x += kStep + kSubstep
            }
            c.translate(110f / kScale, 0f)
        }
    }
}
