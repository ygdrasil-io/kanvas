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
import org.skia.foundation.skcms.skcmsParse

/**
 * Pure Kotlin WebP metadata codec.
 *
 * This first slice only sniffs RIFF/WEBP and parses the container
 * metadata needed by [SkCodec.getInfo]. Pixel reconstruction is only
 * implemented for the current VP8L subset; VP8 alpha metadata is parsed,
 * but VP8 lossy pixel reconstruction and animation are intentionally left
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

    override fun getICCProfile(): SkcmsICCProfile? = metadata.iccProfile

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
        if (metadata.format == WebpBitstreamFormat.VP8) {
            return when (decodeVp8LossyHeader(data, metadata)) {
                Vp8LossyHeaderDecodeResult.Invalid -> Result.kErrorInInput
                Vp8LossyHeaderDecodeResult.Unsupported,
                is Vp8LossyHeaderDecodeResult.Header,
                -> Result.kUnimplemented
            }
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
    val iccProfile: SkcmsICCProfile? = null,
    val exifData: ByteArray? = null,
    val xmpData: ByteArray? = null,
    val alphaChunk: WebpAlphaChunk? = null,
) {
    val hasAlpha: Boolean get() = flags.alpha || alphaChunk != null || format == WebpBitstreamFormat.VP8L
}

internal data class WebpAlphaChunk(
    val compression: WebpAlphaCompression,
    val filtering: Int,
    val preprocessing: Int,
    val payloadOffset: Int,
    val payloadSize: Int,
)

internal enum class WebpAlphaCompression {
    NONE,
    LOSSLESS,
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
    var extended: WebpMetadata? = null
    var extendedBitstream: WebpMetadata? = null
    var iccProfile: SkcmsICCProfile? = null
    var exifData: ByteArray? = null
    var xmpData: ByteArray? = null
    var alphaChunk: WebpAlphaChunk? = null
    var offset = RIFF_HEADER_SIZE
    while (offset <= data.size - CHUNK_HEADER_SIZE) {
        val fourcc = readFourcc(data, offset)
        val size = readU32LE(data, offset + 4)
        val payloadOffset = offset + CHUNK_HEADER_SIZE
        val payloadEnd = payloadOffset.toLong() + size
        if (payloadEnd > data.size.toLong()) return null

        when (fourcc) {
            "VP8X" -> {
                if (extended != null) return null
                extended = parseVp8x(data, payloadOffset, size) ?: return null
            }
            "ICCP" -> {
                if (iccProfile != null || size > Int.MAX_VALUE) return null
                iccProfile = parseIccProfile(data, payloadOffset, size.toInt())
            }
            "EXIF" -> {
                if (exifData != null || size > Int.MAX_VALUE) return null
                exifData = copyChunkPayload(data, payloadOffset, size.toInt()) ?: return null
            }
            "XMP " -> {
                if (xmpData != null || size > Int.MAX_VALUE) return null
                xmpData = copyChunkPayload(data, payloadOffset, size.toInt()) ?: return null
            }
            "ALPH" -> {
                val base = extended ?: return null
                if (alphaChunk != null || size > Int.MAX_VALUE || !base.flags.alpha) return null
                alphaChunk = parseAlphaChunk(data, payloadOffset, size.toInt(), base.width, base.height)
                    ?: return null
            }
            "VP8L" -> {
                val bitstream = parseVp8l(data, payloadOffset, size) ?: return null
                if (extended == null) return bitstream
                if (extendedBitstream != null) return null
                extendedBitstream = bitstream
            }
            "VP8 " -> {
                val bitstream = parseVp8(data, payloadOffset, size) ?: return null
                if (extended == null) return bitstream
                if (extendedBitstream != null) return null
                extendedBitstream = bitstream
            }
        }

        val paddedSize = size + (size and 1L)
        val next = payloadOffset.toLong() + paddedSize
        if (next > Int.MAX_VALUE) return null
        offset = next.toInt()
    }
    val base = extended ?: return null
    val bitstream = extendedBitstream
    if (alphaChunk != null && bitstream?.format != WebpBitstreamFormat.VP8) return null
    return base.copy(
        format = bitstream?.format ?: base.format,
        payloadOffset = bitstream?.payloadOffset ?: base.payloadOffset,
        payloadSize = bitstream?.payloadSize ?: base.payloadSize,
        iccProfile = iccProfile,
        exifData = exifData,
        xmpData = xmpData,
        alphaChunk = alphaChunk,
    )
}

private fun parseAlphaChunk(
    data: ByteArray,
    offset: Int,
    size: Int,
    width: Int,
    height: Int,
): WebpAlphaChunk? {
    if (size <= 1 || offset < 0 || offset + size > data.size) return null
    val control = data[offset].toInt() and 0xFF
    if ((control and 0xE0) != 0) return null
    val compression = when ((control ushr 4) and 0x01) {
        0 -> WebpAlphaCompression.NONE
        1 -> WebpAlphaCompression.LOSSLESS
        else -> return null
    }
    if (compression == WebpAlphaCompression.NONE) {
        val expectedPayloadSize = width.toLong() * height.toLong()
        if (expectedPayloadSize > Int.MAX_VALUE || size - 1 != expectedPayloadSize.toInt()) return null
    }
    return WebpAlphaChunk(
        compression = compression,
        filtering = (control ushr 2) and 0x03,
        preprocessing = control and 0x03,
        payloadOffset = offset + 1,
        payloadSize = size - 1,
    )
}

private fun parseIccProfile(data: ByteArray, offset: Int, size: Int): SkcmsICCProfile? {
    if (size <= 0 || offset < 0 || offset + size > data.size) return null
    return try {
        skcmsParse(data.copyOfRange(offset, offset + size))
    } catch (_: Throwable) {
        null
    }
}

private fun copyChunkPayload(data: ByteArray, offset: Int, size: Int): ByteArray? {
    if (size <= 0 || offset < 0 || offset + size > data.size) return null
    return data.copyOfRange(offset, offset + size)
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
    val frameTag = parseVp8FrameTag(data, offset, size) ?: return null
    if (!frameTag.keyFrame) return null
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
        payloadOffset = offset,
        payloadSize = size.toInt(),
    )
}

internal data class Vp8FrameTag(
    val keyFrame: Boolean,
    val version: Int,
    val showFrame: Boolean,
    val firstPartitionSize: Int,
)

internal fun parseVp8FrameTag(data: ByteArray, offset: Int, size: Long): Vp8FrameTag? {
    if (size < 3 || size > Int.MAX_VALUE || offset < 0 || offset + size > data.size) return null
    val tag = read24LE(data, offset)
    val firstPartitionSize = tag ushr 5
    if (firstPartitionSize > size.toInt() - VP8_KEYFRAME_HEADER_SIZE) return null
    return Vp8FrameTag(
        keyFrame = (tag and 0x01) == 0,
        version = (tag ushr 1) and 0x07,
        showFrame = ((tag ushr 4) and 0x01) != 0,
        firstPartitionSize = firstPartitionSize,
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
    var imageWidth = width
    if (allowTransforms) {
        while ((bits.readBits(1) ?: return Vp8lDecodeResult.Invalid) != 0) {
            val transform = when (bits.readBits(2) ?: return Vp8lDecodeResult.Invalid) {
                VP8L_TRANSFORM_PREDICTOR -> {
                    val sizeBits = (bits.readBits(3) ?: return Vp8lDecodeResult.Invalid) + 2
                    val blockSize = 1 shl sizeBits
                    val transformWidth = ceilDiv(imageWidth, blockSize)
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
                VP8L_TRANSFORM_COLOR -> {
                    val sizeBits = (bits.readBits(3) ?: return Vp8lDecodeResult.Invalid) + 2
                    val blockSize = 1 shl sizeBits
                    val transformWidth = ceilDiv(imageWidth, blockSize)
                    val transformHeight = ceilDiv(height, blockSize)
                    val multipliers = when (val result = decodeVp8lImage(
                        bits = bits,
                        width = transformWidth,
                        height = transformHeight,
                        allowTransforms = false,
                    )) {
                        Vp8lDecodeResult.Invalid -> return Vp8lDecodeResult.Invalid
                        Vp8lDecodeResult.Unsupported -> return Vp8lDecodeResult.Unsupported
                        is Vp8lDecodeResult.Pixels -> result.argb
                    }
                    Vp8lTransform.Color(sizeBits, transformWidth, multipliers)
                }
                VP8L_TRANSFORM_SUBTRACT_GREEN -> Vp8lTransform.SubtractGreen
                VP8L_TRANSFORM_COLOR_INDEXING -> {
                    val tableSize = (bits.readBits(8) ?: return Vp8lDecodeResult.Invalid) + 1
                    val table = when (val result = decodeVp8lImage(
                        bits = bits,
                        width = tableSize,
                        height = 1,
                        allowTransforms = false,
                    )) {
                        Vp8lDecodeResult.Invalid -> return Vp8lDecodeResult.Invalid
                        Vp8lDecodeResult.Unsupported -> return Vp8lDecodeResult.Unsupported
                        is Vp8lDecodeResult.Pixels -> result.argb.cumulativeColorTable()
                    }
                    val widthBits = colorIndexingWidthBits(tableSize)
                    val outputWidth = imageWidth
                    imageWidth = ceilDiv(imageWidth, 1 shl widthBits)
                    Vp8lTransform.ColorIndexing(table, widthBits, outputWidth)
                }
                else -> return Vp8lDecodeResult.Invalid
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

    var pixels = IntArray(imageWidth * height)
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
        val distance = pixelDistanceFromCode(distanceCode, imageWidth)
        if (distance < 1 || distance > i || i + length > pixels.size) return Vp8lDecodeResult.Invalid
        repeat(length) {
            val pixel = pixels[i - distance]
            pixels[i] = pixel
            colorCache?.insert(pixel)
            i++
        }
    }
    var currentWidth = imageWidth
    transforms.asReversed().forEach { transform ->
        when (transform) {
            is Vp8lTransform.Predictor -> {
                if (!pixels.applyPredictorTransform(currentWidth, transform)) return Vp8lDecodeResult.Invalid
            }
            is Vp8lTransform.Color -> {
                if (!pixels.applyColorTransform(currentWidth, transform)) return Vp8lDecodeResult.Invalid
            }
            is Vp8lTransform.ColorIndexing -> {
                val expanded = pixels.applyColorIndexingTransform(currentWidth, height, transform)
                    ?: return Vp8lDecodeResult.Invalid
                pixels = expanded
                currentWidth = transform.outputWidth
            }
            Vp8lTransform.SubtractGreen -> pixels.applySubtractGreenTransform()
        }
    }
    return Vp8lDecodeResult.Pixels(pixels)
}

private const val VP8L_TRANSFORM_PREDICTOR: Int = 0
private const val VP8L_TRANSFORM_COLOR: Int = 1
private const val VP8L_TRANSFORM_SUBTRACT_GREEN: Int = 2
private const val VP8L_TRANSFORM_COLOR_INDEXING: Int = 3
private const val VP8L_LENGTH_PREFIX_CODE_COUNT: Int = 24

private sealed interface Vp8lTransform {
    data class Predictor(
        val sizeBits: Int,
        val width: Int,
        val predictors: IntArray,
    ) : Vp8lTransform
    data class Color(
        val sizeBits: Int,
        val width: Int,
        val multipliers: IntArray,
    ) : Vp8lTransform
    data class ColorIndexing(
        val table: IntArray,
        val widthBits: Int,
        val outputWidth: Int,
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

private fun IntArray.applyColorTransform(width: Int, transform: Vp8lTransform.Color): Boolean {
    for (i in indices) {
        val x = i % width
        val y = i / width
        val multiplierIndex = (y ushr transform.sizeBits) * transform.width + (x ushr transform.sizeBits)
        if (multiplierIndex !in transform.multipliers.indices) return false
        this[i] = inverseColorTransform(this[i], transform.multipliers[multiplierIndex])
    }
    return true
}

private fun IntArray.applyColorIndexingTransform(
    width: Int,
    height: Int,
    transform: Vp8lTransform.ColorIndexing,
): IntArray? {
    if (height < 0 || transform.outputWidth < 0) return null
    if (size != width * height) return null
    val packedPixelsPerPixel = 1 shl transform.widthBits
    if (width != ceilDiv(transform.outputWidth, packedPixelsPerPixel)) return null
    val indexBits = 8 ushr transform.widthBits
    val indexMask = (1 shl indexBits) - 1
    val output = IntArray(transform.outputWidth * height)
    for (y in 0 until height) {
        for (x in 0 until transform.outputWidth) {
            val packed = this[y * width + x / packedPixelsPerPixel]
            val shift = (x and (packedPixelsPerPixel - 1)) * indexBits
            val index = (green(packed) ushr shift) and indexMask
            output[y * transform.outputWidth + x] =
                if (index < transform.table.size) transform.table[index] else 0
        }
    }
    return output
}

private fun IntArray.cumulativeColorTable(): IntArray {
    val table = IntArray(size)
    var previous = 0
    for (i in indices) {
        table[i] = addPixels(this[i], previous)
        previous = table[i]
    }
    return table
}

private fun colorIndexingWidthBits(tableSize: Int): Int =
    when (tableSize) {
        in 1..2 -> 3
        in 3..4 -> 2
        in 5..16 -> 1
        else -> 0
    }

private fun inverseColorTransform(pixel: Int, multiplier: Int): Int {
    val alpha = alpha(pixel)
    val green = green(pixel)
    val greenToRed = blue(multiplier)
    val greenToBlue = green(multiplier)
    val redToBlue = red(multiplier)
    val transformedRed = (red(pixel) + colorTransformDelta(greenToRed, green)) and 0xFF
    val transformedBlue = (
        blue(pixel) +
            colorTransformDelta(greenToBlue, green) +
            colorTransformDelta(redToBlue, transformedRed)
        ) and 0xFF
    return packArgb(alpha, transformedRed, green, transformedBlue)
}

private fun colorTransformDelta(multiplier: Int, color: Int): Int =
    (signedByte(multiplier) * signedByte(color)) shr 5

private fun signedByte(value: Int): Int =
    if ((value and 0x80) == 0) value and 0xFF else (value and 0xFF) - 256

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

internal class Vp8BoolReader(
    private val data: ByteArray,
    private val start: Int = 0,
    private val end: Int = data.size,
) {
    private val validRange: Boolean = start >= 0 && end >= start && end <= data.size
    private var offset: Int = start
    private var range: Int = 255
    private var value: Int = 0
    private var bitCount: Int = 8

    init {
        if (!validRange) {
            offset = end
        } else {
            value = readByte() shl 8
            value = value or readByte()
        }
    }

    fun readBit(probability: Int): Int? {
        if (!validRange || probability !in 1..255) return null
        val split = 1 + (((range - 1) * probability) ushr 8)
        val bigSplit = split shl 8
        var bit = 0
        if (value >= bigSplit) {
            range -= split
            value -= bigSplit
            bit = 1
        } else {
            range = split
        }
        while (range < 128) {
            range = range shl 1
            value = (value shl 1) and 0xFFFF
            bitCount--
            if (bitCount == 0) {
                if (offset >= end) return null
                value = value or readByte()
                bitCount = 8
            }
        }
        return bit
    }

    fun readLiteral(bitCount: Int): Int? {
        if (bitCount !in 0..31) return null
        var value = 0
        repeat(bitCount) {
            value = (value shl 1) or (readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null)
        }
        return value
    }

    private fun readByte(): Int =
        if (offset < end) data[offset++].toInt() and 0xFF else 0
}

private const val VP8_BOOL_HALF_PROBABILITY: Int = 128

internal data class Vp8LossyFrameHeader(
    val colorSpace: Int,
    val clampType: Int,
    val macroblockWidth: Int,
    val macroblockHeight: Int,
    val loopFilter: Vp8LoopFilterHeader,
    val quantization: Vp8QuantizationHeader,
)

internal data class Vp8LoopFilterHeader(
    val simpleFilter: Boolean,
    val level: Int,
    val sharpness: Int,
)

internal data class Vp8QuantizationHeader(
    val yAcIndex: Int,
    val yDcDelta: Int,
    val y2DcDelta: Int,
    val y2AcDelta: Int,
    val uvDcDelta: Int,
    val uvAcDelta: Int,
)

internal sealed interface Vp8LossyHeaderDecodeResult {
    data class Header(val header: Vp8LossyFrameHeader) : Vp8LossyHeaderDecodeResult
    data object Unsupported : Vp8LossyHeaderDecodeResult
    data object Invalid : Vp8LossyHeaderDecodeResult
}

internal data class Vp8MacroblockMode(
    val yMode: Vp8LumaPredictionMode,
    val uvMode: Vp8IntraPredictionMode,
    val skipCoefficients: Boolean,
)

internal enum class Vp8LumaPredictionMode {
    DC,
    VERTICAL,
    HORIZONTAL,
    TRUE_MOTION,
    B_PRED,
}

private fun decodeVp8LossyHeader(data: ByteArray, metadata: WebpMetadata): Vp8LossyHeaderDecodeResult {
    val payloadOffset = metadata.payloadOffset
    val payloadSize = metadata.payloadSize
    if (payloadOffset < 0 || payloadSize < VP8_KEYFRAME_HEADER_SIZE) return Vp8LossyHeaderDecodeResult.Invalid
    if (payloadOffset + payloadSize > data.size) return Vp8LossyHeaderDecodeResult.Invalid
    val frameTag = parseVp8FrameTag(data, payloadOffset, payloadSize.toLong())
        ?: return Vp8LossyHeaderDecodeResult.Invalid
    if (frameTag.firstPartitionSize == 0) return Vp8LossyHeaderDecodeResult.Unsupported

    val firstPartitionOffset = payloadOffset + VP8_KEYFRAME_HEADER_SIZE
    val firstPartitionEnd = firstPartitionOffset + frameTag.firstPartitionSize
    if (firstPartitionEnd > payloadOffset + payloadSize) return Vp8LossyHeaderDecodeResult.Invalid
    val reader = Vp8BoolReader(data, firstPartitionOffset, firstPartitionEnd)
    return readVp8LossyFrameHeader(reader, metadata.width, metadata.height)
}

internal fun readVp8LossyFrameHeader(
    reader: Vp8BoolReader,
    width: Int,
    height: Int,
): Vp8LossyHeaderDecodeResult {
    val colorSpace = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val clampType = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid

    val segmentationEnabled = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid
    if (segmentationEnabled != 0) return Vp8LossyHeaderDecodeResult.Unsupported

    val simpleFilter = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val filterLevel = reader.readLiteral(6) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val sharpness = reader.readLiteral(3) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val loopFilterDeltaEnabled = reader.readBit(VP8_BOOL_HALF_PROBABILITY)
        ?: return Vp8LossyHeaderDecodeResult.Invalid
    if (loopFilterDeltaEnabled != 0) return Vp8LossyHeaderDecodeResult.Unsupported

    val partitionBits = reader.readLiteral(2) ?: return Vp8LossyHeaderDecodeResult.Invalid
    if (partitionBits != 0) return Vp8LossyHeaderDecodeResult.Unsupported

    val yAcIndex = reader.readLiteral(7) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val quantization = Vp8QuantizationHeader(
        yAcIndex = yAcIndex,
        yDcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        y2DcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        y2AcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        uvDcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        uvAcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
    )

    return Vp8LossyHeaderDecodeResult.Header(
        Vp8LossyFrameHeader(
            colorSpace = colorSpace,
            clampType = clampType,
            macroblockWidth = (width + 15) / 16,
            macroblockHeight = (height + 15) / 16,
            loopFilter = Vp8LoopFilterHeader(
                simpleFilter = simpleFilter != 0,
                level = filterLevel,
                sharpness = sharpness,
            ),
            quantization = quantization,
        ),
    )
}

internal fun readVp8KeyFrameMacroblockModes(
    reader: Vp8BoolReader,
    header: Vp8LossyFrameHeader,
    noCoeffSkip: Boolean,
): List<Vp8MacroblockMode>? {
    val macroblocks = ArrayList<Vp8MacroblockMode>(header.macroblockWidth * header.macroblockHeight)
    repeat(header.macroblockWidth * header.macroblockHeight) {
        val skipCoefficients = if (noCoeffSkip) {
            reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null
        } else {
            0
        }
        macroblocks += Vp8MacroblockMode(
            yMode = reader.readVp8KeyFrameYMode() ?: return null,
            uvMode = reader.readVp8KeyFrameUvMode() ?: return null,
            skipCoefficients = skipCoefficients != 0,
        )
    }
    return macroblocks
}

private fun Vp8BoolReader.readVp8KeyFrameYMode(): Vp8LumaPredictionMode? {
    if ((readBit(145) ?: return null) == 0) return Vp8LumaPredictionMode.DC
    if ((readBit(156) ?: return null) == 0) return Vp8LumaPredictionMode.VERTICAL
    if ((readBit(163) ?: return null) == 0) return Vp8LumaPredictionMode.HORIZONTAL
    if ((readBit(128) ?: return null) == 0) return Vp8LumaPredictionMode.TRUE_MOTION
    return Vp8LumaPredictionMode.B_PRED
}

private fun Vp8BoolReader.readVp8KeyFrameUvMode(): Vp8IntraPredictionMode? {
    if ((readBit(142) ?: return null) == 0) return Vp8IntraPredictionMode.DC
    if ((readBit(114) ?: return null) == 0) return Vp8IntraPredictionMode.VERTICAL
    if ((readBit(183) ?: return null) == 0) return Vp8IntraPredictionMode.HORIZONTAL
    return Vp8IntraPredictionMode.TRUE_MOTION
}

private fun Vp8BoolReader.readVp8SignedDelta(): Int? {
    val present = readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null
    if (present == 0) return 0
    val magnitude = readLiteral(4) ?: return null
    val negative = readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null
    return if (negative == 0) magnitude else -magnitude
}

internal fun inverseVp8Dct4x4(input: IntArray): IntArray {
    require(input.size == 16)
    val tmp = IntArray(16)
    for (i in 0 until 4) {
        val a = input[i] + input[8 + i]
        val b = input[i] - input[8 + i]
        val c = ((input[4 + i] * 20091) shr 16) - ((input[12 + i] * 35468) shr 16)
        val d = ((input[4 + i] * 35468) shr 16) + ((input[12 + i] * 20091) shr 16)
        tmp[i] = a + d
        tmp[4 + i] = b + c
        tmp[8 + i] = b - c
        tmp[12 + i] = a - d
    }

    val out = IntArray(16)
    for (i in 0 until 4) {
        val base = i * 4
        val a = tmp[base] + tmp[base + 2]
        val b = tmp[base] - tmp[base + 2]
        val c = ((tmp[base + 1] * 20091) shr 16) - ((tmp[base + 3] * 35468) shr 16)
        val d = ((tmp[base + 1] * 35468) shr 16) + ((tmp[base + 3] * 20091) shr 16)
        out[base] = (a + d + 4) shr 3
        out[base + 1] = (b + c + 4) shr 3
        out[base + 2] = (b - c + 4) shr 3
        out[base + 3] = (a - d + 4) shr 3
    }
    return out
}

internal fun inverseVp8WalshHadamard4x4(input: IntArray): IntArray {
    require(input.size == 16)
    val tmp = IntArray(16)
    for (i in 0 until 4) {
        val base = i * 4
        val a = input[base] + input[base + 3]
        val b = input[base + 1] + input[base + 2]
        val c = input[base + 1] - input[base + 2]
        val d = input[base] - input[base + 3]
        tmp[base] = a + b
        tmp[base + 1] = c + d
        tmp[base + 2] = d - c
        tmp[base + 3] = a - b
    }

    val out = IntArray(16)
    for (i in 0 until 4) {
        val a = tmp[i] + tmp[12 + i]
        val b = tmp[4 + i] + tmp[8 + i]
        val c = tmp[4 + i] - tmp[8 + i]
        val d = tmp[i] - tmp[12 + i]
        out[i] = (a + b + 3) shr 3
        out[4 + i] = (c + d + 3) shr 3
        out[8 + i] = (d - c + 3) shr 3
        out[12 + i] = (a - b + 3) shr 3
    }
    return out
}

internal sealed interface Vp8CoefficientDecodeResult {
    data class Block(val coefficients: IntArray, val hasNonZero: Boolean) : Vp8CoefficientDecodeResult
    data object Invalid : Vp8CoefficientDecodeResult
}

internal fun decodeVp8CoefficientBlock(
    reader: Vp8BoolReader,
    probabilities: IntArray,
    startCoefficient: Int = 0,
): Vp8CoefficientDecodeResult {
    if (probabilities.size != VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT) return Vp8CoefficientDecodeResult.Invalid
    if (probabilities.any { it !in 1..255 }) return Vp8CoefficientDecodeResult.Invalid
    if (startCoefficient !in 0 until VP8_BLOCK_COEFFICIENT_COUNT) return Vp8CoefficientDecodeResult.Invalid

    val coefficients = IntArray(VP8_BLOCK_COEFFICIENT_COUNT)
    var hasNonZero = false
    for (coefficientIndex in startCoefficient until VP8_BLOCK_COEFFICIENT_COUNT) {
        if ((reader.readBit(probabilities[0]) ?: return Vp8CoefficientDecodeResult.Invalid) == 0) {
            return Vp8CoefficientDecodeResult.Block(coefficients, hasNonZero)
        }
        if ((reader.readBit(probabilities[1]) ?: return Vp8CoefficientDecodeResult.Invalid) == 0) continue

        val magnitude = readVp8CoefficientMagnitude(reader, probabilities)
            ?: return Vp8CoefficientDecodeResult.Invalid
        val sign = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8CoefficientDecodeResult.Invalid
        coefficients[VP8_ZIGZAG[coefficientIndex]] = if (sign == 0) magnitude else -magnitude
        hasNonZero = true
    }
    return Vp8CoefficientDecodeResult.Block(coefficients, hasNonZero)
}

internal fun decodeVp8CoefficientBlockWithContext(
    reader: Vp8BoolReader,
    probabilitiesByContext: Array<IntArray>,
    leftHasNonZero: Boolean,
    topHasNonZero: Boolean,
    startCoefficient: Int = 0,
): Vp8CoefficientDecodeResult {
    if (probabilitiesByContext.size != VP8_COEFFICIENT_CONTEXT_COUNT) return Vp8CoefficientDecodeResult.Invalid
    val context = vp8CoefficientContext(leftHasNonZero, topHasNonZero)
    return decodeVp8CoefficientBlock(reader, probabilitiesByContext[context], startCoefficient)
}

internal fun vp8CoefficientContext(leftHasNonZero: Boolean, topHasNonZero: Boolean): Int =
    (if (leftHasNonZero) 1 else 0) + (if (topHasNonZero) 1 else 0)

internal fun dequantizeVp8CoefficientBlock(
    coefficients: IntArray,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    require(coefficients.size == VP8_BLOCK_COEFFICIENT_COUNT)
    require(dcQuant >= 0)
    require(acQuant >= 0)

    return IntArray(VP8_BLOCK_COEFFICIENT_COUNT) { index ->
        coefficients[index] * if (index == 0) dcQuant else acQuant
    }
}

internal fun reconstructVp8Intra4x4Block(
    mode: Vp8IntraPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    coefficients: IntArray,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    val residual = inverseVp8Dct4x4(
        dequantizeVp8CoefficientBlock(
            coefficients = coefficients,
            dcQuant = dcQuant,
            acQuant = acQuant,
        ),
    )
    return reconstructVp8IntraPlane(
        width = 4,
        height = 4,
        mode = mode,
        left = left,
        top = top,
        topLeft = topLeft,
        residual = residual,
    )
}

internal fun reconstructVp8Intra16x16LumaMacroblock(
    mode: Vp8LumaPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    coefficientsByBlock: Array<IntArray>,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    require(mode != Vp8LumaPredictionMode.B_PRED)
    require(coefficientsByBlock.size == VP8_LUMA_BLOCK_COUNT)
    require(left == null || left.size >= VP8_MACROBLOCK_SIZE)
    require(top == null || top.size >= VP8_MACROBLOCK_SIZE)

    val residual = IntArray(VP8_MACROBLOCK_SIZE * VP8_MACROBLOCK_SIZE)
    for (blockY in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
        for (blockX in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
            val blockIndex = blockY * VP8_BLOCKS_PER_MACROBLOCK_SIDE + blockX
            val blockResidual = inverseVp8Dct4x4(
                dequantizeVp8CoefficientBlock(
                    coefficients = coefficientsByBlock[blockIndex],
                    dcQuant = dcQuant,
                    acQuant = acQuant,
                ),
            )
            for (y in 0 until VP8_BLOCK_SIZE) {
                for (x in 0 until VP8_BLOCK_SIZE) {
                    residual[(blockY * VP8_BLOCK_SIZE + y) * VP8_MACROBLOCK_SIZE + blockX * VP8_BLOCK_SIZE + x] =
                        blockResidual[y * VP8_BLOCK_SIZE + x]
                }
            }
        }
    }

    return reconstructVp8IntraPlane(
        width = VP8_MACROBLOCK_SIZE,
        height = VP8_MACROBLOCK_SIZE,
        mode = mode.toIntraPredictionMode(),
        left = left,
        top = top,
        topLeft = topLeft,
        residual = residual,
    )
}

private fun Vp8LumaPredictionMode.toIntraPredictionMode(): Vp8IntraPredictionMode =
    when (this) {
        Vp8LumaPredictionMode.DC -> Vp8IntraPredictionMode.DC
        Vp8LumaPredictionMode.VERTICAL -> Vp8IntraPredictionMode.VERTICAL
        Vp8LumaPredictionMode.HORIZONTAL -> Vp8IntraPredictionMode.HORIZONTAL
        Vp8LumaPredictionMode.TRUE_MOTION -> Vp8IntraPredictionMode.TRUE_MOTION
        Vp8LumaPredictionMode.B_PRED -> error("B_PRED uses per-4x4 luma modes")
    }

private fun readVp8CoefficientMagnitude(reader: Vp8BoolReader, probabilities: IntArray): Int? {
    if ((reader.readBit(probabilities[2]) ?: return null) == 0) return 1
    if ((reader.readBit(probabilities[3]) ?: return null) == 0) {
        if ((reader.readBit(probabilities[4]) ?: return null) == 0) return 2
        return if ((reader.readBit(probabilities[5]) ?: return null) == 0) 3 else 4
    }

    if ((reader.readBit(probabilities[6]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 5, VP8_COEFFICIENT_CATEGORY1_PROBS)
    }
    if ((reader.readBit(probabilities[7]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 7, VP8_COEFFICIENT_CATEGORY2_PROBS)
    }
    if ((reader.readBit(probabilities[8]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 11, VP8_COEFFICIENT_CATEGORY3_PROBS)
    }
    if ((reader.readBit(probabilities[9]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 19, VP8_COEFFICIENT_CATEGORY4_PROBS)
    }
    return if ((reader.readBit(probabilities[10]) ?: return null) == 0) {
        readVp8CoefficientCategory(reader, 35, VP8_COEFFICIENT_CATEGORY5_PROBS)
    } else {
        readVp8CoefficientCategory(reader, 67, VP8_COEFFICIENT_CATEGORY6_PROBS)
    }
}

private fun readVp8CoefficientCategory(reader: Vp8BoolReader, base: Int, probabilities: IntArray): Int? {
    var value = base
    for (probability in probabilities) {
        value = (value shl 1) + (reader.readBit(probability) ?: return null)
    }
    return value - (base shl probabilities.size) + base
}

private const val VP8_BLOCK_COEFFICIENT_COUNT: Int = 16
private const val VP8_COEFFICIENT_CONTEXT_COUNT: Int = 3
private const val VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT: Int = 11
private const val VP8_BLOCK_SIZE: Int = 4
private const val VP8_BLOCKS_PER_MACROBLOCK_SIDE: Int = 4
private const val VP8_LUMA_BLOCK_COUNT: Int = 16
private const val VP8_MACROBLOCK_SIZE: Int = 16

private val VP8_ZIGZAG = intArrayOf(
    0, 1, 4, 8,
    5, 2, 3, 6,
    9, 12, 13, 10,
    7, 11, 14, 15,
)

private val VP8_COEFFICIENT_CATEGORY1_PROBS = intArrayOf(159)
private val VP8_COEFFICIENT_CATEGORY2_PROBS = intArrayOf(165, 145)
private val VP8_COEFFICIENT_CATEGORY3_PROBS = intArrayOf(173, 148, 140)
private val VP8_COEFFICIENT_CATEGORY4_PROBS = intArrayOf(176, 155, 140, 135)
private val VP8_COEFFICIENT_CATEGORY5_PROBS = intArrayOf(180, 157, 141, 134, 130)
private val VP8_COEFFICIENT_CATEGORY6_PROBS = intArrayOf(254, 254, 243, 230, 196, 177, 153, 140, 133, 130, 129)

internal enum class Vp8IntraPredictionMode {
    DC,
    VERTICAL,
    HORIZONTAL,
    TRUE_MOTION,
}

internal fun reconstructVp8IntraPlane(
    width: Int,
    height: Int,
    mode: Vp8IntraPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    residual: IntArray = IntArray(width * height),
): IntArray {
    require(width > 0)
    require(height > 0)
    require(left == null || left.size >= height)
    require(top == null || top.size >= width)
    require(residual.size == width * height)

    val dcPrediction = if (mode == Vp8IntraPredictionMode.DC) dcPrediction(width, height, left, top) else 0
    val out = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val predicted = when (mode) {
                Vp8IntraPredictionMode.DC -> dcPrediction
                Vp8IntraPredictionMode.VERTICAL -> top?.get(x) ?: 127
                Vp8IntraPredictionMode.HORIZONTAL -> left?.get(y) ?: 129
                Vp8IntraPredictionMode.TRUE_MOTION -> {
                    val base = topLeft ?: 128
                    clip8((left?.get(y) ?: 129) + (top?.get(x) ?: 127) - base)
                }
            }
            val index = y * width + x
            out[index] = clip8(predicted + residual[index])
        }
    }
    return out
}

private fun dcPrediction(width: Int, height: Int, left: IntArray?, top: IntArray?): Int {
    return when {
        left != null && top != null -> {
            val sum = left.sumPrefix(height) + top.sumPrefix(width)
            (sum + ((width + height) / 2)) / (width + height)
        }
        left != null -> (left.sumPrefix(height) + (height / 2)) / height
        top != null -> (top.sumPrefix(width) + (width / 2)) / width
        else -> 128
    }
}

private fun IntArray.sumPrefix(count: Int): Int {
    var sum = 0
    for (i in 0 until count) sum += this[i]
    return sum
}

internal fun composeVp8Yuv420ToRgba(
    yPlane: IntArray,
    uPlane: IntArray,
    vPlane: IntArray,
    width: Int,
    height: Int,
): IntArray {
    require(width > 0)
    require(height > 0)
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    require(yPlane.size >= width * height)
    require(uPlane.size >= chromaWidth * chromaHeight)
    require(vPlane.size >= chromaWidth * chromaHeight)

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val luma = yPlane[y * width + x]
            val chromaIndex = (y / 2) * chromaWidth + (x / 2)
            val cb = uPlane[chromaIndex] - 128
            val cr = vPlane[chromaIndex] - 128
            val red = clip8(luma + ((91881 * cr) shr 16))
            val green = clip8(luma - ((22554 * cb + 46802 * cr) shr 16))
            val blue = clip8(luma + ((116130 * cb) shr 16))
            pixels[y * width + x] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    return pixels
}

private fun clip8(value: Int): Int = value.coerceIn(0, 255)
