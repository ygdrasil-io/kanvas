package org.graphiks.kanvas.codec.jpegxl

import java.util.Collections
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap

/** Resource ceilings checked before JPEG XL header parsing. */
public data class JpegXlLimits(
    val maxEncodedBytes: Long = 64L * 1024L * 1024L,
    val maxWidth: Int = 32_768,
    val maxHeight: Int = 32_768,
    val maxPixels: Long = 64L * 1024L * 1024L,
    val maxBoxes: Int = 4_096,
) {
    init {
        require(maxEncodedBytes >= 2)
        require(maxEncodedBytes <= MAX_BIT_READER_BYTES)
        require(maxWidth > 0)
        require(maxHeight > 0)
        require(maxPixels > 0)
        require(maxBoxes > 0)
    }
}

/** Stable JPEG XL malformed-input, limit, or unsupported-feature diagnostic. */
public data class JpegXlDiagnostic(
    val code: String,
    val offset: Long,
    val result: Codec.Result = Codec.Result.kErrorInInput,
)

/** Result of a bounded JPEG XL document open. */
public data class JpegXlOpenResult(
    val document: JpegXlDocument?,
    val diagnostic: JpegXlDiagnostic?,
)

/** Result of JPEG XL decoding. No result is fabricated while entropy is unavailable. */
public data class JpegXlDecodeResult(
    val bitmap: SkBitmap?,
    val diagnostic: JpegXlDiagnostic?,
)

/** Transport selected by the JPEG XL document. */
public enum class JpegXlContainer {
    CODESTREAM,
    CONTAINER,
}

/** Dimensions extracted from the JPEG XL SizeHeader. */
public data class JpegXlFrameInfo(
    val width: Int,
    val height: Int,
)

/** Immutable range record for one top-level ISO-BMFF JPEG XL box. */
public class JpegXlBox internal constructor(
    public val type: String,
    public val offset: Long,
    internal val payloadOffset: Int,
    internal val payloadSize: Int,
)

/**
 * Immutable, bounded JPEG XL document containing a validated SizeHeader.
 *
 * Raw codestreams, an exact `JXL `/`ftyp`/`jxlc` envelope, and ordered
 * version-0 `jxlp` fragments may decode through the deliberately narrow direct
 * sRGB or grayscale Modular profile. Other JPEG XL image features return explicit
 * `kUnimplemented` results instead of manufacturing pixels or silently falling
 * through to another provider.
 */
