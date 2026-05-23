package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefiltersclipped.cpp::imagefilter_convolve_subset`
 * (`DEF_SIMPLE_GM`, name `"imagefilter_convolve_subset"`, 160 × 180).
 *
 * Applies two image filters to `images/filter_reference.png` via
 * [SkCanvas.drawImage] with paint-time filters, stacking them vertically
 * (each row is the image's height tall) :
 *
 *  1. A 3 × 3 sharpening kernel [MatrixConvolution] — kernel
 *     `{1, 1, 1, 1, -7, 1, 1, 1, 1}`, gain = 1, bias = 0.3,
 *     offset = (1, 1), [SkTileMode.kClamp], `convolveAlpha = true` —
 *     with output cropped to `crop` (the image rect inset by 10 px).
 *  2. A [SkImageFilters.Blur] with σ = 10, [SkTileMode.kMirror], output
 *     cropped to the same `crop` rect.
 *
 * The crop rect is applied by wrapping each filter with
 * [SkImageFilters.Crop] (kDecal) — equivalent to upstream's trailing
 * `cropRect` parameter which the header documents as "semantically
 * equivalent to wrapping in `::Crop(rect, kDecal)`".
 *
 * If `images/filter_reference.png` is absent from the classpath, [onDraw]
 * returns silently (the test is `@Disabled`).
 *
 * C++ original — `gm/imagefiltersclipped.cpp` (DEF_SIMPLE_GM section).
 */
public class ImageFilterConvolveSubsetGM : GM() {

    override fun getName(): String = "imagefilter_convolve_subset"
    override fun getISize(): SkISize = SkISize.Make(160, 180)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val reference = ToolUtils.GetResourceAsImage("images/filter_reference.png") ?: return
        val refW = reference.width.toFloat()
        val refH = reference.height.toFloat()

        // SkRect crop = SkRect::Make(reference->dimensions()).makeInset(10, 10)
        val crop = SkRect.MakeIWH(reference.width, reference.height).makeInset(10f, 10f)

        // Closure-style helper: draw filtered image at current canvas position,
        // then translate down by reference.height().
        fun drawFilteredImage(filter: org.skia.foundation.SkImageFilter) {
            val paint = SkPaint().apply { imageFilter = filter }
            c.drawImage(reference, 0f, 0f, SkSamplingOptions.Default, paint)
            c.translate(0f, refH)
        }

        // Filter 1: 3×3 sharpening MatrixConvolution with kClamp + crop.
        val kernel = floatArrayOf(
            1f,  1f, 1f,
            1f, -7f, 1f,
            1f,  1f, 1f,
        )
        val convFilter = SkImageFilters.MatrixConvolution(
            SkISize(3, 3),
            kernel,
            1f,
            0.3f,
            SkIPoint(1, 1),
            SkTileMode.kClamp,
            true,
            null,
            crop,
        )
        drawFilteredImage(convFilter)

        // Filter 2: Blur σ=10 with kMirror + crop (via explicit Crop wrapper,
        // since Blur.cropRect uses kDecal only; we wrap manually for semantic
        // correctness — equivalent to upstream's 5-arg Blur(..., cropRect)).
        val blurRaw = SkImageFilters.Blur(10f, 10f, SkTileMode.kMirror, null)
        val blurFilter = if (blurRaw != null) SkImageFilters.Crop(crop, blurRaw) else null
        if (blurFilter != null) drawFilteredImage(blurFilter)
    }
}
