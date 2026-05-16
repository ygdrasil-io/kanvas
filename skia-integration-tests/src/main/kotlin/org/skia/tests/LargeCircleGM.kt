package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/conicpaths.cpp` `DEF_SIMPLE_GM(largecircle, …)`.
 *
 * Anti-alias regression test: a large stroked circle with the same
 * centre/radius as [ArcCircleGapGM]. The point of the GM is that the
 * circle's edges should be smoothly anti-aliased — this catches
 * rasterizer bugs that bias the coverage at huge radii.
 *
 * Reference image: `largecircle.png`, 250 × 250, default white BG.
 */
public class LargeCircleGM : GM() {

    override fun getName(): String = "largecircle"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(50f, 100f)
        val cx = 1052.5390625f
        val cy = 506.8760978034711f
        val radius = 1096.702150363923f
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        c.drawCircle(cx, cy, radius, paint)
    }
}
