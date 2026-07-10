package org.graphiks.kanvas.codec.png

import java.util.Collections
import java.util.zip.CRC32

public data class PngByteRange(
    public val startInclusive: Long,
    public val endExclusive: Long,
) {
    init {
        require(startInclusive >= 0L) { "startInclusive must be non-negative" }
        require(endExclusive >= startInclusive) { "endExclusive must not precede startInclusive" }
    }

    public val size: Long
        get() = endExclusive - startInclusive
}

public data class PngChunkRecord(
    public val type: String,
    public val ordinal: Int,
    public val rawRange: PngByteRange,
    public val payloadRange: PngByteRange,
) {
    init {
        require(type.length == 4) { "PNG chunk types must contain four characters" }
        require(ordinal >= 0) { "ordinal must be non-negative" }
        require(payloadRange.startInclusive >= rawRange.startInclusive + CHUNK_PREFIX_BYTES) {
            "payloadRange must follow the chunk length and type"
        }
        require(payloadRange.endExclusive + CRC_BYTES == rawRange.endExclusive) {
            "rawRange must include the payload CRC"
        }
    }

    public val isCritical: Boolean
        get() = type[0] in 'A'..'Z'

    public val isAncillary: Boolean
        get() = !isCritical

    public val isSafeToCopy: Boolean
        get() = type[3] in 'a'..'z'
}

public data class PngHeader(
    public val width: Int,
    public val height: Int,
    public val bitDepth: Int,
    public val colorType: Int,
    public val compressionMethod: Int,
    public val filterMethod: Int,
    public val interlaceMethod: Int,
)

public class PngContainer internal constructor(
    public val header: PngHeader,
    chunks: List<PngChunkRecord>,
    public val totalIdatBytes: Long,
) {
    public val chunks: List<PngChunkRecord> = Collections.unmodifiableList(ArrayList(chunks))
}

public enum class PngDiagnosticSeverity {
    ERROR,
}

public data class PngDiagnostic(
    public val code: String,
    public val offset: Long,
    public val message: String,
    public val chunkType: String? = null,
    public val severity: PngDiagnosticSeverity = PngDiagnosticSeverity.ERROR,
)

public sealed interface PngContainerParseResult {
    public data class Success(public val container: PngContainer) : PngContainerParseResult

    public data class Failure(public val diagnostic: PngDiagnostic) : PngContainerParseResult
}

public data class PngContainerLimits(
    public val maxWidth: Int = 100_000,
    public val maxHeight: Int = 100_000,
    public val maxChunkCount: Int = 100_000,
    public val maxTotalIdatBytes: Long = 512L * 1024L * 1024L,
    public val maxAncillaryChunkBytes: Long = 64L * 1024L * 1024L,
    public val maxInputBytes: Long = 1024L * 1024L * 1024L,
) {
    init {
        require(maxWidth > 0) { "maxWidth must be positive" }
        require(maxHeight > 0) { "maxHeight must be positive" }
        require(maxChunkCount > 0) { "maxChunkCount must be positive" }
        require(maxTotalIdatBytes >= 0L) { "maxTotalIdatBytes must be non-negative" }
        require(maxAncillaryChunkBytes >= 0L) { "maxAncillaryChunkBytes must be non-negative" }
        require(maxInputBytes >= 0L) { "maxInputBytes must be non-negative" }
    }

    public companion object {
        public val Default: PngContainerLimits = PngContainerLimits()
    }
}

