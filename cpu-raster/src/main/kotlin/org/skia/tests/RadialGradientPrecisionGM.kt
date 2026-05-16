package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint

/**
 * Port of Skia's `gm/radial_gradient_precision.cpp::radial_gradient_precision`
 * (DEF_SIMPLE_GM, 200 × 200).
 *
 * Single `drawPaint` with a radial gradient whose centre sits far off-
 * canvas at `(1000, 1000)` and whose radius is only 40 px. The visible
 * canvas (`[0..200] × [0..200]`) lands well past the gradient's last
 * stop, so under `kRepeat` tiling each device pixel hits a different
 * "period" of the black ↔ green ramp. Originally caught loss of
 * precision in the GPU radial-gradient distance computation.
 */
public class RadialGradientPrecisionGM : GM() {

    override fun getName(): String = "radial_gradient_precision"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val center = SkPoint.Make(1000f, 1000f)
        val radius = 40f
        val colors = intArrayOf(SK_ColorBLACK, SK_ColorGREEN)
        val paint = SkPaint().apply {
            shader = SkRadialGradient.Make(center, radius, colors, null, SkTileMode.kRepeat)
        }
        c.drawPaint(paint)
    }
}
