package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/bug615686.cpp::bug615686` (DEF_SIMPLE_GM, 250 × 250).
 *
 * Single AA-stroked self-intersecting cubic with `strokeWidth = 20`.
 * Originally exposed a stroker bug in `SkPathStroker::cubicPerpRay` —
 * rays computed at high curvature points would produce inverted
 * outlines, leaving a sliver of un-filled pixels at the loop's
 * crossover. Pure path / stroker stress.
 */
public class Bug615686GM : GM() {

    override fun getName(): String = "bug615686"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
        }
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(200f, 200f, 0f, 200f, 200f, 0f)
            .detach()
        c.drawPath(path, paint)
    }
}
