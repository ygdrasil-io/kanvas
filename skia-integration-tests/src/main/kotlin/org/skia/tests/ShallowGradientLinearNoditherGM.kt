package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/shallowgradient.cpp` —
 * `M(linear, false)` factory, GM name `shallow_gradient_linear_nodither`.
 *
 * A single 800×800 linear gradient between two near-identical greys
 * (`0xFF555555` → `0xFF444444`), endpoints `(0, 0)` to `(800, 800)`.
 * The "nodither" variant matches our pipeline (we don't dither anyway —
 * we render at full byte precision in the working colorspace).
 *
 * Reference image: `shallow_gradient_linear_nodither.png`, 800 × 800.
 *
 * Stresses :
 *  - The gradient lookup on a barely-distinguishable colour pair —
 *    a single ULP error on the lerp shows up as a sharp banding
 *    artefact across pixels;
 *  - 800-pixel horizontal/vertical lookup spans, the longest single
 *    linear gradient in our test suite by a factor of ~2.
 */
public class ShallowGradientLinearNoditherGM : GM() {

    override fun getName(): String = "shallow_gradient_linear_nodither"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val w = 800f
        val h = 800f
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(w, h),
            colors = intArrayOf(0xFF555555.toInt(), 0xFF444444.toInt()),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply { this.shader = shader }
        c.drawRect(SkRect.MakeWH(w, h), paint)
    }
}
