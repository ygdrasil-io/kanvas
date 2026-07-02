package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `mipmap_srgb` GM (260 × 230).
 *
 * Draws an `(x ^ y) & 1` checker-board image (linear and sRGB-tagged)
 * at progressively halved sizes through `kLinear / kLinear` mipmap
 * sampling.
 */
public class MipmapSrgbGM : GM() {

    override fun getName(): String = "mipmap_srgb"
    override fun getISize(): SkISize = SkISize.Make(260, 230)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val limg = makeChecker(null)
        val simg = makeChecker(SkColorSpace.MakeSRGB())

        c.translate(10f, 10f)
        showMips(c, limg)
        c.translate(0f, limg.height + 10f)
        showMips(c, simg)
    }

    private fun makeChecker(cs: SkColorSpace?): SkImage {
        val n = 100
        val info = SkImageInfo.Make(
            n, n, SkColorType.kRGBA_8888,
            org.skia.foundation.SkAlphaType.kPremul,
            cs ?: SkColorSpace.MakeSRGBLinear(),
        )
        val bm = SkBitmap.allocPixels(info)
        for (y in 0 until n) {
            for (x in 0 until n) {
                bm.pixels8888[y * n + x] = if (((x xor y) and 1) != 0) SK_ColorWHITE else SK_ColorBLACK
            }
        }
        return bm.asImage()
    }

    private fun showMips(canvas: SkCanvas, img: SkImage) {
        val sampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear)
        var dst = SkIRect.MakeWH(img.width, img.height)
        while (dst.width() > 5) {
            canvas.drawImageRect(
                img,
                SkRect.MakeWH(img.width.toFloat(), img.height.toFloat()),
                SkRect.MakeLTRB(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat()),
                sampling,
                null,
            )
            dst = SkIRect.MakeXYWH(dst.left + dst.width() + 10, dst.top, dst.width() / 2, dst.height() / 2)
        }
    }
}

/**
 * Port of Skia's `mipmap_gray8_srgb` GM (260 × 230).
 *
 * Same as [MipmapSrgbGM] but with an 8-bit gray gradient image (linear
 * and sRGB-tagged), rendered through `kLinear / kLinear` mipmap
 * sampling.
 */
public class MipmapGray8SrgbGM : GM() {

    override fun getName(): String = "mipmap_gray8_srgb"
    override fun getISize(): SkISize = SkISize.Make(260, 230)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val limg = makeGradient(null)
        val simg = makeGradient(SkColorSpace.MakeSRGB())

        c.translate(10f, 10f)
        showMipsOnly(c, limg)
        c.translate(0f, limg.height + 10f)
        showMipsOnly(c, simg)
    }

    private fun makeGradient(cs: SkColorSpace?): SkImage {
        val n = 100
        val info = SkImageInfo.Make(
            n, n, SkColorType.kGray_8,
            org.skia.foundation.SkAlphaType.kOpaque,
            cs ?: SkColorSpace.MakeSRGBLinear(),
        )
        val bm = SkBitmap.allocPixels(info)
        for (y in 0 until n) {
            for (x in 0 until n) {
                bm.pixelsGray8[y * n + x] =
                    (255f * ((x + y) / (2f * (n - 1)))).toInt().coerceIn(0, 255).toByte()
            }
        }
        return bm.asImage()
    }

    private fun showMipsOnly(canvas: SkCanvas, img: SkImage) {
        val sampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear)
        var dst = SkIRect.MakeWH(img.width / 2, img.height / 2)
        while (dst.width() > 5) {
            canvas.drawImageRect(
                img,
                SkRect.MakeWH(img.width.toFloat(), img.height.toFloat()),
                SkRect.MakeLTRB(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat()),
                sampling,
                null,
            )
            dst = SkIRect.MakeXYWH(dst.left + dst.width() + 10, dst.top, dst.width() / 2, dst.height() / 2)
        }
    }
}
