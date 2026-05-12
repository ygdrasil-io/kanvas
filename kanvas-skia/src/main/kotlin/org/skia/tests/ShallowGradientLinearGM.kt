package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/shallowgradient.cpp` —
 * `M(linear, true)` factory, GM name `shallow_gradient_linear`.
 *
 * Sibling of [ShallowGradientLinearNoditherGM] : same 800 × 800
 * `0xFF555555 → 0xFF444444` linear gradient with endpoints `(0, 0)`
 * to `(800, 800)`, but with `paint.isDither = true`.
 *
 * Our rasterizer doesn't currently apply dither (the [SkPaint.isDither]
 * flag is recorded but no noise is injected on gradient sampling), so
 * the rendered output is byte-for-byte identical to the no-dither
 * variant. The upstream dither variant's reference image only differs
 * from the no-dither one by ≤1 LSB per channel — we report similarity
 * against that target.
 */
public class ShallowGradientLinearGM : GM() {

    override fun getName(): String = "shallow_gradient_linear"
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
        val paint = SkPaint().apply {
            this.shader = shader
            isDither = true
        }
        c.drawRect(SkRect.MakeWH(w, h), paint)
    }
}
