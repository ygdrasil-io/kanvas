package org.graphiks.kanvas.codec.jpegls

import java.util.Collections
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap

/** Resource ceilings applied while opening a JPEG-LS static image. */
public data class JpegLsLimits(
    val maxEncodedBytes: Long = 64L * 1024L * 1024L,
    val maxWidth: Int = 32_768,
    val maxHeight: Int = 32_768,
    val maxPixels: Long = 64L * 1024L * 1024L,
) {
    init {
        require(maxEncodedBytes >= 2) { "maxEncodedBytes must include an SOI marker" }
        require(maxWidth > 0)
        require(maxHeight > 0)
        require(maxPixels > 0)
    }
}

/** Stable refusal or malformed-input detail produced by the JPEG-LS owner. */
public data class JpegLsDiagnostic(
    val code: String,
    val offset: Long,
    val result: Codec.Result = Codec.Result.kErrorInInput,
)

/** Result of [JpegLsDocument.open], which never throws for encoded input. */
public data class JpegLsOpenResult(
    val document: JpegLsDocument?,
    val diagnostic: JpegLsDiagnostic?,
)

/**
 * A non-image marker payload retained byte-for-byte by a [JpegLsDocument].
 * Payload bytes stay owned by the document and are available through
 * [JpegLsDocument.copyPayload] only, so callers cannot mutate the source.
 */
public class JpegLsMetadataSegment internal constructor(
    val marker: Int,
    val offset: Long,
    internal val payloadOffset: Int,
    internal val payloadSize: Int,
)

/** Result of a document-level JPEG-LS decode. */
public data class JpegLsDecodeResult(
    val bitmap: SkBitmap?,
    val diagnostic: JpegLsDiagnostic?,
)

/**
 * Immutable, bounded JPEG-LS frame description for the supported static
 * baseline: a one-component, 8-bit LOCO-I scan. Its [nearLossless] bound is
 * validated against the supported `MAXVAL=255` profile before entropy allocation.
 */
public class JpegLsDocument private constructor(
    private val source: ByteArray,
    internal val frame: JpegLsFrame,
    internal val entropyOffset: Int,
    metadata: List<JpegLsMetadataSegment>,
) {
    public val width: Int get() = frame.width
    public val height: Int get() = frame.height
    public val nearLossless: Int get() = frame.nearLossless
    public val metadataSegments: List<JpegLsMetadataSegment> =
        Collections.unmodifiableList(ArrayList(metadata))

    /** Returns a defensive copy of [segment]'s marker payload. */
    public fun copyPayload(segment: JpegLsMetadataSegment): ByteArray {
        require(metadataSegments.contains(segment)) { "segment does not belong to this JPEG-LS document" }
        return source.copyOfRange(segment.payloadOffset, segment.payloadOffset + segment.payloadSize)
    }

    /** Returns an independent copy of the original structurally preserved stream. */
    public fun copyEncodedBytes(): ByteArray = source.copyOf()

    /** Decode the supported grayscale LOCO-I scan without fabricating a fallback. */
    public fun decode(): JpegLsDecodeResult {
        return try {
            val samples = JpegLsEntropy.decode(source, entropyOffset, frame)
            val bitmap = SkBitmap(width, height)
            samples.forEachIndexed { index, sample ->
                bitmap.setPixel(
                    index % width,
                    index / width,
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                )
            }
            JpegLsDecodeResult(bitmap, null)
        } catch (failure: JpegLsFailure) {
            JpegLsDecodeResult(null, failure.diagnostic)
        }
    }

    public companion object {
        /** Opens a bounded document and validates its marker/header contract. */
        public fun open(data: ByteArray, limits: JpegLsLimits = JpegLsLimits()): JpegLsOpenResult {
            if (data.size.toLong() > limits.maxEncodedBytes) {
                return JpegLsOpenResult(
                    null,
                    JpegLsDiagnostic("jpeg-ls.limit.encoded-bytes", limits.maxEncodedBytes, Codec.Result.kOutOfMemory),
                )
            }
            return try {
                val parsed = JpegLsParser(data, limits).parse()
                JpegLsOpenResult(
                    JpegLsDocument(data.copyOf(), parsed.frame, parsed.entropyOffset, parsed.metadataSegments),
                    null,
                )
            } catch (failure: JpegLsFailure) {
                JpegLsOpenResult(null, failure.diagnostic)
            }
        }

        /**
         * A bounded header-only predicate for dispatcher ownership. A malformed
         * SOF55 still belongs to JPEG-LS: [Codec.MakeFromData] will refuse it
         * deterministically instead of trying the classic JPEG provider.
         */
        internal fun looksLikeJpegLs(data: ByteArray): Boolean {
            if (data.size < 4 || data[0].u8() != SOI_HIGH || data[1].u8() != SOI_LOW) return false
            var position = 2
            while (position + 1 < data.size) {
                if (data[position].u8() != MARKER_PREFIX) return false
                while (position < data.size && data[position].u8() == MARKER_PREFIX) position++
                if (position >= data.size) return false
                val marker = data[position++].u8()
                if (marker == SOF55) return true
                if (marker == EOI || marker == SOS) return false
                if (marker in RESTART_MIN..RESTART_MAX || marker == SOI_LOW) return false
                if (position + 1 >= data.size) return false
                val length = (data[position].u8() shl 8) or data[position + 1].u8()
                if (length < 2 || position + length > data.size) return false
                position += length
            }
            return false
        }
    }
}

