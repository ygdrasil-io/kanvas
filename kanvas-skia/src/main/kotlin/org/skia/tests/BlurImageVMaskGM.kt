package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/blurimagevmask.cpp::DEF_SIMPLE_GM(blurimagevmask, …)`
 * (700 × 1200).
 *
 * Side-by-side comparison of `SkMaskFilter::MakeBlur` (column 2) vs
 * `SkImageFilters::Blur` (column 3) at five sigmas — `3, 8, 16, 24, 32`.
 * Column 1 is the unblurred reference rectangle.
 *
 * The image-blur path uses a `saveLayer` with the paint carrying the
 * image filter — every primitive drawn into the layer is blurred at
 * layer-restore time. This is the canonical "blur a rect via image
 * filter" idiom.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(blurimagevmask, canvas, 700, 1200) {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(SK_ColorBLACK);
 *
 *     SkFont font(ToolUtils::DefaultPortableTypeface(), 25);
 *
 *     const double sigmas[] = {3.0, 8.0, 16.0, 24.0, 32.0};
 *
 *     canvas->drawString("mask blur",  285, 50, font, paint);
 *     canvas->drawString("image blur", 285 + 250, 50, font, paint);
 *
 *
 *     SkRect r = {35, 100, 135, 200};
 *     for (auto sigma:sigmas) {
 *
 *         canvas->drawRect(r, paint);
 *
 *         char out[100];
 *         snprintf(out, std::size(out), "Sigma: %g", sigma);
 *         canvas->drawString(out, r.left(), r.bottom() + 35, font, paint);
 *
 *         r.offset(250, 0);
 *
 *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma));
 *         canvas->drawRect(r, paint);
 *         paint.setMaskFilter(nullptr);
 *
 *         SkPaint imageBlurPaint;
 *         r.offset(250, 0);
 *         imageBlurPaint.setImageFilter(SkImageFilters::Blur(sigma, sigma, nullptr));
 *         canvas->saveLayer(nullptr, &imageBlurPaint);
 *
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *         r.offset(-500, 200);
 *     }
 * }
 * ```
 *
 * Only the first GM in `blurimagevmask.cpp` is ported — the second
 * (`blur_image`, image-with-mask-filter sprite path) needs the
 * `mandrill_128.png` image sprite and is a separate canary for the
 * sprite-blitter bugfix, out of scope for this batch.
 */
public class BlurImageVMaskGM : GM() {

    override fun getName(): String = "blurimagevmask"
    override fun getISize(): SkISize = SkISize.Make(700, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLACK
        }

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 25f)

        // Upstream stores these as double; we pass them through as Float
        // at the SkImageFilters / SkMaskFilter boundary anyway.
        val sigmas = floatArrayOf(3f, 8f, 16f, 24f, 32f)

        c.drawString("mask blur", 285f, 50f, font, paint)
        c.drawString("image blur", 285f + 250f, 50f, font, paint)

        val r = SkRect.MakeLTRB(35f, 100f, 135f, 200f)
        for (sigma in sigmas) {

            c.drawRect(r, paint)

            // Upstream: `"Sigma: %g"` with double sigma — `%g` strips
            // trailing zeros (`3` not `3.0`). Kotlin's `Float.toInt()`
            // for integral sigmas matches that behaviour.
            val out = "Sigma: ${if (sigma == sigma.toInt().toFloat()) sigma.toInt().toString() else sigma.toString()}"
            c.drawString(out, r.left(), r.bottom() + 35f, font, paint)

            r.offset(250f, 0f)

            paint.maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
            c.drawRect(r, paint)
            paint.maskFilter = null

            val imageBlurPaint = SkPaint().apply {
                imageFilter = SkImageFilters.Blur(sigma, sigma, null)
            }
            r.offset(250f, 0f)
            c.saveLayer(null, imageBlurPaint)

            c.drawRect(r, paint)
            c.restore()
            r.offset(-500f, 200f)
        }
    }
}
