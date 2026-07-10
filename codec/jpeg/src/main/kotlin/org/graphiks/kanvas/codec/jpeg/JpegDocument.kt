package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Collections

public data class JpegOpenResult(
    val document: JpegDocument?,
    val diagnostic: JpegDiagnostic?,
)

public data class JpegDecodeRequest(
    val colorType: SkColorType,
    val colorSpace: SkColorSpace?,
)

public data class JpegDecodeResult(
    val bitmap: SkBitmap?,
    val diagnostic: JpegDiagnostic?,
)

public class JpegDocument internal constructor(
    source: ByteArray,
    segments: List<JpegSegment>,
) {
    private val source: ByteArray = source

    /** Snapshot that rejects mutation even when callers cast it to [MutableList]. */
    public val segments: List<JpegSegment> = Collections.unmodifiableList(segments.toList())

    /** Returns a defensive copy of [segment]'s encoded payload. */
    public fun copyPayload(segment: JpegSegment): ByteArray {
        require(segments.any { it === segment }) { "JPEG segment does not belong to this document" }
        if (segment.range.isEmpty()) return ByteArray(0)
        val start = segment.range.first
        val endExclusive = segment.range.last + 1
        require(start >= 0 && endExclusive <= source.size) { "JPEG segment range is outside this document" }
        return source.copyOfRange(start, endExclusive)
    }

    public fun decode(request: JpegDecodeRequest): JpegDecodeResult =
        JpegCodec.Decoder.decode(source, request)

    internal fun makeCodec(): JpegCodec? = JpegCodec.Decoder.makeFromDocumentSource(source)

    public companion object {
        public fun open(
            data: ByteArray,
            limits: JpegLimits = JpegLimits.DEFAULT,
        ): JpegOpenResult = parseJpegDocument(data, limits)

        public fun open(
            stream: InputStream,
            limits: JpegLimits = JpegLimits.DEFAULT,
        ): JpegOpenResult {
            validateLimits(limits)?.let { return it }
            val input = readAtMost(stream, limits.maxEncodedBytes) ?: return encodedBytesLimit(0)
            return parseJpegDocument(input, limits, copySource = false)
        }
    }
}

internal fun parseJpegDocument(data: ByteArray, limits: JpegLimits): JpegOpenResult =
    parseJpegDocument(data, limits, copySource = true)