public class JpegXlDocument private constructor(
    private val source: ByteArray,
    private val decodeSource: ByteArray,
    public val container: JpegXlContainer,
    public val frame: JpegXlFrameInfo,
    private val codestreamStart: Int,
    private val codestreamEndExclusive: Int,
    private val entropyOffset: Int,
    boxes: List<JpegXlBox>,
) {
    /** Top-level container boxes; empty for a naked JPEG XL codestream. */
    public val boxes: List<JpegXlBox> = Collections.unmodifiableList(ArrayList(boxes))

    /** Returns an independent copy of the original encoded data. */
    public fun copyEncodedBytes(): ByteArray = source.copyOf()

    /** Returns a defensive copy of a retained top-level box payload. */
    public fun copyPayload(box: JpegXlBox): ByteArray {
        require(boxes.contains(box)) { "box does not belong to this JPEG XL document" }
        return source.copyOfRange(box.payloadOffset, box.payloadOffset + box.payloadSize)
    }

    /** Decodes the deliberately narrow, proven JPEG XL Modular profile. */
    public fun decode(): JpegXlDecodeResult {
        if (container == JpegXlContainer.CONTAINER && !isNarrowPixelContainer()) {
            return JpegXlDecodeResult(
                bitmap = null,
                diagnostic = JpegXlDiagnostic(
                    code = "jpegxl.container.topology.unimplemented",
                    offset = codestreamStart.toLong(),
                    result = Codec.Result.kUnimplemented,
                ),
            )
        }
        return decodeNarrowJpegXlModular(
            source = decodeSource,
            codestreamStart = codestreamStart,
            codestreamEndExclusive = codestreamEndExclusive,
            frame = frame,
            fallbackOffset = entropyOffset,
        )
    }

    private fun isNarrowPixelContainer(): Boolean {
        val types = boxes.map(JpegXlBox::type)
        return types == listOf("JXL ", "ftyp", "jxlc") ||
            (types.size >= 3 && types.take(2) == listOf("JXL ", "ftyp") && types.drop(2).all { it == "jxlp" })
    }

    public companion object {
        /** Opens a JPEG XL codestream or ISO-BMFF container after validating its SizeHeader. */
        public fun open(data: ByteArray, limits: JpegXlLimits = JpegXlLimits()): JpegXlOpenResult {
            if (data.size.toLong() > limits.maxEncodedBytes) {
                return JpegXlOpenResult(
                    document = null,
                    diagnostic = JpegXlDiagnostic(
                        code = "jpegxl.limit.encoded-bytes",
                        offset = limits.maxEncodedBytes,
                        result = Codec.Result.kOutOfMemory,
                    ),
                )
            }
            return try {
                val source = data.copyOf()
                val parsed = when {
                    isRawCodestream(source) -> parseRawCodestream(source, limits).let { raw ->
                        ParsedDocument(
                            decodeSource = null,
                            container = JpegXlContainer.CODESTREAM,
                            frame = raw.frame,
                            codestreamStart = raw.codestreamStart,
                            codestreamEndExclusive = raw.codestreamEndExclusive,
                            entropyOffset = raw.entropyOffset,
                            boxes = emptyList(),
                        )
                    }
                    isContainerSignature(source) -> parseContainer(source, limits)
                    else -> jxlFailure("jpegxl.signature.missing", 0)
                }
                JpegXlOpenResult(
                    document = JpegXlDocument(
                        source = source,
                        decodeSource = parsed.decodeSource ?: source,
                        container = parsed.container,
                        frame = parsed.frame,
                        codestreamStart = parsed.codestreamStart,
                        codestreamEndExclusive = parsed.codestreamEndExclusive,
                        entropyOffset = parsed.entropyOffset,
                        boxes = parsed.boxes,
                    ),
                    diagnostic = null,
                )
            } catch (failure: JpegXlFailure) {
                JpegXlOpenResult(null, failure.diagnostic)
            }
        }

        /** Ownership predicate for raw JPEG XL and the ISO-BMFF signature box. */
        internal fun looksLikeJpegXl(data: ByteArray): Boolean =
            isRawCodestream(data) || isContainerSignature(data)
    }
}

private data class ParsedCodestream(
    val frame: JpegXlFrameInfo,
    val codestreamStart: Int,
    val codestreamEndExclusive: Int,
    val entropyOffset: Int,
)

private data class ParsedDocument(
    val decodeSource: ByteArray?,
    val container: JpegXlContainer,
    val frame: JpegXlFrameInfo,
    val codestreamStart: Int,
    val codestreamEndExclusive: Int,
    val entropyOffset: Int,
    val boxes: List<JpegXlBox>,
)

private class JpegXlFailure(
    val diagnostic: JpegXlDiagnostic,
) : RuntimeException(diagnostic.code)

private fun jxlFailure(
    code: String,
    offset: Int,
    result: Codec.Result = Codec.Result.kErrorInInput,
): Nothing = throw JpegXlFailure(JpegXlDiagnostic(code, offset.toLong(), result))

