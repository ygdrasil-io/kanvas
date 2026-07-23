package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkImageInfo
import org.skia.utils.PixmapUtils
import java.io.ByteArrayOutputStream
import java.util.Collections

/** Immutable DHP declaration for a hierarchical JPEG document. */
public class JpegHierarchyDefinition internal constructor(
    public val precision: Int,
    public val width: Int,
    public val height: Int,
    internal val componentLayouts: List<HierarchyComponentLayout>,
) {
    public val componentIds: List<Int> = Collections.unmodifiableList(componentLayouts.map(HierarchyComponentLayout::id))
}

/** Per-axis EXP expansion applied to the preceding frame before a differential frame. */
public data class JpegExpansion(
    val horizontal: Boolean,
    val vertical: Boolean,
) {
    internal companion object {
        val NONE: JpegExpansion = JpegExpansion(horizontal = false, vertical = false)
    }
}

/** Immutable frame relationship within a parsed DHP hierarchy. */
public class JpegHierarchyFrame internal constructor(
    public val index: Int,
    public val sofMarker: Int,
    public val width: Int,
    public val height: Int,
    componentIds: List<Int>,
    public val referenceFrameIndex: Int?,
    public val expansion: JpegExpansion,
) {
    public val componentIds: List<Int> = Collections.unmodifiableList(componentIds.toList())
}

/**
 * Immutable document-level DHP/EXP frame graph.
 *
 * A hierarchy is linear by definition: each differential frame references the
 * preceding reconstructed frame. Entropy state is intentionally not exposed;
 * it remains owned by the document decoder.
 */
public class JpegHierarchy internal constructor(
    public val definition: JpegHierarchyDefinition,
    frames: List<JpegHierarchyFrame>,
    parsedFrames: List<ParsedJpeg>,
) {
    public val frames: List<JpegHierarchyFrame> = Collections.unmodifiableList(frames.toList())
    internal val parsedFrames: List<ParsedJpeg> = Collections.unmodifiableList(parsedFrames.toList())
}

internal data class JpegHierarchyParseResult(
    val hierarchy: JpegHierarchy?,
    val diagnostic: JpegDiagnostic?,
)

/** Bounds temporary JPEG streams built while parsing each DHP frame. */
internal data class JpegHierarchyReparseBudget(
    val maxFrameCount: Int,
    val maxMaterializedBytes: Long,
)

