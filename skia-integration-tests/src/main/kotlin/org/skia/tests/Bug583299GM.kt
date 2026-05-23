package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathMeasure
import org.graphiks.math.SkISize
import org.skia.utils.SkParsePath

/**
 * Port of Skia's `gm/arcto.cpp` (`DEF_SIMPLE_GM(bug583299, …, 300, 300)`).
 *
 * Regression test for bug 583299. Draws a closed figure-eight arc (two
 * overlapping 50-radius circles sharing the same chord `60,60 → 160,60`)
 * using:
 *  - stroke-only, width 100, AA, green (`#008200`), square caps,
 *  - a dash effect with intervals `{0, pathLength}` and phase `0`.
 *
 * The `{0, pathLength}` dash pattern produces a single "off" segment
 * covering the entire path — so the dash effect renders nothing — exercising
 * the degenerate dash/cap code path. The reference image should be a blank
 * 300×300 white canvas.
 *
 * Reference image: `bug583299.png`, 300 × 300, white background.
 */
public class Bug583299GM : GM() {

    override fun getName(): String = "bug583299"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val d = "M60,60 A50,50 0 0 0 160,60 A50,50 0 0 0 60,60z"
        val path = SkParsePath.FromSVGString(d) ?: return
        val meas = SkPathMeasure(path, false)
        val length = meas.getLength()
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 100f
            isAntiAlias = true
            color = 0xFF008200.toInt()
            strokeCap = SkPaint.Cap.kSquare_Cap
            pathEffect = SkDashPathEffect.Make(floatArrayOf(0f, length), 0f)
        }
        c.drawPath(path, paint)
    }
}