private fun parseRawCodestream(
    data: ByteArray,
    limits: JpegXlLimits,
    start: Int = 0,
    endExclusive: Int = data.size,
): ParsedCodestream {
    if (!isRawCodestream(data, start, endExclusive)) jxlFailure("jpegxl.signature.missing", start)
    val reader = JpegXlBitReader(data, start = start + RAW_SIGNATURE_SIZE, endExclusive = endExclusive)
    val frame = try {
        readSizeHeader(reader)
    } catch (_: JpegXlTruncated) {
        jxlFailure("jpegxl.header.truncated", reader.byteOffset)
    }
    if (
        frame.width !in 1..limits.maxWidth || frame.height !in 1..limits.maxHeight ||
        frame.width.toLong() * frame.height.toLong() > limits.maxPixels
    ) {
        jxlFailure("jpegxl.limit.pixels", reader.byteOffset, Codec.Result.kOutOfMemory)
    }
    return ParsedCodestream(frame, start, endExclusive, reader.byteOffsetCeil)
}

private fun parseContainer(data: ByteArray, limits: JpegXlLimits): ParsedDocument {
    val boxes = JpegXlBoxParser(data, limits.maxBoxes).parse()
    val signature = boxes.firstOrNull() ?: jxlFailure("jpegxl.container.signature.missing", 0)
    if (
        signature.type != "JXL " || signature.payloadSize != JXL_SIGNATURE_PAYLOAD.size ||
        !data.matchesBytes(signature.payloadOffset, JXL_SIGNATURE_PAYLOAD)
    ) {
        jxlFailure("jpegxl.container.signature.missing", signature.offset.toInt())
    }
    boxes.drop(1).firstOrNull { it.type == "JXL " }?.let { duplicate ->
        jxlFailure("jpegxl.container.signature.duplicate", duplicate.offset.toInt())
    }
    val ftyp = boxes.getOrNull(1) ?: jxlFailure("jpegxl.container.ftyp.missing", data.size)
    if (ftyp.type != "ftyp") jxlFailure("jpegxl.container.ftyp.missing", ftyp.offset.toInt())
    val fileTypeVersion = validateFileType(data, ftyp)
    boxes.drop(2).firstOrNull { it.type == "ftyp" }?.let { duplicate ->
        jxlFailure("jpegxl.container.ftyp.duplicate", duplicate.offset.toInt())
    }
    val codestreamBoxes = boxes.filter { it.type == "jxlc" }
    val partialCodestreamBoxes = boxes.filter { it.type == "jxlp" }
    if (codestreamBoxes.isNotEmpty() && partialCodestreamBoxes.isNotEmpty()) {
        jxlFailure("jpegxl.container.codestream.mixed", partialCodestreamBoxes.first().offset.toInt(), Codec.Result.kUnimplemented)
    }
    val decodedSource: ByteArray?
    val parsed: ParsedCodestream
    if (partialCodestreamBoxes.isNotEmpty()) {
        if (fileTypeVersion != 0) {
            jxlFailure("jpegxl.container.jxlp.version.unimplemented", ftyp.offset.toInt(), Codec.Result.kUnimplemented)
        }
        decodedSource = reassembleJxlp(data, partialCodestreamBoxes)
        parsed = parseRawCodestream(decodedSource, limits)
    } else {
        val codestream = when (codestreamBoxes.size) {
            0 -> jxlFailure("jpegxl.container.jxlc.missing", data.size)
            1 -> codestreamBoxes.single()
            else -> jxlFailure("jpegxl.container.jxlc.duplicate", codestreamBoxes[1].offset.toInt())
        }
        decodedSource = null
        parsed = parseRawCodestream(
            data,
            limits,
            start = codestream.payloadOffset,
            endExclusive = codestream.payloadOffset + codestream.payloadSize,
        )
    }
    return ParsedDocument(
        decodeSource = decodedSource,
        container = JpegXlContainer.CONTAINER,
        frame = parsed.frame,
        codestreamStart = parsed.codestreamStart,
        codestreamEndExclusive = parsed.codestreamEndExclusive,
        entropyOffset = parsed.entropyOffset,
        boxes = boxes,
    )
}

