package org.graphiks.kanvas.codec.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorType

/**
 * Differential DHP/EXP fixtures and pixel oracles are documented beside their binary resources.
 *
 * See `src/test/resources/jpeg-hierarchy/README.md` for the external ISO/IEC 10918-7
 * reference implementation, exact commands, commit, licence, and SHA-256 provenance.
 */
class JpegHierarchyDecodeTest {

    @Test
    fun `DHP frames expose immutable references and EXP expansion`() {
        val document = JpegDocument.open(fixture("sof5-huffman-sequential-exp11.jpg")).document
        assertNotNull(document)

        val hierarchy = document!!.hierarchy
        assertNotNull(hierarchy)
        val resolvedHierarchy = hierarchy!!

        assertEquals(4, resolvedHierarchy.definition.width)
        assertEquals(4, resolvedHierarchy.definition.height)
        assertEquals(8, resolvedHierarchy.definition.precision)
        assertEquals(listOf(0), resolvedHierarchy.definition.componentIds)
        assertEquals(2, resolvedHierarchy.frames.size)
        assertEquals(0xC1, resolvedHierarchy.frames[0].sofMarker)
        assertEquals(null, resolvedHierarchy.frames[0].referenceFrameIndex)
        assertEquals(0xC5, resolvedHierarchy.frames[1].sofMarker)
        assertEquals(0, resolvedHierarchy.frames[1].referenceFrameIndex)
        assertEquals(JpegExpansion(horizontal = true, vertical = true), resolvedHierarchy.frames[1].expansion)

        @Suppress("UNCHECKED_CAST")
        val mutableFrames = resolvedHierarchy.frames as MutableList<JpegHierarchyFrame>
        assertThrows(UnsupportedOperationException::class.java) { mutableFrames.clear() }
        assertFalse(resolvedHierarchy.frames.isEmpty())
    }

    @Test
    fun `all externally-oracled differential frame families decode pixels`() {
        for (case in cases) {
            val document = JpegDocument.open(fixture(case.jpeg)).document
            assertNotNull(document, case.jpeg)
            val resolvedDocument = document!!
            val actual = resolvedDocument.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
            assertEquals(null, actual.diagnostic, case.jpeg)

            assertNotNull(actual.bitmap, case.jpeg)
            val bitmap = actual.bitmap!!
            val expected = pgmPixels(case.oracle)
            assertEquals(expected.width, bitmap.width, case.jpeg)
            assertEquals(expected.height, bitmap.height, case.jpeg)
            for (y in 0 until expected.height) {
                for (x in 0 until expected.width) {
                    val actualSample = (bitmap.getPixel(x, y) ushr 16) and 0xFF
                    val error = kotlin.math.abs(expected.sample(x, y) - actualSample)
                    assertTrue(
                        error <= case.maxError,
                        "$case x=$x y=$y expected=${expected.sample(x, y)} actual=$actualSample error=$error",
                    )
                }
            }
        }
    }

    @Test
    fun `hierarchy retains signed composition until pixel normalization`() {
        val document = requireNotNull(
            JpegDocument.open(fixture("sof15-arithmetic-lossless-context-rst.jpg")).document,
        )
        val samples = decodeJpegHierarchy(requireNotNull(document.hierarchy))
        assertEquals(256, samples.planes.single()[8], "IDCT rounding plus the exact residual")

        val decoded = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
        assertEquals(null, decoded.diagnostic)
        assertNotNull(decoded.bitmap)
        assertEquals(255, (decoded.bitmap!!.getPixel(0, 1) ushr 16) and 0xFF)
    }

