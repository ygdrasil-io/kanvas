package org.graphiks.kanvas.codec.jpegls

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

/** Pure Kotlin static JPEG-LS owner for verified 8-bit grayscale and RGB LOCO-I profiles. */
public class JpegLsCodec private constructor(
    private val document: JpegLsDocument,
) : Codec() {
    private val info: SkImageInfo = SkImageInfo.Make(
        width = document.width,
        height = document.height,
        colorType = SkColorType.kRGBA_8888,
        alphaType = SkAlphaType.kUnpremul,
        colorSpace = SkColorSpace.makeSRGB(),
    )

    override fun getInfo(): SkImageInfo = info
    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG
    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (
            info.width != this.info.width ||
            info.height != this.info.height ||
            info.colorType != SkColorType.kRGBA_8888 ||
            info.alphaType != SkAlphaType.kUnpremul ||
            info.colorSpace !== this.info.colorSpace
        ) {
            return Result.kInvalidConversion
        }
        if (dst.width != info.width || dst.height != info.height || dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        val decoded = document.decode()
        val bitmap = decoded.bitmap ?: return decoded.diagnostic?.result ?: Result.kErrorInInput
        System.arraycopy(bitmap.pixels8888, 0, dst.pixels8888, 0, bitmap.pixels8888.size)
        return Result.kSuccess
    }

    internal companion object Decoder : Codec.Decoder {
        override val name: String = "jpeg-ls"

        override fun matches(data: ByteArray): Boolean = JpegLsDocument.looksLikeJpegLs(data)

        override fun make(data: ByteArray): Codec? = JpegLsDocument.open(data).document?.let(::JpegLsCodec)
    }
}

/** ServiceLoader provider with one and only one static JPEG-LS decoder owner. */
public class JpegLsKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(JpegLsCodec.Decoder)
}