/** Parses DHP/EXP relationships from the already container-validated marker list. */
internal fun parseJpegHierarchy(
    source: ByteArray,
    segments: List<JpegSegment>,
    metadata: JpegMetadata,
    reparseBudget: JpegHierarchyReparseBudget,
): JpegHierarchyParseResult {
    val dhp = segments.filter { it.marker == MARKER_DHP }
    if (dhp.isEmpty()) {
        // A standalone differential SOF retains 6c's stable reference-required refusal.
        // There is no DHP container here to validate as a hierarchy.
        return JpegHierarchyParseResult(null, null)
    }
    if (dhp.size != 1) return hierarchyFailure("jpeg.hierarchy.dhp.duplicate", dhp[1].offset)
    val declarationSegment = dhp.single()
    val declaration = parseDhp(source, declarationSegment) ?: return hierarchyFailure(
        "jpeg.hierarchy.dhp.invalid",
        declarationSegment.offset,
    )

    val firstSof = segments.indexOfFirst { JpegFrameSpec.fromSof(it.marker) != null }
    if (firstSof < 0) return hierarchyFailure("jpeg.hierarchy.frame.missing", declarationSegment.offset)
    val dhpIndex = segments.indexOf(declarationSegment)
    if (dhpIndex > firstSof) return hierarchyFailure("jpeg.hierarchy.dhp.order", declarationSegment.offset)

    val frames = ArrayList<JpegHierarchyFrame>()
    var pendingExpansion = JpegExpansion.NONE
    var hasPendingExpansion = false
    var sawInitial = false
    for (segment in segments.drop(dhpIndex + 1)) {
        when (segment.marker) {
            MARKER_DHP -> return hierarchyFailure("jpeg.hierarchy.dhp.duplicate", segment.offset)
            MARKER_EXP -> {
                if (!sawInitial) return hierarchyFailure("jpeg.hierarchy.exp.order", segment.offset)
                if (hasPendingExpansion) return hierarchyFailure("jpeg.hierarchy.exp.duplicate", segment.offset)
                pendingExpansion = parseExp(source, segment) ?: return hierarchyFailure(
                    "jpeg.hierarchy.exp.invalid",
                    segment.offset,
                )
                hasPendingExpansion = true
            }
            else -> {
                val spec = JpegFrameSpec.fromSof(segment.marker) ?: continue
                val frame = parseHierarchyFrame(source, segment, spec) ?: return hierarchyFailure(
                    "jpeg.hierarchy.frame.invalid",
                    segment.offset,
                )
                if (
                    frame.precision != declaration.precision ||
                    frame.componentLayouts != declaration.componentLayouts ||
                    frame.width > declaration.width ||
                    frame.height > declaration.height
                ) {
                    return hierarchyFailure("jpeg.hierarchy.frame.incompatible", segment.offset)
                }
                if (!sawInitial) {
                    if (spec.differential) return hierarchyFailure("jpeg.hierarchy.reference.missing", segment.offset)
                    if (pendingExpansion != JpegExpansion.NONE) return hierarchyFailure("jpeg.hierarchy.exp.order", segment.offset)
                    frames += JpegHierarchyFrame(
                        index = 0,
                        sofMarker = segment.marker,
                        width = frame.width,
                        height = frame.height,
                        componentIds = frame.componentIds,
                        referenceFrameIndex = null,
                        expansion = JpegExpansion.NONE,
                    )
                    sawInitial = true
                } else {
                    if (!spec.differential) return hierarchyFailure("jpeg.hierarchy.frame.order", segment.offset)
                    val reference = frames.last()
                    val expectedWidth = if (pendingExpansion.horizontal) reference.width * 2 else reference.width
                    val expectedHeight = if (pendingExpansion.vertical) reference.height * 2 else reference.height
                    if (frame.width != expectedWidth || frame.height != expectedHeight) {
                        return hierarchyFailure("jpeg.hierarchy.reference.geometry", segment.offset)
                    }
                    frames += JpegHierarchyFrame(
                        index = frames.size,
                        sofMarker = segment.marker,
                        width = frame.width,
                        height = frame.height,
                        componentIds = frame.componentIds,
                        referenceFrameIndex = reference.index,
                        expansion = pendingExpansion,
                    )
                }
                pendingExpansion = JpegExpansion.NONE
                hasPendingExpansion = false
            }
        }
    }
    if (!sawInitial) return hierarchyFailure("jpeg.hierarchy.frame.missing", declarationSegment.offset)
    if (hasPendingExpansion) return hierarchyFailure("jpeg.hierarchy.exp.dangling", declarationSegment.offset)
    if (frames.last().width != declaration.width || frames.last().height != declaration.height) {
        return hierarchyFailure("jpeg.hierarchy.final.geometry", declarationSegment.offset)
    }
    val reparsePlan = hierarchyFrameReparsePlan(segments)
        ?: return hierarchyFailure("jpeg.hierarchy.frame.parse", declarationSegment.offset)
    validateHierarchyReparseBudget(source, segments, reparsePlan, reparseBudget)?.let { diagnostic ->
        return JpegHierarchyParseResult(null, diagnostic)
    }
    val parsedFrames = parseHierarchyFrameStreams(source, segments, metadata, reparsePlan)
        ?: return hierarchyFailure("jpeg.hierarchy.frame.parse", declarationSegment.offset)
    if (parsedFrames.size != frames.size || parsedFrames.zip(frames).any { (parsed, frame) ->
            parsed.frameSpec.marker != frame.sofMarker || parsed.width != frame.width || parsed.height != frame.height
        }
    ) {
        return hierarchyFailure("jpeg.hierarchy.frame.parse", declarationSegment.offset)
    }
    return JpegHierarchyParseResult(JpegHierarchy(declaration, frames, parsedFrames), null)
}

