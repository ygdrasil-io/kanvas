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

    @Test
    fun `registered decoder overrides existing decoder with same name`() {
        val firstBytes = byteArrayOf(0x10, 0x20, 0x30)
        val secondBytes = byteArrayOf(0x10, 0x20, 0x30)
        val first = object : ImageDecoder {
            override val name: String = "override-test"
            override fun matches(data: ByteArray): Boolean = data.contentEquals(firstBytes)
            override fun decode(data: ByteArray): ImageDecodeResult = ImageDecodeResult.Success(
                Image.fromPixels(1, 1, byteArrayOf(0x01, 0x02, 0x03, 0x04), sourceId = "first"),
            )
        }
        val second = object : ImageDecoder {
            override val name: String = "override-test"
            override fun matches(data: ByteArray): Boolean = data.contentEquals(secondBytes)
            override fun decode(data: ByteArray): ImageDecodeResult = ImageDecodeResult.Success(
                Image.fromPixels(1, 1, byteArrayOf(0x05, 0x06, 0x07, 0x08), sourceId = "second"),
            )
        }
        ImageDecoderRegistry.register(first)
        try {
            ImageDecoderRegistry.register(second)

            val image = Image.decode(firstBytes)

            assertEquals("second", image.sourceId)
            assertArrayEquals(byteArrayOf(0x05, 0x06, 0x07, 0x08), image.pixels)
        } finally {
            ImageDecoderRegistry.unregister(first.name)
        }
    }

    @Test
    fun `concurrent registry reads are stable during first provider load`() {
        val results = (0 until 32).map {
            kotlin.concurrent.thread {
                repeat(32) {
                    ImageDecoderRegistry.all()
                    Image.decode(ByteArray(8), "image/png")
                }
            }
        }

        results.forEach { it.join() }
    }
}
