package org.graphiks.kanvas.codec.jpeg

import java.io.ByteArrayOutputStream
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedOrigin

/** A geometric JPEG transform that may be executed without re-encoding pixels. */
public sealed interface JpegTransform {
    public data object Identity : JpegTransform
    public data class Crop(val x: Int, val y: Int, val width: Int, val height: Int) : JpegTransform
    public data object FlipHorizontal : JpegTransform
    public data object FlipVertical : JpegTransform
    public data object Rotate90 : JpegTransform
    public data object Rotate180 : JpegTransform
    public data object Rotate270 : JpegTransform
}

/** The encoded transform result, or its stable reason for refusal. */
public data class JpegTranscodeResult(
    val bytes: ByteArray?,
    val diagnostic: JpegDiagnostic?,
)

/**
 * JPEG coefficient-domain transformation for the intentionally small, proven
 * process subset: one interleaved SOF0/SOF1 Huffman DCT scan.  This never
 * invokes [JpegEncoder], so it cannot turn a geometric operation into a
 * lossy pixel round-trip.
 */
internal fun transcodeJpegDocument(
    document: JpegDocument,
    source: ByteArray,
    transform: JpegTransform,
    metadataPolicy: JpegMetadataPolicy,
): JpegTranscodeResult {
    if (metadataPolicy == JpegMetadataPolicy.ReplaceKnown) {
        return transformDiagnostic(document.encodedSize, "jpeg.transform.metadata-policy.unsupported")
    }
    if (document.hierarchy != null || document.hierarchyDiagnostic != null) {
        return transformDiagnostic(document.encodedSize, "jpeg.transform.hierarchy.unsupported")
    }

    val frameSegment = document.segments.firstOrNull { JpegFrameSpec.fromSof(it.marker) != null }
        ?: return transformInputDiagnostic(document.encodedSize)
    val frameSpec = JpegFrameSpec.fromSof(frameSegment.marker) ?: return transformInputDiagnostic(frameSegment.offset)
    if (
        frameSpec.sampleCoding != JpegSampleCoding.DCT_SEQUENTIAL ||
        frameSpec.entropyCoding != JpegEntropyCoding.HUFFMAN ||
        frameSpec.differential
    ) {
        return transformDiagnostic(frameSegment.offset, "jpeg.transform.process.unsupported")
    }
    if (document.metadata.origin != SkEncodedOrigin.kTopLeft) {
        return transformDiagnostic(frameSegment.offset, "jpeg.transform.orientation.unsupported")
    }
    if (document.segments.count { it.marker == TRANSFORM_MARKER_SOS } != 1) {
        return transformDiagnostic(frameSegment.offset, "jpeg.transform.scan.unsupported")
    }

    val frame = try {
        parseJpeg(source, document.metadata)
    } catch (_: IllegalArgumentException) {
        null
    } ?: return transformInputDiagnostic(frameSegment.offset)
    if (frame.scans.size != 1 || !isCompleteInterleavedSequentialScan(frame)) {
        return transformDiagnostic(frameSegment.offset, "jpeg.transform.scan.unsupported")
    }
    if (!fitsTransformCoefficientBudget(frame)) {
        return transformDiagnostic(frameSegment.offset, "jpeg.transform.limit.coefficients", Codec.Result.kOutOfMemory)
    }

    val geometry = transformGeometry(frame, transform)
        ?: return transformDiagnostic(frameSegment.offset, "jpeg.transform.not-mcu-aligned", Codec.Result.kInvalidParameters)
    val coefficients = try {
        decodeSequentialCoefficients(frame)
    } catch (_: IllegalArgumentException) {
        return transformDiagnostic(frameSegment.offset, "jpeg.transform.entropy.invalid", Codec.Result.kErrorInInput)
    }
    if (transform == JpegTransform.Identity && metadataPolicy == JpegMetadataPolicy.Preserve) {
        return JpegTranscodeResult(source.copyOf(), null)
    }
    val transformed = transformCoefficients(coefficients, transform, geometry)
    return try {
        JpegTranscodeResult(
            serializeTransformedJpeg(document, source, frame, transformed, geometry, metadataPolicy),
            null,
        )
    } catch (_: TransformOutputLimitException) {
        transformDiagnostic(frameSegment.offset, "jpeg.transform.limit.encoded-bytes", Codec.Result.kInvalidInput)
    } catch (_: IllegalArgumentException) {
        // Reordering can require a Huffman symbol absent from an unusually
        // restricted source DHT.  Refuse rather than silently substituting a
        // generated table or falling back to pixel encoding.
        transformDiagnostic(frameSegment.offset, "jpeg.transform.entropy.unrepresentable", Codec.Result.kUnimplemented)
    }
}