/**
 * Re-materializes each hierarchy frame as a self-contained JPEG stream for the
 * existing pure Kotlin frame parser. Marker tables that precede the frame are
 * copied into its prefix; its own entropy bytes remain verbatim. This is a
 * parser boundary only: no external decoder and no non-differential fallback
 * is involved.
 */
private fun parseHierarchyFrameStreams(
    source: ByteArray,
    segments: List<JpegSegment>,
    metadata: JpegMetadata,
    reparsePlan: HierarchyFrameReparsePlan,
): List<ParsedJpeg>? {
    return reparsePlan.frameIndices.mapIndexed { position, segmentIndex ->
        val frameSegment = segments[segmentIndex]
        val nextFrameIndex = reparsePlan.frameIndices.getOrNull(position + 1) ?: reparsePlan.endOfImageIndex
        val endOffset = segments[nextFrameIndex].offset.toInt()
        if (endOffset <= frameSegment.offset || endOffset > source.size) return null
        val isolated = ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xD8)
            for (prefixSegment in segments.take(segmentIndex)) {
                if (prefixSegment.marker in TABLE_PREFIX_MARKERS) {
                    writeRawSegment(source, prefixSegment)
                }
            }
            write(source, frameSegment.offset.toInt(), endOffset - frameSegment.offset.toInt())
            write(0xFF)
            write(0xD9)
        }.toByteArray()
        parseJpeg(isolated, metadata) ?: return null
    }
}

private data class HierarchyFrameReparsePlan(
    val frameIndices: List<Int>,
    val endOfImageIndex: Int,
)

/**
 * Plans frame reparsing without copying any encoded bytes, so the hierarchy
 * budget can reject repeated table prefixes before they are materialized.
 */
private fun hierarchyFrameReparsePlan(segments: List<JpegSegment>): HierarchyFrameReparsePlan? {
    val frameIndices = segments.indices.filter { JpegFrameSpec.fromSof(segments[it].marker) != null }
    if (frameIndices.isEmpty()) return null
    val endOfImageIndex = segments.indexOfFirst { it.marker == MARKER_EOI }
    if (endOfImageIndex < 0) return null
    return HierarchyFrameReparsePlan(frameIndices, endOfImageIndex)
}

private fun validateHierarchyReparseBudget(
    source: ByteArray,
    segments: List<JpegSegment>,
    reparsePlan: HierarchyFrameReparsePlan,
    budget: JpegHierarchyReparseBudget,
): JpegDiagnostic? {
    val frameIndices = reparsePlan.frameIndices
    if (frameIndices.size > budget.maxFrameCount) {
        return hierarchyBudgetFailure(
            "jpeg.hierarchy.reparse.frames",
            segments[frameIndices[budget.maxFrameCount]].offset,
        )
    }

    var prefixBytes = 0L
    var materializedBytes = 0L
    var nextSegment = 0
    for ((position, frameIndex) in frameIndices.withIndex()) {
        while (nextSegment < frameIndex) {
            val prefixSegment = segments[nextSegment]
            if (prefixSegment.marker in TABLE_PREFIX_MARKERS) {
                prefixBytes += rawSegmentByteCount(source, prefixSegment)
                    ?: return hierarchyBudgetFailure("jpeg.hierarchy.frame.parse", prefixSegment.offset)
            }
            nextSegment++
        }
        val frameSegment = segments[frameIndex]
        val nextFrameIndex = frameIndices.getOrNull(position + 1) ?: reparsePlan.endOfImageIndex
        val endOffset = segments[nextFrameIndex].offset
        val frameBytes = endOffset - frameSegment.offset
        if (frameBytes <= 0 || endOffset > source.size.toLong()) {
            return hierarchyBudgetFailure("jpeg.hierarchy.frame.parse", frameSegment.offset)
        }
        val isolatedBytes = frameBytes + prefixBytes + SOI_AND_EOI_BYTES
        if (isolatedBytes > budget.maxMaterializedBytes - materializedBytes) {
            return hierarchyBudgetFailure("jpeg.hierarchy.reparse.bytes", frameSegment.offset)
        }
        materializedBytes += isolatedBytes
        nextSegment = frameIndex + 1
    }
    return null
}

