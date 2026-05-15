package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.SkShaderMaskFilter
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/shadermaskfilter.cpp::DEF_SIMPLE_GM(
 * shadermaskfilter_gradient, canvas, 512, 512)`.
 *
 * Builds a [SkLinearGradient] from `(0, 0)` to `(100, 150)` with stops
 * `transparent → opaque white`, wraps it in a [SkShaderMaskFilter], and
 * uses it as the mask filter when drawing a red oval at `[0,0,100,150]`.
 * The CTM is `translate(20,20).scale(2,2)` so the mask filter alpha
 * gradient extends across the painted oval at 2× device-space scale.
 */
public class ShaderMaskFilterGM : GM() {

    override fun getName(): String = "shadermaskfilter_gradient"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val r = SkRect.MakeLTRB(0f, 0f, 100f, 150f)

        // make_shader : LinearGradient(p0, p1, [transparent, white], kRepeat)
        val transparent = SkColor4f.kTransparent.toSkColor()
        val white = SkColor4f.kWhite.toSkColor()
        val shader = SkLinearGradient.Make(
            SkPoint(r.left, r.top),
            SkPoint(r.right, r.bottom),
            intArrayOf(transparent, white),
            null,
            SkTileMode.kRepeat,
        )
        val mf = SkShaderMaskFilter.Make(shader)

        c.translate(20f, 20f)
        c.scale(2f, 2f)

        val paint = SkPaint().apply {
            maskFilter = mf
            color = SK_ColorRED
            isAntiAlias = true
        }
        c.drawOval(r, paint)
    }
}
