package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/cubicpaths.cpp::bug5099` (DEF_SIMPLE_GM, 50 × 50).
 *
 * Single AA-stroked cubic at width 10 — the cubic has near-coincident
 * control points making the stroker emit a degenerate normal at the
 * highly-curved tip. Originally exposed a stroker bug where the
 * outline self-intersected at that tip and left a crescent of un-filled
 * pixels.
 */
public class Bug5099GM : GM() {

    override fun getName(): String = "bug5099"
    override fun getISize(): SkISize = SkISize.Make(50, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 10f
        }
        val path = SkPathBuilder()
            .moveTo(6f, 27f)
            .cubicTo(31.5f, 1.5f, 3.5f, 4.5f, 29f, 29f)
            .detach()
        c.drawPath(path, p)
    }
}
