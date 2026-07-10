package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import java.io.ByteArrayOutputStream
import java.io.InputStream

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
    internal val source: ByteArray,
    public val segments: List<JpegSegment>,
) {
    public fun decode(request: JpegDecodeRequest): JpegDecodeResult =
        JpegCodec.Decoder.decode(this, request)

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
            return parseJpegDocument(input, limits)
        }
    }
}

internal fun parseJpegDocument(data: ByteArray, limits: JpegLimits): JpegOpenResult {
    validateLimits(limits)?.let { return it }
    if (data.size.toLong() > limits.maxEncodedBytes) {
        return encodedBytesLimit(limits.maxEncodedBytes)
    }
    if (data.size < 2 || data[0] != MARKER_PREFIX || data[1] != MARKER_SOI.toByte()) {
        return invalidJpeg(0)
    }

    val source = data.copyOf()
    val segments = ArrayList<JpegSegment>()
    var scanCount = 0
    var offset = 2

    fun appendSegment(marker: Int, payloadStart: Int, payloadEnd: Int, markerOffset: Int): JpegOpenResult? {
        if (segments.size >= limits.maxSegments) return segmentLimit(markerOffset.toLong())
        segments += JpegSegment(
            marker = marker,
            payload = source.copyOfRange(payloadStart, payloadEnd),
            offset = markerOffset.toLong(),
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
                appendSegment(marker, offset + 2, payloadEnd, markerOffset)?.let { return it }
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
                    if (payloadEnd - payloadStart < 5) return invalidJpeg(markerOffset.toLong())
                    val height = readU16(source, payloadStart + 1)
                    val width = readU16(source, payloadStart + 3)
                    if (width == 0 || height == 0) return invalidJpeg(markerOffset.toLong())
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
    if (maxEncodedBytes < 0) return null
    val readLimit = maxEncodedBytes.saturatedIncrement()
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

private fun Long.saturatedIncrement(): Long = if (this == Long.MAX_VALUE) this else this + 1

private fun segmentPayloadEnd(data: ByteArray, lengthOffset: Int): Int? {
    if (lengthOffset + 2 > data.size) return null
    val length = readU16(data, lengthOffset)
    if (length < 2) return null
    val payloadEnd = lengthOffset.toLong() + length.toLong()
    return payloadEnd.takeIf { it <= data.size.toLong() }?.toInt()
}

private fun isStartOfFrame(marker: Int): Boolean =
    marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

private fun readU16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

private fun validateLimits(limits: JpegLimits): JpegOpenResult? = when {
    limits.maxEncodedBytes < 0 -> encodedBytesLimit(0)
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
