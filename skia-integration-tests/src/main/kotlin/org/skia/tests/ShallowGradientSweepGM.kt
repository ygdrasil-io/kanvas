package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/shallowgradient.cpp::ShallowGradientGM`
 * (`shallow_gradient_sweep` / `shallow_gradient_sweep_nodither`,
 * 800 × 800).
 *
 * 800 × 800 rect filled with a sweep gradient between two near-
 * identical greys (`0xFF555555 → 0xFF444444`) centred at the canvas
 * midpoint. The shallow ramp is a regression test for gradient
 * banding ; with `kClamp` the wraparound at 360° creates a hard line
 * across the negative-X axis.
 *
 * Two variants : with and without `paint.isDither = true`. Our
 * rasterizer doesn't apply dither (no noise pattern injected on the
 * gradient texel lookup), so the dither variant's reference image is
 * approximate ; the no-dither variant matches upstream pixel-for-pixel.
 */
public open class ShallowGradientSweepGM(
    private val dither: Boolean,
) : GM() {

    override fun getName(): String =
        if (dither) "shallow_gradient_sweep" else "shallow_gradient_sweep_nodither"

    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rect = SkRect.MakeLTRB(0f, 0f, 800f, 800f)
        val paint = SkPaint().apply {
            shader = SkSweepGradient.Make(
                center = SkPoint.Make(rect.width() / 2f, rect.height() / 2f),
                colors = intArrayOf(0xFF555555.toInt(), 0xFF444444.toInt()),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
            isDither = dither
        }
        c.drawRect(rect, paint)
    }
}

public class ShallowGradientSweepDitherGM : ShallowGradientSweepGM(dither = true)
public class ShallowGradientSweepNoDitherGM : ShallowGradientSweepGM(dither = false)
