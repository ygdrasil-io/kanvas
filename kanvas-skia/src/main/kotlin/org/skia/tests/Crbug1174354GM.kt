package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1174354.cpp::crbug_1174354`.
 *
 * Regression test for `crbug.com/1174354`. The initial fix for
 * `crbug.com/1156805` had an issue where the border added to the
 * downsampled texture that was used as input to the blur could
 * actually bring in transparency when there wasn't any within the
 * original src bounds. It was populated using a filtering draw from
 * the full-res source that could read from outside the pixels
 * surrounding the original src bounds.
 *
 * The GM emits four 50×50 sweep-gradient tiles down a 70×250 canvas,
 * each subjected to a different `σ` backdrop blur via two stacked
 * save-layers. The outer layer holds the sweep + transparent border ;
 * the inner uses `SaveLayerRec.backdrop = Blur(σ, σ, kClamp, _,
 * srcRect)` so the blur reads only the opaque region.
 *
 * C++ original:
 * ```cpp
 * static void draw_bg_blur(SkCanvas* canvas, SkIRect rect, float sigma) {
 *     auto outsetRect = SkRect::Make(rect).makeOutset(10, 10);
 *     canvas->saveLayer(outsetRect, nullptr);
 *     const SkColor4f colors[] = {SkColors::kRed, SkColors::kBlue, SkColors::kGreen};
 *     float cx = (rect.left() + rect.right()) / 2.f;
 *     float cy = (rect.top()  + rect.bottom()) / 2.f;
 *     auto g = SkShaders::SweepGradient({cx, cy}, 0, 45,
 *                                       {{colors, {}, SkTileMode::kMirror}, {}});
 *     SkPaint paint;
 *     paint.setShader(std::move(g));
 *     canvas->drawRect(SkRect::Make(rect), paint);
 *     SkCanvas::SaveLayerRec rec;
 *     auto blur = SkImageFilters::Blur(sigma, sigma, SkTileMode::kClamp, nullptr, rect);
 *     rec.fBounds = &outsetRect;
 *     rec.fBackdrop = blur.get();
 *     canvas->saveLayer(rec);
 *     canvas->restore();
 *     canvas->restore();
 * }
 *
 * DEF_SIMPLE_GM(crbug_1174354, canvas, 70, 250) {
 *     draw_bg_blur(canvas, SkIRect::MakeXYWH(10,  10, 50, 50),  5);
 *     draw_bg_blur(canvas, SkIRect::MakeXYWH(10,  70, 50, 50), 15);
 *     draw_bg_blur(canvas, SkIRect::MakeXYWH(10, 130, 50, 50), 30);
 *     draw_bg_blur(canvas, SkIRect::MakeXYWH(10, 190, 50, 50), 70);
 * }
 * ```
 */
public class Crbug1174354GM : GM() {
    override fun getName(): String = "crbug_1174354"
    override fun getISize(): SkISize = SkISize.Make(70, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawBgBlur(c, SkIRect.MakeXYWH(10, 10, 50, 50), 5f)
        drawBgBlur(c, SkIRect.MakeXYWH(10, 70, 50, 50), 15f)
        drawBgBlur(c, SkIRect.MakeXYWH(10, 130, 50, 50), 30f)
        drawBgBlur(c, SkIRect.MakeXYWH(10, 190, 50, 50), 70f)
    }

    private fun drawBgBlur(canvas: SkCanvas, rect: SkIRect, sigma: Float) {
        val outsetRect = SkRect.Make(rect).makeOutset(10f, 10f)
        canvas.saveLayer(outsetRect, null)

        val colors = intArrayOf(SK_ColorRED, SK_ColorBLUE, SK_ColorGREEN)
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val shader = SkSweepGradient.Make(
            center = SkPoint(cx, cy),
            startAngle = 0f,
            endAngle = 45f,
            colors = colors,
            positions = null,
            tileMode = SkTileMode.kMirror,
        )
        val paint = SkPaint().apply { this.shader = shader }
        canvas.drawRect(SkRect.Make(rect), paint)

        // Now the backdrop-blur save-layer that should only read the
        // opaque region : `cropRect = rect` restricts the blur input
        // to the painted square.
        val blur = SkImageFilters.Blur(
            sigmaX = sigma,
            sigmaY = sigma,
            tileMode = SkTileMode.kClamp,
            input = null,
            cropRect = rect,
        )
        canvas.saveLayer(
            SaveLayerRec(
                bounds = outsetRect,
                paint = null,
                backdrop = blur,
                flags = 0,
            )
        )
        canvas.restore()
        canvas.restore()
    }
}