private fun isCompleteInterleavedSequentialScan(frame: ParsedJpeg): Boolean {
    val scan = frame.scans.single().scan
    if (scan.components.size != frame.components.size) return false
    if (scan.spectralStart != 0 || scan.spectralEnd != 63 || scan.successiveApprox != 0) return false
    return scan.components.map { it.frameIndex }.toSet() == frame.components.indices.toSet()
}

/**
 * Coefficient transforms retain their source grid while producing a second
 * transformed grid.  Bound the source representation before allocating it;
 * unlike pixel decode, a tiny entropy stream can declare an enormous MCU
 * geometry.  The cap is 64 MiB of coefficient values before array overhead.
 */
private fun fitsTransformCoefficientBudget(frame: ParsedJpeg): Boolean {
    val maxH = frame.components.maxOf { it.h }
    val maxV = frame.components.maxOf { it.v }
    val mcusWide = (frame.width + maxH * 8 - 1) / (maxH * 8)
    val mcusHigh = (frame.height + maxV * 8 - 1) / (maxV * 8)
    val blocks = mcusWide.toLong() * mcusHigh.toLong() * frame.components.sumOf { component ->
        component.h.toLong() * component.v.toLong()
    }
    return blocks <= MAX_TRANSFORM_COEFFICIENT_BLOCKS
}

private data class TransformGeometry(
    val width: Int,
    val height: Int,
    val mcusWide: Int,
    val mcusHigh: Int,
    val swapsSamplingAxes: Boolean,
)

private fun transformGeometry(frame: ParsedJpeg, transform: JpegTransform): TransformGeometry? {
    val maxH = frame.components.maxOf { it.h }
    val maxV = frame.components.maxOf { it.v }
    val mcuWidth = maxH * 8
    val mcuHeight = maxV * 8
    val sourceMcusWide = (frame.width + mcuWidth - 1) / mcuWidth
    val sourceMcusHigh = (frame.height + mcuHeight - 1) / mcuHeight

    fun sourceIsMcuAligned(): Boolean = frame.width % mcuWidth == 0 && frame.height % mcuHeight == 0
    return when (transform) {
        JpegTransform.Identity -> TransformGeometry(frame.width, frame.height, sourceMcusWide, sourceMcusHigh, false)
        is JpegTransform.Crop -> {
            if (
                transform.x < 0 || transform.y < 0 || transform.width <= 0 || transform.height <= 0 ||
                transform.x > frame.width - transform.width || transform.y > frame.height - transform.height ||
                transform.x % mcuWidth != 0 || transform.y % mcuHeight != 0 ||
                transform.width % mcuWidth != 0 || transform.height % mcuHeight != 0
            ) {
                null
            } else {
                TransformGeometry(
                    transform.width,
                    transform.height,
                    transform.width / mcuWidth,
                    transform.height / mcuHeight,
                    false,
                )
            }
        }
        JpegTransform.FlipHorizontal,
        JpegTransform.FlipVertical,
        JpegTransform.Rotate180 -> if (sourceIsMcuAligned()) {
            TransformGeometry(frame.width, frame.height, sourceMcusWide, sourceMcusHigh, false)
        } else {
            null
        }
        JpegTransform.Rotate90,
        JpegTransform.Rotate270 -> if (sourceIsMcuAligned()) {
            TransformGeometry(frame.height, frame.width, sourceMcusHigh, sourceMcusWide, true)
        } else {
            null
        }
    }
}

