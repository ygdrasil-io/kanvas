package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_938592.cpp::crbug_938592` (DEF_SIMPLE_GM,
 * 500 × 300).
 *
 * Hard-stop linear gradient (`blue → red → green` at `9/20` and
 * `11/20`) drawn on a 150 × 30 rect, mirrored 4 ways via
 * `translate + scale(±1, ±1)`. Originally a GPU-only test : the
 * triangulator winding could differ between the two halves of the rect
 * making interpolants disagree across rows and producing jagged hard-
 * stops. CPU raster doesn't have that issue ; we use this as a stress
 * for `kClamp` linear gradient with hard-stop positions.
 */
public class Crbug938592GM : GM() {

    override fun getName(): String = "crbug_938592"
    override fun getISize(): SkISize = SkISize.Make(500, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pts = arrayOf(SkPoint.Make(0f, 0f), SkPoint.Make(0f, 30f))
        // 6 stops, 6 colors — hard-stops at 9/20 and 11/20.
        val pos = floatArrayOf(0f, 9f / 20f, 9f / 20f, 11f / 20f, 11f / 20f, 20f / 20f)
        val colors = intArrayOf(
            SK_ColorBLUE, SK_ColorBLUE,
            SK_ColorRED, SK_ColorRED,
            SK_ColorGREEN, SK_ColorGREEN,
        )
        val grad = SkLinearGradient.Make(pts[0], pts[1], colors, pos, SkTileMode.kClamp)
        val paint = SkPaint().apply { shader = grad }

        val mirrorX = 400
        val mirrorY = 200
        c.translate(50f, 50f)
        for (i in 0 until 4) {
            c.save()
            if ((i and 0b01) != 0) {
                c.translate(0f, mirrorY.toFloat())
                c.scale(1f, -1f)
            }
            if ((i and 0b10) != 0) {
                c.translate(mirrorX.toFloat(), 0f)
                c.scale(-1f, 1f)
            }
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 150f, 30f), paint)
            c.restore()
        }
    }
}