    @Test
    fun `SOF15 threshold fixture crosses DAC U boundary and restarts`() {
        val document = requireNotNull(
            JpegDocument.open(fixture("sof15-arithmetic-lossless-context-threshold-rst.jpg")).document,
        )
        val hierarchy = requireNotNull(document.hierarchy)
        val residual = decodeDifferentialArithmeticLossless(hierarchy.parsedFrames[1]).planes.single()
        // Independent reference trace values: first scan row, then values on
        // both sides of a DRI=32 restart boundary.
        assertEquals(-55, residual[0])
        assertEquals(-152, residual[3])
        assertEquals(-39, residual[5])
        assertEquals(51, residual[16])
        assertEquals(-23, residual[31])
        assertEquals(-49, residual[32])
        assertEquals(-28, residual[64])
        assertEquals(-64, residual[128])
        assertEquals(-4, residual[191])
        assertEquals(-64, residual[255])
        assertTrue(residual.any { kotlin.math.abs(it) >= 2 }, "reaches the |D|=2 U=1 boundary")
        assertTrue(residual.any { kotlin.math.abs(it) > 2 }, "reaches the high-magnitude context")
        assertEquals(32, document.copyPayload(document.segments.first { it.marker == 0xDD }).let {
            ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF)
        })
        assertTrue(document.segments.count { it.marker in 0xD0..0xD7 } >= 4, "RST sequence")
    }

    @Test
    fun `SOF15 4x4 raw residual matches the reference exactly`() {
        val document = requireNotNull(
            JpegDocument.open(fixture("sof15-arithmetic-lossless-exp00.jpg")).document,
        )
        val hierarchy = requireNotNull(document.hierarchy)

        val residual = decodeDifferentialArithmeticLossless(hierarchy.parsedFrames[1]).planes.single()

        assertEquals(
            listOf(0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0),
            residual.toList(),
        )
    }

    @Test
    fun `hierarchy reparse budget refuses document before entropy decode`() {
        val source = fixture("sof5-huffman-sequential-exp11.jpg")
        val limits = JpegLimits(
            maxEncodedBytes = source.size.toLong(),
            maxPixels = JpegLimits.DEFAULT.maxPixels,
            maxScans = JpegLimits.DEFAULT.maxScans,
            maxSegments = JpegLimits.DEFAULT.maxSegments,
        )

        val document = requireNotNull(JpegDocument.open(source, limits).document)

        assertEquals("jpeg.hierarchy.reparse.bytes", document.hierarchyDiagnostic?.code)
    }

    @Test
    fun `DHP and EXP validation refuses invalid references before entropy decode`() {
        val source = fixture("sof5-huffman-sequential-exp11.jpg")
        val sourceDocument = requireNotNull(JpegDocument.open(source).document)
        val initialSof = sourceDocument.segments.first { it.marker == 0xC1 }
        val differentialSof = sourceDocument.segments.first { it.marker == 0xC5 }
        val expansion = sourceDocument.segments.first { it.marker == 0xDF }

        assertHierarchyDiagnostic(
            source.copyOf().also { it[initialSof.offset.toInt() + 1] = 0xC5.toByte() },
            "jpeg.hierarchy.reference.missing",
        )
        assertHierarchyDiagnostic(
            source.copyOf().also { it[differentialSof.range.first + 4] = 3 },
            "jpeg.hierarchy.reference.geometry",
        )
        assertHierarchyDiagnostic(
            source.copyOf().also { it[differentialSof.range.first + 7] = 0x12 },
            "jpeg.hierarchy.frame.incompatible",
        )
        assertHierarchyDiagnostic(
            source.copyOf().also { it[expansion.range.first] = 0x22 },
            "jpeg.hierarchy.exp.invalid",
        )
        assertHierarchyDiagnostic(
            source.insertSegment(differentialSof.offset.toInt(), marker = 0xDF, payload = byteArrayOf(0)),
            "jpeg.hierarchy.exp.duplicate",
        )
        assertHierarchyDiagnostic(
            source.insertSegment(source.size - 2, marker = 0xDF, payload = byteArrayOf(0)),
            "jpeg.hierarchy.exp.dangling",
        )
    }

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/jpeg-hierarchy/$name")) { name }.readBytes()

    private fun assertHierarchyDiagnostic(data: ByteArray, expected: String) {
        val document = requireNotNull(JpegDocument.open(data).document)
        val decoded = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
        assertEquals(expected, decoded.diagnostic?.code)
    }

    private fun ByteArray.insertSegment(offset: Int, marker: Int, payload: ByteArray): ByteArray {
        val segment = byteArrayOf(0xFF.toByte(), marker.toByte(), 0, (payload.size + 2).toByte()) + payload
        return ByteArray(size + segment.size).also { output ->
            copyInto(output, endIndex = offset)
            segment.copyInto(output, destinationOffset = offset)
            copyInto(output, destinationOffset = offset + segment.size, startIndex = offset)
        }
    }

    private fun pgmPixels(name: String): PgmPixels {
        val bytes = fixture(name)
        require(bytes.size >= 11 && bytes[0] == 'P'.code.toByte() && bytes[1] == '5'.code.toByte())
        var cursor = 2
        fun token(): Int {
            while (bytes[cursor].toInt().toChar().isWhitespace()) cursor++
            val start = cursor
            while (!bytes[cursor].toInt().toChar().isWhitespace()) cursor++
            return bytes.copyOfRange(start, cursor).decodeToString().toInt()
        }
        val width = token()
        val height = token()
        require(token() == 255)
        require(bytes[cursor].toInt().toChar().isWhitespace())
        cursor++
        return PgmPixels(width, height, bytes.copyOfRange(cursor, bytes.size))
    }

    private data class PgmPixels(val width: Int, val height: Int, val samples: ByteArray) {
        init {
            require(samples.size == width * height)
        }

        fun sample(x: Int, y: Int): Int = samples[y * width + x].toInt() and 0xFF
    }

    private data class HierarchyCase(val jpeg: String, val oracle: String, val maxError: Int)

    private companion object {
        val cases = listOf(
            HierarchyCase("sof5-huffman-sequential-exp11.jpg", "sof5-huffman-sequential-exp11.pgm", maxError = 1),
            HierarchyCase("sof6-huffman-progressive-exp11.jpg", "sof6-huffman-progressive-exp11.pgm", maxError = 1),
            // `-y 0` begins with a lossy SOF1 base frame; the lossless differential
            // itself is exact, but the final composition retains the base IDCT's
            // implementation-defined one-sample rounding latitude.
            HierarchyCase("sof7-huffman-lossless-exp00.jpg", "sof7-huffman-lossless-exp00.pgm", maxError = 1),
            HierarchyCase("sof13-arithmetic-sequential-exp11.jpg", "sof13-arithmetic-sequential-exp11.pgm", maxError = 1),
            HierarchyCase("sof14-arithmetic-progressive-exp11.jpg", "sof14-arithmetic-progressive-exp11.pgm", maxError = 1),
            HierarchyCase("sof15-arithmetic-lossless-exp00.jpg", "sof15-arithmetic-lossless-exp00.pgm", maxError = 1),
            HierarchyCase(
                "sof15-arithmetic-lossless-context-rst.jpg",
                "sof15-arithmetic-lossless-context-rst.pgm",
                maxError = 1,
            ),
        )
    }
}