internal data class CoefficientImage(
    val mcusWide: Int,
    val mcusHigh: Int,
    val planes: List<CoefficientPlane>,
)

internal data class CoefficientPlane(
    val componentIndex: Int,
    val horizontalSampling: Int,
    val verticalSampling: Int,
    val blocksWide: Int,
    val blocksHigh: Int,
    val blocks: Array<IntArray>,
)

internal fun decodeSequentialCoefficients(frame: ParsedJpeg): CoefficientImage {
    val scan = frame.scans.single()
    val maxH = frame.components.maxOf { it.h }
    val maxV = frame.components.maxOf { it.v }
    val mcusWide = (frame.width + maxH * 8 - 1) / (maxH * 8)
    val mcusHigh = (frame.height + maxV * 8 - 1) / (maxV * 8)
    val planes = frame.components.map { component ->
        CoefficientPlane(
            componentIndex = component.frameIndex,
            horizontalSampling = component.h,
            verticalSampling = component.v,
            blocksWide = mcusWide * component.h,
            blocksHigh = mcusHigh * component.v,
            blocks = Array(mcusWide * component.h * mcusHigh * component.v) { IntArray(64) },
        )
    }
    val reader = EntropyBitReader(scan.data)
    val previousDc = IntArray(frame.components.size)
    var nextRestartMarker = 0
    var mcu = 0
    val totalMcus = mcusWide * mcusHigh

    for (mcuY in 0 until mcusHigh) {
        for (mcuX in 0 until mcusWide) {
            for (component in scan.scan.components) {
                val plane = planes[component.frameIndex]
                for (blockY in 0 until component.v) {
                    for (blockX in 0 until component.h) {
                        plane.blocks[(mcuY * component.v + blockY) * plane.blocksWide + mcuX * component.h + blockX] =
                            readQuantizedSequentialBlock(frame.precision, scan, component, reader, previousDc)
                    }
                }
            }
            mcu++
            if (scan.restartInterval > 0 && mcu % scan.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
            }
        }
    }
    reader.finish()
    return CoefficientImage(mcusWide, mcusHigh, planes)
}

private fun readQuantizedSequentialBlock(
    precision: Int,
    entropyScan: EntropyScan,
    component: Component,
    reader: EntropyBitReader,
    previousDc: IntArray,
): IntArray {
    val dcTable = entropyScan.dcTables.getOrNull(component.dcTable) ?: fail()
    val acTable = entropyScan.acTables.getOrNull(component.acTable) ?: fail()
    val coefficients = IntArray(64)
    val dcCategory = dcTable.decode(reader)
    if (dcCategory !in 0..if (precision == 8) 11 else 15) fail()
    previousDc[component.frameIndex] += receiveAndExtend(reader, dcCategory)
    coefficients[0] = previousDc[component.frameIndex]
    var coefficient = 1
    while (coefficient < 64) {
        val runAndSize = acTable.decode(reader)
        if (runAndSize == 0) break
        val run = runAndSize ushr 4
        val size = runAndSize and 0x0F
        if (size == 0) {
            if (run != 15 || coefficient + 16 > 64) fail()
            coefficient += 16
            continue
        }
        if (size > if (precision == 8) 10 else 14) fail()
        coefficient += run
        if (coefficient >= 64) fail()
        coefficients[JPEG_ZIGZAG[coefficient]] = receiveAndExtend(reader, size)
        coefficient++
    }
    return coefficients
}

