package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::RadialGradient3GM`
 * (`radial_gradient3` / `radial_gradient3_nodither`, 500 x 500).
 *
 * Single 500x500 drawRect filled with a 2-stop radial gradient
 * (white -> opaque black). The centre is at `(0, 0)` and the radius
 * is 3000 -- the visible canvas occupies only ~17 % of the gradient
 * span. Designed to exhibit banding under low-precision raster.
 *
 * C++ original :
 * ```cpp
 * SkISize getISize() override { return {500, 500}; }
 * void onOnceBeforeDraw() override {
 *     const SkPoint center = { 0, 0 };
 *     const SkScalar kRadius = 3000;
 *     const SkColor4f kColors[] = { {1,1,1,1}, {0,0,0,1} };
 *     fShader = SkShaders::RadialGradient(center, kRadius,
 *                                         {{kColors, {}, SkTileMode::kClamp}, {}});
 * }
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setShader(fShader);
 *     paint.setDither(fDither);
 *     canvas->drawRect(SkRect::MakeWH(500, 500), paint);
 * }
 * ```
 */
public class RadialGradient3GM(
    private val dither: Boolean = true,
) : GM() {

    private var shader: SkShader? = null

    override fun getName(): String =
        if (dither) "radial_gradient3" else "radial_gradient3_nodither"

    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onOnceBeforeDraw() {
        val center = SkPoint(0f, 0f)
        val kRadius = 3000f
        val colors = intArrayOf(
            SkColor4f(1f, 1f, 1f, 1f).toSkColor(),
            SkColor4f(0f, 0f, 0f, 1f).toSkColor(),
        )
        shader = SkRadialGradient.Make(
            center = center,
            radius = kRadius,
            colors = colors,
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            this.shader = this@RadialGradient3GM.shader
            isDither = dither
        }
        c.drawRect(SkRect.MakeWH(500f, 500f), paint)
    }
}
