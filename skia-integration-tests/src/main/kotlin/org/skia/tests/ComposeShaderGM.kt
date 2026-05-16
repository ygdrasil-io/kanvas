package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's
 * [`gm/composeshader.cpp::ComposeShaderGM`](https://github.com/google/skia/blob/main/gm/composeshader.cpp).
 *
 * Draws a 100×100 green rectangle, then overlays a `Blend(DstIn,
 * red→blue horizontal gradient, opaque-black→half-transparent-black
 * vertical gradient)` shader. The DstIn semantic forces the gradient
 * pair's alpha (the vertical gradient) to mask the colour pair (the
 * horizontal gradient), producing a left-to-right red→blue swatch
 * fading from opaque at the top to half-transparent at the bottom.
 *
 * Unblocked by R-final.2 — the `SkShader.makeWithLocalMatrix` API
 * adopt-up that the rest of the file's GMs depend on transitively
 * brought this base case into scope. Pure pre-existing shaders
 * ([SkLinearGradient.Make] + [SkShaders.Blend]) — no LM call site
 * here, but the GM gates on the R-final.2 file landing because its
 * upstream sibling tests (`composeshader_bitmap_lm`, etc.) needed
 * `makeWithLocalMatrix` to be portable as a unit.
 *
 * C++ original :
 * ```cpp
 * static sk_sp<SkShader> make_shader(SkBlendMode mode) {
 *     SkPoint pts[2];
 *     SkColor4f colors[2];
 *     pts[0].set(0, 0);
 *     pts[1].set(SkIntToScalar(100), 0);
 *     colors[0] = SkColors::kRed;
 *     colors[1] = SkColors::kBlue;
 *     auto shaderA = SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}});
 *
 *     pts[0].set(0, 0);
 *     pts[1].set(0, SkIntToScalar(100));
 *     colors[0] = SkColors::kBlack;
 *     colors[1] = {0,0,0, 0x80/255.f};
 *     auto shaderB = SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}});
 *
 *     return SkShaders::Blend(mode, std::move(shaderA), std::move(shaderB));
 * }
 *
 * class ComposeShaderGM : public skiagm::GM {
 *     void onOnceBeforeDraw() override { fShader = make_shader(SkBlendMode::kDstIn); }
 *     SkString getName() const override { return SkString("composeshader"); }
 *     SkISize getISize() override { return SkISize::Make(120, 120); }
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         canvas->drawRect(SkRect::MakeWH(100, 100), paint);
 *         paint.setShader(fShader);
 *         canvas->drawRect(SkRect::MakeWH(100, 100), paint);
 *     }
 * };
 * ```
 */
public class ComposeShaderGM : GM() {

    private var fShader: SkShader? = null

    override fun onOnceBeforeDraw() {
        fShader = makeShader(SkBlendMode.kDstIn)
    }

    override fun getName(): String = "composeshader"
    override fun getISize(): SkISize = SkISize.Make(120, 120)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { color = SK_ColorGREEN }
        c.drawRect(SkRect.MakeWH(100f, 100f), paint)
        paint.shader = fShader
        c.drawRect(SkRect.MakeWH(100f, 100f), paint)
    }

    /**
     * Mirrors upstream's `make_shader(mode)` — builds the two
     * orthogonal linear gradients and composes them via `SkShaders::Blend`.
     * Upstream uses [SkColor4f] colours ; we route through 8-bit
     * [org.skia.math.SkColor] ints since [SkLinearGradient.Make]
     * takes an `IntArray` here. The half-transparent black is encoded
     * as `argb(0x80, 0, 0, 0)` — same byte representation upstream
     * produces from `{0, 0, 0, 0x80/255.f}`.
     */
    private fun makeShader(mode: SkBlendMode): SkShader {
        val shaderA = SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(100f, 0f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val shaderB = SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(0f, 100f),
            colors = intArrayOf(SK_ColorBLACK, SkColorSetARGB(0x80, 0, 0, 0)),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        return SkShaders.Blend(mode, shaderA, shaderB)
    }
}
