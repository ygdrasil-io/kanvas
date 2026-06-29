package org.graphiks.kanvas.codec.wbmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.SkCodec
import org.graphiks.kanvas.codec.test.CodecNegativeFixtures
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat

class SkWbmpKotlinCodecTest {

    @Test
    fun `decodes packed type zero pixels`() {
        val codec = SkWbmpKotlinCodec.Decoder.make(
            wbmp(
                width = 3,
                height = 2,
                raster = byteArrayOf(
                    0b1010_0000.toByte(),
                    0b0101_1111.toByte(),
                ),
            ),
        )

        assertNotNull(codec)
        assertTrue(codec is SkWbmpKotlinCodec)
        assertEquals(SkEncodedImageFormat.kWBMP, codec!!.getEncodedFormat())
        assertEquals(3, codec.getInfo().width)
        assertEquals(2, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(WHITE, bitmap!!.getPixel(0, 0))
        assertEquals(BLACK, bitmap.getPixel(1, 0))
        assertEquals(WHITE, bitmap.getPixel(2, 0))
        assertEquals(BLACK, bitmap.getPixel(0, 1))
        assertEquals(WHITE, bitmap.getPixel(1, 1))
        assertEquals(BLACK, bitmap.getPixel(2, 1))
    }

    @Test
    fun `rejects invalid signature and fixed header`() {
        val magicCases = listOf(
            CodecNegativeFixtures.invalidMagic("empty WBMP input", ByteArray(0)),
            CodecNegativeFixtures.invalidMagic("ASCII non-WBMP payload", "not-a-wbmp"),
        )
        for (case in magicCases) {
            assertFalse(SkWbmpKotlinCodec.Decoder.matches(case.data), case.name)
        }

        val headerCases = listOf(
            CodecNegativeFixtures.invalidMagic("unsupported WBMP type", byteArrayOf(1, 0, 1, 1, 0)),
            CodecNegativeFixtures.invalidMagic("non-zero WBMP fixed header", byteArrayOf(0, 0x20, 1, 1, 0)),
        )
        for (case in headerCases) {
            assertNull(SkWbmpKotlinCodec.Decoder.make(case.data), case.name)
        }
    }

    @Test
    fun `rejects zero and overflow dimensions`() {
        val cases = listOf(
            CodecNegativeFixtures.invalidSize("zero WBMP width", byteArrayOf(0, 0, 0, 1, 0)),
            CodecNegativeFixtures.invalidSize("zero WBMP height", byteArrayOf(0, 0, 1, 0, 0)),
            CodecNegativeFixtures.invalidSize(
                "overflow WBMP width",
                byteArrayOf(0, 0, 0x84.toByte(), 0x80.toByte(), 0, 1, 0),
            ),
            CodecNegativeFixtures.invalidSize(
                "overflow WBMP height",
                byteArrayOf(0, 0, 1, 0x84.toByte(), 0x80.toByte(), 0, 0),
            ),
        )

        for (case in cases) {
            assertNull(SkWbmpKotlinCodec.Decoder.make(case.data), case.name)
        }
    }

    @Test
    fun `rejects truncated vlq dimensions`() {
        val cases = listOf(
            CodecNegativeFixtures.truncated("truncated WBMP width VLQ", byteArrayOf(0, 0, 0x81.toByte()), size = 3),
            CodecNegativeFixtures.truncated("truncated WBMP height VLQ", byteArrayOf(0, 0, 1, 0x81.toByte()), size = 4),
            CodecNegativeFixtures.truncated(
                "unterminated WBMP width VLQ",
                byteArrayOf(
                    0,
                    0,
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                    0x81.toByte(),
                ),
                size = 11,
            ),
        )

        for (case in cases) {
            assertNull(SkWbmpKotlinCodec.Decoder.make(case.data), case.name)
        }
    }

    @Test
    fun `rejects truncated raster`() {
        assertNull(
            SkWbmpKotlinCodec.Decoder.make(
                wbmp(
                    width = 9,
                    height = 2,
                    raster = byteArrayOf(
                        0xFF.toByte(),
                        0x80.toByte(),
                        0x00.toByte(),
                    ),
                ),
            ),
        )
    }

    private fun wbmp(width: Int, height: Int, raster: ByteArray): ByteArray =
        byteArrayOf(0, 0) + vlq(width) + vlq(height) + raster

    private fun vlq(value: Int): ByteArray {
        require(value >= 0)
        var remaining = value
        val bytes = ArrayDeque<Byte>()
        bytes.addFirst((remaining and 0x7F).toByte())
        remaining = remaining ushr 7
        while (remaining != 0) {
            bytes.addFirst(((remaining and 0x7F) or 0x80).toByte())
            remaining = remaining ushr 7
        }
        return bytes.toByteArray()
    }
}

private const val BLACK: Int = -0x1000000
private const val WHITE: Int = -0x1
