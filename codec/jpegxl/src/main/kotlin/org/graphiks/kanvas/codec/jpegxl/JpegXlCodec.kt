package org.graphiks.kanvas.codec.jpegxl

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.color.icc.IccProfile

/** Pure Kotlin JPEG XL dispatcher owner for the bounded raw Modular profile. */
public class JpegXlCodec private constructor(
    private val document: JpegXlDocument,
) : Codec() {
    private val info: ImageInfo = ImageInfo.make(
        width = document.frame.width,
        height = document.frame.height,
        colorType = ColorType.RGBA_8888,
        alphaType = AlphaType.UNPREMUL,
        colorSpace = ImageColorSpace.sRGB(),
    )

    override fun getInfo(): ImageInfo = info

    override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.JPEGXL

    override fun getICCProfile(): IccProfile? = null

    override fun getPixels(info: ImageInfo, dst: Bitmap): Result {
        if (info.width != this.info.width || info.height != this.info.height) return Result.kInvalidScale
        if (
            info.colorType != ColorType.RGBA_8888 || info.alphaType != AlphaType.UNPREMUL ||
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
        override val name: String = "jpegxl"

        override fun matches(data: ByteArray): Boolean = JpegXlDocument.looksLikeJpegXl(data)

        override fun make(data: ByteArray): Codec? = JpegXlDocument.open(data).document?.let(::JpegXlCodec)
    }
}

/** ServiceLoader bridge for the one JPEG XL dispatcher owner. */
public class JpegXlKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(JpegXlCodec.Decoder)
}