public object PngContainerParser {
    public fun parse(
        data: ByteArray,
        limits: PngContainerLimits = PngContainerLimits.Default,
    ): PngContainerParseResult {
        if (data.size.toLong() > limits.maxInputBytes) {
            return failure("png.input.limit", 0L, message = "PNG input exceeds the configured byte limit")
        }
        if (!hasSignature(data)) {
            return failure("png.signature.invalid", 0L, message = "Invalid or truncated PNG signature")
        }

        val chunks = ArrayList<PngChunkRecord>()
        var header: PngHeader? = null
        var offset = PNG_SIGNATURE.size.toLong()
        var sawPalette = false
        var sawIdat = false
        var idatClosed = false
        var totalIdatBytes = 0L

        while (offset < data.size.toLong()) {
            if (data.size.toLong() - offset < CHUNK_OVERHEAD_BYTES) {
                return failure("png.chunk.truncated", offset, message = "Truncated PNG chunk framing")
            }

            val chunkOffset = offset
            val chunkOffsetInt = chunkOffset.toInt()
            val payloadLength = readU32(data, chunkOffsetInt)
            if (payloadLength > Int.MAX_VALUE.toLong()) {
                return failure(
                    "png.chunk.length.invalid",
                    chunkOffset,
                    message = "PNG chunk length exceeds the format range",
                )
            }
            val payloadOffset = chunkOffset + CHUNK_PREFIX_BYTES
            val chunkEnd = payloadOffset + payloadLength + CRC_BYTES
            if (chunkEnd > data.size.toLong()) {
                return failure("png.chunk.truncated", chunkOffset, message = "PNG chunk exceeds the input range")
            }
            if (chunks.size >= limits.maxChunkCount) {
                return failure("png.chunk.count.limit", chunkOffset, message = "PNG chunk count exceeds the configured limit")
            }

            val typeOffset = chunkOffsetInt + LENGTH_BYTES
            val type = readType(data, typeOffset)
            if (!isAsciiLetterType(data, typeOffset)) {
                return failure(
                    "png.chunk.type.invalid",
                    chunkOffset,
                    type,
                    "PNG chunk type bytes must be ASCII letters",
                )
            }
            if (data[typeOffset + 2].toInt() !in 'A'.code..'Z'.code) {
                return failure(
                    "png.chunk.type.reserved",
                    chunkOffset,
                    type,
                    "PNG chunk type reserved bit must be uppercase",
                )
            }
            if (!crcMatches(data, typeOffset, payloadLength.toInt(), (chunkEnd - CRC_BYTES).toInt())) {
                return failure("png.chunk.crc.invalid", chunkOffset, type, "PNG chunk CRC mismatch")
            }
            if (type in APNG_CHUNK_TYPES) {
                return failure("png.apng.unsupported", chunkOffset, type, "APNG is outside the static PNG scope")
            }

            val isAncillary = data[typeOffset].toInt() in 'a'.code..'z'.code
            if (isAncillary && payloadLength > limits.maxAncillaryChunkBytes) {
                return failure(
                    "png.ancillary.limit",
                    chunkOffset,
                    type,
                    "PNG ancillary chunk exceeds the configured byte limit",
                )
            }

            if (type == TYPE_IDAT) {
                if (payloadLength > limits.maxTotalIdatBytes - totalIdatBytes) {
                    return failure(
                        "png.idat.limit",
                        chunkOffset,
                        type,
                        "PNG IDAT bytes exceed the configured total limit",
                    )
                }
                totalIdatBytes += payloadLength
            }

            when (type) {
                TYPE_IHDR -> {
                    if (header != null) {
                        return failure("png.ihdr.duplicate", chunkOffset, type, "PNG contains more than one IHDR")
                    }
                    if (chunks.isNotEmpty()) {
                        return failure("png.ihdr.order", chunkOffset, type, "IHDR must be the first PNG chunk")
                    }
                    if (payloadLength != IHDR_BYTES.toLong()) {
                        return failure("png.ihdr.length", chunkOffset, type, "IHDR payload must be 13 bytes")
                    }
                    val width = readU32(data, payloadOffset.toInt())
                    val height = readU32(data, payloadOffset.toInt() + 4)
                    if (
                        width == 0L || height == 0L ||
                        width > Int.MAX_VALUE.toLong() || height > Int.MAX_VALUE.toLong()
                    ) {
                        return failure(
                            "png.ihdr.dimensions.invalid",
                            chunkOffset,
                            type,
                            "PNG dimensions must be positive 31-bit values",
                        )
                    }
                    if (width > limits.maxWidth.toLong() || height > limits.maxHeight.toLong()) {
                        return failure(
                            "png.dimension.limit",
                            chunkOffset,
                            type,
                            "PNG dimensions exceed the configured limits",
                        )
                    }
                    val parsedHeader = parseHeader(data, payloadOffset.toInt())
                        ?: return failure(
                            "png.ihdr.encoding.invalid",
                            chunkOffset,
                            type,
                            "IHDR contains an unsupported PNG encoding value",
                        )
                    header = parsedHeader
                }

                TYPE_PLTE -> {
                    if (header == null) {
                        return failure("png.ihdr.order", chunkOffset, type, "IHDR must be the first PNG chunk")
                    }
                    if (sawPalette) {
                        return failure("png.plte.duplicate", chunkOffset, type, "PNG contains more than one PLTE")
                    }
                    if (sawIdat) {
                        return failure("png.plte.order", chunkOffset, type, "PLTE must precede IDAT")
                    }
                    sawPalette = true
                }

                TYPE_IDAT -> {
                    if (header == null) {
                        return failure("png.ihdr.order", chunkOffset, type, "IHDR must be the first PNG chunk")
                    }
                    if (idatClosed) {
                        return failure(
                            "png.idat.noncontiguous",
                            chunkOffset,
                            type,
                            "IDAT chunks must be contiguous",
                        )
                    }
                    sawIdat = true
                }

                TYPE_IEND -> {
                    if (header == null) {
                        return failure("png.ihdr.order", chunkOffset, type, "IHDR must be the first PNG chunk")
                    }
                    if (payloadLength != 0L) {
                        return failure("png.iend.length", chunkOffset, type, "IEND payload must be empty")
                    }
                    if (!sawIdat) {
                        return failure("png.idat.required", chunkOffset, type, "PNG requires at least one IDAT chunk")
                    }
                }

                else -> {
                    if (header == null) {
                        return failure("png.ihdr.order", chunkOffset, type, "IHDR must be the first PNG chunk")
                    }
                    if (!isAncillary) {
                        return failure(
                            "png.critical.unsupported",
                            chunkOffset,
                            type,
                            "Unknown critical PNG chunk",
                        )
                    }
                }
            }

            if (sawIdat && type != TYPE_IDAT) idatClosed = true
            chunks += PngChunkRecord(
                type = type,
                ordinal = chunks.size,
                rawRange = PngByteRange(chunkOffset, chunkEnd),
                payloadRange = PngByteRange(payloadOffset, payloadOffset + payloadLength),
            )
            offset = chunkEnd

            if (type == TYPE_IEND) {
                if (offset != data.size.toLong()) {
                    return failure("png.iend.trailing_data", chunkOffset, type, "Bytes follow the terminal IEND chunk")
                }
                return PngContainerParseResult.Success(
                    PngContainer(
                        header = requireNotNull(header),
                        chunks = chunks,
                        totalIdatBytes = totalIdatBytes,
                    ),
                )
            }
        }

        return when {
            header == null -> failure("png.ihdr.required", offset, message = "PNG requires an IHDR chunk")
            !sawIdat -> failure("png.idat.required", offset, message = "PNG requires at least one IDAT chunk")
            else -> failure("png.iend.required", offset, message = "PNG requires a terminal IEND chunk")
        }
    }