private fun transformCoefficients(
    source: CoefficientImage,
    transform: JpegTransform,
    geometry: TransformGeometry,
): CoefficientImage = when (transform) {
    JpegTransform.Identity -> source
    is JpegTransform.Crop -> cropCoefficients(source, transform, geometry)
    JpegTransform.FlipHorizontal -> mapCoefficientBlocks(
        source, geometry, false,
        destination = { plane, x, y -> (plane.blocksWide - 1 - x) to y },
        transformBlock = ::flipBlockHorizontal,
    )
    JpegTransform.FlipVertical -> mapCoefficientBlocks(
        source, geometry, false,
        destination = { plane, x, y -> x to (plane.blocksHigh - 1 - y) },
        transformBlock = ::flipBlockVertical,
    )
    JpegTransform.Rotate180 -> mapCoefficientBlocks(
        source, geometry, false,
        destination = { plane, x, y -> (plane.blocksWide - 1 - x) to (plane.blocksHigh - 1 - y) },
        transformBlock = ::rotateBlock180,
    )
    JpegTransform.Rotate90 -> mapCoefficientBlocks(
        source, geometry, true,
        destination = { plane, x, y -> (plane.blocksHigh - 1 - y) to x },
        transformBlock = ::rotateBlock90,
    )
    JpegTransform.Rotate270 -> mapCoefficientBlocks(
        source, geometry, true,
        destination = { plane, x, y -> y to (plane.blocksWide - 1 - x) },
        transformBlock = ::rotateBlock270,
    )
}

private fun cropCoefficients(
    source: CoefficientImage,
    crop: JpegTransform.Crop,
    geometry: TransformGeometry,
): CoefficientImage {
    val maxH = source.planes.maxOf { it.horizontalSampling }
    val maxV = source.planes.maxOf { it.verticalSampling }
    val sourceMcuWidth = maxH * 8
    val sourceMcuHeight = maxV * 8
    return CoefficientImage(
        geometry.mcusWide,
        geometry.mcusHigh,
        source.planes.map { plane ->
            val startX = crop.x / sourceMcuWidth * plane.horizontalSampling
            val startY = crop.y / sourceMcuHeight * plane.verticalSampling
            val blocksWide = geometry.mcusWide * plane.horizontalSampling
            val blocksHigh = geometry.mcusHigh * plane.verticalSampling
            CoefficientPlane(
                plane.componentIndex,
                plane.horizontalSampling,
                plane.verticalSampling,
                blocksWide,
                blocksHigh,
                Array(blocksWide * blocksHigh) { index ->
                    val x = index % blocksWide
                    val y = index / blocksWide
                    plane.blocks[(startY + y) * plane.blocksWide + startX + x].copyOf()
                },
            )
        },
    )
}

private fun mapCoefficientBlocks(
    source: CoefficientImage,
    geometry: TransformGeometry,
    swapSamplingAxes: Boolean,
    destination: (CoefficientPlane, Int, Int) -> Pair<Int, Int>,
    transformBlock: (IntArray) -> IntArray,
): CoefficientImage = CoefficientImage(
    geometry.mcusWide,
    geometry.mcusHigh,
    source.planes.map { plane ->
        val blocksWide = if (swapSamplingAxes) plane.blocksHigh else plane.blocksWide
        val blocksHigh = if (swapSamplingAxes) plane.blocksWide else plane.blocksHigh
        val horizontalSampling = if (swapSamplingAxes) plane.verticalSampling else plane.horizontalSampling
        val verticalSampling = if (swapSamplingAxes) plane.horizontalSampling else plane.verticalSampling
        val blocks = Array(blocksWide * blocksHigh) { IntArray(64) }
        for (y in 0 until plane.blocksHigh) for (x in 0 until plane.blocksWide) {
            val (targetX, targetY) = destination(plane, x, y)
            blocks[targetY * blocksWide + targetX] = transformBlock(plane.blocks[y * plane.blocksWide + x])
        }
        CoefficientPlane(plane.componentIndex, horizontalSampling, verticalSampling, blocksWide, blocksHigh, blocks)
    },
)

private fun flipBlockHorizontal(source: IntArray): IntArray = IntArray(64) { index ->
    val x = index and 7
    if ((x and 1) == 0) source[index] else -source[index]
}

