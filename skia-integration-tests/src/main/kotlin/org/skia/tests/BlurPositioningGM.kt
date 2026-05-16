package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalarCeilToInt

/**
 * Port of Skia's `gm/blurpositioning.cpp` (`check_small_sigma_offset`).
 *
 * Validates that small-sigma blur image filters preserve geometric
 * centering: for each sigma in `{0.0, 0.1, 0.2, 0.3, 0.4, 0.6, 0.8,
 * 1.0, 1.2}` we draw a red stroked outline that matches the blur
 * filter's `±ceil(3·σ)` expansion bound and a black filled rectangle
 * underneath. The black box should remain centred inside the red
 * outline for every sigma — the bug this GM guards against is the
 * filter shifting the output by half-a-pixel when the sigma is so
 * small that the gauss-kernel collapses to a no-op.
 *
 * **Registered GM name differs from the source filename** : upstream
 * registers via `DEF_SIMPLE_GM(check_small_sigma_offset, ...)`, so
 * the reference PNG is `check_small_sigma_offset.png` (and our
 * [getName] must match for the test harness to resolve it).
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(check_small_sigma_offset, canvas, 200, 1200) {
 *     for (auto sigma : {0.0, 0.1, 0.2, 0.3, 0.4, 0.6, 0.8, 1.0, 1.2}) {
 *         int border = SkScalarCeilToInt(sigma * 3);
 *         SkRect r = SkRect::MakeXYWH(50, 50, 100, 50);
 *         SkRect b = r.makeOutset(border + 1, border + 1);
 *         b.inset(0.5f, 0.5f);
 *         SkPaint p;
 *         p.setColor(SK_ColorRED);
 *         p.setStyle(SkPaint::Style::kStroke_Style);
 *         canvas->drawRect(b, p);
 *
 *         p.reset();
 *         p.setColor(SK_ColorBLACK);
 *         p.setImageFilter(SkImageFilters::Blur(sigma, sigma, nullptr));
 *         canvas->drawRect(r, p);
 *
 *         canvas->translate(0, 100);
 *     }
 * }
 * ```
 */
public class BlurPositioningGM : GM() {
    override fun getName(): String = "check_small_sigma_offset"
    override fun getISize(): SkISize = SkISize.Make(200, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val sigmas = floatArrayOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 0.8f, 1.0f, 1.2f)
        for (sigma in sigmas) {
            // Border calculation from SkBlurImageFilter — output grows by
            // ±ceil(3·σ) per axis. The red outline lands exactly one
            // pixel outside the blur's expanded bounding box.
            val border = SkScalarCeilToInt(sigma * 3f)

            val r = SkRect.MakeXYWH(50f, 50f, 100f, 50f)
            val b = r.makeOutset((border + 1).toFloat(), (border + 1).toFloat())
            b.inset(0.5f, 0.5f)

            val p = SkPaint()
            p.color = SK_ColorRED
            p.style = SkPaint.Style.kStroke_Style
            c.drawRect(b, p)

            p.reset()
            p.color = SK_ColorBLACK
            p.imageFilter = SkImageFilters.Blur(sigma, sigma, null)
            c.drawRect(r, p)

            c.translate(0f, 100f)
        }
    }
}
