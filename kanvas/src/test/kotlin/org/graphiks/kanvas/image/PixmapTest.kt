package org.graphiks.kanvas.image

import java.nio.ByteBuffer
import org.graphiks.kanvas.color.ImageColorSpace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class PixmapTest {

    @Test
    fun `retains ImageInfo and row bytes`() {
        val info = ImageInfo.make(
            2,
            1,
            ColorType.RGBA_8888,
            AlphaType.UNPREMUL,
            ImageColorSpace.sRGB(),
        )

        val pixmap = Pixmap(info, ByteBuffer.allocate(info.minRowBytes()), info.minRowBytes())

        assertEquals(info, pixmap.info)
        assertEquals(info.minRowBytes(), pixmap.rowBytes)
    }

    @Test
    fun `rejects a row stride smaller than the pixel data`() {
        val info = ImageInfo.make(2, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB())

        assertThrows<IllegalArgumentException> {
            Pixmap(info, ByteBuffer.allocate(info.minRowBytes()), info.minRowBytes() - 1)
        }
    }

    @Test
    fun `rejects a buffer shorter than its strided pixel data`() {
        val info = ImageInfo.make(2, 2, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB())
        val rowBytes = info.minRowBytes() + 4

        assertThrows<IllegalArgumentException> {
            Pixmap(info, ByteBuffer.allocate(19), rowBytes)
        }
    }

    @Test
    fun `reads RGBA pixels from little endian storage`() {
        val info = ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB())
        val data = ByteBuffer.wrap(byteArrayOf(0x12, 0x34, 0x56, 0x7F))
        val pixmap = Pixmap(info, data, info.minRowBytes())

        assertEquals(0x7F123456, pixmap.getArgb(0, 0))
    }

    @Test
    fun `reads from the supplied buffer window`() {
        val info = ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB())
        val data = ByteBuffer.wrap(byteArrayOf(0x00, 0x12, 0x34, 0x56, 0x7F))
        data.position(1)
        val pixmap = Pixmap(info, data, info.minRowBytes())

        assertEquals(0x7F123456, pixmap.getArgb(0, 0))
    }
}
