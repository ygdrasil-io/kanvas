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
 * metadata needed by [SkCodec.getInfo]. Pixel reconstruction is only
 * implemented for the current VP8L subset; VP8, alpha chunks, ICC,
 * EXIF, and animation are intentionally left for later slices.
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

    return decodeVp8lImage(bits, metadata.width, metadata.height, allowTransforms = true)
}

private fun decodeVp8lImage(
    bits: Vp8lBitReader,
    width: Int,
    height: Int,
    allowTransforms: Boolean,
): Vp8lDecodeResult {
    val transforms = ArrayList<Vp8lTransform>()
    if (allowTransforms) {
        while ((bits.readBits(1) ?: return Vp8lDecodeResult.Invalid) != 0) {
            val transform = when (bits.readBits(2) ?: return Vp8lDecodeResult.Invalid) {
                VP8L_TRANSFORM_PREDICTOR -> {
                    val sizeBits = (bits.readBits(3) ?: return Vp8lDecodeResult.Invalid) + 2
                    val blockSize = 1 shl sizeBits
                    val transformWidth = ceilDiv(width, blockSize)
                    val transformHeight = ceilDiv(height, blockSize)
                    val predictors = when (val result = decodeVp8lImage(
                        bits = bits,
                        width = transformWidth,
                        height = transformHeight,
                        allowTransforms = false,
                    )) {
                        Vp8lDecodeResult.Invalid -> return Vp8lDecodeResult.Invalid
                        Vp8lDecodeResult.Unsupported -> return Vp8lDecodeResult.Unsupported
                        is Vp8lDecodeResult.Pixels -> result.argb
                    }
                    Vp8lTransform.Predictor(sizeBits, transformWidth, predictors)
                }
                VP8L_TRANSFORM_SUBTRACT_GREEN -> Vp8lTransform.SubtractGreen
                else -> return Vp8lDecodeResult.Unsupported
            }
            transforms += transform
        }
    }

    val colorCache = when (bits.readBits(1) ?: return Vp8lDecodeResult.Invalid) {
        0 -> null
        else -> {
            val colorCacheBits = bits.readBits(4) ?: return Vp8lDecodeResult.Invalid
            if (colorCacheBits !in 1..11) return Vp8lDecodeResult.Invalid
            Vp8lColorCache(colorCacheBits)
        }
    }

    val metaPrefixPresent = bits.readBits(1) ?: return Vp8lDecodeResult.Invalid
    if (metaPrefixPresent != 0) return Vp8lDecodeResult.Unsupported

    val group = when (val groupResult = SimpleVp8lPrefixGroup.read(bits, colorCacheSize = colorCache?.size ?: 0)) {
        Vp8lPrefixGroupReadResult.Invalid -> return Vp8lDecodeResult.Invalid
        Vp8lPrefixGroupReadResult.Unsupported -> return Vp8lDecodeResult.Unsupported
        is Vp8lPrefixGroupReadResult.Group -> groupResult.group
    }

    val pixels = IntArray(width * height)
    var i = 0
    while (i < pixels.size) {
        val green = group.green.decode(bits) ?: return Vp8lDecodeResult.Invalid
        if (green < 256) {
            val red = group.red.decode(bits) ?: return Vp8lDecodeResult.Invalid
            val blue = group.blue.decode(bits) ?: return Vp8lDecodeResult.Invalid
            val alpha = group.alpha.decode(bits) ?: return Vp8lDecodeResult.Invalid
            val pixel = packArgb(alpha, red, green, blue)
            pixels[i++] = pixel
            colorCache?.insert(pixel)
            continue
        }
        if (green >= 256 + VP8L_LENGTH_PREFIX_CODE_COUNT) {
            val cache = colorCache ?: return Vp8lDecodeResult.Unsupported
            val cacheIndex = green - 256 - VP8L_LENGTH_PREFIX_CODE_COUNT
            val pixel = cache[cacheIndex] ?: return Vp8lDecodeResult.Invalid
            pixels[i++] = pixel
            cache.insert(pixel)
            continue
        }
        val length = readVp8lPrefixValue(bits, green - 256) ?: return Vp8lDecodeResult.Invalid
        val distancePrefix = group.distance.decode(bits) ?: return Vp8lDecodeResult.Invalid
        val distanceCode = readVp8lPrefixValue(bits, distancePrefix) ?: return Vp8lDecodeResult.Invalid
        val distance = pixelDistanceFromCode(distanceCode, width)
        if (distance < 1 || distance > i || i + length > pixels.size) return Vp8lDecodeResult.Invalid
        repeat(length) {
            val pixel = pixels[i - distance]
            pixels[i] = pixel
            colorCache?.insert(pixel)
            i++
        }
    }
    transforms.asReversed().forEach { transform ->
        when (transform) {
            is Vp8lTransform.Predictor -> {
                if (!pixels.applyPredictorTransform(width, transform)) return Vp8lDecodeResult.Invalid
            }
            Vp8lTransform.SubtractGreen -> pixels.applySubtractGreenTransform()
        }
    }
    return Vp8lDecodeResult.Pixels(pixels)
}

