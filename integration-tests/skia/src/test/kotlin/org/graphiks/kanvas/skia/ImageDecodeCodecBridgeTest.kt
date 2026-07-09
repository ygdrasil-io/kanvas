package org.graphiks.kanvas.skia

import org.graphiks.kanvas.image.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ImageDecodeCodecBridgeTest {
    @Test
    fun `Image decode materializes JPEG pixels through codec runtime`() {
        val bytes = javaClass.classLoader
            .getResourceAsStream("images/mandrill_512_q075.jpg")
            ?.readBytes()

        assertNotNull(bytes)

        val image = Image.decode(bytes!!, "image/jpeg")

        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertEquals(org.graphiks.kanvas.image.ColorType.RGBA_8888, image.colorType)
        assertEquals(
            "codec:kJPEG:77244:153c3e7a54a0a9e56db0f0f8d91d160770a2a27a1f559ae9461a5e96ce1d798e",
            image.sourceId,
        )
        assertEquals(512 * 512 * 4, image.pixels?.size)
        assertOpaque(image.pixels!!, x = 0, y = 0)
        assertOpaque(image.pixels!!, x = 256, y = 256)
        assertNotUniformWhiteOrTransparent(image.pixels!!)
    }

    private fun assertOpaque(pixels: ByteArray, x: Int, y: Int) {
        val offset = (y * 512 + x) * 4
        assertEquals(255, pixels[offset + 3].toInt() and 0xFF, "a($x,$y)")
    }

    private fun assertNotUniformWhiteOrTransparent(pixels: ByteArray) {
        val first = pixels.take(4)
        val centerOffset = (256 * 512 + 256) * 4
        val center = pixels.slice(centerOffset until centerOffset + 4)
        val white = listOf(255, 255, 255, 255)
        val transparent = listOf(0, 0, 0, 0)

        assertNotEquals(white, first.map { it.toInt() and 0xFF })
        assertNotEquals(transparent, first.map { it.toInt() and 0xFF })
        assertNotEquals(first, center)
    }
}
