package org.skia.codec.webp

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

/**
 * Pure Kotlin WebP metadata codec.
 *
 * This first slice only sniffs RIFF/WEBP and parses the container
 * metadata needed by [SkCodec.getInfo]. Pixel reconstruction for VP8,
 * VP8L, alpha chunks, ICC, EXIF, and animation is intentionally left
 * for later slices.
 */
public class SkWebpKotlinCodec internal constructor(
    internal val metadata: WebpMetadata,
    private val data: ByteArray,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = metadata.width,
            height = metadata.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = if (metadata.hasAlpha) SkAlphaType.kUnpremul else SkAlphaType.kOpaque,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kWEBP

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (info.width != metadata.width || info.height != metadata.height) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidParameters
        }
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (metadata.format != WebpBitstreamFormat.VP8L) {
            return Result.kUnimplemented
        }
        return when (val decoded = decodeSimpleVp8l(data, metadata)) {
            Vp8lDecodeResult.Unsupported -> Result.kUnimplemented
            Vp8lDecodeResult.Invalid -> Result.kErrorInInput
            is Vp8lDecodeResult.Pixels -> {
                decoded.argb.copyInto(dst.pixels8888)
                Result.kSuccess
            }
        }
    }

    internal companion object Decoder : SkCodec.Decoder {
        override val name: String = "webp"

        override fun matches(data: ByteArray): Boolean =
            data.size >= RIFF_HEADER_SIZE &&
                data.hasAscii(0, "RIFF") &&
                data.hasAscii(8, "WEBP")

        override fun make(data: ByteArray): SkCodec? {
            if (!matches(data)) return null
            val metadata = parseMetadata(data) ?: return null
            return SkWebpKotlinCodec(metadata, data)
        }
    }
}

public class WebpKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkWebpKotlinCodec.Decoder)
}

internal data class WebpMetadata(
    val width: Int,
    val height: Int,
    val format: WebpBitstreamFormat,
    val flags: WebpVp8xFlags = WebpVp8xFlags(),
    val payloadOffset: Int = -1,
    val payloadSize: Int = 0,
) {
    val hasAlpha: Boolean get() = flags.alpha || format == WebpBitstreamFormat.VP8L
}

internal enum class WebpBitstreamFormat {
    VP8X,
    VP8L,
    VP8,
}

internal data class WebpVp8xFlags(
    val icc: Boolean = false,
    val alpha: Boolean = false,
    val exif: Boolean = false,
    val xmp: Boolean = false,
    val animation: Boolean = false,
    val raw: Int = 0,
)

private const val RIFF_HEADER_SIZE: Int = 12
private const val CHUNK_HEADER_SIZE: Int = 8
private const val VP8X_PAYLOAD_SIZE: Int = 10
private const val VP8L_HEADER_SIZE: Int = 5
private const val VP8_KEYFRAME_HEADER_SIZE: Int = 10
private const val MAX_WEBP_DIMENSION: Int = 16_777_216

private fun parseMetadata(data: ByteArray): WebpMetadata? {
    var offset = RIFF_HEADER_SIZE
    while (offset <= data.size - CHUNK_HEADER_SIZE) {
        val fourcc = readFourcc(data, offset)
        val size = readU32LE(data, offset + 4)
        val payloadOffset = offset + CHUNK_HEADER_SIZE
        val payloadEnd = payloadOffset.toLong() + size
        if (payloadEnd > data.size.toLong()) return null

        when (fourcc) {
            "VP8X" -> return parseVp8x(data, payloadOffset, size)
            "VP8L" -> return parseVp8l(data, payloadOffset, size)
            "VP8 " -> return parseVp8(data, payloadOffset, size)
        }

        val paddedSize = size + (size and 1L)
        val next = payloadOffset.toLong() + paddedSize
        if (next > Int.MAX_VALUE) return null
        offset = next.toInt()
    }
    return null
}

private fun parseVp8x(data: ByteArray, offset: Int, size: Long): WebpMetadata? {
    if (size < VP8X_PAYLOAD_SIZE) return null
    val rawFlags = data[offset].toInt() and 0xFF
    val width = read24LE(data, offset + 4) + 1
    val height = read24LE(data, offset + 7) + 1
    if (!validDimensions(width, height)) return null
    return WebpMetadata(
        width = width,
        height = height,
        format = WebpBitstreamFormat.VP8X,
        flags = WebpVp8xFlags(
            icc = (rawFlags and 0x20) != 0,
            alpha = (rawFlags and 0x10) != 0,
            exif = (rawFlags and 0x08) != 0,
            xmp = (rawFlags and 0x04) != 0,
            animation = (rawFlags and 0x02) != 0,
            raw = rawFlags,
        ),
    )
}

