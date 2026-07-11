package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import java.nio.ByteBuffer

class JpegSequentialEncodeTest {

    @Test
    fun `sequential Huffman options emit SOF0 and SOF1 that Kanvas decodes`() {
        val source = gradient(19, 13)
        val eightBit = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                process = JpegEncodeProcess.SequentialHuffman,
                precision = 8,
                sampling = JpegSampling.S444,
            ),
        )!!
        val twelveBit = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                process = JpegEncodeProcess.SequentialHuffman,
                precision = 12,
                sampling = JpegSampling.S420,
            ),
        )!!

        assertEquals(0xC0, firstMarker(eightBit, setOf(0xC0, 0xC1)))
        assertEquals(0xC1, firstMarker(twelveBit, setOf(0xC0, 0xC1)))
        assertReasonableRoundTrip(source, eightBit)
        assertReasonableRoundTrip(source, twelveBit)
    }

    @Test
    fun `SOF1 uses sixteen bit DQT and an AC DHT covering extended categories`() {
        val bytes = JpegEncoder.encode(
            gradient(19, 13),
            JpegEncoder.Options(precision = 12, sampling = JpegSampling.S444),
        )!!
        val document = JpegDocument.open(bytes).document!!
        val dqt = document.copyPayload(document.segments.single { it.marker == 0xDB })
        val acLuma = document.copyPayload(
            document.segments.single { segment ->
                segment.marker == 0xC4 && (document.copyPayload(segment)[0].toInt() and 0xFF) == 0x10
            },
        )

        assertEquals(0x10, dqt[0].toInt() and 0xFF)
        assertEquals(0x11, dqt[129].toInt() and 0xFF)
        assertEquals(255, acLuma[1 + 7].toInt() and 0xFF)
        val symbols = acLuma.copyOfRange(17, acLuma.size).map { it.toInt() and 0xFF }.toSet()
        assertTrue((0x0B..0x0E).all(symbols::contains), "AC categories 11..14 must be representable")
        assertDecodes(bytes)
    }

    @Test
    fun `all declared sampling factors are written and factor validation is bounded`() {
        val source = gradient(17, 11)
        assertEquals(0x11, sofSampling(JpegEncoder.encode(source, JpegEncoder.Options(sampling = JpegSampling.S444))!!).first())
        assertEquals(0x21, sofSampling(JpegEncoder.encode(source, JpegEncoder.Options(sampling = JpegSampling.S422))!!).first())
        assertEquals(0x22, sofSampling(JpegEncoder.encode(source, JpegEncoder.Options(sampling = JpegSampling.S420))!!).first())
        val sampling = JpegSampling(
            listOf(
                JpegSamplingFactor(4, 3),
                JpegSamplingFactor(2, 1),
                JpegSamplingFactor(1, 2),
            ),
        )
        val bytes = JpegEncoder.encode(source, JpegEncoder.Options(sampling = sampling))!!
        assertEquals(listOf(0x43, 0x21, 0x12), sofSampling(bytes))
        assertThrows(IllegalArgumentException::class.java) { JpegSamplingFactor(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { JpegSamplingFactor(1, 5) }
        assertThrows(IllegalArgumentException::class.java) { JpegSampling(emptyList()) }
    }

    @Test
    fun `nondivisible sampling cells use an overlap weighted area average`() {
        val values = intArrayOf(
            0, 1, 2, 3,
            10, 11, 12, 13,
            20, 21, 22, 23,
            30, 31, 32, 33,
        )
        val actual = areaAverage(
            left = 0.0,
            top = 4.0 / 3.0,
            right = 4.0 / 3.0,
            bottom = 8.0 / 3.0,
        ) { x, y -> values[y * 4 + x].toDouble() }

        // Independent overlap calculation: x weights [1, 1/3], y weights [2/3, 2/3].
        val expected = (
            10.0 * 2.0 / 3.0 + 11.0 * 2.0 / 9.0 +
                20.0 * 2.0 / 3.0 + 21.0 * 2.0 / 9.0
            ) / (16.0 / 9.0)
        assertEquals(expected, actual, 1e-12)
    }

    @Test
    fun `restart interval emits DRI and ordered restart markers`() {
        val source = gradient(49, 25)
        val bytes = JpegEncoder.encode(
            source,
            JpegEncoder.Options(restartInterval = 2, sampling = JpegSampling.S420),
        )!!
        val noRestart = JpegEncoder.encode(source, JpegEncoder.Options(sampling = JpegSampling.S420))!!

        assertEquals(2, dri(bytes))
        assertTrue(entropyRestartMarkers(bytes).zipWithNext().all { (left, right) -> right == (left + 1) and 7 })
        assertTrue(entropyRestartMarkers(bytes).isNotEmpty())
        assertEquals(decode(noRestart).pixels.toList(), decode(bytes).pixels.toList(), "RST must reset DC predictors")
        assertDecodes(bytes)
    }

    @Test
    fun `writer emits requested metadata segments and preserves their payloads`() {
        val icc = ByteArray(65_520) { index -> (index * 31).toByte() }
        val exif = orientationTiff(SkEncodedOrigin.kRightTop.exifValue)
        val xmp = "<x:xmpmeta>kanvas</x:xmpmeta>".encodeToByteArray()
        val comment = "static JPEG metadata".encodeToByteArray()
        val bytes = JpegEncoder.encode(
            gradient(9, 7),
            JpegEncoder.Options(
                metadata = JpegEncodeMetadata(
                    icc = icc,
                    exif = exif,
                    xmp = xmp,
                    comment = comment,
                    adobeTransform = 1,
                ),
            ),
        )!!
        val document = JpegDocument.open(bytes).document!!

        assertEquals(SkEncodedOrigin.kRightTop, document.metadata.origin)
        assertArrayEquals(xmp, document.metadata.xmp)
        assertEquals(1, document.metadata.adobeTransform)
        assertArrayEquals(comment, document.copyPayload(document.segments.single { it.marker == 0xFE }))
        assertArrayEquals(icc, reassembleIcc(document))
    }

    @Test
    fun `typed options snapshot metadata bytes and sampling factors at construction`() {
        val comment = "before".encodeToByteArray()
        val factors = mutableListOf(
            JpegSamplingFactor(2, 2),
            JpegSamplingFactor(1, 1),
            JpegSamplingFactor(1, 1),
        )
        val options = JpegEncoder.Options(
            sampling = JpegSampling(factors),
            metadata = JpegEncodeMetadata(comment = comment),
        )
        comment[0] = 'X'.code.toByte()
        factors[0] = JpegSamplingFactor(1, 1)

        val document = JpegDocument.open(JpegEncoder.encode(gradient(9, 7), options)!!).document!!
        assertArrayEquals("before".encodeToByteArray(), document.copyPayload(document.segments.single { it.marker == 0xFE }))
        assertEquals(listOf(0x22, 0x11, 0x11), sofSampling(JpegEncoder.encode(gradient(9, 7), options)!!))
    }

    @Test
    fun `legacy alpha mapping and typed alpha policy are explicit`() {
        val transparentRed = flat(16, 16, 0x00FF0000)
        val ignored = JpegEncoder.encode(
            transparentRed,
            JpegEncoder.Options(alphaPolicy = JpegAlphaPolicy.Ignore),
        )!!
        val blended = JpegEncoder.encode(
            transparentRed,
            JpegEncoder.Options(alphaPolicy = JpegAlphaPolicy.BlendOnBlack),
        )!!
        val legacy = JpegEncoder.encode(
            transparentRed,
            JpegEncoder.Options(alphaOption = JpegEncoder.AlphaOption.kBlendOnBlack),
        )!!

        assertTrue(redAt(ignored) > 220)
        assertTrue(redAt(blended) < 8)
        assertTrue(redAt(legacy) < 8)
    }

    @Test
    fun `invalid options fail early and advanced processes are refused without fallback`() {
        assertThrows(IllegalArgumentException::class.java) { JpegEncoder.Options(precision = 10) }
        assertThrows(IllegalArgumentException::class.java) { JpegEncoder.Options(restartInterval = 65_536) }
        assertThrows(IllegalArgumentException::class.java) {
            JpegEncoder.Options(metadata = JpegEncodeMetadata(comment = ByteArray(65_534)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JpegEncodeMetadata(icc = ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JpegEncodeMetadata(adobeTransform = 0)
        }
        val source = flat(8, 8, 0xFF808080.toInt())
        assertNull(JpegEncoder.encode(source, JpegEncoder.Options(process = JpegEncodeProcess.ProgressiveHuffman)))
        assertNull(JpegEncoder.encode(source, JpegEncoder.Options(process = JpegEncodeProcess.LosslessHuffman)))
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(hierarchy = listOf(JpegHierarchyLevel(1, 2, JpegEncodeProcess.SequentialHuffman))),
            ),
        )
    }

    @Test
    fun `encoder refuses dimensions outside SOF range before writing output`() {
        for (source in listOf(SkBitmap(65_536, 1), SkBitmap(1, 65_536))) {
            val output = java.io.ByteArrayOutputStream()
            assertFalse(JpegEncoder.encode(output, source))
            assertEquals(0, output.size())
            assertNull(JpegEncoder.encode(source))
        }
        val oversizedPixmap = SkPixmap(
            SkImageInfo.Make(65_536, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul, SkColorSpace.makeSRGB()),
            ByteBuffer.allocate(65_536 * 4),
            65_536 * 4,
        )
        assertNull(JpegEncoder.encode(oversizedPixmap))
    }

    private fun assertReasonableRoundTrip(source: SkBitmap, bytes: ByteArray) {
        val decoded = decode(bytes)
        assertEquals(source.width, decoded.width)
        assertEquals(source.height, decoded.height)
        var totalError = 0L
        for (index in source.pixels.indices) {
            val expected = source.pixels[index]
            val actual = decoded.pixels[index]
            totalError += kotlin.math.abs(((expected ushr 16) and 0xFF) - ((actual ushr 16) and 0xFF))
            totalError += kotlin.math.abs(((expected ushr 8) and 0xFF) - ((actual ushr 8) and 0xFF))
            totalError += kotlin.math.abs((expected and 0xFF) - (actual and 0xFF))
        }
        assertTrue(totalError / (source.width * source.height * 3) < 40, "mean RGB error: $totalError")
    }

    private fun assertDecodes(bytes: ByteArray) {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        assertEquals(Codec.Result.kSuccess, codec!!.getImage().second)
    }

    private fun decode(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes) ?: error("Kanvas did not recognize encoder output")
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        return bitmap ?: error("Kanvas did not decode encoder output")
    }

    private fun redAt(bytes: ByteArray): Int = (decode(bytes).getPixel(0, 0) ushr 16) and 0xFF

    private fun flat(width: Int, height: Int, color: Int): SkBitmap = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888).also { bitmap ->
        bitmap.pixels.fill(color)
    }

    private fun gradient(width: Int, height: Int): SkBitmap = flat(width, height, 0).also { bitmap ->
        for (y in 0 until height) for (x in 0 until width) {
            val r = x * 255 / (width - 1).coerceAtLeast(1)
            val g = y * 255 / (height - 1).coerceAtLeast(1)
            val b = ((x * 13 + y * 29) and 0xFF)
            bitmap.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }

    private fun firstMarker(bytes: ByteArray, expected: Set<Int>): Int {
        var offset = 2
        while (offset + 3 < bytes.size) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            if (marker in expected) return marker
            val length = u16(bytes, offset + 2)
            offset += 2 + length
        }
        error("marker not found")
    }

    private fun sofSampling(bytes: ByteArray): List<Int> {
        var offset = 2
        while (offset + 3 < bytes.size) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            val length = u16(bytes, offset + 2)
            if (marker in setOf(0xC0, 0xC1)) {
                val payload = offset + 4
                val components = bytes[payload + 5].toInt() and 0xFF
                return (0 until components).map { component -> bytes[payload + 7 + component * 3].toInt() and 0xFF }
            }
            offset += 2 + length
        }
        error("SOF marker not found")
    }

    private fun dri(bytes: ByteArray): Int {
        var offset = 2
        while (offset + 3 < bytes.size) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            val length = u16(bytes, offset + 2)
            if (marker == 0xDD) return u16(bytes, offset + 4)
            offset += 2 + length
        }
        error("DRI marker not found")
    }

    private fun entropyRestartMarkers(bytes: ByteArray): List<Int> {
        val sos = firstSegment(bytes, 0xDA)
        val start = sos + 2 + u16(bytes, sos + 2)
        val markers = ArrayList<Int>()
        var offset = start
        while (offset + 1 < bytes.size) {
            if (bytes[offset] == 0xFF.toByte() && bytes[offset + 1] in 0xD0.toByte()..0xD7.toByte()) {
                markers += (bytes[offset + 1].toInt() and 0xFF) - 0xD0
                offset += 2
            } else {
                offset++
            }
        }
        return markers
    }

    private fun firstSegment(bytes: ByteArray, wantedMarker: Int): Int {
        var offset = 2
        while (offset + 3 < bytes.size) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            if (marker == wantedMarker) return offset
            offset += 2 + u16(bytes, offset + 2)
        }
        error("marker 0x${wantedMarker.toString(16)} not found")
    }

    private fun reassembleIcc(document: JpegDocument): ByteArray {
        val chunks = document.segments.filter { it.marker == 0xE2 }.map { document.copyPayload(it) }
            .filter { payload -> payload.copyOfRange(0, 12).contentEquals("ICC_PROFILE\u0000".encodeToByteArray()) }
            .sortedBy { payload -> payload[12].toInt() and 0xFF }
        return chunks.flatMap { payload -> payload.drop(14).asIterable() }.toByteArray()
    }

    private fun orientationTiff(value: Int): ByteArray = byteArrayOf(
        'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A,
        0x00, 0x00, 0x00, 0x08,
        0x00, 0x01,
        0x01, 0x12,
        0x00, 0x03,
        0x00, 0x00, 0x00, 0x01,
        0x00, value.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    )

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
