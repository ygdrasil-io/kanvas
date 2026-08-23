package org.graphiks.kanvas.codec.wbmp

import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.color.icc.IccProfile

/**
 * Pure Kotlin WBMP decoder. This is the first non-AWT backend in the
 * long-term codec split: it parses the WAP type-0 monochrome container
 * directly from bytes and writes opaque black/white RGBA pixels.
 */
public class WbmpCodec internal constructor(
    private val bytes: ByteArray,
    private val pixelOffset: Int,
    private val width: Int,
    private val height: Int,
) : Codec() {

    private val cachedInfo: ImageInfo by lazy {
        ImageInfo.make(
            width = width,
            height = height,
            colorType = ColorType.RGBA_8888,
            alphaType = AlphaType.UNPREMUL,
            colorSpace = ImageColorSpace.sRGB(),
        )
    }

    override fun getInfo(): ImageInfo = cachedInfo

    override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.WBMP

    override fun getICCProfile(): IccProfile? = null

    override fun getPixels(info: ImageInfo, dst: Bitmap): Result {
        if (info.width != cachedInfo.width || info.height != cachedInfo.height) return Result.kInvalidScale
        if (
            info.colorType != ColorType.RGBA_8888 ||
            info.alphaType != cachedInfo.alphaType ||
            info.colorSpace !== cachedInfo.colorSpace
        ) return Result.kInvalidConversion
        if (dst.info != info) return Result.kInvalidParameters

        val rowBytes = (width + 7) / 8
        val required = pixelOffset + rowBytes * height
        if (required > bytes.size) return Result.kIncompleteInput

        for (y in 0 until height) {
            val row = pixelOffset + y * rowBytes
            for (x in 0 until width) {
                val packed = bytes[row + x / 8].toInt() and 0xFF
                val bit = (packed ushr (7 - (x and 7))) and 1
                dst.setArgb(x, y, if (bit == 1) WHITE else BLACK)
            }
        }
        return Result.kSuccess
    }

    internal companion object Decoder : Codec.Decoder {
        override val name: String = "wbmp"

        override fun matches(data: ByteArray): Boolean = parseHeader(data) != null

        override fun make(data: ByteArray): Codec? {
            val header = parseHeader(data) ?: return null
            val required = requiredBytes(header)
            if (required > data.size.toLong()) return null
            return WbmpCodec(
                bytes = data,
                pixelOffset = header.pixelOffset,
                width = header.width,
                height = header.height,
            )
        }

        private fun parseHeader(data: ByteArray): Header? {
            if (data.size < 4) return null
            if (data[0] != 0.toByte()) return null
            if (data[1] != 0.toByte()) return null
            val width = readDimensionVlq(data, 2) ?: return null
            val height = readDimensionVlq(data, width.nextOffset) ?: return null
            return Header(
                width = width.value.toInt(),
                height = height.value.toInt(),
                pixelOffset = height.nextOffset,
            )
        }

        private fun readDimensionVlq(data: ByteArray, start: Int): Vlq? {
            var n = 0L
            var i = start
            var consumed = 0
            while (i < data.size && consumed < 9) {
                val b = data[i].toInt() and 0xFF
                n = (n shl 7) or (b and 0x7F).toLong()
                if (n > MAX_DIMENSION) return null
                i++
                consumed++
                if ((b and 0x80) == 0) {
                    if (n == 0L) return null
                    return Vlq(n, i)
                }
            }
            return null
        }

        private fun requiredBytes(header: Header): Long {
            val rowBytes = ((header.width.toLong() + 7L) / 8L)
            return header.pixelOffset.toLong() + rowBytes * header.height.toLong()
        }
    }

    private data class Header(val width: Int, val height: Int, val pixelOffset: Int)
    private data class Vlq(val value: Long, val nextOffset: Int)
}

public class WbmpKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(WbmpCodec.Decoder)
}

private const val BLACK: Int = -0x1000000
private const val WHITE: Int = -0x1
private const val MAX_DIMENSION: Long = 0xFFFFL
