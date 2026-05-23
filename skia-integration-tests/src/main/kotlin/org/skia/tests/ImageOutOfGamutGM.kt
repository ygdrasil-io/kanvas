package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGRAY
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkAlphaType
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/bitmappremul.cpp` `DEF_SIMPLE_GM(image_out_of_gamut, …)`.
 *
 * Draws two 31 × 31 images whose pixels have **out-of-gamut** colours —
 * i.e. the per-channel RGB values exceed the alpha (premul RGB > A),
 * which historically triggered asserts or undefined behaviour in Skia's
 * legacy blitters. The GM exercises both `kRGBA_8888` and `kBGRA_8888`
 * colour types to hit the `N32 → N32` blit path in both channel orders.
 *
 * Canvas: `2 * kBoxSize + 3 * kPadding` × `kBoxSize + 2 * kPadding`
 *       = `77` × `41`, background grey.
 *
 * Pixel encoding (upstream C++ verbatim) :
 * ```cpp
 * *bmp.getAddr32(x, y) = (0x40000000 | ((x * 8) << 8) | ((y * 8) << 0));
 * ```
 * The raw `uint32_t` is channel-decoded per the colour type's memory order.
 * For `kRGBA_8888` (bytes `[R,G,B,A]` LE): R=y*8, G=x*8, B=0, A=0x40.
 * For `kBGRA_8888` (bytes `[B,G,R,A]` LE): B=y*8, G=x*8, R=0, A=0x40.
 * In both cases the non-zero channel(s) (up to 240) far exceed alpha=0x40=64.
 *
 * The odd dimension (31) is intentional: upstream comment says
 * "Odd dimensions so that we hit the different implementation in the
 * SIMD tail handling".
 *
 * Upstream C++ (abbreviated):
 * ```cpp
 * static constexpr int kBoxSize  = 31;
 * static constexpr int kPadding  = 5;
 *
 * static sk_sp<SkImage> make_out_of_gamut_image(SkColorType ct) {
 *     SkBitmap bmp;
 *     bmp.allocPixels(SkImageInfo::Make(kBoxSize, kBoxSize, ct, kPremul_SkAlphaType));
 *     for (int y = 0; y < kBoxSize; ++y)
 *         for (int x = 0; x < kBoxSize; ++x)
 *             *bmp.getAddr32(x, y) = (0x40000000 | ((x * 8) << 8) | ((y * 8) << 0));
 *     return bmp.asImage();
 * }
 *
 * DEF_SIMPLE_GM(image_out_of_gamut, canvas, 2 * kBoxSize + 3 * kPadding,
 *                                           kBoxSize + 2 * kPadding) {
 *     canvas->clear(SK_ColorGRAY);
 *     auto rgba = make_out_of_gamut_image(kRGBA_8888_SkColorType),
 *          bgra = make_out_of_gamut_image(kBGRA_8888_SkColorType);
 *     canvas->translate(kPadding, kPadding);
 *     canvas->drawImage(rgba, 0, 0);
 *     canvas->translate(kBoxSize + kPadding, 0);
 *     canvas->drawImage(bgra, 0, 0);
 * }
 * ```
 */
public class ImageOutOfGamutGM : GM() {

    override fun getName(): String = "image_out_of_gamut"

    override fun getISize(): SkISize =
        SkISize.Make(2 * kBoxSize + 3 * kPadding, kBoxSize + 2 * kPadding)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorGRAY)

        val rgba = makeOutOfGamutImage(SkColorType.kRGBA_8888)
        val bgra = makeOutOfGamutImage(SkColorType.kBGRA_8888)

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.drawImage(rgba, 0f, 0f)
        c.translate((kBoxSize + kPadding).toFloat(), 0f)
        c.drawImage(bgra, 0f, 0f)
    }

    private companion object {
        const val kBoxSize: Int = 31
        const val kPadding: Int = 5

        /**
         * Build a [kBoxSize] × [kBoxSize] bitmap of the given [colorType]
         * filled with out-of-gamut premul pixels where the colour channels
         * exceed the alpha value (= 0x40 = 64).
         *
         * The C++ writes the raw `uint32_t` value
         * `0x40000000 | ((x * 8) << 8) | (y * 8)` directly to memory.
         * Decoding that raw value into [SkColor] (ARGB) depends on the
         * colour type's channel ordering in memory:
         *
         *  - **`kRGBA_8888`** : memory layout is `[R, G, B, A]` from
         *    byte-0 (LE), so `R = y*8`, `G = x*8`, `B = 0`, `A = 0x40`.
         *    SkColor = `SkColorSetARGB(0x40, y*8, x*8, 0)`.
         *
         *  - **`kBGRA_8888`** : memory layout is `[B, G, R, A]` from
         *    byte-0 (LE), so `B = y*8`, `G = x*8`, `R = 0`, `A = 0x40`.
         *    SkColor = `SkColorSetARGB(0x40, 0, x*8, y*8)`.
         *
         * Both are written through [SkBitmap.setPixel] using the [SkColor]
         * (ARGB) convention per the kanvas-skia colour-type contract.
         */
        private fun makeOutOfGamutImage(colorType: SkColorType): SkImage {
            val bmp = SkBitmap.allocPixels(
                SkImageInfo.Make(kBoxSize, kBoxSize, colorType, SkAlphaType.kPremul),
            )
            for (y in 0 until kBoxSize) {
                for (x in 0 until kBoxSize) {
                    // Raw C++ uint32: 0x40000000 | ((x * 8) << 8) | (y * 8)
                    // kRGBA_8888 memory [R,G,B,A]: R=y*8, G=x*8, B=0, A=0x40
                    // kBGRA_8888 memory [B,G,R,A]: B=y*8, G=x*8, R=0, A=0x40
                    val color = when (colorType) {
                        SkColorType.kRGBA_8888 -> SkColorSetARGB(0x40, y * 8, x * 8, 0)
                        SkColorType.kBGRA_8888 -> SkColorSetARGB(0x40, 0, x * 8, y * 8)
                        else -> SkColorSetARGB(0x40, 0, x * 8, y * 8)
                    }
                    bmp.setPixel(x, y, color)
                }
            }
            return bmp.asImage()
        }
    }
}