internal data class JpegLsFrame(
    val width: Int,
    val height: Int,
    val nearLossless: Int,
    val parameters: JpegLsCodingParameters,
)

/** Effective default JPEG-LS coding parameters for the supported `MAXVAL=255` profile. */
internal data class JpegLsCodingParameters(
    val maximumSampleValue: Int,
    val threshold1: Int,
    val threshold2: Int,
    val threshold3: Int,
    val reset: Int,
) {
    companion object {
        fun defaults(nearLossless: Int): JpegLsCodingParameters {
            require(nearLossless in 0..127)
            val threshold1 = defaultThreshold(3 + 3 * nearLossless, nearLossless + 1)
            val threshold2 = defaultThreshold(7 + 5 * nearLossless, threshold1)
            val threshold3 = defaultThreshold(21 + 7 * nearLossless, threshold2)
            return JpegLsCodingParameters(255, threshold1, threshold2, threshold3, 64)
        }

        const val maximumNearLossless: Int = 127

        private fun defaultThreshold(candidate: Int, lowerBound: Int): Int =
            if (candidate !in lowerBound..255) lowerBound else candidate
    }
}

internal data class ParsedJpegLs(
    val frame: JpegLsFrame,
    val entropyOffset: Int,
    val metadataSegments: List<JpegLsMetadataSegment>,
)

internal class JpegLsFailure(
    val diagnostic: JpegLsDiagnostic,
) : RuntimeException(diagnostic.code)

internal fun jpeglsFailure(code: String, offset: Int, result: Codec.Result = Codec.Result.kErrorInInput): Nothing =
    throw JpegLsFailure(JpegLsDiagnostic(code, offset.toLong(), result))

