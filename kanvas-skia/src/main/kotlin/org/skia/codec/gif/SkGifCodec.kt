package org.skia.codec.gif

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
 * GIF decoder — D3.3 implementation of the [SkCodec] facade.
 *
 * Mirrors Skia's
 * [`SkGifDecoder`](https://github.com/google/skia/blob/main/include/codec/SkGifDecoder.h),
 * delegating the bitstream parse to [javax.imageio.ImageIO]. GIF is a
 * palette-based format with a single (or per-pixel) transparent
 * colour ; ImageIO surfaces transparency through `getRGB`'s alpha
 * byte, so the decoder produces a [SkColorType.kRGBA_8888] +
 * [SkAlphaType.kUnpremul] bitmap that matches the PNG / JPEG paths
 * for downstream consumers.
 *
 * **Animation : deferred.** GIF files can carry multiple frames via
 * the Graphic Control Extension. D3.3 honours
 * [MIGRATION_PLAN_RASTER_COMPLETION.md] § D3 ("GIF includes optional
 * animated extension (defer animation)") by decoding the **first
 * frame only** through `ImageIO.read`. Multi-frame access (the
 * Skia `SkCodec::getFrameCount` / `getFrameInfo` surface) lands in a
 * follow-up slice when a GM / consumer needs it.
 *
 * **No ICC profile handling.** GIF can technically carry colour
 * profile data via Application Extensions, but no widely-deployed
 * GIF in the wild uses them ; the codec tags every bitmap with sRGB
 * for parity with the rest of the D3 family.
 */
public class SkGifCodec internal constructor(
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

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kGIF

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

        override val name: String = "gif"

        /**
         * GIF87a (`47 49 46 38 37 61`) and GIF89a (`47 49 46 38 39 61`)
         * are the two header magics defined by the GIF spec. We accept
         * both ; ImageIO's GIF reader handles both transparently.
         */
        private val SIGNATURE_PREFIX = byteArrayOf(0x47, 0x49, 0x46, 0x38)
        private val SIGNATURE_SUFFIX_BYTE = 0x61.toByte()

        override fun matches(data: ByteArray): Boolean {
            if (data.size < 6) return false
            for (i in SIGNATURE_PREFIX.indices) {
                if (data[i] != SIGNATURE_PREFIX[i]) return false
            }
            // Byte 4 is '7' or '9' ; we don't pin it.
            val v = data[4]
            if (v != 0x37.toByte() && v != 0x39.toByte()) return false
            return data[5] == SIGNATURE_SUFFIX_BYTE
        }

        override fun make(data: ByteArray): SkCodec? {
            val image = try {
                ImageIO.read(ByteArrayInputStream(data))
            } catch (_: Throwable) {
                null
            } ?: return null
            return SkGifCodec(image)
        }
    }
}