private fun flipBlockVertical(source: IntArray): IntArray = IntArray(64) { index ->
    val y = index ushr 3
    if ((y and 1) == 0) source[index] else -source[index]
}

private fun rotateBlock180(source: IntArray): IntArray = IntArray(64) { index ->
    val x = index and 7
    val y = index ushr 3
    if (((x + y) and 1) == 0) source[index] else -source[index]
}

private fun rotateBlock90(source: IntArray): IntArray = IntArray(64) { index ->
    val x = index and 7
    val y = index ushr 3
    val coefficient = source[x * 8 + y]
    if ((x and 1) == 0) coefficient else -coefficient
}

private fun rotateBlock270(source: IntArray): IntArray = IntArray(64) { index ->
    val x = index and 7
    val y = index ushr 3
    val coefficient = source[x * 8 + y]
    if ((y and 1) == 0) coefficient else -coefficient
}

private fun serializeTransformedJpeg(
    document: JpegDocument,
    source: ByteArray,
    frame: ParsedJpeg,
    coefficients: CoefficientImage,
    geometry: TransformGeometry,
    metadataPolicy: JpegMetadataPolicy,
): ByteArray {
    val entropy = writeSequentialCoefficients(frame, coefficients, document.transcodeEncodedByteLimit)
    val out = BoundedTransformOutput(document.transcodeEncodedByteLimit, source.size)
    var entropyWritten = false
    val usedQuantizationTables = frame.components.map { it.quantTable }.toSet()
    for (segment in document.segments) {
        when {
            segment.marker in TRANSFORM_MARKER_RST0..TRANSFORM_MARKER_RST7 -> Unit
            segment.marker == frame.frameSpec.marker -> writeTransformedSof(out, document.copyPayload(segment), segment.marker, geometry)
            segment.marker == TRANSFORM_MARKER_DQT && !entropyWritten && geometry.swapsSamplingAxes ->
                writeTransposedDqt(out, document.copyPayload(segment), segment.marker, usedQuantizationTables)
            segment.marker == TRANSFORM_MARKER_SOS -> {
                writeRawSegment(out, source, segment)
                out.write(entropy)
                entropyWritten = true
            }
            metadataPolicy == JpegMetadataPolicy.StripAll && (segment.marker in 0xE0..0xEF || segment.marker == 0xFE) -> Unit
            else -> writeRawSegment(out, source, segment)
        }
    }
    if (!entropyWritten) fail()
    return out.toByteArray()
}

private fun writeTransformedSof(
    out: ByteArrayOutputStream,
    originalPayload: ByteArray,
    marker: Int,
    geometry: TransformGeometry,
) {
    if (originalPayload.size < 8) fail()
    val payload = originalPayload.copyOf()
    payload[1] = (geometry.height ushr 8).toByte()
    payload[2] = geometry.height.toByte()
    payload[3] = (geometry.width ushr 8).toByte()
    payload[4] = geometry.width.toByte()
    val componentCount = payload[5].toInt() and 0xFF
    if (payload.size != 6 + componentCount * 3) fail()
    if (geometry.swapsSamplingAxes) {
        for (component in 0 until componentCount) {
            val samplingOffset = 7 + component * 3
            val sampling = payload[samplingOffset].toInt() and 0xFF
            payload[samplingOffset] = ((sampling shl 4) or (sampling ushr 4)).toByte()
        }
    }
    writeSegment(out, marker, payload)
}

/**
 * DQT values are serialized in zigzag order, while coefficient transforms use
 * natural [v, u] order. A quarter turn therefore needs Q'[v,u] = Q[u,v].
 * Only tables selected by the frame's components are changed; unrelated DQT
 * payloads remain raw through the general segment-preservation path.
 */