private fun parseVp8l(data: ByteArray, offset: Int, size: Long): WebpMetadata? {
    if (size < VP8L_HEADER_SIZE) return null
    if ((data[offset].toInt() and 0xFF) != 0x2F) return null
    val b1 = data[offset + 1].toInt() and 0xFF
    val b2 = data[offset + 2].toInt() and 0xFF
    val b3 = data[offset + 3].toInt() and 0xFF
    val b4 = data[offset + 4].toInt() and 0xFF
    val width = 1 + (((b2 and 0x3F) shl 8) or b1)
    val height = 1 + (((b4 and 0x0F) shl 10) or (b3 shl 2) or ((b2 and 0xC0) ushr 6))
    if (!validDimensions(width, height)) return null
    return WebpMetadata(
        width = width,
        height = height,
        format = WebpBitstreamFormat.VP8L,
        payloadOffset = offset,
        payloadSize = size.toInt(),
    )
}

private fun parseVp8(data: ByteArray, offset: Int, size: Long): WebpMetadata? {
    if (size < VP8_KEYFRAME_HEADER_SIZE) return null
    val frameTag = data[offset].toInt() and 0xFF
    if ((frameTag and 0x01) != 0) return null
    if ((data[offset + 3].toInt() and 0xFF) != 0x9D ||
        (data[offset + 4].toInt() and 0xFF) != 0x01 ||
        (data[offset + 5].toInt() and 0xFF) != 0x2A
    ) {
        return null
    }
    val width = readU16LE(data, offset + 6) and 0x3FFF
    val height = readU16LE(data, offset + 8) and 0x3FFF
    if (!validDimensions(width, height)) return null
    return WebpMetadata(
        width = width,
        height = height,
        format = WebpBitstreamFormat.VP8,
    )
}

private fun validDimensions(width: Int, height: Int): Boolean =
    width in 1..MAX_WEBP_DIMENSION && height in 1..MAX_WEBP_DIMENSION

private fun ByteArray.hasAscii(offset: Int, text: String): Boolean {
    if (offset < 0 || offset + text.length > size) return false
    for (i in text.indices) {
        if (this[offset + i] != text[i].code.toByte()) return false
    }
    return true
}

private fun readFourcc(bytes: ByteArray, offset: Int): String =
    CharArray(4) { (bytes[offset + it].toInt() and 0xFF).toChar() }.concatToString()

private fun read24LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16)

private fun readU16LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8)

private fun readU32LE(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xFFL) or
        ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

private sealed interface Vp8lDecodeResult {
    data class Pixels(val argb: IntArray) : Vp8lDecodeResult
    data object Unsupported : Vp8lDecodeResult
    data object Invalid : Vp8lDecodeResult
}

private fun decodeSimpleVp8l(data: ByteArray, metadata: WebpMetadata): Vp8lDecodeResult {
    val payloadOffset = metadata.payloadOffset
    val payloadSize = metadata.payloadSize
    if (payloadOffset < 0 || payloadSize < VP8L_HEADER_SIZE) return Vp8lDecodeResult.Invalid
    if (payloadOffset + payloadSize > data.size) return Vp8lDecodeResult.Invalid
    if ((data[payloadOffset].toInt() and 0xFF) != 0x2F) return Vp8lDecodeResult.Invalid

    val bits = Vp8lBitReader(data, payloadOffset + 1, payloadOffset + payloadSize)
    val width = bits.readBits(14) ?: return Vp8lDecodeResult.Invalid
    val height = bits.readBits(14) ?: return Vp8lDecodeResult.Invalid
    bits.readBits(1) ?: return Vp8lDecodeResult.Invalid // alpha_is_used is only a hint.
    val version = bits.readBits(3) ?: return Vp8lDecodeResult.Invalid
    if (version != 0) return Vp8lDecodeResult.Invalid
    if (width + 1 != metadata.width || height + 1 != metadata.height) return Vp8lDecodeResult.Invalid

    val transformPresent = bits.readBits(1) ?: return Vp8lDecodeResult.Invalid
    if (transformPresent != 0) return Vp8lDecodeResult.Unsupported

    val colorCachePresent = bits.readBits(1) ?: return Vp8lDecodeResult.Invalid
    if (colorCachePresent != 0) return Vp8lDecodeResult.Unsupported

    val metaPrefixPresent = bits.readBits(1) ?: return Vp8lDecodeResult.Invalid
    if (metaPrefixPresent != 0) return Vp8lDecodeResult.Unsupported

    val group = when (val groupResult = SimpleVp8lPrefixGroup.read(bits)) {
        Vp8lPrefixGroupReadResult.Invalid -> return Vp8lDecodeResult.Invalid
        Vp8lPrefixGroupReadResult.Unsupported -> return Vp8lDecodeResult.Unsupported
        is Vp8lPrefixGroupReadResult.Group -> groupResult.group
    }
    if (!group.isLiteralOnly) return Vp8lDecodeResult.Unsupported

    val pixels = IntArray(metadata.width * metadata.height)
    for (i in pixels.indices) {
        val green = group.green.decode(bits) ?: return Vp8lDecodeResult.Invalid
        if (green >= 256) return Vp8lDecodeResult.Unsupported
        val red = group.red.decode(bits) ?: return Vp8lDecodeResult.Invalid
        val blue = group.blue.decode(bits) ?: return Vp8lDecodeResult.Invalid
        val alpha = group.alpha.decode(bits) ?: return Vp8lDecodeResult.Invalid
        pixels[i] = packArgb(alpha, red, green, blue)
    }
    return Vp8lDecodeResult.Pixels(pixels)
}

private fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

private sealed interface Vp8lPrefixGroupReadResult {
    data class Group(val group: SimpleVp8lPrefixGroup) : Vp8lPrefixGroupReadResult
    data object Unsupported : Vp8lPrefixGroupReadResult
    data object Invalid : Vp8lPrefixGroupReadResult
}

private data class SimpleVp8lPrefixGroup(
    val green: SimpleVp8lHuffmanCode,
    val red: SimpleVp8lHuffmanCode,
    val blue: SimpleVp8lHuffmanCode,
    val alpha: SimpleVp8lHuffmanCode,
    val distance: SimpleVp8lHuffmanCode,
) {
    val isLiteralOnly: Boolean get() = green.maxSymbol < 256

    companion object {
        fun read(bits: Vp8lBitReader): Vp8lPrefixGroupReadResult {
            val green = when (val result = SimpleVp8lHuffmanCode.read(bits, alphabetSize = 256 + 24)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val red = when (val result = SimpleVp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val blue = when (val result = SimpleVp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val alpha = when (val result = SimpleVp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val distance = when (val result = SimpleVp8lHuffmanCode.read(bits, alphabetSize = 40)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            return Vp8lPrefixGroupReadResult.Group(SimpleVp8lPrefixGroup(green, red, blue, alpha, distance))
        }
    }
}

private sealed interface Vp8lHuffmanReadResult {
    data class Code(val code: SimpleVp8lHuffmanCode) : Vp8lHuffmanReadResult
    data object Unsupported : Vp8lHuffmanReadResult
    data object Invalid : Vp8lHuffmanReadResult
}

private class SimpleVp8lHuffmanCode(
    private val symbols: IntArray,
) {
    val maxSymbol: Int = symbols.max()

    fun decode(bits: Vp8lBitReader): Int? {
        if (symbols.size == 1) return symbols[0]
        val bit = bits.readBits(1) ?: return null
        return symbols[bit]
    }

    companion object {
        fun read(bits: Vp8lBitReader, alphabetSize: Int): Vp8lHuffmanReadResult {
            val isSimple = bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid
            if (isSimple == 0) return Vp8lHuffmanReadResult.Unsupported
            val symbolCount = (bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid) + 1
            val isFirst8Bits = bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid
            val first = bits.readBits(1 + 7 * isFirst8Bits) ?: return Vp8lHuffmanReadResult.Invalid
            val symbols = if (symbolCount == 1) {
                intArrayOf(first)
            } else {
                val second = bits.readBits(8) ?: return Vp8lHuffmanReadResult.Invalid
                intArrayOf(first, second).also { it.sort() }
            }
            if (symbols.any { it !in 0 until alphabetSize }) return Vp8lHuffmanReadResult.Invalid
            return Vp8lHuffmanReadResult.Code(SimpleVp8lHuffmanCode(symbols))
        }
    }
}

private class Vp8lBitReader(
    private val data: ByteArray,
    private val start: Int,
    private val end: Int,
) {
    private var bitOffset: Int = 0

    fun readBits(count: Int): Int? {
        var value = 0
        for (i in 0 until count) {
            val byteOffset = start + ((bitOffset + i) ushr 3)
            if (byteOffset >= end) return null
            val bit = (data[byteOffset].toInt() ushr ((bitOffset + i) and 7)) and 1
            value = value or (bit shl i)
        }
        bitOffset += count
        return value
    }
}
