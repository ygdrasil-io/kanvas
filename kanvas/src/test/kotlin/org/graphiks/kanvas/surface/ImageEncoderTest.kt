package org.graphiks.kanvas.surface

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ImageEncoderTest {
    @Test fun `find unknown format`() { assertNull(ImageEncoderRegistry.find("unknown")) }

    @Test fun `toPng throws without encoder`() {
        val r = RenderResult(UByteArray(16){128u}, 2, 2, PixelFormat.RGBA8, org.graphiks.kanvas.types.ColorSpace.SRGB, Diagnostics(), RenderStats(0,0,0,0,0f))
        try { r.toPng(); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("codec:png")) }
    }

    @Test fun `toJpeg throws without encoder`() {
        val r = RenderResult(UByteArray(16){128u}, 2, 2, PixelFormat.RGBA8, org.graphiks.kanvas.types.ColorSpace.SRGB, Diagnostics(), RenderStats(0,0,0,0,0f))
        try { r.toJpeg(); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("codec:jpeg")) }
    }

    @Test fun `toWebP throws without encoder`() {
        val r = RenderResult(UByteArray(16){128u}, 2, 2, PixelFormat.RGBA8, org.graphiks.kanvas.types.ColorSpace.SRGB, Diagnostics(), RenderStats(0,0,0,0,0f))
        try { r.toWebP(); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("codec:webp")) }
    }

    @Test
    fun `BGRA result preserves bytes and layout through image and all encoders`() {
        val pixels = ubyteArrayOf(3u, 2u, 1u, 4u)
        val result = RenderResult(
            pixels,
            1,
            1,
            PixelFormat.BGRA8,
            org.graphiks.kanvas.types.ColorSpace.SRGB,
            Diagnostics(),
            RenderStats(1, 0, 1, 1, 1f),
        )
        val metadata = mutableMapOf<String, ImageEncoder.Metadata>()
        val names = listOf("png", "jpeg", "webp")
        val previous = names.associateWith { name ->
            ImageEncoderRegistry.replaceForTesting(name, object : ImageEncoder {
                override fun encode(
                    pixels: ByteArray,
                    width: Int,
                    height: Int,
                    metadataValue: ImageEncoder.Metadata,
                    options: Map<String, String>,
                ): ByteArray {
                    assertArrayEquals(byteArrayOf(3, 2, 1, 4), pixels)
                    assertEquals(1, width)
                    assertEquals(1, height)
                    metadata[name] = metadataValue
                    return name.encodeToByteArray()
                }
            })
        }
        try {
            val image = result.toImage()
            assertEquals(org.graphiks.kanvas.image.ColorType.BGRA_8888, image.colorType)
            assertArrayEquals(byteArrayOf(3, 2, 1, 4), image.pixels)
            result.toPng()
            result.toJpeg()
            result.toWebP()

            assertEquals(
                setOf(ImageEncoder.PixelLayout.BGRA8),
                metadata.values.map { it.format }.toSet(),
            )
            assertEquals(setOf("png", "jpeg", "webp"), metadata.keys)
        } finally {
            previous.forEach { (name, encoder) -> ImageEncoderRegistry.replaceForTesting(name, encoder) }
        }
    }
}