private const val VP8L_TRANSFORM_PREDICTOR: Int = 0
private const val VP8L_TRANSFORM_SUBTRACT_GREEN: Int = 2
private const val VP8L_LENGTH_PREFIX_CODE_COUNT: Int = 24

private sealed interface Vp8lTransform {
    data class Predictor(
        val sizeBits: Int,
        val width: Int,
        val predictors: IntArray,
    ) : Vp8lTransform
    data object SubtractGreen : Vp8lTransform
}

private class Vp8lColorCache(
    private val bits: Int,
) {
    private val colors = IntArray(1 shl bits)

    val size: Int get() = colors.size

    operator fun get(index: Int): Int? =
        if (index in colors.indices) colors[index] else null

    fun insert(color: Int) {
        colors[index(color)] = color
    }

    private fun index(color: Int): Int =
        (color * VP8L_COLOR_CACHE_HASH_MULTIPLIER) ushr (32 - bits)
}

private const val VP8L_COLOR_CACHE_HASH_MULTIPLIER: Int = 0x1e35a7bd

private fun IntArray.applySubtractGreenTransform() {
    for (i in indices) {
        val pixel = this[i]
        val green = (pixel ushr 8) and 0xFF
        val red = (((pixel ushr 16) and 0xFF) + green) and 0xFF
        val blue = ((pixel and 0xFF) + green) and 0xFF
        this[i] = packArgb((pixel ushr 24) and 0xFF, red, green, blue)
    }
}

private fun IntArray.applyPredictorTransform(width: Int, transform: Vp8lTransform.Predictor): Boolean {
    for (i in indices) {
        val x = i % width
        val y = i / width
        val predictor = predictorPixel(x, y, width, transform) ?: return false
        this[i] = addPixels(this[i], predictor)
    }
    return true
}

private fun IntArray.predictorPixel(
    x: Int,
    y: Int,
    width: Int,
    transform: Vp8lTransform.Predictor,
): Int? {
    if (x == 0 && y == 0) return VP8L_BLACK_PREDICTOR
    if (y == 0) return this[y * width + x - 1]
    if (x == 0) return this[(y - 1) * width + x]

    val modeIndex = (y ushr transform.sizeBits) * transform.width + (x ushr transform.sizeBits)
    if (modeIndex !in transform.predictors.indices) return null
    val mode = (transform.predictors[modeIndex] ushr 8) and 0xFF
    if (mode !in 0..13) return null

    val left = this[y * width + x - 1]
    val top = this[(y - 1) * width + x]
    val topLeft = this[(y - 1) * width + x - 1]
    val topRight = if (x == width - 1) this[y * width] else this[(y - 1) * width + x + 1]
    return when (mode) {
        0 -> VP8L_BLACK_PREDICTOR
        1 -> left
        2 -> top
        3 -> topRight
        4 -> topLeft
        5 -> averagePixels(averagePixels(left, topRight), top)
        6 -> averagePixels(left, topLeft)
        7 -> averagePixels(left, top)
        8 -> averagePixels(topLeft, top)
        9 -> averagePixels(top, topRight)
        10 -> averagePixels(averagePixels(left, topLeft), averagePixels(top, topRight))
        11 -> selectPredictor(left, top, topLeft)
        12 -> clampAddSubtractFull(left, top, topLeft)
        else -> clampAddSubtractHalf(averagePixels(left, top), topLeft)
    }
}

private const val VP8L_BLACK_PREDICTOR: Int = -0x1000000

