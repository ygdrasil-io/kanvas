package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShaders
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/composeshader.cpp::composeshader_bitmap2` (`DEF_SIMPLE_GM`).
 *
 * Draws a 255×255 blue rectangle, then overlays a `Blend(SrcIn, maskShader, srcShader)`
 * where :
 *  - `srcShader` is a 255×255 N32 bitmap where pixel `(x, y)` is
 *    `SkPackARGB32(0xFF, x, y, 0)` — an RGB gradient that sweeps green
 *    along Y, red along X, and has zero blue.
 *  - `maskShader` is a 255×255 Alpha8 bitmap where pixel `(x, y)` is
 *    `(y + x) / 2` — a diagonal alpha ramp.
 *
 * The `SrcIn` blend `src * da` cuts the colour bitmap through the alpha mask,
 * producing a colour gradient whose opacity ramps diagonally from the top-left
 * (transparent) to the bottom-right (opaque). The blue background bleeds
 * through wherever the mask is semi-transparent.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(composeshader_bitmap2, canvas, 200, 200) {
 *     int width = 255, height = 255;
 *     SkTDArray<uint8_t>  dst8Storage;  dst8Storage.resize(width * height);
 *     SkTDArray<uint32_t> dst32Storage; dst32Storage.resize(width * height * sizeof(int32_t));
 *     for (int y = 0; y < height; ++y)
 *         for (int x = 0; x < width; ++x) {
 *             dst8Storage[y * width + x]  = (y + x) / 2;
 *             dst32Storage[y * width + x] = SkPackARGB32(0xFF, x, y, 0);
 *         }
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(SK_ColorBLUE);
 *     SkRect r = {0, 0, SkIntToScalar(width), SkIntToScalar(height)};
 *     canvas->drawRect(r, paint);
 *     SkBitmap skBitmap, skMask;
 *     SkImageInfo imageInfo = SkImageInfo::Make(width, height,
 *             SkColorType::kN32_SkColorType, kPremul_SkAlphaType);
 *     skBitmap.installPixels(imageInfo, dst32Storage.begin(), width * sizeof(int32_t), …);
 *     imageInfo = SkImageInfo::Make(width, height,
 *             SkColorType::kAlpha_8_SkColorType, kPremul_SkAlphaType);
 *     skMask.installPixels(imageInfo, dst8Storage.begin(), width, …);
 *     sk_sp<SkImage> skSrc      = skBitmap.asImage();
 *     sk_sp<SkImage> skMaskImage = skMask.asImage();
 *     paint.setShader(
 *         SkShaders::Blend(SkBlendMode::kSrcIn,
 *                          skMaskImage->makeShader(SkSamplingOptions()),
 *                          skSrc->makeShader(SkSamplingOptions())));
 *     canvas->drawRect(r, paint);
 * }
 * ```
 */
public class ComposeShaderBitmap2GM : GM() {

    override fun getName(): String = "composeshader_bitmap2"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val width = 255
        val height = 255

        // Build the N32 colour bitmap: pixel(x, y) = argb(0xFF, x, y, 0).
        // SkPackARGB32(0xFF, x, y, 0) == SkColorSetARGB(0xFF, x, y, 0).
        val skBitmap = SkBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                skBitmap.setPixel(x, y, SkColorSetARGB(0xFF, x, y, 0))
            }
        }

        // Build the Alpha8 mask bitmap: pixel(x, y) = (y + x) / 2.
        val skMask = SkBitmap.allocPixels(SkImageInfo.MakeA8(width, height))
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (y + x) / 2
                // Alpha8 stores opacity in alpha channel; setPixel takes an ARGB int
                // where only the alpha byte is meaningful for kAlpha_8 bitmaps.
                skMask.setPixel(x, y, SkColorSetARGB(alpha, 0, 0, 0))
            }
        }

        val skSrc = skBitmap.asImage()
        val skMaskImage = skMask.asImage()

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLUE
        }
        val r = SkRect.MakeWH(width.toFloat(), height.toFloat())
        c.drawRect(r, paint)

        paint.shader = SkShaders.Blend(
            SkBlendMode.kSrcIn,
            skMaskImage.makeShader(SkSamplingOptions.Default),
            skSrc.makeShader(SkSamplingOptions.Default),
        )
        c.drawRect(r, paint)
    }
}
