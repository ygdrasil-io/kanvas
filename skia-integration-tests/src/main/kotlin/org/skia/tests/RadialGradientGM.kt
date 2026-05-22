package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::RadialGradientGM`
 * (`radial_gradient`, 1280 x 1280).
 *
 * Single `drawRect(0,0,1280,1280)` filled with a 3-stop radial gradient
 * centred at the canvas centre (640, 640), radius 640. Colours `(0x7f7f7f7f,
 * 0x7f7f7f7f, 0xb2000000)` at positions `(0.0, 0.35, 1.0)` over an opaque
 * black background. Upstream paints with `setDither(true)` — our raster
 * pipeline doesn't currently apply dither, so we accept the resulting
 * ratchet floor instead of byte-exact match against the reference.
 *
 * C++ original :
 * ```cpp
 * SkString getName() const override { return SkString("radial_gradient"); }
 * SkISize getISize() override { return {1280, 1280}; }
 * void onDraw(SkCanvas* canvas) override {
 *     const SkISize dim = this->getISize();
 *     canvas->drawColor(0xFF000000);
 *     SkPaint paint;
 *     paint.setDither(true);
 *     SkPoint center;
 *     center.set(SkIntToScalar(dim.width())/2, SkIntToScalar(dim.height())/2);
 *     SkScalar radius = SkIntToScalar(dim.width())/2;
 *     const SkScalar pos[] = { 0.0f, 0.35f, 1.0f };
 *     SkColorConverter conv({ 0x7f7f7f7f, 0x7f7f7f7f, 0xb2000000 });
 *     paint.setShader(SkShaders::RadialGradient(center, radius,
 *                                           {{conv.colors4f(), pos, SkTileMode::kClamp}, {}}));
 *     canvas->drawRect({0, 0, dim.width(), dim.height()}, paint);
 * }
 * ```
 */
public class RadialGradientGM : GM() {

    override fun getName(): String = "radial_gradient"
    override fun getISize(): SkISize = SkISize.Make(1280, 1280)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val w = 1280f
        val h = 1280f
        c.drawColor(0xFF000000.toInt())
        val center = SkPoint(w / 2f, h / 2f)
        val radius = w / 2f
        val pos = floatArrayOf(0f, 0.35f, 1f)
        // Upstream encodes the stops as 0xAARRGGBB ints with a 0x7f / 0xb2
        // alpha — our SkColor convention is identical, so we can pass them
        // straight through.
        val colors = intArrayOf(0x7f7f7f7f, 0x7f7f7f7f, 0xb2000000.toInt())
        val paint = SkPaint().apply {
            isDither = true
            shader = SkRadialGradient.Make(
                center = center,
                radius = radius,
                colors = colors,
                positions = pos,
                tileMode = SkTileMode.kClamp,
            )
        }
        c.drawRect(SkRect.MakeLTRB(0f, 0f, w, h), paint)
    }
}
