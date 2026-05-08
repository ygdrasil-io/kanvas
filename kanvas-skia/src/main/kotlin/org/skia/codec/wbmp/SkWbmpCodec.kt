package org.skia.codec.wbmp

import org.skia.codec.SkCodec
import org.skia.codec.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.skcms.SkcmsICCProfile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * WBMP (Wireless Bitmap) decoder — D3.3 implementation of the
 * [SkCodec] facade. Mirrors Skia's `SkWbmpCodec`.
 *
 * WBMP is a 1-bit black-and-white format used by old WAP browsers ;
 * it has no fixed magic number, so detection has to read the actual
 * header. Per the WAP-spec / [upstream port][upstream] the bytes are :
 *
 * ```
 * byte 0 : type field (only 0 = "B&W, no compression" is defined)
 * byte 1 : fixed header — bits 0..4 and bit 7 must be zero
 * bytes 2.. : multi-byte VLQ for width then height (each must be > 0
 *             and ≤ 65535)
 * ```
 *
 * Anything else is rejected. Because the magic is so loose, we
 * register WBMP **last** in [SkCodec.Decoders] — every other format
 * has a stronger signature and gets first refusal.
 *
 * The decoded raster is 1-bit B&W ; ImageIO surfaces it as a
 * `TYPE_BYTE_BINARY` [BufferedImage] whose `getRGB` returns
 * `0xFF000000` (black) or `0xFFFFFFFF` (white). The codec emits
 * [SkColorType.kRGBA_8888] / [SkAlphaType.kUnpremul] for parity with
 * the rest of the D3 family.
 *
 * [upstream]: https://github.com/google/skia/blob/main/src/codec/SkWbmpCodec.cpp
 */
public class SkWbmpCodec internal constructor(
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

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kWBMP

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

        override val name: String = "wbmp"

        override fun matches(data: ByteArray): Boolean {
            if (data.size < 4) return false
            if (data[0] != 0.toByte()) return false
            if ((data[1].toInt() and 0x9F) != 0) return false
            // Read VLQ width — must be > 0 and ≤ 0xFFFF.
            return readVlq(data, 2)?.let { (width, after) ->
                width in 1..0xFFFF && readVlq(data, after)?.let { (height, _) ->
                    height in 1..0xFFFF
                } ?: false
            } ?: false
        }

        /**
         * Read a multi-byte VLQ (ITU-T T.69 / WAP) starting at [start].
         * Returns `(value, bytesAfterEnd)` or `null` on truncation /
         * overflow ; treats more than 9 bytes as overflow (the fixed
         * 64-bit upper bound from upstream Skia's `read_mbf`).
         */
        private fun readVlq(data: ByteArray, start: Int): Pair<Long, Int>? {
            var n = 0L
            var i = start
            var consumed = 0
            while (i < data.size && consumed < 9) {
                val b = data[i].toInt() and 0xFF
                n = (n shl 7) or (b and 0x7F).toLong()
                i++
                consumed++
                if ((b and 0x80) == 0) return n to i
            }
            return null
        }

        override fun make(data: ByteArray): SkCodec? {
            val image = try {
                ImageIO.read(ByteArrayInputStream(data))
            } catch (_: Throwable) {
                null
            } ?: return null
            return SkWbmpCodec(image)
        }
    }
}
