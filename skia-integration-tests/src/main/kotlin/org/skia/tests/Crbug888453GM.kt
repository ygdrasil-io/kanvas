package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/circulararcs.cpp::crbug_888453` (DEF_SIMPLE_GM,
 * 480 × 150).
 *
 * 19 small full-circle arcs at increasing radii (`r = 2..20`) drawn in
 * 3 rows : filled, hairline, and 2-px stroked. Originally a regression
 * test for a too-large-tolerance conic→quad chopping bug — at certain
 * radii the GPU path renderers produced visibly non-circular arcs.
 */
public class Crbug888453GM : GM() {

    override fun getName(): String = "crbug_888453"
    override fun getISize(): SkISize = SkISize.Make(480, 150)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val fill = SkPaint().apply { isAntiAlias = true }
        val hairline = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        val stroke = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }
        var x = 4f
        val y0 = 25f
        val y1 = 75f
        val y2 = 125f
        for (r in 2..20) {
            val rf = r.toFloat()
            c.drawArc(SkRect.MakeXYWH(x - rf, y0 - rf, 2f * rf, 2f * rf), 0f, 360f, false, fill)
            c.drawArc(SkRect.MakeXYWH(x - rf, y1 - rf, 2f * rf, 2f * rf), 0f, 360f, false, hairline)
            c.drawArc(SkRect.MakeXYWH(x - rf, y2 - rf, 2f * rf, 2f * rf), 0f, 360f, false, stroke)
            x += 2f * rf + 4f
        }
    }
}
