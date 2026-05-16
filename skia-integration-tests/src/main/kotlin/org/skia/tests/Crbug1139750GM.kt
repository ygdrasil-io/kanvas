package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1139750.cpp::crbug_1139750` (DEF_SIMPLE_GM_BG,
 * 50 × 50, BG = white).
 *
 * Forces the elliptical-RRect path with `strokeWidth = 2 * radius`,
 * making the inner radii exactly 0. The original GPU shader divided by
 * the inner radii to compute coverage and produced infinity, dropping
 * the geometry entirely. Pure correctness probe : we expect a normal
 * rounded-rect ring on the rendered image.
 */
public class Crbug1139750GM : GM() {

    init { setBGColor(SK_ColorWHITE) }

    override fun getName(): String = "crbug_1139750"
    override fun getISize(): SkISize = SkISize.Make(50, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }
        val r = SkRect.MakeXYWH(1f, 1f, 19f, 19f)
        val rr = SkRRect.MakeRectXY(r, 1f, 1f)
        c.translate(10f, 10f)
        c.scale(1.47619f, 1.52381f)
        c.drawRRect(rr, p)
    }
}
