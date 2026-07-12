package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType

/**
 * Release-facing matrix for the JPEG processes the portable codec actually
 * implements.  Every ISO SOF is either exercised by a static/generated stream
 * or recorded here as an explicit refusal; this is deliberately not a claim
 * that a structurally recognized SOF is universally supported.
 */
class JpegConformanceTest {

    @Test
    fun `static JPEG matrix decodes every evidenced process and hierarchy route`() {
        val streams = JpegConformanceFixtures.streams()

        for (stream in streams) {
            val opened = JpegDocument.open(stream.bytes)
            assertNull(opened.diagnostic, stream.name)
            val document = requireNotNull(opened.document) { stream.name }
            val sofMarkers = document.segments.mapNotNull { JpegFrameSpec.fromSof(it.marker)?.marker }.toSet()
            assertEquals(stream.sofMarkers, sofMarkers, stream.name)

            val decoded = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
            assertNull(decoded.diagnostic, stream.name)
            val bitmap = requireNotNull(decoded.bitmap) { stream.name }
            stream.width?.let { assertEquals(it, bitmap.width, stream.name) }
            stream.height?.let { assertEquals(it, bitmap.height, stream.name) }
        }
    }

    @Test
    fun `every non-reserved SOF has evidence or an explicit stable refusal`() {
        val evidenced = JpegConformanceFixtures.streams()
            .flatMapTo(sortedSetOf()) { it.sofMarkers }
        val refused = mapOf(
            0xC6 to "jpeg.differential.reference.required",
            0xCB to "jpeg.arithmetic.lossless.unsupported",
        )
        val allSofs = (0xC0..0xCF).filter { JpegFrameSpec.fromSof(it) != null }.toSortedSet()

        assertEquals(allSofs, (evidenced + refused.keys).toSortedSet())

        val bareProgressiveDifferential = JpegConformanceFixtures.differentialProbe(0xC6)
        val differential = requireNotNull(JpegDocument.open(bareProgressiveDifferential).document)
            .decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
        assertEquals(refused.getValue(0xC6), differential.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, differential.diagnostic?.result)

        val arithmeticLossless = requireNotNull(JpegDocument.open(JpegConformanceFixtures.sof11Probe()).document)
            .decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
        assertEquals(refused.getValue(0xCB), arithmeticLossless.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, arithmeticLossless.diagnostic?.result)
    }

    @Test
    fun `unsupported static encode requests are refused without a process fallback`() {
        val source = JpegConformanceFixtures.grayscale(16, 12)

        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.LosslessArithmetic,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    losslessParameters = JpegLosslessParameters(1, 0),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    progressiveScans = listOf(
                        JpegProgressiveScan(
                            componentIds = listOf(1),
                            spectralStart = 0,
                            spectralEnd = 0,
                            successiveLow = 1,
                        ),
                    ),
                ),
            ),
        )
    }
}

/** Shared deterministic sources used by conformance, fuzzing, oracle and performance evidence. */
internal object JpegConformanceFixtures {
    internal data class Stream(
        val name: String,
        val bytes: ByteArray,
        val sofMarkers: Set<Int>,
        val width: Int? = null,
        val height: Int? = null,
    )