private fun parseJpegDocument(
    data: ByteArray,
    limits: JpegLimits,
    copySource: Boolean,
): JpegOpenResult {
    validateLimits(limits)?.let { return it }
    if (data.size.toLong() > limits.maxEncodedBytes) {
        return encodedBytesLimit(limits.maxEncodedBytes)
    }
    if (data.size < 2 || data[0] != MARKER_PREFIX || data[1] != MARKER_SOI.toByte()) {
        return invalidJpeg(0)
    }

    val source = if (copySource) data.copyOf() else data
    val segments = ArrayList<JpegSegment>()
    var scanCount = 0
    var offset = 2

    fun appendSegment(marker: Int, payloadStart: Int, payloadEnd: Int, markerOffset: Int): JpegOpenResult? {
        if (segments.size >= limits.maxSegments) return segmentLimit(markerOffset.toLong())
        segments += JpegSegment(
            marker = marker,
            offset = markerOffset.toLong(),
            range = payloadStart until payloadEnd,
        )
        return null
    }

    appendSegment(MARKER_SOI, 0, 0, 0)?.let { return it }
    while (offset < source.size) {
        if (source[offset] != MARKER_PREFIX) return invalidJpeg(offset.toLong())
        val markerOffset = offset
        while (offset < source.size && source[offset] == MARKER_PREFIX) offset++
        if (offset == source.size) return invalidJpeg(markerOffset.toLong())

        val marker = source[offset].toInt() and 0xFF
        if (marker == 0) return invalidJpeg(markerOffset.toLong())
        offset++
        when (marker) {
            MARKER_EOI -> {
                appendSegment(marker, offset, offset, markerOffset)?.let { return it }
                return JpegOpenResult(JpegDocument(source, segments.toList()), null)
            }

            MARKER_SOI, in MARKER_RST0..MARKER_RST7 -> return invalidJpeg(markerOffset.toLong())

            MARKER_TEM -> {
                appendSegment(marker, offset, offset, markerOffset)?.let { return it }
            }

            MARKER_SOS -> {
                val payloadEnd = segmentPayloadEnd(source, offset) ?: return invalidJpeg(markerOffset.toLong())
                val payloadStart = offset + 2
                if (!isValidStartOfScan(source, payloadStart, payloadEnd)) {
                    return invalidJpeg(markerOffset.toLong())
                }
                appendSegment(marker, payloadStart, payloadEnd, markerOffset)?.let { return it }
                scanCount++
                if (scanCount > limits.maxScans) return scanLimit(markerOffset.toLong())
                offset = payloadEnd

                while (offset < source.size) {
                    if (source[offset] != MARKER_PREFIX) {
                        offset++
                        continue
                    }
                    val entropyMarkerOffset = offset
                    while (offset < source.size && source[offset] == MARKER_PREFIX) offset++
                    if (offset == source.size) return invalidJpeg(entropyMarkerOffset.toLong())
                    val entropyMarker = source[offset].toInt() and 0xFF
                    when {
                        entropyMarker == 0 -> offset++
                        entropyMarker in MARKER_RST0..MARKER_RST7 -> {
                            appendSegment(entropyMarker, offset, offset, entropyMarkerOffset)?.let { return it }
                            offset++
                        }

                        else -> {
                            offset = entropyMarkerOffset
                            break
                        }
                    }
                }
                if (offset == source.size) return invalidJpeg(offset.toLong())
            }

            else -> {
                val payloadEnd = segmentPayloadEnd(source, offset) ?: return invalidJpeg(markerOffset.toLong())
                val payloadStart = offset + 2
                if (isStartOfFrame(marker)) {
                    val dimensions = validStartOfFrameDimensions(source, payloadStart, payloadEnd)
                        ?: return invalidJpeg(markerOffset.toLong())
                    val (width, height) = dimensions
                    if (width.toLong() * height.toLong() > limits.maxPixels) {
                        return pixelLimit(markerOffset.toLong())
                    }
                }
                appendSegment(marker, payloadStart, payloadEnd, markerOffset)?.let { return it }
                offset = payloadEnd
            }
        }
    }
    return invalidJpeg(source.size.toLong())
}

private fun readAtMost(stream: InputStream, maxEncodedBytes: Long): ByteArray? {
    if (maxEncodedBytes !in 0..MAX_ENCODED_BYTES_WITH_SENTINEL) return null
    val readLimit = maxEncodedBytes + 1
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_READ_BUFFER_SIZE)
    var readBytes = 0L
    while (readBytes < readLimit) {
        val requested = minOf(buffer.size.toLong(), readLimit - readBytes).toInt()
        val count = stream.read(buffer, 0, requested)
        if (count < 0) break
        if (count == 0) {
            val byte = stream.read()
            if (byte < 0) break
            output.write(byte)
            readBytes++
        } else {
            output.write(buffer, 0, count)
            readBytes += count.toLong()
        }
    }
    return output.toByteArray()
}

private fun segmentPayloadEnd(data: ByteArray, lengthOffset: Int): Int? {
    if (lengthOffset + 2 > data.size) return null
    val length = readU16(data, lengthOffset)
    if (length < 2) return null
    val payloadEnd = lengthOffset.toLong() + length.toLong()
    return payloadEnd.takeIf { it <= data.size.toLong() }?.toInt()
}

private fun isStartOfFrame(marker: Int): Boolean =
    marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

private fun isValidStartOfScan(data: ByteArray, start: Int, end: Int): Boolean {
    val payloadSize = end - start
    if (payloadSize < MIN_SOS_PAYLOAD_BYTES) return false
    val componentCount = data[start].toInt() and 0xFF
    return componentCount > 0 && payloadSize == SOS_FIXED_PAYLOAD_BYTES + componentCount * 2
}