private class JpegLsParser(
    private val data: ByteArray,
    private val limits: JpegLsLimits,
) {
    private var position: Int = 0
    private var frameDimensions: FrameDimensions? = null
    private var sawSof: Boolean = false
    private var sawLse: Boolean = false
    private var lse: LseParameters? = null
    private val metadata = ArrayList<JpegLsMetadataSegment>()

    fun parse(): ParsedJpegLs {
        if (data.size < 2 || data[0].u8() != SOI_HIGH || data[1].u8() != SOI_LOW) {
            jpeglsFailure("jpeg-ls.soi.missing", 0)
        }
        position = 2
        while (position < data.size) {
            val markerOffset = position
            val marker = readMarker()
            when (marker) {
                SOF55 -> parseSof55(markerOffset)
                LSE -> parseLse(markerOffset)
                SOS -> return parseSos(markerOffset)
                EOI -> jpeglsFailure("jpeg-ls.sos.missing", markerOffset)
                DRI -> jpeglsFailure("jpeg-ls.restart.unsupported", markerOffset, Codec.Result.kUnimplemented)
                in APP_MIN..APP_MAX, COM -> retainMetadata(marker, markerOffset)
                else -> jpeglsFailure("jpeg-ls.marker.unsupported", markerOffset, Codec.Result.kUnimplemented)
            }
        }
        jpeglsFailure("jpeg-ls.sos.missing", data.size)
    }

    private fun readMarker(): Int {
        if (position >= data.size || data[position].u8() != MARKER_PREFIX) {
            jpeglsFailure("jpeg-ls.marker.expected", position)
        }
        while (position < data.size && data[position].u8() == MARKER_PREFIX) position++
        if (position >= data.size) jpeglsFailure("jpeg-ls.marker.truncated", data.size)
        return data[position++].u8()
    }

    private fun segment(markerOffset: Int, truncatedCode: String): SegmentBounds {
        if (position + 2 > data.size) jpeglsFailure(truncatedCode, markerOffset)
        val length = (data[position].u8() shl 8) or data[position + 1].u8()
        if (length < 2 || position + length > data.size) jpeglsFailure(truncatedCode, markerOffset)
        val payloadOffset = position + 2
        position += length
        return SegmentBounds(payloadOffset, length - 2)
    }

    private fun parseSof55(markerOffset: Int) {
        if (sawSof) jpeglsFailure("jpeg-ls.sof.duplicate", markerOffset)
        val segment = segment(markerOffset, "jpeg-ls.sof.truncated")
        if (segment.payloadSize < 9) jpeglsFailure("jpeg-ls.sof.invalid", markerOffset)
        val start = segment.payloadOffset
        val precision = data[start].u8()
        val height = u16(start + 1)
        val width = u16(start + 3)
        val components = data[start + 5].u8()
        if (segment.payloadSize != 6 + components * 3 || precision != 8 || components != 1) {
            jpeglsFailure("jpeg-ls.frame.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        if (width <= 0 || height <= 0) jpeglsFailure("jpeg-ls.frame.dimensions", markerOffset)
        if (width > limits.maxWidth || height > limits.maxHeight || width.toLong() * height > limits.maxPixels) {
            jpeglsFailure("jpeg-ls.limit.pixels", markerOffset, Codec.Result.kOutOfMemory)
        }
        val component = start + 6
        if (data[component].u8() != 1 || data[component + 1].u8() != 0x11 || data[component + 2].u8() != 0) {
            jpeglsFailure("jpeg-ls.frame.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        frameDimensions = FrameDimensions(width, height)
        sawSof = true
    }

    private fun parseLse(markerOffset: Int) {
        if (sawLse) jpeglsFailure("jpeg-ls.lse.duplicate", markerOffset)
        val segment = segment(markerOffset, "jpeg-ls.lse.truncated")
        if (segment.payloadSize != 11 || data[segment.payloadOffset].u8() != 1) {
            jpeglsFailure("jpeg-ls.lse.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        val start = segment.payloadOffset
        lse = LseParameters(
            maximumSampleValue = u16(start + 1),
            threshold1 = u16(start + 3),
            threshold2 = u16(start + 5),
            threshold3 = u16(start + 7),
            reset = u16(start + 9),
        )
        sawLse = true
    }

    private fun retainMetadata(marker: Int, markerOffset: Int) {
        val segment = segment(markerOffset, "jpeg-ls.metadata.truncated")
        metadata += JpegLsMetadataSegment(marker, markerOffset.toLong(), segment.payloadOffset, segment.payloadSize)
    }

    private fun parseSos(markerOffset: Int): ParsedJpegLs {
        val dimensions = frameDimensions ?: jpeglsFailure("jpeg-ls.sof.missing", markerOffset)
        val segment = segment(markerOffset, "jpeg-ls.sos.truncated")
        if (segment.payloadSize != 6) jpeglsFailure("jpeg-ls.sos.invalid", markerOffset)
        val start = segment.payloadOffset
        if (
            data[start].u8() != 1 || data[start + 1].u8() != 1 || data[start + 2].u8() != 0 ||
            data[start + 4].u8() != 0 || data[start + 5].u8() != 0
        ) {
            jpeglsFailure("jpeg-ls.scan.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        val nearLossless = data[start + 3].u8()
        val parameters = effectiveParameters(nearLossless, markerOffset)
        return ParsedJpegLs(
            JpegLsFrame(dimensions.width, dimensions.height, nearLossless, parameters),
            position,
            metadata.toList(),
        )
    }

    private fun effectiveParameters(nearLossless: Int, markerOffset: Int): JpegLsCodingParameters {
        val raw = lse
        if (raw != null && raw.maximumSampleValue != 0 && raw.maximumSampleValue != 255) {
            jpeglsFailure("jpeg-ls.lse.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        if (nearLossless > JpegLsCodingParameters.maximumNearLossless) {
            jpeglsFailure("jpeg-ls.scan.near.invalid", markerOffset)
        }
        val defaults = JpegLsCodingParameters.defaults(nearLossless)
        if (raw == null) return defaults
        if (
            (raw.threshold1 != 0 && raw.threshold1 != defaults.threshold1) ||
            (raw.threshold2 != 0 && raw.threshold2 != defaults.threshold2) ||
            (raw.threshold3 != 0 && raw.threshold3 != defaults.threshold3) ||
            (raw.reset != 0 && raw.reset != defaults.reset)
        ) {
            jpeglsFailure("jpeg-ls.lse.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        return defaults
    }

    private fun u16(offset: Int): Int = (data[offset].u8() shl 8) or data[offset + 1].u8()

    private data class SegmentBounds(val payloadOffset: Int, val payloadSize: Int)
    private data class FrameDimensions(val width: Int, val height: Int)
    private data class LseParameters(
        val maximumSampleValue: Int,
        val threshold1: Int,
        val threshold2: Int,
        val threshold3: Int,
        val reset: Int,
    )
}

internal fun Byte.u8(): Int = toInt() and 0xFF

internal const val MARKER_PREFIX: Int = 0xFF
internal const val SOI_HIGH: Int = 0xFF
internal const val SOI_LOW: Int = 0xD8
internal const val EOI: Int = 0xD9
internal const val SOS: Int = 0xDA
internal const val DRI: Int = 0xDD
internal const val COM: Int = 0xFE
internal const val SOF55: Int = 0xF7
internal const val LSE: Int = 0xF8
internal const val APP_MIN: Int = 0xE0
internal const val APP_MAX: Int = 0xEF
internal const val RESTART_MIN: Int = 0xD0
internal const val RESTART_MAX: Int = 0xD7