private fun rawSegmentByteCount(source: ByteArray, segment: JpegSegment): Long? {
    val start = segment.offset
    val endExclusive = if (segment.range.isEmpty()) {
        start + JPEG_LENGTH_SEGMENT_BYTES
    } else {
        segment.range.last.toLong() + 1L
    }
    if (start < 0 || endExclusive !in start..source.size.toLong()) return null
    return endExclusive - start
}

private fun ByteArrayOutputStream.writeRawSegment(source: ByteArray, segment: JpegSegment) {
    val byteCount = rawSegmentByteCount(source, segment)?.toInt() ?: fail()
    write(source, segment.offset.toInt(), byteCount)
}

private fun parseDhp(source: ByteArray, segment: JpegSegment): JpegHierarchyDefinition? {
    val payload = source.copyPayload(segment)
    if (payload.size < HIERARCHY_FIXED_PAYLOAD_BYTES) return null
    val precision = payload[0].toInt() and 0xFF
    val height = readHierarchyU16(payload, 1)
    val width = readHierarchyU16(payload, 3)
    val componentCount = payload[5].toInt() and 0xFF
    if (
        precision !in 2..16 ||
        width == 0 ||
        height == 0 ||
        componentCount == 0 ||
        payload.size != HIERARCHY_FIXED_PAYLOAD_BYTES + componentCount * HIERARCHY_COMPONENT_BYTES
    ) {
        return null
    }
    val components = ArrayList<HierarchyComponentLayout>(componentCount)
    var offset = HIERARCHY_FIXED_PAYLOAD_BYTES
    repeat(componentCount) {
        val id = payload[offset].toInt() and 0xFF
        val sampling = payload[offset + 1].toInt() and 0xFF
        val quantization = payload[offset + 2].toInt() and 0xFF
        if (
            components.any { it.id == id } ||
            (sampling ushr 4) !in 1..4 ||
            (sampling and 0x0F) !in 1..4 ||
            quantization !in 0..3
        ) {
            return null
        }
        components += HierarchyComponentLayout(id, sampling ushr 4, sampling and 0x0F, quantization)
        offset += HIERARCHY_COMPONENT_BYTES
    }
    return JpegHierarchyDefinition(precision, width, height, components)
}

private fun parseHierarchyFrame(
    source: ByteArray,
    segment: JpegSegment,
    spec: JpegFrameSpec,
): ParsedHierarchyFrame? {
    val payload = source.copyPayload(segment)
    if (payload.size < HIERARCHY_FIXED_PAYLOAD_BYTES) return null
    val precision = payload[0].toInt() and 0xFF
    val height = readHierarchyU16(payload, 1)
    val width = readHierarchyU16(payload, 3)
    val componentCount = payload[5].toInt() and 0xFF
    val validPrecision = when (spec.sampleCoding) {
        JpegSampleCoding.LOSSLESS -> precision in 2..16
        else -> precision == 8 || precision == 12
    }
    if (
        !validPrecision ||
        width == 0 ||
        height == 0 ||
        componentCount == 0 ||
        payload.size != HIERARCHY_FIXED_PAYLOAD_BYTES + componentCount * HIERARCHY_COMPONENT_BYTES
    ) {
        return null
    }
    val components = ArrayList<HierarchyComponentLayout>(componentCount)
    var offset = HIERARCHY_FIXED_PAYLOAD_BYTES
    repeat(componentCount) {
        val id = payload[offset].toInt() and 0xFF
        val sampling = payload[offset + 1].toInt() and 0xFF
        val quantization = payload[offset + 2].toInt() and 0xFF
        if (
            components.any { it.id == id } ||
            (sampling ushr 4) !in 1..4 ||
            (sampling and 0x0F) !in 1..4 ||
            quantization !in 0..3
        ) {
            return null
        }
        components += HierarchyComponentLayout(id, sampling ushr 4, sampling and 0x0F, quantization)
        offset += HIERARCHY_COMPONENT_BYTES
    }
    return ParsedHierarchyFrame(precision, width, height, components)
}

