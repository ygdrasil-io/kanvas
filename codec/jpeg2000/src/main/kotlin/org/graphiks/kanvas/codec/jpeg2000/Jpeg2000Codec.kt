package org.graphiks.kanvas.codec.jpeg2000

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.color.icc.IccProfile

/** Static pure-Kotlin JPEG 2000 dispatcher owner for the bounded raw J2K profile. */
public class Jpeg2000Codec private constructor(
    private val document: Jpeg2000Document,
) : Codec() {
    private val info: ImageInfo = ImageInfo.make(
        width = document.frame.width,
        height = document.frame.height,
        colorType = ColorType.RGBA_8888,
        alphaType = AlphaType.UNPREMUL,
        colorSpace = ImageColorSpace.sRGB(),
    )

    override fun getInfo(): ImageInfo = info

    override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.JPEG2000

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
