package org.skia.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.skia.codec.png.PngImageIoDecoderProvider
import org.skia.codec.png.PngKotlinDecoderProvider
import org.skia.codec.test.CodecTestFixtures
import org.skia.codec.test.CodecTestFixtures.decodePixels

class CodecAwtKotlinComparisonTest {
    @Test
    fun `png fixture decodes identically through awt and kotlin backends`() {
        val data = CodecTestFixtures.simpleRgbaPng()
        val awt = PngImageIoDecoderProvider().decoders().single().make(data)
        val kotlin = PngKotlinDecoderProvider().decoders().single().make(data)

        assertNotNull(awt)
        assertNotNull(kotlin)
        assertEquals(awt!!.getInfo(), kotlin!!.getInfo())
        assertRowsEqual(decodePixels(awt), decodePixels(kotlin))
    }

    private fun assertRowsEqual(expected: List<IntArray>, actual: List<IntArray>) {
        assertEquals(expected.size, actual.size)
        for (y in expected.indices) {
            assertArrayEquals(expected[y], actual[y], "row=$y")
        }
    }
}