private fun addPixels(residual: Int, predictor: Int): Int =
    packArgb(
        alpha = (alpha(residual) + alpha(predictor)) and 0xFF,
        red = (red(residual) + red(predictor)) and 0xFF,
        green = (green(residual) + green(predictor)) and 0xFF,
        blue = (blue(residual) + blue(predictor)) and 0xFF,
    )

private fun averagePixels(a: Int, b: Int): Int =
    packArgb(
        alpha = (alpha(a) + alpha(b)) ushr 1,
        red = (red(a) + red(b)) ushr 1,
        green = (green(a) + green(b)) ushr 1,
        blue = (blue(a) + blue(b)) ushr 1,
    )

private fun selectPredictor(left: Int, top: Int, topLeft: Int): Int {
    val pa = alpha(left) + alpha(top) - alpha(topLeft)
    val pr = red(left) + red(top) - red(topLeft)
    val pg = green(left) + green(top) - green(topLeft)
    val pb = blue(left) + blue(top) - blue(topLeft)
    val leftDistance = kotlin.math.abs(pa - alpha(left)) +
        kotlin.math.abs(pr - red(left)) +
        kotlin.math.abs(pg - green(left)) +
        kotlin.math.abs(pb - blue(left))
    val topDistance = kotlin.math.abs(pa - alpha(top)) +
        kotlin.math.abs(pr - red(top)) +
        kotlin.math.abs(pg - green(top)) +
        kotlin.math.abs(pb - blue(top))
    return if (leftDistance < topDistance) left else top
}

private fun clampAddSubtractFull(left: Int, top: Int, topLeft: Int): Int =
    packArgb(
        alpha = clampByte(alpha(left) + alpha(top) - alpha(topLeft)),
        red = clampByte(red(left) + red(top) - red(topLeft)),
        green = clampByte(green(left) + green(top) - green(topLeft)),
        blue = clampByte(blue(left) + blue(top) - blue(topLeft)),
    )

private fun clampAddSubtractHalf(average: Int, topLeft: Int): Int =
    packArgb(
        alpha = clampByte(alpha(average) + (alpha(average) - alpha(topLeft)) / 2),
        red = clampByte(red(average) + (red(average) - red(topLeft)) / 2),
        green = clampByte(green(average) + (green(average) - green(topLeft)) / 2),
        blue = clampByte(blue(average) + (blue(average) - blue(topLeft)) / 2),
    )

private fun clampByte(value: Int): Int = value.coerceIn(0, 255)

private fun alpha(pixel: Int): Int = (pixel ushr 24) and 0xFF
private fun red(pixel: Int): Int = (pixel ushr 16) and 0xFF
private fun green(pixel: Int): Int = (pixel ushr 8) and 0xFF
private fun blue(pixel: Int): Int = pixel and 0xFF

private fun ceilDiv(value: Int, divisor: Int): Int =
    (value + divisor - 1) / divisor

private fun readVp8lPrefixValue(bits: Vp8lBitReader, prefixCode: Int): Int? {
    if (prefixCode !in 0..39) return null
    if (prefixCode < 4) return prefixCode + 1
    val extraBits = (prefixCode - 2) ushr 1
    val offset = (2 + (prefixCode and 1)) shl extraBits
    return offset + (bits.readBits(extraBits) ?: return null) + 1
}

private fun pixelDistanceFromCode(distanceCode: Int, width: Int): Int {
    if (distanceCode > VP8L_DISTANCE_MAP.size) return distanceCode - VP8L_DISTANCE_MAP.size
    val (x, y) = VP8L_DISTANCE_MAP[distanceCode - 1]
    return maxOf(1, x + y * width)
}

private fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

private sealed interface Vp8lPrefixGroupReadResult {
    data class Group(val group: SimpleVp8lPrefixGroup) : Vp8lPrefixGroupReadResult
    data object Unsupported : Vp8lPrefixGroupReadResult
    data object Invalid : Vp8lPrefixGroupReadResult
}

private data class SimpleVp8lPrefixGroup(
    val green: Vp8lHuffmanCode,
    val red: Vp8lHuffmanCode,
    val blue: Vp8lHuffmanCode,
    val alpha: Vp8lHuffmanCode,
    val distance: Vp8lHuffmanCode,
) {
    companion object {
        fun read(bits: Vp8lBitReader, colorCacheSize: Int): Vp8lPrefixGroupReadResult {
            val greenAlphabetSize = 256 + VP8L_LENGTH_PREFIX_CODE_COUNT + colorCacheSize
            val green = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = greenAlphabetSize)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val red = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val blue = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val alpha = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val distance = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 40)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            return Vp8lPrefixGroupReadResult.Group(SimpleVp8lPrefixGroup(green, red, blue, alpha, distance))
        }
    }
}