private fun writeTransposedDqt(
    out: ByteArrayOutputStream,
    originalPayload: ByteArray,
    marker: Int,
    usedTables: Set<Int>,
) {
    val payload = originalPayload.copyOf()
    var offset = 0
    while (offset < payload.size) {
        val spec = payload[offset++].toInt() and 0xFF
        val precision = spec ushr 4
        val tableId = spec and 0x0F
        val bytesPerValue = when (precision) {
            0 -> 1
            1 -> 2
            else -> fail()
        }
        val valuesOffset = offset
        if (offset + 64 * bytesPerValue > payload.size) fail()
        if (tableId in usedTables) {
            val natural = IntArray(64)
            for (zigZagIndex in 0 until 64) {
                val valueOffset = valuesOffset + zigZagIndex * bytesPerValue
                natural[JPEG_ZIGZAG[zigZagIndex]] = if (bytesPerValue == 1) {
                    payload[valueOffset].toInt() and 0xFF
                } else {
                    ((payload[valueOffset].toInt() and 0xFF) shl 8) or (payload[valueOffset + 1].toInt() and 0xFF)
                }
            }
            for (zigZagIndex in 0 until 64) {
                val destination = JPEG_ZIGZAG[zigZagIndex]
                val u = destination and 7
                val v = destination ushr 3
                val value = natural[u * 8 + v]
                val valueOffset = valuesOffset + zigZagIndex * bytesPerValue
                if (bytesPerValue == 1) {
                    payload[valueOffset] = value.toByte()
                } else {
                    payload[valueOffset] = (value ushr 8).toByte()
                    payload[valueOffset + 1] = value.toByte()
                }
            }
        }
        offset += 64 * bytesPerValue
    }
    if (offset != payload.size) fail()
    writeSegment(out, marker, payload)
}

private fun writeSegment(out: ByteArrayOutputStream, marker: Int, payload: ByteArray) {
    out.write(0xFF)
    out.write(marker)
    out.write((payload.size + 2) ushr 8)
    out.write((payload.size + 2) and 0xFF)
    out.write(payload)
}

private fun writeRawSegment(out: ByteArrayOutputStream, source: ByteArray, segment: JpegSegment) {
    val start = segment.offset.toInt()
    val endExclusive = if (segment.range.isEmpty()) start + 2 else segment.range.last + 1
    if (start < 0 || endExclusive !in start..source.size) fail()
    out.write(source, start, endExclusive - start)
}

private fun writeSequentialCoefficients(
    frame: ParsedJpeg,
    coefficients: CoefficientImage,
    maxEncodedBytes: Long,
): ByteArray {
    val scan = frame.scans.single()
    val out = BoundedTransformOutput(maxEncodedBytes, 0)
    val writer = CoefficientEntropyWriter(out)
    val previousDc = IntArray(frame.components.size)
    var nextRestartMarker = 0
    var mcu = 0
    val totalMcus = coefficients.mcusWide * coefficients.mcusHigh
    for (mcuY in 0 until coefficients.mcusHigh) {
        for (mcuX in 0 until coefficients.mcusWide) {
            for (scanComponent in scan.scan.components) {
                val plane = coefficients.planes[scanComponent.frameIndex]
                val dcTable = scan.dcTables.getOrNull(scanComponent.dcTable) ?: fail()
                val acTable = scan.acTables.getOrNull(scanComponent.acTable) ?: fail()
                for (blockY in 0 until plane.verticalSampling) {
                    for (blockX in 0 until plane.horizontalSampling) {
                        writer.writeBlock(
                            plane.blocks[(mcuY * plane.verticalSampling + blockY) * plane.blocksWide + mcuX * plane.horizontalSampling + blockX],
                            scanComponent.frameIndex,
                            previousDc,
                            dcTable,
                            acTable,
                            frame.precision,
                        )
                    }
                }
            }
            mcu++
            if (scan.restartInterval > 0 && mcu % scan.restartInterval == 0 && mcu < totalMcus) {
                writer.restart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
            }
        }
    }
    writer.flush()
    return out.toByteArray()
}

private class CoefficientEntropyWriter(private val out: ByteArrayOutputStream) {
    private var buffer = 0
    private var bitCount = 0

