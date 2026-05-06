package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/bug530095.cpp::bug591993` (40 × 140).
 *
 * One drawn line `(20, 20) → (120, 20)` strokes 10 px wide with round
 * caps and dash `[100, 100]` phase 100 — the dasher should produce a
 * single fully-painted stroke segment topped by round caps at each end.
 * Tests that the dasher's caps are honoured even when the dash phase
 * lands the start exactly on an "off" interval.
 */
public class Bug591993GM : GM() {

    override fun getName(): String = "bug591993"
    override fun getISize(): SkISize = SkISize.Make(40, 140)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeWidth = 10f
            pathEffect = SkDashPathEffect.Make(floatArrayOf(100f, 100f), 100f)
        }
        c.drawLine(20f, 20f, 120f, 20f, p)
    }
}
