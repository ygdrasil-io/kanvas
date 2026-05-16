package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/imagefiltersunpremul.cpp`
 * (`DEF_SIMPLE_GM_BG(imagefiltersunpremul, canvas, 64, 64, SK_ColorBLACK)`).
 *
 * Renders a 64×64 unpremultiplied red-50/255 bitmap through
 * `SkImageFilters::Image` on a plain `drawPaint`. Verifies that the
 * alpha channel is correctly blended onto the black background.
 *
 * Phase G7 wires `drawPaint` to route through a `saveLayer + drawPaint +
 * restore` shim when `paint.imageFilter` is set, which is what makes
 * this GM render at all in `:kanvas-skia` (previously the image filter
 * was silently ignored, producing a fully-black canvas).
 *
 * Upstream uses `SkCubicResampler::Mitchell()` sampling — `:kanvas-skia`
 * does not implement bicubic resampling yet, so we fall back to the
 * default sampling (`kNearest`). Since `dstRect` == `srcRect` ==
 * `(0, 0, 64, 64)` and no scaling occurs, sampling mode is moot: every
 * destination pixel reads exactly one source pixel, so the result is
 * identical to Mitchell sampling at this size.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_BG(imagefiltersunpremul, canvas, 64, 64, SK_ColorBLACK) {
 *     SkBitmap bitmap;
 *     bitmap.allocPixels(SkImageInfo::Make(64, 64, kRGBA_8888_SkColorType,
 *                                          kUnpremul_SkAlphaType));
 *     bitmap.eraseColor(SkColorSetARGB(50, 255, 0, 0));
 *     SkPaint paint;
 *     paint.setImageFilter(SkImageFilters::Image(SkImages::RasterFromBitmap(bitmap),
 *                                                SkCubicResampler::Mitchell()));
 *     canvas->drawPaint(paint);
 * }
 * ```
 */
public class ImageFiltersUnpremulGM : GM() {
    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "imagefiltersunpremul"
    override fun getISize(): SkISize = SkISize.Make(64, 64)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bitmap = SkBitmap(64, 64).also {
            it.eraseColor(SkColorSetARGB(50, 255, 0, 0))
        }
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.Image(bitmap.asImage())
        }
        c.drawPaint(paint)
    }
}
