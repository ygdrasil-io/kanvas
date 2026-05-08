package org.skia.codec.webp

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
 * WEBP decoder — D3.4 implementation of the [SkCodec] facade.
 *
 * Mirrors Skia's
 * [`SkWebpDecoder`](https://github.com/google/skia/blob/main/include/codec/SkWebpDecoder.h).
 * Unlike the rest of the D3 family, WEBP is **not** supported by
 * the JVM's stock `javax.imageio` plugin set ; we pull the
 * decode in via the
 * [TwelveMonkeys `imageio-webp` plugin](https://github.com/haraldk/TwelveMonkeys/tree/master/imageio/imageio-webp)
 * declared in `kanvas-skia/build.gradle.kts`. The plugin
 * registers a `WebPImageReaderSpi` with the JVM's ImageIO SPI
 * automatically on classpath load, so [ImageIO.read] handles the
 * VP8 / VP8L / VP8X bitstreams transparently — no FFI, no native
 * library, just a pure-Java decoder.
 *
 * **Read-only.** TwelveMonkeys ships a WEBP reader but no writer ;
 * D3.4 therefore does not extend the encoder family added in D3.5.
 * If a future workflow needs to *write* WEBP, the options are
 * (a) port libwebp's lossless encoder to pure Kotlin or (b) bind
 * to a native libwebp via JNI / FFI. Both are out of scope here —
 * the plan ([MIGRATION_PLAN_RASTER_COMPLETION.md] § D3.4) flagged
 * this as the "Option B" external-dep approach to deliver decode
 * support cheaply.
 *
 * **Alpha** is supported : VP8L and VP8X carry an alpha channel,
 * and ImageIO surfaces it through `getRGB`. The codec emits
 * `kRGBA_8888 / kUnpremul / sRGB` like every other D3 sibling.
 *
 * **No ICC profile handling.** WEBP's `VP8X` chunk can carry an
 * embedded ICC profile in an `ICCP` sub-chunk, but TwelveMonkeys
 * does not surface it through the public API ; the codec returns
 * `null` from [getICCProfile] and tags the bitmap with sRGB. Same
 * pragmatism as the GIF / BMP / WBMP codecs in D3.3.
 */
public class SkWebpCodec internal constructor(
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

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kWEBP

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

        override val name: String = "webp"

        /**
         * WEBP signature : `RIFF` + 4-byte file size (little-endian)
         * + `WEBP` ASCII tag. We check the leading `RIFF` and the
         * `WEBP` at offset 8 ; the 4-byte size in between is whatever
         * the file declares (we don't care about its value for
         * dispatch).
         */
        private val RIFF = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
        private val WEBP = byteArrayOf(0x57, 0x45, 0x42, 0x50) // "WEBP"

        override fun matches(data: ByteArray): Boolean {
            if (data.size < 12) return false
            for (i in RIFF.indices) {
                if (data[i] != RIFF[i]) return false
            }
            for (i in WEBP.indices) {
                if (data[8 + i] != WEBP[i]) return false
            }
            return true
        }

        override fun make(data: ByteArray): SkCodec? {
            val image = try {
                ImageIO.read(ByteArrayInputStream(data))
            } catch (_: Throwable) {
                null
            } ?: return null
            return SkWebpCodec(image)
        }
    }
}