    private fun parseHeader(data: ByteArray, offset: Int): PngHeader? {
        val width = readU32(data, offset)
        val height = readU32(data, offset + 4)
        val bitDepth = data[offset + 8].toInt() and 0xFF
        val colorType = data[offset + 9].toInt() and 0xFF
        val compressionMethod = data[offset + 10].toInt() and 0xFF
        val filterMethod = data[offset + 11].toInt() and 0xFF
        val interlaceMethod = data[offset + 12].toInt() and 0xFF
        if (!isValidColorDepth(colorType, bitDepth) || compressionMethod != 0 || filterMethod != 0 || interlaceMethod !in 0..1) {
            return null
        }
        return PngHeader(
            width = width.toInt(),
            height = height.toInt(),
            bitDepth = bitDepth,
            colorType = colorType,
            compressionMethod = compressionMethod,
            filterMethod = filterMethod,
            interlaceMethod = interlaceMethod,
        )
    }

    private fun isValidColorDepth(colorType: Int, bitDepth: Int): Boolean = when (colorType) {
        0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16
        2 -> bitDepth == 8 || bitDepth == 16
        3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8
        4 -> bitDepth == 8 || bitDepth == 16
        6 -> bitDepth == 8 || bitDepth == 16
        else -> false
    }

    private fun hasSignature(data: ByteArray): Boolean {
        if (data.size < PNG_SIGNATURE.size) return false
        for (index in PNG_SIGNATURE.indices) {
            if (data[index] != PNG_SIGNATURE[index]) return false
        }
        return true
    }

    private fun isAsciiLetterType(data: ByteArray, offset: Int): Boolean {
        for (index in 0 until TYPE_BYTES) {
            val value = data[offset + index].toInt() and 0xFF
            if (value !in 'A'.code..'Z'.code && value !in 'a'.code..'z'.code) return false
        }
        return true
    }

    private fun readType(data: ByteArray, offset: Int): String = buildString(TYPE_BYTES) {
        for (index in 0 until TYPE_BYTES) append((data[offset + index].toInt() and 0xFF).toChar())
    }

    private fun crcMatches(data: ByteArray, typeOffset: Int, payloadLength: Int, crcOffset: Int): Boolean {
        val crc = CRC32()
        crc.update(data, typeOffset, TYPE_BYTES + payloadLength)
        return crc.value.toInt() == readI32(data, crcOffset)
    }

    private fun readU32(data: ByteArray, offset: Int): Long = readI32(data, offset).toLong() and 0xFFFF_FFFFL

    private fun readI32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun failure(
        code: String,
        offset: Long,
        chunkType: String? = null,
        message: String,
    ): PngContainerParseResult.Failure = PngContainerParseResult.Failure(
        PngDiagnostic(
            code = code,
            offset = offset,
            chunkType = chunkType,
            message = message,
        ),
    )

    private const val LENGTH_BYTES: Int = 4
    private const val TYPE_BYTES: Int = 4
    private const val CRC_BYTES: Long = 4L
    private const val CHUNK_PREFIX_BYTES: Long = 8L
    private const val CHUNK_OVERHEAD_BYTES: Long = 12L
    private const val IHDR_BYTES: Int = 13
    private const val TYPE_IHDR: String = "IHDR"
    private const val TYPE_PLTE: String = "PLTE"
    private const val TYPE_IDAT: String = "IDAT"
    private const val TYPE_IEND: String = "IEND"
    private val APNG_CHUNK_TYPES: Set<String> = setOf("acTL", "fcTL", "fdAT")
    private val PNG_SIGNATURE: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
}

private const val CHUNK_PREFIX_BYTES: Long = 8L
private const val CRC_BYTES: Long = 4L