private fun validateFileType(data: ByteArray, box: JpegXlBox): Int {
    if (
        box.payloadSize < 12 || (box.payloadSize - FILE_TYPE_HEADER_SIZE) % COMPATIBLE_BRAND_SIZE != 0 ||
        data.ascii4(box.payloadOffset) != "jxl "
    ) {
        jxlFailure("jpegxl.container.ftyp.invalid", box.offset.toInt())
    }
    val version = data.u32(box.payloadOffset + 4)
    if (version > 1L) {
        jxlFailure("jpegxl.container.ftyp.version", box.offset.toInt(), Codec.Result.kUnimplemented)
    }
    return version.toInt()
}

private fun reassembleJxlp(data: ByteArray, boxes: List<JpegXlBox>): ByteArray {
    val fragments = HashMap<Int, JpegXlBox>()
    var lastIndex: Int? = null
    boxes.forEach { box ->
        if (box.payloadSize < 4) jxlFailure("jpegxl.container.jxlp.invalid", box.offset.toInt())
        val counter = data.u32(box.payloadOffset)
        val index = (counter and 0x7FFF_FFFFL).toInt()
        if (fragments.put(index, box) != null) jxlFailure("jpegxl.container.jxlp.duplicate", box.offset.toInt())
        if ((counter and 0x8000_0000L) != 0L) {
            if (lastIndex != null) jxlFailure("jpegxl.container.jxlp.last.duplicate", box.offset.toInt())
            lastIndex = index
        }
    }
    val terminal = lastIndex ?: jxlFailure("jpegxl.container.jxlp.last.missing", boxes.last().offset.toInt())
    if (fragments.size != terminal + 1 || (0..terminal).any { it !in fragments }) {
        jxlFailure("jpegxl.container.jxlp.sequence", boxes.first().offset.toInt())
    }
    if (boxes.map { (data.u32(it.payloadOffset) and 0x7FFF_FFFFL).toInt() } != (0..terminal).toList()) {
        jxlFailure("jpegxl.container.jxlp.order", boxes.first().offset.toInt(), Codec.Result.kUnimplemented)
    }
    val size = fragments.values.sumOf { it.payloadSize - 4 }
    return ByteArray(size).also { output ->
        var offset = 0
        for (index in 0..terminal) {
            val fragment = requireNotNull(fragments[index])
            val payloadOffset = fragment.payloadOffset + 4
            val payloadSize = fragment.payloadSize - 4
            data.copyInto(output, offset, payloadOffset, payloadOffset + payloadSize)
            offset += payloadSize
        }
    }
}

private class JpegXlBoxParser(
    private val data: ByteArray,
    private val maxBoxes: Int,
) {
    private var position: Int = 0

    fun parse(): List<JpegXlBox> {
        val boxes = ArrayList<JpegXlBox>()
        while (position < data.size) {
            if (boxes.size == maxBoxes) {
                jxlFailure("jpegxl.container.limit.boxes", position, Codec.Result.kOutOfMemory)
            }
            if (data.size - position < 8) jxlFailure("jpegxl.container.box.truncated", position)
            val offset = position
            var length = data.u32(position)
            val type = data.ascii4(position + 4)
            var headerSize = 8
            if (length == 1L) {
                if (data.size - position < 16) jxlFailure("jpegxl.container.box.truncated", position)
                length = data.u64(position + 8)
                headerSize = 16
            }
            if (length == 0L) length = (data.size - position).toLong()
            if (length < headerSize || length > (data.size - position).toLong()) {
                jxlFailure("jpegxl.container.box.truncated", offset)
            }
            val size = length.toInt()
            boxes += JpegXlBox(type, offset.toLong(), offset + headerSize, size - headerSize)
            position += size
        }
        return boxes
    }
}

private fun readSizeHeader(reader: JpegXlBitReader): JpegXlFrameInfo {
    val small = reader.readBits(1) != 0
    val height = if (small) (reader.readBits(5) + 1) * 8 else reader.readU32OffsetOne()
    val ratio = reader.readBits(3)
    val width = when {
        ratio != 0 -> fixedAspectWidth(height, ratio)
        small -> (reader.readBits(5) + 1) * 8
        else -> reader.readU32OffsetOne()
    }
    return JpegXlFrameInfo(width, height)
}

