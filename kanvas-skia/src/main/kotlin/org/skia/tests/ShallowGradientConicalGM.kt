package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/shallowgradient.cpp::ShallowGradientGM`
 * (`shallow_gradient_conical` / `shallow_gradient_conical_nodither`,
 * 800 × 800).
 *
 * 800 × 800 rect filled with a 2-point conical gradient sharing the
 * same centre (effectively a radial-gradient with a tiny inner radius
 * `width / 64 = 12.5 px` and outer radius `width / 2 = 400 px`)
 * between two near-identical greys.
 *
 * Two variants : with and without `paint.isDither = true`. Our
 * rasterizer doesn't apply dither, so the no-dither variant matches
 * pixel-for-pixel ; the dither variant's reference image is
 * approximate.
 */
public open class ShallowGradientConicalGM(
    private val dither: Boolean,
) : GM() {

    override fun getName(): String =
        if (dither) "shallow_gradient_conical" else "shallow_gradient_conical_nodither"

    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rect = SkRect.MakeLTRB(0f, 0f, 800f, 800f)
        val center = SkPoint.Make(rect.width() / 2f, rect.height() / 2f)
        val paint = SkPaint().apply {
            shader = SkConicalGradient.Make(
                start = center, startRadius = rect.width() / 64f,
                end = center, endRadius = rect.width() / 2f,
                colors = intArrayOf(0xFF555555.toInt(), 0xFF444444.toInt()),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
            isDither = dither
        }
        c.drawRect(rect, paint)
    }
}

public class ShallowGradientConicalDitherGM : ShallowGradientConicalGM(dither = true)
public class ShallowGradientConicalNoDitherGM : ShallowGradientConicalGM(dither = false)
