package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.tools.ToolUtils

/**
 * Port of the `ImageFilterMatrixWLocalMatrix` GM defined in
 * [`gm/imagefilterstransformed.cpp`](https://github.com/google/skia/blob/main/gm/imagefilterstransformed.cpp)
 * (registered name : `imagefilter_matrix_localmatrix`).
 *
 * Validates that
 * [`SkImageFilter.makeWithLocalMatrix`][org.skia.foundation.SkImageFilter.makeWithLocalMatrix]
 * (R-final.2) correctly composes a `MatrixTransform(rotate)` filter
 * with a local matrix that pre-translates and pre-scales the input.
 *
 * Static rendering at upstream's start angle (132°) — the upstream GM
 * animates `fDegrees` from 132 onwards ; in a one-shot raster context
 * we lock the value to 132 to match the one-frame `original-888/`
 * reference. Skia's GMs sample at the GM's static `onDraw` state for
 * reference snapshots, so this 132° lock matches the canonical PNG.
 *
 * The filter chain :
 *  - rotate `fDegrees` around `(64, 64)` (the half-resolution centre
 *    of the post-localMatrix-scale image),
 *  - wrap with a local matrix that translates `(128, 128)` then
 *    scales `2×` — simulating a 2× hi-DPI device.
 *
 * Output : a single 256×256 mandrill image rendered at `(128, 128)`
 * with the composed filter applied.
 *
 * C++ original (collapsed) :
 * ```cpp
 * class ImageFilterMatrixWLocalMatrix : public skiagm::GM {
 *     ImageFilterMatrixWLocalMatrix() : fDegrees(132.f) {}
 *
 *     SkString getName() const override { return "imagefilter_matrix_localmatrix"; }
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkMatrix localMatrix;
 *         localMatrix.preTranslate(128, 128);
 *         localMatrix.preScale(2.0f, 2.0f);
 *
 *         SkMatrix filterMatrix;
 *         filterMatrix.setRotate(fDegrees, 64, 64);
 *
 *         sk_sp<SkImageFilter> filter = SkImageFilters::MatrixTransform(
 *                 filterMatrix, SkSamplingOptions(SkFilterMode::kLinear), nullptr)
 *               ->makeWithLocalMatrix(localMatrix);
 *
 *         SkPaint p;
 *         p.setImageFilter(filter);
 *         canvas->drawImage(fImage.get(), 128, 128, SkSamplingOptions(), &p);
 *     }
 * };
 * ```
 */
public class ImageFiltersTransformedGM : GM() {

    private var fImage: SkImage? = null
    private val fDegrees: Float = 132f

    override fun getName(): String = "imagefilter_matrix_localmatrix"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onOnceBeforeDraw() {
        fImage = ToolUtils.GetResourceAsImage("images/mandrill_256.png")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = fImage ?: return

        // localMatrix = Identity · Translate(128, 128) · Scale(2, 2)
        val localMatrix = SkMatrix.MakeTrans(128f, 128f).preScale(2f, 2f)
        // filterMatrix : rotate fDegrees around (64, 64).
        val filterMatrix = SkMatrix.MakeRotate(fDegrees, 64f, 64f)

        val rotateFilter: SkImageFilter? = SkImageFilters.MatrixTransform(
            filterMatrix,
            SkSamplingOptions(SkFilterMode.kLinear),
            null,
        )
        val filter: SkImageFilter? = rotateFilter?.makeWithLocalMatrix(localMatrix)

        val paint = SkPaint().apply { imageFilter = filter }
        c.drawImage(image, 128f, 128f, paint = paint)
    }
}
