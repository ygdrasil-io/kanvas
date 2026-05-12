package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/shallowgradient.cpp` —
 * `M(radial, true)` factory, GM name `shallow_gradient_radial`.
 *
 * Sibling of [ShallowGradientRadialNoditherGM] : same 800 × 800
 * `0xFF555555 → 0xFF444444` radial gradient (centre `(400, 400)`,
 * radius `400`, kClamp tile mode) but with `paint.isDither = true`.
 *
 * Our rasterizer doesn't currently apply dither, so the rendered
 * output is identical to the no-dither variant. The upstream dither
 * variant's reference image only differs from the no-dither one by
 * ≤1 LSB per channel — we report similarity against that target.
 */
public class ShallowGradientRadialGM : GM() {

    override fun getName(): String = "shallow_gradient_radial"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val w = 800f
        val h = 800f
        val shader = SkRadialGradient.Make(
            center = SkPoint(w / 2f, h / 2f),
            radius = w / 2f,
            colors = intArrayOf(0xFF555555.toInt(), 0xFF444444.toInt()),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            this.shader = shader
            isDither = true
        }
        c.drawRect(SkRect.MakeWH(w, h), paint)
    }
}