private sealed interface Vp8lHuffmanReadResult {
    data class Code(val code: Vp8lHuffmanCode) : Vp8lHuffmanReadResult
    data object Unsupported : Vp8lHuffmanReadResult
    data object Invalid : Vp8lHuffmanReadResult
}

private class Vp8lHuffmanCode(
    private val symbols: IntArray,
    private val entries: List<Vp8lHuffmanEntry>,
    private val maxCodeLength: Int,
) {
    val maxSymbol: Int = symbols.max()

    fun decode(bits: Vp8lBitReader): Int? {
        if (symbols.size == 1) return symbols[0]
        var code = 0
        for (length in 1..maxCodeLength) {
            val bit = bits.readBits(1) ?: return null
            code = code or (bit shl (length - 1))
            val entry = entries.firstOrNull { it.length == length && it.reversedCode == code }
            if (entry != null) return entry.symbol
        }
        return null
    }

    companion object {
        fun read(bits: Vp8lBitReader, alphabetSize: Int): Vp8lHuffmanReadResult {
            val isSimple = bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid
            if (isSimple == 0) return readNormal(bits, alphabetSize)
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
            return fromCodeLengths(IntArray(alphabetSize) { symbol ->
                if (symbol in symbols) 1 else 0
            })
        }

        private fun readNormal(bits: Vp8lBitReader, alphabetSize: Int): Vp8lHuffmanReadResult {
            val codeLengthCodeLengths = IntArray(CODE_LENGTH_CODE_COUNT)
            val codeLengthCount = 4 + (bits.readBits(4) ?: return Vp8lHuffmanReadResult.Invalid)
            if (codeLengthCount > CODE_LENGTH_CODE_ORDER.size) return Vp8lHuffmanReadResult.Invalid
            for (i in 0 until codeLengthCount) {
                codeLengthCodeLengths[CODE_LENGTH_CODE_ORDER[i]] =
                    bits.readBits(3) ?: return Vp8lHuffmanReadResult.Invalid
            }
            val codeLengthCode = when (val result = fromCodeLengths(codeLengthCodeLengths)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lHuffmanReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lHuffmanReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val maxSymbol = if ((bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid) == 0) {
                alphabetSize
            } else {
                val lengthBits = 2 + 2 * (bits.readBits(3) ?: return Vp8lHuffmanReadResult.Invalid)
                2 + (bits.readBits(lengthBits) ?: return Vp8lHuffmanReadResult.Invalid)
            }
            if (maxSymbol > alphabetSize) return Vp8lHuffmanReadResult.Invalid

            val codeLengths = IntArray(alphabetSize)
            var symbol = 0
            var previousLength = 8
            while (symbol < maxSymbol) {
                when (val codeLength = codeLengthCode.decode(bits) ?: return Vp8lHuffmanReadResult.Invalid) {
                    in 0..15 -> {
                        codeLengths[symbol++] = codeLength
                        if (codeLength != 0) previousLength = codeLength
                    }
                    16 -> {
                        val repeat = 3 + (bits.readBits(2) ?: return Vp8lHuffmanReadResult.Invalid)
                        if (symbol + repeat > maxSymbol) return Vp8lHuffmanReadResult.Invalid
                        repeat(repeat) { codeLengths[symbol++] = previousLength }
                    }
                    17 -> {
                        val repeat = 3 + (bits.readBits(3) ?: return Vp8lHuffmanReadResult.Invalid)
                        if (symbol + repeat > maxSymbol) return Vp8lHuffmanReadResult.Invalid
                        symbol += repeat
                    }
                    18 -> {
                        val repeat = 11 + (bits.readBits(7) ?: return Vp8lHuffmanReadResult.Invalid)
                        if (symbol + repeat > maxSymbol) return Vp8lHuffmanReadResult.Invalid
                        symbol += repeat
                    }
                    else -> return Vp8lHuffmanReadResult.Invalid
                }
            }
            return fromCodeLengths(codeLengths)
        }

        private fun fromCodeLengths(codeLengths: IntArray): Vp8lHuffmanReadResult {
            val symbols = codeLengths.indices.filter { codeLengths[it] > 0 }.toIntArray()
            if (symbols.isEmpty()) return Vp8lHuffmanReadResult.Invalid
            if (codeLengths.any { it !in 0..MAX_HUFFMAN_CODE_LENGTH }) return Vp8lHuffmanReadResult.Invalid

            val counts = IntArray(MAX_HUFFMAN_CODE_LENGTH + 1)
            for (length in codeLengths) {
                if (length > 0) counts[length]++
            }
            var remaining = 1
            for (length in 1..MAX_HUFFMAN_CODE_LENGTH) {
                remaining = (remaining shl 1) - counts[length]
                if (remaining < 0) return Vp8lHuffmanReadResult.Invalid
            }
            if (symbols.size > 1 && remaining != 0) return Vp8lHuffmanReadResult.Invalid

            val nextCode = IntArray(MAX_HUFFMAN_CODE_LENGTH + 1)
            var code = 0
            for (bits in 1..MAX_HUFFMAN_CODE_LENGTH) {
                code = (code + counts[bits - 1]) shl 1
                nextCode[bits] = code
            }
            val entries = ArrayList<Vp8lHuffmanEntry>()
            for (symbol in codeLengths.indices) {
                val length = codeLengths[symbol]
                if (length == 0) continue
                entries += Vp8lHuffmanEntry(symbol, length, reverseBits(nextCode[length], length))
                nextCode[length]++
            }
            return Vp8lHuffmanReadResult.Code(
                Vp8lHuffmanCode(
                    symbols = symbols,
                    entries = entries,
                    maxCodeLength = entries.maxOf { it.length },
                ),
            )
        }
    }

}

private data class Vp8lHuffmanEntry(
    val symbol: Int,
    val length: Int,
    val reversedCode: Int,
)

private const val CODE_LENGTH_CODE_COUNT: Int = 19
private const val MAX_HUFFMAN_CODE_LENGTH: Int = 15
private val CODE_LENGTH_CODE_ORDER = intArrayOf(
    17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
)

private val VP8L_DISTANCE_MAP = arrayOf(
    0 to 1, 1 to 0, 1 to 1, -1 to 1, 0 to 2, 2 to 0, 1 to 2, -1 to 2,
    2 to 1, -2 to 1, 2 to 2, -2 to 2, 0 to 3, 3 to 0, 1 to 3, -1 to 3,
    3 to 1, -3 to 1, 2 to 3, -2 to 3, 3 to 2, -3 to 2, 0 to 4, 4 to 0,
    1 to 4, -1 to 4, 4 to 1, -4 to 1, 3 to 3, -3 to 3, 2 to 4, -2 to 4,
    4 to 2, -4 to 2, 0 to 5, 5 to 0, 1 to 5, -1 to 5, 5 to 1, -5 to 1,
    4 to 3, -4 to 3, 3 to 4, -3 to 4, 2 to 5, -2 to 5, 5 to 2, -5 to 2,
    4 to 4, -4 to 4, 3 to 5, -3 to 5, 5 to 3, -5 to 3, 0 to 6, 6 to 0,
    1 to 6, -1 to 6, 6 to 1, -6 to 1, 2 to 6, -2 to 6, 6 to 2, -6 to 2,
    4 to 5, -4 to 5, 5 to 4, -5 to 4, 3 to 6, -3 to 6, 6 to 3, -6 to 3,
    0 to 7, 7 to 0, 1 to 7, -1 to 7, 5 to 5, -5 to 5, 7 to 1, -7 to 1,
    4 to 6, -4 to 6, 6 to 4, -6 to 4, 2 to 7, -2 to 7, 7 to 2, -7 to 2,
    3 to 7, -3 to 7, 7 to 3, -7 to 3, 5 to 6, -5 to 6, 6 to 5, -6 to 5,
    8 to 0, 4 to 7, -4 to 7, 7 to 4, -7 to 4, 8 to 1, 8 to 2, 6 to 6,
    -6 to 6, 8 to 3, 5 to 7, -5 to 7, 7 to 5, -7 to 5, 8 to 4, 6 to 7,
    -6 to 7, 7 to 6, -7 to 6, 8 to 5, 7 to 7, -7 to 7, 8 to 6, 8 to 7,
)

private fun reverseBits(value: Int, length: Int): Int {
    var result = 0
    for (i in 0 until length) {
        result = (result shl 1) or ((value ushr i) and 1)
    }
    return result
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