    fun streams(): List<Stream> {
        val color = color(17, 9)
        val gray = grayscale(16, 12)
        val colorProgressiveScans = listOf(
            JpegProgressiveScan(listOf(1, 2, 3), 0, 0),
            JpegProgressiveScan(listOf(1), 1, 63),
            JpegProgressiveScan(listOf(2), 1, 63),
            JpegProgressiveScan(listOf(3), 1, 63),
        )
        val grayscaleProgressiveScans = listOf(
            JpegProgressiveScan(listOf(1), 0, 0),
            JpegProgressiveScan(listOf(1), 1, 63),
        )
        return listOf(
            generated(
                "SOF0 sequential Huffman 8-bit YCbCr",
                color,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.SequentialHuffman,
                    colorModel = JpegEncodeColorModel.YCbCr,
                    sampling = JpegSampling.S420,
                    restartInterval = 1,
                ),
                setOf(0xC0),
            ),
            generated(
                "SOF1 sequential Huffman 12-bit YCbCr",
                color,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.SequentialHuffman,
                    precision = 12,
                    colorModel = JpegEncodeColorModel.YCbCr,
                    sampling = JpegSampling.S444,
                ),
                setOf(0xC1),
            ),
            generated(
                "SOF2 progressive Huffman",
                color,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    colorModel = JpegEncodeColorModel.YCbCr,
                    sampling = JpegSampling.S420,
                    restartInterval = 1,
                    progressiveScans = colorProgressiveScans,
                ),
                setOf(0xC2),
            ),
            generated(
                "SOF3 lossless Huffman 16-bit grayscale",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.LosslessHuffman,
                    precision = 16,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 4,
                    losslessParameters = JpegLosslessParameters(7, 0),
                ),
                setOf(0xC3),
            ),
            generated(
                "SOF3 lossless Huffman 12-bit RGB",
                color,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.LosslessHuffman,
                    precision = 12,
                    colorModel = JpegEncodeColorModel.Rgb,
                    sampling = JpegSampling.S444,
                    restartInterval = 4,
                    losslessParameters = JpegLosslessParameters(7, 0),
                ),
                setOf(0xC3),
            ),
            generated(
                "SOF9 sequential arithmetic",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.SequentialArithmetic,
                    precision = 12,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 1,
                ),
                setOf(0xC9),
            ),
            generated(
                "SOF10 progressive arithmetic",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveArithmetic,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 1,
                    progressiveScans = grayscaleProgressiveScans,
                ),
                setOf(0xCA),
            ),
            generated(
                "DHP SOF5 sequential Huffman",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.SequentialHuffman,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 1,
                    hierarchy = listOf(JpegHierarchyLevel(1, 2, JpegEncodeProcess.DifferentialSequentialHuffman)),
                ),
                setOf(0xC0, 0xC5),
            ),
            generated(
                "DHP SOF7 lossless Huffman",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.SequentialHuffman,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 64,
                    hierarchy = listOf(JpegHierarchyLevel(1, 1, JpegEncodeProcess.DifferentialLosslessHuffman)),
                ),
                setOf(0xC0, 0xC7),
            ),
            generated(
                "DHP SOF13 sequential arithmetic",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.SequentialArithmetic,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 1,
                    hierarchy = listOf(JpegHierarchyLevel(1, 2, JpegEncodeProcess.DifferentialSequentialArithmetic)),
                ),
                setOf(0xC9, 0xCD),
            ),
            generated(
                "DHP SOF14 progressive arithmetic",
                gray,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveArithmetic,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    restartInterval = 1,
                    progressiveScans = grayscaleProgressiveScans,
                    hierarchy = listOf(JpegHierarchyLevel(1, 2, JpegEncodeProcess.DifferentialProgressiveArithmetic)),
                ),
                setOf(0xCA, 0xCE),
            ),
            Stream(
                name = "DHP SOF15 arithmetic lossless corpus",
                bytes = resource("/jpeg-hierarchy/sof15-arithmetic-lossless-context-rst.jpg"),
                sofMarkers = setOf(0xC9, 0xCF),
            ),
        )
    }

    fun grayscale(width: Int, height: Int): SkBitmap = bitmap(width, height) { x, y ->
        val value = (x * 29 + y * 17) and 0xFF
        0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
    }

    fun color(width: Int, height: Int): SkBitmap = bitmap(width, height) { x, y ->
        val red = x * 255 / (width - 1).coerceAtLeast(1)
        val green = y * 255 / (height - 1).coerceAtLeast(1)
        val blue = (x * 13 + y * 31) and 0xFF
        0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }

    fun differentialProbe(marker: Int): ByteArray = generated(
        "differential probe",
        grayscale(8, 8),
        JpegEncoder.Options(
            process = JpegEncodeProcess.SequentialHuffman,
            colorModel = JpegEncodeColorModel.Grayscale,
            sampling = JpegSampling.S444,
        ),
        setOf(0xC0),
    ).bytes.also { bytes -> bytes[firstSofOffset(bytes) + 1] = marker.toByte() }

    fun sof11Probe(): ByteArray = generated(
        "SOF11 probe",
        grayscale(8, 8),
        JpegEncoder.Options(
            process = JpegEncodeProcess.SequentialArithmetic,
            colorModel = JpegEncodeColorModel.Grayscale,
            sampling = JpegSampling.S444,
        ),
        setOf(0xC9),
    ).bytes.also { bytes ->
        bytes[firstSofOffset(bytes) + 1] = 0xCB.toByte()
        val sos = firstMarkerOffset(bytes, 0xDA)
        bytes[sos + 7] = 1
        bytes[sos + 8] = 0
    }

    private fun generated(
        name: String,
        source: SkBitmap,
        options: JpegEncoder.Options,
        sofMarkers: Set<Int>,
    ): Stream = Stream(
        name = name,
        bytes = requireNotNull(JpegEncoder.encode(source, options)) { name },
        sofMarkers = sofMarkers,
        width = source.width,
        height = source.height,
    )

    private fun bitmap(width: Int, height: Int, pixel: (Int, Int) -> Int): SkBitmap =
        SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888).also { bitmap ->
            for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, pixel(x, y))
        }

    private fun resource(path: String): ByteArray =
        requireNotNull(JpegConformanceFixtures::class.java.getResourceAsStream(path)) { path }.readBytes()

    private fun firstSofOffset(bytes: ByteArray): Int = bytes.indices.first { offset ->
        offset + 1 < bytes.size && bytes[offset] == 0xFF.toByte() && JpegFrameSpec.fromSof(bytes[offset + 1].toInt() and 0xFF) != null
    }

    private fun firstMarkerOffset(bytes: ByteArray, marker: Int): Int = bytes.indices.first { offset ->
        offset + 1 < bytes.size && bytes[offset] == 0xFF.toByte() && (bytes[offset + 1].toInt() and 0xFF) == marker
    }
}
