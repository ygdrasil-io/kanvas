package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM(BlurSmallSigma, …, 512, 256)`.
 *
 * Two-quadrant smoke test for the image-filter blur identity guards :
 *
 *  - Left rect (64..192, 64..192) — `Blur(16, 1e-5)`. The vertical sigma
 *    is effectively zero ; the result should be a horizontal-only blur
 *    (no red because no red is drawn).
 *  - Right rect (320..448, 64..192) — red base + black with
 *    `Blur(1e-5, 1e-5)`. Both sigmas collapse to identity; the black
 *    should fully cover the red.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(BlurSmallSigma, canvas, 512, 256) {
 *     {
 *         SkPaint paint;
 *         paint.setImageFilter(SkImageFilters::Blur(16.f, 1e-5f, nullptr));
 *         canvas->drawRect(SkRect::MakeLTRB(64, 64, 192, 192), paint);
 *     }
 *
 *     {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorRED);
 *         SkRect rect = SkRect::MakeLTRB(320, 64, 448, 192);
 *         canvas->drawRect(rect, paint);
 *         paint.setColor(SK_ColorBLACK);
 *         paint.setImageFilter(SkImageFilters::Blur(1e-5f, 1e-5f, nullptr));
 *         canvas->drawRect(rect, paint);
 *     }
 * }
 * ```
 */
public class BlurSmallSigmaGM : GM() {
    override fun getName(): String = "BlurSmallSigma"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Left rect — horizontal-only blur (sigmaY → 0 collapses to identity in y).
        run {
            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.Blur(16f, 1e-5f, null)
            }
            c.drawRect(SkRect.MakeLTRB(64f, 64f, 192f, 192f), paint)
        }

        // Right rect — tiny blur, treated as identity ; black rect should
        // completely cover the underlying red rect.
        run {
            val paint = SkPaint()
            paint.color = SK_ColorRED
            val rect = SkRect.MakeLTRB(320f, 64f, 448f, 192f)
            c.drawRect(rect, paint)

            paint.color = SK_ColorBLACK
            paint.imageFilter = SkImageFilters.Blur(1e-5f, 1e-5f, null)
            c.drawRect(rect, paint)
        }
    }
}
