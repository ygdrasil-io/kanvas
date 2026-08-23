package org.graphiks.kanvas.codec.jpegls

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.color.icc.IccProfile

/** Pure Kotlin static JPEG-LS owner for verified 8-bit grayscale and RGB LOCO-I profiles. */
public class JpegLsCodec private constructor(
    private val document: JpegLsDocument,
) : Codec() {
    private val info: ImageInfo = ImageInfo.make(
        width = document.width,
        height = document.height,
        colorType = ColorType.RGBA_8888,
        alphaType = AlphaType.UNPREMUL,
        colorSpace = ImageColorSpace.sRGB(),
    )

    override fun getInfo(): ImageInfo = info
    override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.JPEG
    override fun getICCProfile(): IccProfile? = null

    override fun getPixels(info: ImageInfo, dst: Bitmap): Result {
        if (info.width != this.info.width || info.height != this.info.height) return Result.kInvalidScale
        if (
            info.colorType != ColorType.RGBA_8888 ||
            info.alphaType != AlphaType.UNPREMUL ||
            info.colorSpace !== this.info.colorSpace
        ) {
            return Result.kInvalidConversion
        }
        if (dst.info != info) return Result.kInvalidParameters
        val decoded = document.decode()
        val bitmap = decoded.bitmap ?: return decoded.diagnostic?.result ?: Result.kErrorInInput
        for (y in 0 until dst.height) for (x in 0 until dst.width) dst.setArgb(x, y, bitmap.getArgb(x, y))
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