private fun parseExp(source: ByteArray, segment: JpegSegment): JpegExpansion? {
    val payload = source.copyPayload(segment)
    if (payload.size != 1) return null
    val value = payload.single().toInt() and 0xFF
    val horizontal = value ushr 4
    val vertical = value and 0x0F
    if (horizontal !in 0..1 || vertical !in 0..1) return null
    return JpegExpansion(horizontal == 1, vertical == 1)
}

private fun ByteArray.copyPayload(segment: JpegSegment): ByteArray {
    if (segment.range.isEmpty()) return ByteArray(0)
    return copyOfRange(segment.range.first, segment.range.last + 1)
}

private fun readHierarchyU16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

private fun hierarchyFailure(code: String, offset: Long): JpegHierarchyParseResult =
    JpegHierarchyParseResult(null, JpegDiagnostic(code, offset, Codec.Result.kErrorInInput))

private fun hierarchyBudgetFailure(code: String, offset: Long): JpegDiagnostic =
    JpegDiagnostic(code, offset, Codec.Result.kErrorInInput)

internal data class HierarchyComponentLayout(
    val id: Int,
    val horizontalSampling: Int,
    val verticalSampling: Int,
    val quantizationTable: Int,
)

private data class ParsedHierarchyFrame(
    val precision: Int,
    val width: Int,
    val height: Int,
    val componentLayouts: List<HierarchyComponentLayout>,
)
private val ParsedHierarchyFrame.componentIds: List<Int>
    get() = componentLayouts.map(HierarchyComponentLayout::id)

private const val MARKER_DHP = 0xDE
private const val MARKER_EXP = 0xDF
private const val MARKER_EOI = 0xD9
private const val HIERARCHY_FIXED_PAYLOAD_BYTES = 6
private const val HIERARCHY_COMPONENT_BYTES = 3
private const val SOI_AND_EOI_BYTES = 4L
private const val JPEG_LENGTH_SEGMENT_BYTES = 4L
private val TABLE_PREFIX_MARKERS = setOf(0xDB, 0xC4, 0xCC, 0xDD)

internal class JpegHierarchyException(
    val diagnosticCode: String,
) : IllegalArgumentException(diagnosticCode)

private fun hierarchyDecodeFailure(code: String): Nothing = throw JpegHierarchyException(code)

/** Routes each validated differential SOF to its matching pure Kotlin entropy decoder. */
internal fun decodeJpegHierarchy(hierarchy: JpegHierarchy): DecodedJpegSamples {
    val parsed = hierarchy.parsedFrames
    if (parsed.size != hierarchy.frames.size || parsed.isEmpty()) hierarchyDecodeFailure("jpeg.hierarchy.frame.state")
    var reconstructed = decodeAbsoluteHierarchyFrame(parsed.first())
    for (index in 1 until parsed.size) {
        val frame = parsed[index]
        val relationship = hierarchy.frames[index]
        if (!frame.frameSpec.differential || relationship.referenceFrameIndex != index - 1) {
            hierarchyDecodeFailure("jpeg.hierarchy.reference.order")
        }
        val residual = decodeDifferentialHierarchyFrame(frame)
        val expanded = expandHierarchyReference(reconstructed, relationship.expansion)
        reconstructed = mergeHierarchyResidual(expanded, residual)
    }
    return reconstructed
}

