package org.graphiks.kanvas.image

import org.graphiks.kanvas.types.ColorSpace
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ImageTest {
    @Test fun `Image data class`() { val i = Image(320, 240, ColorType.RGBA_8888, "test"); assertEquals(320, i.width); assertEquals("test", i.sourceId) }
    @Test fun `Image decode placeholder`() { val i = Image.decode(ByteArray(8), "image/png"); assertEquals(0, i.width) }
    @Test fun `ColorType enum values`() { assertEquals(7, ColorType.entries.size) }

    @Test
    fun `Image decode uses registered Kanvas decoder before placeholder`() {
        val bytes = byteArrayOf(0x42, 0x4D, 0x01, 0x02)
        val decoder = object : ImageDecoder {
            override val name: String = "test-bitmap"
            override fun matches(data: ByteArray): Boolean = data.contentEquals(bytes)
            override fun decode(data: ByteArray): ImageDecodeResult = ImageDecodeResult.Success(
                image = Image(
                    width = 1,
                    height = 1,
                    colorType = ColorType.RGBA_8888,
                    sourceId = "test-decoder",
                    pixels = byteArrayOf(0x11, 0x22, 0x33, 0x44),
                    colorSpace = ColorSpace.SRGB,
                ),
            )
        }
        ImageDecoderRegistry.register(decoder)
        try {
            val image = Image.decode(bytes)

            assertEquals(1, image.width)
            assertEquals(1, image.height)
            assertEquals("test-decoder", image.sourceId)
            assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44), image.pixels)
        } finally {
            ImageDecoderRegistry.unregister(decoder.name)
        }
    }
}
