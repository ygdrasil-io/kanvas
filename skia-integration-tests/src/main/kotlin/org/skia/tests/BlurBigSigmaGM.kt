package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM(BlurBigSigma, …, 1024, 1024)`.
 *
 * Draws a large (700 × 800) rect with an image-filter blur of σ = 500 on both
 * axes. The extreme sigma value exercises the "big sigma" blur path which may
 * use a different internally tiled implementation to avoid stack overflows or
 * other resource limits.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(BlurBigSigma, canvas, 1024, 1024) {
 *     SkPaint layerPaint, p;
 *     p.setImageFilter(SkImageFilters::Blur(500, 500, nullptr));
 *     canvas->drawRect(SkRect::MakeWH(700, 800), p);
 * }
 * ```
 */
public class BlurBigSigmaGM : GM() {

    override fun getName(): String = "BlurBigSigma"
    override fun getISize(): SkISize = SkISize.Make(1024, 1024)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            imageFilter = SkImageFilters.Blur(500f, 500f, null)
        }
        c.drawRect(SkRect.MakeWH(700f, 800f), p)
    }
}