private fun decodeAbsoluteHierarchyFrame(frame: ParsedJpeg): DecodedJpegSamples {
    if (frame.frameSpec.differential) hierarchyDecodeFailure("jpeg.hierarchy.reference.missing")
    return when (frame.coding) {
        JpegCoding.kBaseline -> when (frame.entropyCoding) {
            JpegEntropyCoding.HUFFMAN -> decodeSequentialDct(frame)
            JpegEntropyCoding.ARITHMETIC -> decodeArithmeticSequentialDct(frame)
        }
        JpegCoding.kProgressive -> when (frame.entropyCoding) {
            JpegEntropyCoding.HUFFMAN -> decodeProgressiveDct(frame)
            JpegEntropyCoding.ARITHMETIC -> decodeArithmeticProgressiveDct(frame)
        }
        JpegCoding.kLossless -> when (frame.entropyCoding) {
            JpegEntropyCoding.HUFFMAN -> decodeLossless(frame)
            // Preserve SOF11's existing explicit refusal. SOF15 has its own differential route below.
            JpegEntropyCoding.ARITHMETIC -> arithmeticFailure("jpeg.arithmetic.lossless.unsupported")
        }
    }
}

private fun decodeDifferentialHierarchyFrame(frame: ParsedJpeg): DecodedJpegSamples {
    if (!frame.frameSpec.differential) hierarchyDecodeFailure("jpeg.hierarchy.frame.order")
    return when (frame.frameSpec.marker) {
        0xC5 -> decodeDifferentialSequentialDct(frame)
        0xC6 -> decodeDifferentialProgressiveDct(frame)
        0xC7 -> decodeDifferentialLossless(frame)
        0xCD -> decodeDifferentialArithmeticSequentialDct(frame)
        0xCE -> decodeDifferentialArithmeticProgressiveDct(frame)
        0xCF -> decodeDifferentialArithmeticLossless(frame)
        else -> hierarchyDecodeFailure("jpeg.hierarchy.differential.unsupported")
    }
}

/** T.81's integer reference expansion: copy even samples and average adjacent odd samples. */
private fun expandHierarchyReference(samples: DecodedJpegSamples, expansion: JpegExpansion): DecodedJpegSamples {
    var width = samples.width
    var height = samples.height
    var planes = samples.planes.map { it.copyOf() }
    if (expansion.horizontal) {
        val expandedWidth = width * 2
        planes = planes.map { plane ->
            IntArray(expandedWidth * height).also { output ->
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val value = plane[y * width + x]
                        output[y * expandedWidth + x * 2] = value
                        output[y * expandedWidth + x * 2 + 1] =
                            (value + plane[y * width + (x + 1).coerceAtMost(width - 1)]) shr 1
                    }
                }
            }
        }
        width = expandedWidth
    }
    if (expansion.vertical) {
        val expandedHeight = height * 2
        planes = planes.map { plane ->
            IntArray(width * expandedHeight).also { output ->
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val value = plane[y * width + x]
                        output[y * 2 * width + x] = value
                        output[(y * 2 + 1) * width + x] =
                            (value + plane[(y + 1).coerceAtMost(height - 1) * width + x]) shr 1
                    }
                }
            }
        }
        height = expandedHeight
    }
    return DecodedJpegSamples(width, height, samples.precision, planes)
}

private fun mergeHierarchyResidual(reference: DecodedJpegSamples, residual: DecodedJpegSamples): DecodedJpegSamples {
    if (
        reference.width != residual.width ||
        reference.height != residual.height ||
        reference.precision != residual.precision ||
        reference.planes.size != residual.planes.size
    ) {
        hierarchyDecodeFailure("jpeg.hierarchy.reference.geometry")
    }
    val planes = reference.planes.indices.map { index ->
        val base = reference.planes[index]
        val delta = residual.planes[index]
        if (base.size != delta.size) hierarchyDecodeFailure("jpeg.hierarchy.reference.component")
        IntArray(base.size) { sample ->
            // The reference merger retains its signed, wider intermediate
            // result. Pixel normalization performs the public 0..max sample
            // saturation afterwards; rejecting here would turn a legal
            // one-unit IDCT rounding difference at an endpoint into an input
            // error before that normalization can happen.
            val reconstructed = base[sample].toLong() + delta[sample].toLong()
            if (reconstructed !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                hierarchyDecodeFailure("jpeg.hierarchy.sample.overflow")
            }
            reconstructed.toInt()
        }
    }
    return DecodedJpegSamples(reference.width, reference.height, reference.precision, planes)
}

