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
 * baseline: 8-bit grayscale or RGB LOCO-I scans. RGB supports `ILV=0`
 * (non-interleaved R/G/B scans), `ILV=1` (line interleave), and `ILV=2`
 * (sample interleave), unit sampling, and either no `mrfx` APP8 marker or
 * `mrfx` transform zero. HP1/HP2/HP3 colour transforms are explicitly
 * refused; its [nearLossless] bound is validated before entropy allocation.
 */
public class JpegLsDocument private constructor(
    private val source: ByteArray,
    internal val frame: JpegLsFrame,
    internal val scans: List<JpegLsScan>,
    metadata: List<JpegLsMetadataSegment>,
) {
    public val width: Int get() = frame.width
    public val height: Int get() = frame.height
    public val nearLossless: Int get() = frame.nearLossless
    /** Number of image components in the validated SOF55 frame (one or three). */
    public val componentCount: Int get() = frame.components.size
    /** JPEG-LS SOS ILV value: `0` grayscale/non-interleaved, `1` line or `2` sample RGB interleave. */
    public val interleaveMode: Int get() = frame.interleaveMode
    public val metadataSegments: List<JpegLsMetadataSegment> =
        Collections.unmodifiableList(ArrayList(metadata))

    /** Returns a defensive copy of [segment]'s marker payload. */
    public fun copyPayload(segment: JpegLsMetadataSegment): ByteArray {
        require(metadataSegments.contains(segment)) { "segment does not belong to this JPEG-LS document" }
        return source.copyOfRange(segment.payloadOffset, segment.payloadOffset + segment.payloadSize)
    }

    /** Returns an independent copy of the original structurally preserved stream. */
    public fun copyEncodedBytes(): ByteArray = source.copyOf()

    /** Decode the supported LOCO-I scan sequence without a fallback. */
    public fun decode(): JpegLsDecodeResult {
        return try {
            val samples = JpegLsEntropy.decode(source, scans, frame)
            val bitmap = SkBitmap(width, height)
            val components = frame.components.size
            repeat(width * height) { index ->
                val base = index * components
                val red = samples[base]
                val green = if (components == 1) red else samples[base + 1]
                val blue = if (components == 1) red else samples[base + 2]
                bitmap.setPixel(
                    index % width,
                    index / width,
                    0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue,
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
                    JpegLsDocument(data.copyOf(), parsed.frame, parsed.scans, parsed.metadataSegments),
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
    val components: List<JpegLsComponent>,
    val nearLossless: Int,
    val parameters: JpegLsCodingParameters,
    val interleaveMode: Int,
)

/** A bounded entropy span and SOS contract, retained in image scan order. */
internal data class JpegLsScan(
    val markerOffset: Int,
    val componentIds: List<Int>,
    val nearLossless: Int,
    val interleaveMode: Int,
    val entropyOffset: Int,
    val entropyEnd: Int,
)

/** SOF55 component identity retained to keep SOS routing strict. */
internal data class JpegLsComponent(val id: Int)

/** Effective validated JPEG-LS coding parameters for the supported `MAXVAL=255` profile. */
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
            return JpegLsCodingParameters(maximumSampleValue, threshold1, threshold2, threshold3, 64)
        }

        const val maximumSampleValue: Int = 255
        const val maximumNearLossless: Int = maximumSampleValue / 2

        private fun defaultThreshold(candidate: Int, lowerBound: Int): Int =
            if (candidate !in lowerBound..maximumSampleValue) lowerBound else candidate
    }
}

internal data class ParsedJpegLs(
    val frame: JpegLsFrame,
    val scans: List<JpegLsScan>,
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
    private var frameLayout: FrameLayout? = null
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
                SOS -> return parseScanSequence(markerOffset)
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
        if (segment.payloadSize != 6 + components * 3 || precision != 8 || components !in setOf(1, 3)) {
            jpeglsFailure("jpeg-ls.frame.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        if (width <= 0 || height <= 0) jpeglsFailure("jpeg-ls.frame.dimensions", markerOffset)
        if (width > limits.maxWidth || height > limits.maxHeight || width.toLong() * height > limits.maxPixels) {
            jpeglsFailure("jpeg-ls.limit.pixels", markerOffset, Codec.Result.kOutOfMemory)
        }
        val frameComponents = ArrayList<JpegLsComponent>(components)
        repeat(components) { index ->
            val component = start + 6 + index * 3
            if (
                data[component].u8() != index + 1 || data[component + 1].u8() != 0x11 ||
                data[component + 2].u8() != 0
            ) {
                jpeglsFailure("jpeg-ls.frame.unsupported", markerOffset, Codec.Result.kUnimplemented)
            }
            frameComponents += JpegLsComponent(index + 1)
        }
        frameLayout = FrameLayout(width, height, frameComponents)
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
            markerOffset = markerOffset,
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
        if (
            marker == APP8 &&
            isMrfxColorTransform(segment) &&
            data[segment.payloadOffset + 4].u8() != 0
        ) {
            jpeglsFailure("jpeg-ls.color-transform.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        metadata += JpegLsMetadataSegment(marker, markerOffset.toLong(), segment.payloadOffset, segment.payloadSize)
    }

    private fun isMrfxColorTransform(segment: SegmentBounds): Boolean =
        segment.payloadSize >= 5 &&
            data[segment.payloadOffset].u8() == 'm'.code &&
            data[segment.payloadOffset + 1].u8() == 'r'.code &&
            data[segment.payloadOffset + 2].u8() == 'f'.code &&
            data[segment.payloadOffset + 3].u8() == 'x'.code

    private fun parseScanSequence(firstMarkerOffset: Int): ParsedJpegLs {
        val layout = frameLayout ?: jpeglsFailure("jpeg-ls.sof.missing", firstMarkerOffset)
        val scans = ArrayList<JpegLsScan>(layout.components.size)
        scans += parseSos(firstMarkerOffset)
        while (true) {
            val last = scans.lastIndex
            val entropyEnd = findEntropyEnd()
            scans[last] = scans[last].copy(entropyEnd = entropyEnd)
            position = entropyEnd
            val markerOffset = position
            when (val marker = readMarker()) {
                SOS -> scans += parseSos(markerOffset)
                EOI -> {
                    if (position != data.size) jpeglsFailure("jpeg-ls.trailing-data", position)
                    return parsedScans(layout, scans)
                }
                DRI -> jpeglsFailure("jpeg-ls.restart.unsupported", markerOffset, Codec.Result.kUnimplemented)
                else -> jpeglsFailure("jpeg-ls.marker.unsupported", markerOffset, Codec.Result.kUnimplemented)
            }
        }
    }

    private fun parseSos(markerOffset: Int): JpegLsScan {
        val layout = frameLayout ?: jpeglsFailure("jpeg-ls.sof.missing", markerOffset)
        val segment = segment(markerOffset, "jpeg-ls.sos.truncated")
        if (segment.payloadSize < 6) jpeglsFailure("jpeg-ls.sos.invalid", markerOffset)
        val start = segment.payloadOffset
        val componentCount = data[start].u8()
        if (componentCount !in 1..layout.components.size || segment.payloadSize != 4 + componentCount * 2) {
            jpeglsFailure("jpeg-ls.scan.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        val componentIds = ArrayList<Int>(componentCount)
        repeat(componentCount) { index ->
            val offset = start + 1 + index * 2
            val componentId = data[offset].u8()
            if (
                componentId !in layout.components.map(JpegLsComponent::id) ||
                componentId in componentIds ||
                data[offset + 1].u8() != 0
            ) {
                jpeglsFailure("jpeg-ls.scan.unsupported", markerOffset, Codec.Result.kUnimplemented)
            }
            componentIds += componentId
        }
        val parameterOffset = start + 1 + componentCount * 2
        val nearLossless = data[parameterOffset].u8()
        val interleaveMode = data[parameterOffset + 1].u8()
        if (
            (componentCount == 1 && interleaveMode != 0) ||
            (componentCount == layout.components.size && layout.components.size == 3 && interleaveMode !in 1..2) ||
            data[parameterOffset + 2].u8() != 0
        ) {
            jpeglsFailure("jpeg-ls.scan.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        if (componentCount == layout.components.size && interleaveMode == 2 && nearLossless != 0) {
            jpeglsFailure("jpeg-ls.scan.unsupported", markerOffset, Codec.Result.kUnimplemented)
        }
        if (nearLossless > JpegLsCodingParameters.maximumNearLossless) {
            jpeglsFailure("jpeg-ls.scan.near.invalid", markerOffset)
        }
        return JpegLsScan(markerOffset, componentIds, nearLossless, interleaveMode, position, -1)
    }

    private fun parsedScans(layout: FrameLayout, scans: List<JpegLsScan>): ParsedJpegLs {
        val first = scans.first()
        if (scans.any { it.nearLossless != first.nearLossless }) {
            jpeglsFailure("jpeg-ls.scan.unsupported", scans.first { it.nearLossless != first.nearLossless }.markerOffset, Codec.Result.kUnimplemented)
        }
        when (layout.components.size) {
            1 -> if (scans.size != 1 || scans.single().componentIds != listOf(1) || first.interleaveMode != 0) {
                jpeglsFailure("jpeg-ls.scan.unsupported", first.markerOffset, Codec.Result.kUnimplemented)
            }
            3 -> if (scans.size == 1) {
                if (first.componentIds != listOf(1, 2, 3) || first.interleaveMode !in 1..2) {
                    jpeglsFailure("jpeg-ls.scan.unsupported", first.markerOffset, Codec.Result.kUnimplemented)
                }
            } else if (
                scans.size != 3 ||
                scans.any { it.interleaveMode != 0 } ||
                scans.map { it.componentIds.singleOrNull() } != listOf(1, 2, 3) ||
                first.nearLossless != 0
            ) {
                jpeglsFailure("jpeg-ls.scan.unsupported", first.markerOffset, Codec.Result.kUnimplemented)
            }
            else -> error("validated JPEG-LS component count")
        }
        val parameters = effectiveParameters(first.nearLossless, first.markerOffset)
        return ParsedJpegLs(
            JpegLsFrame(layout.width, layout.height, layout.components, first.nearLossless, parameters, first.interleaveMode),
            scans.toList(),
            metadata.toList(),
        )
    }

    private fun findEntropyEnd(): Int {
        var cursor = position
        while (cursor < data.size) {
            if (data[cursor].u8() != MARKER_PREFIX) {
                cursor++
                continue
            }
            var marker = cursor + 1
            while (marker < data.size && data[marker].u8() == MARKER_PREFIX) marker++
            if (marker >= data.size) jpeglsFailure("jpeg-ls.entropy.truncated", data.size)
            // JPEG-LS bit-stuffs one leading zero bit after 0xFF. The rest of
            // that byte remains entropy payload, so every value below 0x80 is
            // data; marker codes have the high bit set.
            if (data[marker].u8() < 0x80) {
                cursor = marker + 1
            } else {
                return cursor
            }
        }
        jpeglsFailure("jpeg-ls.entropy.truncated", data.size)
    }

    private fun effectiveParameters(nearLossless: Int, markerOffset: Int): JpegLsCodingParameters {
        val raw = lse
        val lseOffset = raw?.markerOffset ?: markerOffset
        if (raw != null && raw.maximumSampleValue != 0 && raw.maximumSampleValue != 255) {
            jpeglsFailure("jpeg-ls.lse.unsupported", lseOffset, Codec.Result.kUnimplemented)
        }
        if (nearLossless > JpegLsCodingParameters.maximumNearLossless) {
            jpeglsFailure("jpeg-ls.scan.near.invalid", markerOffset)
        }
        val defaults = JpegLsCodingParameters.defaults(nearLossless)
        if (raw == null) return defaults
        val threshold1 = raw.threshold1.takeUnless { it == 0 } ?: defaults.threshold1
        val threshold2 = raw.threshold2.takeUnless { it == 0 } ?: defaults.threshold2
        val threshold3 = raw.threshold3.takeUnless { it == 0 } ?: defaults.threshold3
        val reset = raw.reset.takeUnless { it == 0 } ?: defaults.reset
        if (
            threshold1 !in (nearLossless + 1)..JpegLsCodingParameters.maximumSampleValue ||
            threshold2 !in threshold1..JpegLsCodingParameters.maximumSampleValue ||
            threshold3 !in threshold2..JpegLsCodingParameters.maximumSampleValue ||
            reset !in 3..JpegLsCodingParameters.maximumSampleValue
        ) {
            jpeglsFailure("jpeg-ls.lse.invalid", lseOffset)
        }
        return JpegLsCodingParameters(
            JpegLsCodingParameters.maximumSampleValue,
            threshold1,
            threshold2,
            threshold3,
            reset,
        )
    }

    private fun u16(offset: Int): Int = (data[offset].u8() shl 8) or data[offset + 1].u8()

    private data class SegmentBounds(val payloadOffset: Int, val payloadSize: Int)
    private data class FrameLayout(val width: Int, val height: Int, val components: List<JpegLsComponent>)
    private data class LseParameters(
        val markerOffset: Int,
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
internal const val APP8: Int = 0xE8
internal const val RESTART_MIN: Int = 0xD0
internal const val RESTART_MAX: Int = 0xD7