private fun validStartOfFrameDimensions(data: ByteArray, start: Int, end: Int): Pair<Int, Int>? {
    val payloadSize = end - start
    if (payloadSize < MIN_SOF_PAYLOAD_BYTES) return null
    val precision = data[start].toInt() and 0xFF
    val height = readU16(data, start + 1)
    val width = readU16(data, start + 3)
    val componentCount = data[start + SOF_COMPONENT_COUNT_OFFSET].toInt() and 0xFF
    if (
        precision !in MIN_SAMPLE_PRECISION..MAX_SAMPLE_PRECISION ||
        width == 0 ||
        height == 0 ||
        componentCount == 0 ||
        payloadSize != SOF_FIXED_PAYLOAD_BYTES + componentCount * SOF_COMPONENT_BYTES
    ) {
        return null
    }

    val componentIds = HashSet<Int>(componentCount)
    var componentOffset = start + SOF_FIXED_PAYLOAD_BYTES
    repeat(componentCount) {
        val id = data[componentOffset].toInt() and 0xFF
        val sampling = data[componentOffset + 1].toInt() and 0xFF
        val quantizationTable = data[componentOffset + 2].toInt() and 0xFF
        if (
            !componentIds.add(id) ||
            (sampling ushr 4) !in MIN_SAMPLING_FACTOR..MAX_SAMPLING_FACTOR ||
            (sampling and 0x0F) !in MIN_SAMPLING_FACTOR..MAX_SAMPLING_FACTOR ||
            quantizationTable !in 0..MAX_QUANTIZATION_TABLE
        ) {
            return null
        }
        componentOffset += SOF_COMPONENT_BYTES
    }
    return width to height
}

private fun readU16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

private fun validateLimits(limits: JpegLimits): JpegOpenResult? = when {
    limits.maxEncodedBytes !in 0..MAX_MATERIALIZABLE_ENCODED_BYTES -> encodedBytesLimit(0)
    limits.maxPixels < 0 -> pixelLimit(0)
    limits.maxScans < 0 -> scanLimit(0)
    limits.maxSegments < 0 -> segmentLimit(0)
    else -> null
}

private fun invalidJpeg(offset: Long): JpegOpenResult =
    JpegOpenResult(null, JpegDiagnostic("jpeg.input.invalid", offset, Codec.Result.kErrorInInput))

private fun encodedBytesLimit(offset: Long): JpegOpenResult =
    JpegOpenResult(null, JpegDiagnostic("jpeg.limit.encoded-bytes", offset, Codec.Result.kInvalidInput))

private fun pixelLimit(offset: Long): JpegOpenResult =
    JpegOpenResult(null, JpegDiagnostic("jpeg.limit.pixels", offset, Codec.Result.kInvalidInput))

private fun scanLimit(offset: Long): JpegOpenResult =
    JpegOpenResult(null, JpegDiagnostic("jpeg.limit.scans", offset, Codec.Result.kInvalidInput))

private fun segmentLimit(offset: Long): JpegOpenResult =
    JpegOpenResult(null, JpegDiagnostic("jpeg.limit.segments", offset, Codec.Result.kInvalidInput))

private const val MARKER_PREFIX: Byte = 0xFF.toByte()
private const val MARKER_SOI: Int = 0xD8
private const val MARKER_EOI: Int = 0xD9
private const val MARKER_SOS: Int = 0xDA
private const val MARKER_TEM: Int = 0x01
private const val MARKER_RST0: Int = 0xD0
private const val MARKER_RST7: Int = 0xD7
private const val DEFAULT_READ_BUFFER_SIZE: Int = 8 * 1024
private const val MAX_MATERIALIZABLE_ENCODED_BYTES: Long = Int.MAX_VALUE.toLong() - 8L
private const val MAX_ENCODED_BYTES_WITH_SENTINEL: Long = MAX_MATERIALIZABLE_ENCODED_BYTES - 1L
private const val MIN_SOS_PAYLOAD_BYTES: Int = 6
private const val SOS_FIXED_PAYLOAD_BYTES: Int = 4
private const val MIN_SOF_PAYLOAD_BYTES: Int = 9
private const val SOF_FIXED_PAYLOAD_BYTES: Int = 6
private const val SOF_COMPONENT_COUNT_OFFSET: Int = 5
private const val SOF_COMPONENT_BYTES: Int = 3
private const val MIN_SAMPLE_PRECISION: Int = 2
private const val MAX_SAMPLE_PRECISION: Int = 16
private const val MIN_SAMPLING_FACTOR: Int = 1
private const val MAX_SAMPLING_FACTOR: Int = 4
private const val MAX_QUANTIZATION_TABLE: Int = 3