internal fun decodeJpegHierarchy(
    document: JpegDocument,
    hierarchy: JpegHierarchy,
    request: JpegDecodeRequest,
): JpegDecodeResult {
    val source = hierarchy.parsedFrames.lastOrNull()
        ?: return JpegDecodeResult(null, JpegDiagnostic("jpeg.hierarchy.frame.missing", document.encodedSize, Codec.Result.kErrorInInput))
    if (request.colorType !in setOf(SkColorType.kRGBA_8888, SkColorType.kRGBA_F16Norm)) {
        return JpegDecodeResult(null, JpegDiagnostic("jpeg.decode.kInvalidConversion", document.encodedSize, Codec.Result.kInvalidConversion))
    }
    return try {
        val samples = decodeJpegHierarchy(hierarchy)
        val colorModel = source.colorModel()
        val info = SkImageInfo.Make(
            width = if (source.metadata.origin.swapsWidthHeight()) samples.height else samples.width,
            height = if (source.metadata.origin.swapsWidthHeight()) samples.width else samples.height,
            colorType = request.colorType,
            alphaType = source.metadata.origin.let { org.skia.foundation.SkAlphaType.kUnpremul },
            colorSpace = request.colorSpace ?: source.metadata.iccProfile?.let(org.skia.foundation.SkColorSpace::makeProfileAware)
                ?: org.skia.foundation.SkColorSpace.makeSRGB(),
        )
        val bitmap = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)
        val raw = if (source.metadata.origin == SkEncodedOrigin.kTopLeft) bitmap else {
            SkBitmap(samples.width, samples.height, info.colorSpace, info.colorType)
        }
        val write = writeHierarchyPixels(raw, samples, colorModel)
        if (write != Codec.Result.kSuccess) {
            return JpegDecodeResult(null, JpegDiagnostic("jpeg.decode.${write.name}", document.encodedSize, write))
        }
        if (raw !== bitmap && !PixmapUtils.Orient(bitmap, raw, source.metadata.origin)) {
            return JpegDecodeResult(null, JpegDiagnostic("jpeg.decode.kInvalidParameters", document.encodedSize, Codec.Result.kInvalidParameters))
        }
        JpegDecodeResult(bitmap, null)
    } catch (failure: JpegHierarchyException) {
        JpegDecodeResult(null, JpegDiagnostic(failure.diagnosticCode, document.encodedSize, Codec.Result.kErrorInInput))
    } catch (failure: ArithmeticJpegException) {
        JpegDecodeResult(null, JpegDiagnostic(failure.diagnosticCode, document.encodedSize, Codec.Result.kErrorInInput))
    } catch (failure: LosslessJpegException) {
        JpegDecodeResult(null, JpegDiagnostic(failure.diagnosticCode, document.encodedSize, Codec.Result.kErrorInInput))
    } catch (failure: ProgressiveJpegException) {
        JpegDecodeResult(null, JpegDiagnostic(failure.diagnosticCode, document.encodedSize, Codec.Result.kErrorInInput))
    } catch (_: IllegalArgumentException) {
        JpegDecodeResult(null, JpegDiagnostic("jpeg.hierarchy.entropy.invalid", document.encodedSize, Codec.Result.kErrorInInput))
    }
}

private fun writeHierarchyPixels(
    bitmap: SkBitmap,
    samples: DecodedJpegSamples,
    colorModel: JpegColorModel,
): Codec.Result = when (bitmap.colorType) {
    SkColorType.kRGBA_8888 -> {
        val pixels = composePixels(samples, colorModel)
        System.arraycopy(pixels, 0, bitmap.pixels8888, 0, pixels.size)
        Codec.Result.kSuccess
    }
    SkColorType.kRGBA_F16Norm -> {
        val pixels = composeF16Pixels(samples, colorModel)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val offset = (y * bitmap.width + x) * 4
                bitmap.setPixelF16(x, y, pixels[offset], pixels[offset + 1], pixels[offset + 2], pixels[offset + 3])
            }
        }
        Codec.Result.kSuccess
    }
    else -> Codec.Result.kInvalidConversion
}
