package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/gradients.cpp::small_color_stop`
 * (`DEF_SIMPLE_GM(small_color_stop, canvas, 100, 150)`).
 *
 * Regression test for very-small gradient color stop intervals
 * (`pos = {0, 0.003, 1}`). Renders a 3-stop two-point-conical
 * gradient over a yellow background — the second stop at 0.003
 * stresses the gradient evaluator's interpolation precision.
 */
public class SmallColorStopGM : GM() {

    override fun getName(): String = "small_color_stop"
    override fun getISize(): SkISize = SkISize.Make(100, 150)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val colors = intArrayOf(
            SkColorSetARGB(0xFF, 0, 0xFF, 0),     // green
            SkColorSetARGB(0xFF, 0xFF, 0, 0),     // red
            SkColorSetARGB(0xFF, 0xFF, 0xFF, 0),  // yellow
        )
        val pos = floatArrayOf(0f, 0.003f, 1f)
        val c0 = SkPoint(200f, 25f)
        val c1 = SkPoint(200f, 25f)

        val paint = SkPaint().apply { color = SK_ColorYELLOW }
        c.drawRect(SkRect.MakeWH(100f, 150f), paint)

        paint.shader = SkConicalGradient.Make(
            c0, 20f, c1, 10f,
            colors, pos, SkTileMode.kClamp,
        )
        c.drawRect(SkRect.MakeWH(100f, 150f), paint)
    }
}