    fun writeBlock(
        coefficients: IntArray,
        componentIndex: Int,
        previousDc: IntArray,
        dcTable: HuffmanTable,
        acTable: HuffmanTable,
        precision: Int,
    ) {
        val difference = coefficients[0] - previousDc[componentIndex]
        previousDc[componentIndex] = coefficients[0]
        val dcSize = coefficientSize(difference)
        if (dcSize > if (precision == 8) 11 else 15) fail()
        writeSymbol(dcTable, dcSize)
        if (dcSize > 0) writeBits(amplitudeBits(difference, dcSize), dcSize)

        var zeroRun = 0
        for (zigZagPosition in 1 until 64) {
            val value = coefficients[JPEG_ZIGZAG[zigZagPosition]]
            if (value == 0) {
                zeroRun++
                continue
            }
            while (zeroRun >= 16) {
                writeSymbol(acTable, 0xF0)
                zeroRun -= 16
            }
            val size = coefficientSize(value)
            if (size > if (precision == 8) 10 else 14) fail()
            writeSymbol(acTable, (zeroRun shl 4) or size)
            writeBits(amplitudeBits(value, size), size)
            zeroRun = 0
        }
        if (zeroRun > 0) writeSymbol(acTable, 0)
    }

    fun restart(markerIndex: Int) {
        flush()
        out.write(0xFF)
        out.write(TRANSFORM_MARKER_RST0 + markerIndex)
    }

    fun flush() {
        if (bitCount > 0) writeByte((buffer shl (8 - bitCount)) or ((1 shl (8 - bitCount)) - 1))
    }

    private fun writeSymbol(table: HuffmanTable, symbol: Int) {
        writeBits(table.code(symbol), table.length(symbol))
    }

    private fun writeBits(value: Int, length: Int) {
        for (bit in length - 1 downTo 0) {
            buffer = (buffer shl 1) or ((value ushr bit) and 1)
            bitCount++
            if (bitCount == 8) writeByte(buffer)
        }
    }

    private fun writeByte(value: Int) {
        val byte = value and 0xFF
        out.write(byte)
        if (byte == 0xFF) out.write(0)
        buffer = 0
        bitCount = 0
    }
}

private class TransformOutputLimitException : IllegalArgumentException()

/** Bounds both entropy emission and assembled JPEG bytes by the open document's limit. */
private class BoundedTransformOutput(
    private val maximumBytes: Long,
    initialCapacity: Int,
) : ByteArrayOutputStream(minOf(initialCapacity.toLong(), maximumBytes).toInt()) {
    override fun write(value: Int) {
        reserve(1)
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        reserve(length)
        super.write(bytes, offset, length)
    }

    private fun reserve(length: Int) {
        if (length < 0 || count.toLong() > maximumBytes - length.toLong()) {
            throw TransformOutputLimitException()
        }
    }
}

private fun coefficientSize(value: Int): Int {
    if (value == 0) return 0
    var magnitude = if (value == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(value)
    var size = 0
    while (magnitude > 0) {
        magnitude = magnitude ushr 1
        size++
    }
    return size
}

private fun amplitudeBits(value: Int, size: Int): Int =
    if (value >= 0) value else value + (1 shl size) - 1

private fun transformDiagnostic(
    offset: Long,
    code: String,
    result: Codec.Result = Codec.Result.kUnimplemented,
): JpegTranscodeResult = JpegTranscodeResult(null, JpegDiagnostic(code, offset, result))

private fun transformInputDiagnostic(offset: Long): JpegTranscodeResult =
    transformDiagnostic(offset, "jpeg.transform.input.invalid", Codec.Result.kErrorInInput)

private const val TRANSFORM_MARKER_SOS = 0xDA
private const val TRANSFORM_MARKER_DQT = 0xDB
private const val TRANSFORM_MARKER_RST0 = 0xD0
private const val TRANSFORM_MARKER_RST7 = 0xD7
private const val MAX_TRANSFORM_COEFFICIENT_BLOCKS: Long = 262_144L
