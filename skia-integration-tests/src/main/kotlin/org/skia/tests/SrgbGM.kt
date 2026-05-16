package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/srgb.cpp::srgb_colorfilter`
 * (`DEF_SIMPLE_GM(srgb_colorfilter, canvas, 512, 256*3)`).
 *
 * The GM lays out a 2 × 3 grid of `mandrill_256.png` images :
 *  - Row 0 : reference image, then a "negate red" matrix filter.
 *  - Row 1 : `LinearToSRGBGamma` alone, then composed with the matrix.
 *  - Row 2 : `SRGBToLinearGamma` alone, then composed with the matrix.
 *
 * Exercises [SkColorFilters.LinearToSRGBGamma] /
 * [SkColorFilters.SRGBToLinearGamma] (R-final.3) and the existing
 * [SkColorFilter.makeComposed] entry point. Mandrill_256.png is the
 * standard reference image shared with the upstream PNG-comparison
 * harness.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(srgb_colorfilter, canvas, 512, 256*3) {
 *     auto img = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *
 *     const float array[] = {
 *         1, 0, 0, 0, 0,
 *         0, 1, 0, 0, 0,
 *         0, 0, 1, 0, 0,
 *         -1, 0, 0, 1, 0,
 *     };
 *     auto cf0 = SkColorFilters::Matrix(array);
 *     auto cf1 = SkColorFilters::LinearToSRGBGamma();
 *     auto cf2 = SkColorFilters::SRGBToLinearGamma();
 *
 *     SkSamplingOptions sampling;
 *     SkPaint p;
 *     p.setColorFilter(cf0);
 *     canvas->drawImage(img, 0, 0);
 *     canvas->drawImage(img, 256, 0, sampling, &p);
 *
 *     p.setColorFilter(cf1);
 *     canvas->drawImage(img, 0, 256, sampling, &p);
 *     p.setColorFilter(cf1->makeComposed(cf0));
 *     canvas->drawImage(img, 256, 256, sampling, &p);
 *
 *     p.setColorFilter(cf2);
 *     canvas->drawImage(img, 0, 512, sampling, &p);
 *     p.setColorFilter(cf2->makeComposed(cf0));
 *     canvas->drawImage(img, 256, 512, sampling, &p);
 * }
 * ```
 */
public class SrgbGM : GM() {

    override fun getName(): String = "srgb_colorfilter"
    override fun getISize(): SkISize = SkISize.Make(512, 256 * 3)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return

        // 4 × 5 affine colour matrix that subtracts the input alpha
        // from each red sample (and leaves the alpha row intact).
        // Encoded row-major.
        val array = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 1f, 0f,
        )
        val cf0 = SkColorFilters.Matrix(array)
        val cf1 = SkColorFilters.LinearToSRGBGamma()
        val cf2 = SkColorFilters.SRGBToLinearGamma()

        val sampling = SkSamplingOptions.Default
        val p = SkPaint()

        // Row 0 — reference + negate-red matrix.
        p.colorFilter = cf0
        c.drawImage(img, 0f, 0f)
        c.drawImage(img, 256f, 0f, sampling, p)

        // Row 1 — sRGB encoding (treats input as linear) alone, and
        // composed with the negate-red matrix.
        p.colorFilter = cf1
        c.drawImage(img, 0f, 256f, sampling, p)
        p.colorFilter = cf1.makeComposed(cf0)
        c.drawImage(img, 256f, 256f, sampling, p)

        // Row 2 — sRGB decoding (treats input as encoded), alone and
        // composed.
        p.colorFilter = cf2
        c.drawImage(img, 0f, 512f, sampling, p)
        p.colorFilter = cf2.makeComposed(cf0)
        c.drawImage(img, 256f, 512f, sampling, p)
    }
}
