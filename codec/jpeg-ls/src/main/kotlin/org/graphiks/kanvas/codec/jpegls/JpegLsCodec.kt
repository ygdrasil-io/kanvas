package org.graphiks.kanvas.codec.jpegls

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.image.AlphaType
import org.skia.foundation.SkBitmap
import org.graphiks.kanvas.color.ImageColorSpace
import org.skia.foundation.SkColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.graphiks.kanvas.color.icc.IccProfile

/** Pure Kotlin static JPEG-LS owner for verified 8-bit grayscale and RGB LOCO-I profiles. */
public class JpegLsCodec private constructor(
    private val document: JpegLsDocument,
) : Codec() {
    private val info: SkImageInfo = SkImageInfo.Make(
        width = document.width,
        height = document.height,
        colorType = SkColorType.kRGBA_8888,
        alphaType = AlphaType.UNPREMUL,
        colorSpace = ImageColorSpace.sRGB(),
    )

    override fun getInfo(): SkImageInfo = info
    override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.JPEG
    override fun getICCProfile(): IccProfile? = null

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (
            info.width != this.info.width ||
            info.height != this.info.height ||
            info.colorType != SkColorType.kRGBA_8888 ||
            info.alphaType != AlphaType.UNPREMUL ||
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
