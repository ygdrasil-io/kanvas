package org.graphiks.kanvas.codec.jpegxl

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

/** Pure Kotlin JPEG XL dispatcher owner for the validated SizeHeader boundary. */
public class JpegXlCodec private constructor(
    private val document: JpegXlDocument,
) : Codec() {
    private val info: SkImageInfo = SkImageInfo.Make(
        width = document.frame.width,
        height = document.frame.height,
        colorType = SkColorType.kRGBA_8888,
        alphaType = SkAlphaType.kUnpremul,
        colorSpace = SkColorSpace.makeSRGB(),
    )

    override fun getInfo(): SkImageInfo = info

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEGXL

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
        override val name: String = "jpegxl"

        override fun matches(data: ByteArray): Boolean = JpegXlDocument.looksLikeJpegXl(data)

        override fun make(data: ByteArray): Codec? = JpegXlDocument.open(data).document?.let(::JpegXlCodec)
    }
}

/** ServiceLoader bridge for the one JPEG XL dispatcher owner. */
public class JpegXlKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(JpegXlCodec.Decoder)
}
