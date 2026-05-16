package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM(BlurDrawImage, …, 256, 256)`.
 *
 * Clears the canvas to light-green, then scales to 0.25× and draws
 * `mandrill_512_q075.jpg` at `(256, 256)` (so post-scale it lands at
 * `(64, 64)` and renders at 128 × 128 px) with a normal-style mask
 * blur of sigma = 10. Exercises the mask-blur path on a JPG-decoded
 * opaque image — same code path as [BlurImageGM] but with non-square
 * coordinates and a scaled CTM.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(BlurDrawImage, canvas, 256, 256) {
 *     SkPaint paint;
 *     paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 10));
 *     canvas->clear(0xFF88FF88);
 *     if (auto image = ToolUtils::GetResourceAsImage("images/mandrill_512_q075.jpg")) {
 *         canvas->scale(0.25, 0.25);
 *         canvas->drawImage(image, 256, 256, SkSamplingOptions(), &paint);
 *     }
 * }
 * ```
 */
public class BlurDrawImageGM : GM() {
    override fun getName(): String = "BlurDrawImage"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 10f)
        }
        c.clear(0xFF88FF88.toInt())
        val image = ToolUtils.GetResourceAsImage("images/mandrill_512_q075.jpg") ?: return
        c.scale(0.25f, 0.25f)
        c.drawImage(image, 256f, 256f, SkSamplingOptions.Default, paint)
    }
}
