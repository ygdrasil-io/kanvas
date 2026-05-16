package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/blurtextsmallradii.cpp::blurSmallRadii`.
 *
 * Regression test for chrome bug `b/745290`. Renders the string
 * `"guest"` six times stacked vertically (20 px row stride), each
 * row drawn twice : first in red with a `kNormal` blur mask filter
 * at a small σ, then in green without any mask filter so the green
 * crisp glyph sits on top of the red halo.
 *
 * Sigmas walked : `0.25, 0.5, 0.75, 1.0, 1.5, 2.5`. The "small radii"
 * regime stresses the path where σ rounds to a per-row 1-pixel-radius
 * kernel — historical bug : the blur was sometimes dropped or
 * mis-rasterised when the kernel radius collapsed to ≤ 1.
 *
 * **Note on naming** : the upstream file is `blurtextsmallradii.cpp`
 * but the GM is registered as `blurSmallRadii` (camel-cased) — the
 * reference PNG is `blurSmallRadii.png` and [getName] therefore
 * returns the camel-cased registered name, not the snake-cased file
 * name.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(blurSmallRadii, canvas, 100, 150) {
 *     double sigmas[] = {0.25, 0.5, 0.75, 1.0, 1.5, 2.5};
 *     SkPaint paint;
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *
 *     for (auto sigma : sigmas) {
 *         paint.setColor(SK_ColorRED);
 *         paint.setAntiAlias(true);
 *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma));
 *         canvas->drawString("guest", 20, 10, font, paint);
 *
 *         paint.setMaskFilter(nullptr);
 *         paint.setColor(SK_ColorGREEN);
 *         canvas->drawString("guest", 20, 10, font, paint);
 *         canvas->translate(0, 20);
 *     }
 * }
 * ```
 */
public class BlurTextSmallRadiiGM : GM() {
    override fun getName(): String = "blurSmallRadii"
    override fun getISize(): SkISize = SkISize.Make(100, 150)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sigmas = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.5f)
        val paint = SkPaint()
        val font = ToolUtils.DefaultPortableFont()

        for (sigma in sigmas) {
            paint.color = SK_ColorRED
            paint.isAntiAlias = true
            paint.maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
            c.drawString("guest", 20f, 10f, font, paint)

            paint.maskFilter = null
            paint.color = SK_ColorGREEN
            c.drawString("guest", 20f, 10f, font, paint)
            c.translate(0f, 20f)
        }
    }
}
