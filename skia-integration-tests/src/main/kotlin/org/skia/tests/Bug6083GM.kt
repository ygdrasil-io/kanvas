package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/cubicpaths.cpp::bug6083` (DEF_SIMPLE_GM, 100 × 50).
 *
 * Two thin-stroked move/line/cubic paths drawn under a large negative
 * translate (`-500, -130`). The cubics are nearly identical (`p2.y`
 * differs by ~0.2 between the two), exercising stroker numerical
 * robustness on near-coincident outlines under big translates.
 */
public class Bug6083GM : GM() {

    override fun getName(): String = "bug6083"
    override fun getISize(): SkISize = SkISize.Make(100, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 15f
        }
        c.translate(-500f, -130f)

        // First path : moveTo + lineTo + cubicTo.
        val p1x = 526.109f; val p1y = 155.200f
        val p3x = 526.109f; val p3y = 241.840f

        val path1 = SkPathBuilder()
            .moveTo(500.988f, 155.200f)
            .lineTo(p1x, p1y)
            .cubicTo(p1x, p1y, 525.968f, 212.968f, p3x, p3y)
            .detach()
        c.drawPath(path1, p)
        c.translate(50f, 0f)

        // Second path : same shape with `p2.y = 213.172` instead of 212.968.
        val path2 = SkPathBuilder()
            .moveTo(500.988f, 155.200f)
            .lineTo(p1x, p1y)
            .cubicTo(p1x, p1y, 525.968f, 213.172f, p3x, p3y)
            .detach()
        c.drawPath(path2, p)
    }
}
