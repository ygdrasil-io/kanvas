package org.skia.codec.bmp

import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * BMP (Windows Bitmap) decoder — D3.3 implementation of the
 * [SkCodec] facade. Mirrors Skia's `SkBmpCodec` ; signature is
 * the 2-byte ASCII `"BM"` at offset 0.
 *
 * BMP supports 1 / 4 / 8 / 16 / 24 / 32 bits per pixel. ImageIO's
 * BMP plugin handles every common variant and surfaces alpha through
 * `getRGB` for the 32-bpp BGRA case ; the codec emits
 * [SkColorType.kRGBA_8888] / [SkAlphaType.kUnpremul] regardless of
 * the source depth (the upper bits of the input are quantised down
 * to 8 bits per channel by ImageIO before we read them).
 *
 * **No ICC profile handling.** `BITMAPV5HEADER` files can embed an
 * ICC profile in the trailing bytes of the file, but the variant is
 * rare in practice and not exposed by ImageIO ; we tag every bitmap
 * with sRGB for parity with the GIF and WBMP paths. A proper BMP
 * profile reader is a follow-up if a GM ever needs it.
 *
 * Skia upstream's BMP codec also speaks the `"IC"`, `"PT"`, `"CI"`,
 * `"CP"`, `"BA"` magic variants (non-standard Windows / OS/2
 * derivatives) ; the upstream spec lists them as TODO and they are
 * not produced by any DM reference, so we accept only `"BM"` for
 * D3.3.
 */
public class SkBmpCodec internal constructor(
    private val image: BufferedImage,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = image.width,
            height = image.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kBMP

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidConversion
        }
        image.getRGB(0, 0, image.width, image.height, dst.pixels, 0, image.width)
        return Result.kSuccess
    }

    internal companion object Decoder : SkCodec.Decoder {

        override val name: String = "bmp"

        /** ASCII `"BM"` — the only Windows BMP variant we accept. */
        private val SIGNATURE = byteArrayOf(0x42, 0x4D)

        override fun matches(data: ByteArray): Boolean {
            if (data.size < SIGNATURE.size) return false
            for (i in SIGNATURE.indices) {
                if (data[i] != SIGNATURE[i]) return false
            }
            return true
        }

        override fun make(data: ByteArray): SkCodec? {
            val image = try {
                ImageIO.read(ByteArrayInputStream(data))
            } catch (_: Throwable) {
                null
            } ?: return null
            return SkBmpCodec(image)
        }
    }
}