private fun fixedAspectWidth(height: Int, ratio: Int): Int {
    val (numerator, denominator) = when (ratio) {
        1 -> 1L to 1L
        2 -> 12L to 10L
        3 -> 4L to 3L
        4 -> 3L to 2L
        5 -> 16L to 9L
        6 -> 5L to 4L
        7 -> 2L to 1L
        else -> error("three-bit JPEG XL aspect ratio outside 0..7")
    }
    val width = height.toLong() * numerator / denominator
    if (width !in 1..Int.MAX_VALUE.toLong()) jxlFailure("jpegxl.header.dimensions", 0)
    return width.toInt()
}

private class JpegXlTruncated : RuntimeException()

/** Little-endian, least-significant-bit-first reader used by JPEG XL fields. */
private class JpegXlBitReader(
    private val data: ByteArray,
    start: Int,
    private val endExclusive: Int,
) {
    private var bitPosition: Int = start * 8

    val byteOffset: Int
        get() = bitPosition / 8

    val byteOffsetCeil: Int
        get() = (bitPosition + 7) / 8

    fun readBits(count: Int): Int {
        require(count in 0..30)
        var value = 0
        repeat(count) { index ->
            if (bitPosition / 8 >= endExclusive) throw JpegXlTruncated()
            value = value or (((data[bitPosition / 8].toInt() ushr (bitPosition and 7)) and 1) shl index)
            bitPosition++
        }
        return value
    }

    fun readU32OffsetOne(): Int {
        val selector = readBits(2)
        val bitCount = when (selector) {
            0 -> 9
            1 -> 13
            2 -> 18
            else -> 30
        }
        return readBits(bitCount) + 1
    }
}

private fun isRawCodestream(data: ByteArray): Boolean = isRawCodestream(data, 0, data.size)

private fun isRawCodestream(data: ByteArray, start: Int, endExclusive: Int): Boolean =
    start >= 0 && endExclusive <= data.size && endExclusive - start >= RAW_SIGNATURE_SIZE &&
        data[start].u8() == 0xFF && data[start + 1].u8() == 0x0A

private fun isContainerSignature(data: ByteArray): Boolean = data.size >= JXL_CONTAINER_SIGNATURE.size &&
    JXL_CONTAINER_SIGNATURE.indices.all { index -> data[index] == JXL_CONTAINER_SIGNATURE[index] }

private fun Byte.u8(): Int = toInt() and 0xFF

private fun ByteArray.u32(offset: Int): Long =
    (this[offset].u8().toLong() shl 24) or
        (this[offset + 1].u8().toLong() shl 16) or
        (this[offset + 2].u8().toLong() shl 8) or
        this[offset + 3].u8().toLong()

private fun ByteArray.u64(offset: Int): Long {
    val high = u32(offset)
    val low = u32(offset + 4)
    if (high > Int.MAX_VALUE.toLong()) jxlFailure("jpegxl.container.box.length", offset)
    return (high shl 32) or low
}

private fun ByteArray.ascii4(offset: Int): String = String(this, offset, 4, Charsets.ISO_8859_1)

private fun ByteArray.matchesBytes(offset: Int, expected: ByteArray): Boolean =
    offset >= 0 && offset <= size - expected.size && expected.indices.all { index -> this[offset + index] == expected[index] }

private const val RAW_SIGNATURE_SIZE: Int = 2
private const val MAX_BIT_READER_BYTES: Long = Int.MAX_VALUE.toLong() / 8L
private const val FILE_TYPE_HEADER_SIZE: Int = 8
private const val COMPATIBLE_BRAND_SIZE: Int = 4

private val JXL_CONTAINER_SIGNATURE: ByteArray = byteArrayOf(
    0x00, 0x00, 0x00, 0x0C,
    0x4A, 0x58, 0x4C, 0x20,
    0x0D, 0x0A, 0x87.toByte(), 0x0A,
)

private val JXL_SIGNATURE_PAYLOAD: ByteArray = byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A)
