package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.effects.SkColorFilterPriv
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColorSetARGB

/**
 * Port of Skia's `gm/shadowutils.cpp` :
 * `DEF_SIMPLE_GM(shadow_utils_gaussian_colorfilter, canvas, 512, 256)`.
 *
 * Renders two side-by-side 256×256 cells:
 *  - **Left** : a radial gradient (transparent-black → opaque-black) drawn
 *    over a red rectangle — raw alpha gradient over a colour.
 *  - **Right** : same composition, but the gradient paint carries a
 *    Gaussian colour filter via [SkColorFilterPriv.makeGaussian]. This
 *    exercises the soft-falloff filter used internally by the shadow mesh
 *    tessellator for penumbra edge blending.
 *
 * C++ source : `gm/shadowutils.cpp::shadow_utils_gaussian_colorfilter`.
 * Reference : `shadow_utils_gaussian_colorfilter.png` (512 × 256).
 *
 * ```cpp
 * DEF_SIMPLE_GM(shadow_utils_gaussian_colorfilter, canvas, 512, 256) {
 *     const SkRect r = SkRect::MakeWH(256, 256);
 *     const SkColor4f colors[] = { {0,0,0,0}, {0,0,0,1} };
 *     auto sh = SkShaders::RadialGradient(r.center(), r.width(), ...);
 *     SkPaint redPaint; redPaint.setColor(SK_ColorRED);
 *     SkPaint paint; paint.setShader(sh);
 *     canvas->drawRect(r, redPaint); canvas->drawRect(r, paint);
 *     canvas->translate(256, 0);
 *     paint.setColorFilter(SkColorFilterPriv::MakeGaussian());
 *     canvas->drawRect(r, redPaint); canvas->drawRect(r, paint);
 * }
 * ```
 */
public class ShadowUtilsGaussianColorFilterGM : GM() {

    override fun getName(): String = "shadow_utils_gaussian_colorfilter"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val r = SkRect.MakeWH(256f, 256f)

        // Radial gradient: transparent-black (alpha=0) at centre → opaque-black at edge.
        // Upstream uses SkColor4f {{0,0,0,0},{0,0,0,1}} with SkTileMode::kClamp.
        val gradColors = intArrayOf(
            SkColorSetARGB(0, 0, 0, 0),   // transparent black
            SkColorSetARGB(255, 0, 0, 0), // opaque black
        )
        val center = SkPoint.Make(r.centerX(), r.centerY())
        val radius = r.width()              // 256 — matches r.width() in upstream
        val shader = SkRadialGradient.Make(
            center = center,
            radius = radius,
            colors = gradColors,
            positions = null,
            tileMode = SkTileMode.kClamp,
        )

        val redPaint = SkPaint().apply { color = SK_ColorRED }
        val gradPaint = SkPaint().apply { this.shader = shader }

        // Left cell: raw gradient over red.
        c.drawRect(r, redPaint)
        c.drawRect(r, gradPaint)

        c.translate(256f, 0f)

        // Right cell: same, but gradient paint carries the Gaussian color filter.
        val gaussFilter = SkColorFilterPriv.makeGaussian()
        gradPaint.colorFilter = gaussFilter
        c.drawRect(r, redPaint)
        c.drawRect(r, gradPaint)
    }
}
