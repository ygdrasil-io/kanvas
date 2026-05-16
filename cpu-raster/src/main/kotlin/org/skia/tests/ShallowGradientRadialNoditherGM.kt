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
 * `M(radial, false)` factory, GM name `shallow_gradient_radial_nodither`.
 *
 * Sibling of [ShallowGradientLinearNoditherGM], same 0xFF555555→0xFF444444
 * stops but rendered with a radial gradient: centre `(400, 400)`, radius
 * `400`, kClamp tile mode.
 *
 * Reference image: `shallow_gradient_radial_nodither.png`, 800 × 800.
 *
 * Stresses :
 *  - Radial gradient with tight stops on a long radial sweep — the
 *    sqrt-of-distance lookup must agree with upstream to within 1 ULP
 *    of the lerp parameter to keep the banding from drifting;
 *  - Centre pixel near `(400, 400)` exercises the t=0 boundary on a
 *    radial gradient (every neighbouring sample lerps *very* slightly
 *    away from t=0).
 */
public class ShallowGradientRadialNoditherGM : GM() {

    override fun getName(): String = "shallow_gradient_radial_nodither"
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
        val paint = SkPaint().apply { this.shader = shader }
        c.drawRect(SkRect.MakeWH(w, h), paint)
    }
}
