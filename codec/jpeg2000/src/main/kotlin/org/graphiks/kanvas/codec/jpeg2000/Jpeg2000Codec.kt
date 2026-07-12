package org.graphiks.kanvas.codec.jpeg2000

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

/** Static pure-Kotlin JPEG 2000 dispatcher owner for the bounded raw J2K profile. */
public class Jpeg2000Codec private constructor(
    private val document: Jpeg2000Document,
) : Codec() {
    private val info: SkImageInfo = SkImageInfo.Make(
        width = document.frame.width,
        height = document.frame.height,
        colorType = SkColorType.kRGBA_8888,
        alphaType = SkAlphaType.kUnpremul,
        colorSpace = SkColorSpace.makeSRGB(),
    )

    override fun getInfo(): SkImageInfo = info

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG2000

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height || dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (
            info.width != this.info.width || info.height != this.info.height ||
            info.colorType != SkColorType.kRGBA_8888 || info.alphaType != SkAlphaType.kUnpremul
        ) {
            return Result.kInvalidConversion
        }
        val decoded = document.decode()
        val bitmap = decoded.bitmap ?: return decoded.diagnostic?.result ?: Result.kErrorInInput
        System.arraycopy(bitmap.pixels8888, 0, dst.pixels8888, 0, bitmap.pixels8888.size)
        return Result.kSuccess
    }

    internal companion object Decoder : Codec.Decoder {
        override val name: String = "jpeg2000"

        override fun matches(data: ByteArray): Boolean = Jpeg2000Document.looksLikeJpeg2000(data)

        override fun make(data: ByteArray): Codec? = Jpeg2000Document.open(data).document
            ?.takeIf(Jpeg2000Document::supportsImageCodec)
            ?.let(::Jpeg2000Codec)
    }
}

/** ServiceLoader bridge for the sole J2K/JP2 dispatcher owner. */
public class Jpeg2000KotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(Jpeg2000Codec.Decoder)
}
