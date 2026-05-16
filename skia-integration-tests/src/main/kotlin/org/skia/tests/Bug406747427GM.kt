package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/circulararcs.cpp::bug406747427` (DEF_SIMPLE_GM,
 * 400 × 400).
 *
 * Three `drawArc` calls with `kRound_Cap` and stroke widths that exceed
 * the arc's radius : 50 px, 48 px, 80 px stroked over 50 × 50 ovals.
 * Originally exposed a stroker bug where round-cap radius extended past
 * the arc's path length and the resulting outline self-intersected.
 */
public class Bug406747427GM : GM() {

    override fun getName(): String = "bug406747427"
    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeCap = SkPaint.Cap.kRound_Cap
        }

        paint.color = SkColorSetARGB(255, 255, 0, 0)
        paint.strokeWidth = 50f
        c.drawArc(SkRect.MakeXYWH(100f, 40f, 50f, 50f), 45f, 275f, false, paint)

        paint.color = SkColorSetARGB(255, 0, 0, 255)
        paint.strokeWidth = 48f
        c.drawArc(SkRect.MakeXYWH(100f, 140f, 50f, 50f), 45f, 275f, false, paint)

        paint.color = SkColorSetARGB(255, 0, 255, 0)
        paint.strokeWidth = 80f
        c.drawArc(SkRect.MakeXYWH(100f, 280f, 50f, 50f), 45f, 275f, false, paint)
    }
}
