package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

/**
 * Port of upstream Skia's `gm/gradients.cpp::GradientsDegenrate2PointGM`
 * (`DEF_GM(return new GradientsDegenrate2PointGM(true);)` — dither variant).
 *
 * 320×320 canvas filled with blue, then overdrawn with a 2-point conical
 * gradient designed to trigger the degenerate "linear" code path in the
 * conical gradient evaluator (X² coefficient = 0 — see the JS example in
 * the upstream comment). Stops are red→green→green→red at 0 / 0.01 / 0.99 / 1.
 *
 * **Note** : the `dither` parameter from upstream is not exposed by the
 * kanvas raster pipeline ; we render the dither=true variant which is the
 * default GM upstream registers first.
 */
public class GradientsDegenrate2PointGM : GM() {

    override fun getName(): String = "gradients_degenerate_2pt"
    override fun getISize(): SkISize = SkISize.Make(320, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorBLUE)

        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorGREEN, SK_ColorRED)
        val pos = floatArrayOf(0f, 0.01f, 0.99f, 1f)

        val c0 = SkPoint(-80f, 25f)
        val r0 = 70f
        val c1 = SkPoint(0f, 25f)
        val r1 = 150f

        val shader = SkConicalGradient.Make(c0, r0, c1, r1, colors, pos, SkTileMode.kClamp)
        val paint = SkPaint().apply { this.shader = shader }
        c.drawPaint(paint)
    }
}
