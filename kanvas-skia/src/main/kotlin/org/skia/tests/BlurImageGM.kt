package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/blurimagevmask.cpp::DEF_SIMPLE_GM_CAN_FAIL(blur_image, …, 500, 500)`.
 *
 * Both [drawImage] calls should render with the same normal-style mask
 * blur (sigma = 4). Pre-fix the unscaled draw took the sprite-blitter
 * code path and silently dropped the mask filter — the regression hunt
 * lives in commit 8c5e2d8.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_CAN_FAIL(blur_image, canvas, errorMsg, 500, 500) {
 *     auto image = ToolUtils::GetResourceAsImage("images/mandrill_128.png");
 *     if (!image) {
 *         *errorMsg = "Could not load mandrill_128.png. Did you forget to set the resourcePath?";
 *         return skiagm::DrawResult::kFail;
 *     }
 *
 *     SkPaint paint;
 *     paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 4));
 *
 *     // both of these should draw with the blur, but (formerally) we had a bug where the unscaled
 *     // version (taking the spriteblitter code path) ignore the maskfilter.
 *
 *     canvas->drawImage(image, 10, 10, SkSamplingOptions(), &paint);
 *     canvas->scale(1.01f, 1.01f);
 *     canvas->drawImage(image, 10 + image->width() + 10.f, 10, SkSamplingOptions(), &paint);
 *     return skiagm::DrawResult::kOk;
 * }
 * ```
 */
public class BlurImageGM : GM() {
    override fun getName(): String = "blur_image"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return

        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 4f)
        }

        c.drawImage(image, 10f, 10f, SkSamplingOptions.Default, paint)
        c.scale(1.01f, 1.01f)
        c.drawImage(image, 10f + image.width + 10f, 10f, SkSamplingOptions.Default, paint)
    }
}
