package org.graphiks.kanvas.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.math.color.floatToHalf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `getArgb preserves out of bounds sentinel but refuses inactive CPU formats`() {
        val readable = Pixmap(
            ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB()),
            ByteBuffer.allocate(4),
            4,
        )
        val inactive = Pixmap(
            ImageInfo.make(1, 1, ColorType.UNKNOWN, AlphaType.UNKNOWN, ImageColorSpace.sRGB()),
            ByteBuffer.allocate(0),
            0,
        )

        assertEquals(0, readable.getArgb(1, 0))
        val refusal = assertThrows<UnsupportedOperationException> { inactive.getArgb(0, 0) }
        assertEquals("unsupported CPU-readable color type: UNKNOWN", refusal.message)
        assertEquals(0, inactive.getArgb(1, 0))
    }

    @Test
    fun `reads premultiplied F16 pixels with row padding`() {
        val info = ImageInfo.make(2, 2, ColorType.RGBA_F16_NORM, AlphaType.PREMUL, ImageColorSpace.sRGB())
        val rowBytes = info.minRowBytes() + 8
        val data = ByteBuffer.allocate(rowBytes * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0, floatToHalf(0.125f))
            putShort(2, floatToHalf(0.25f))
            putShort(4, floatToHalf(0.375f))
            putShort(6, floatToHalf(0.5f))
            putShort(rowBytes + 8, floatToHalf(0.625f))
            putShort(rowBytes + 10, floatToHalf(0.75f))
            putShort(rowBytes + 12, floatToHalf(0.875f))
            putShort(rowBytes + 14, floatToHalf(1f))
        }
        val pixmap = Pixmap(info, data, rowBytes)
        val out = FloatArray(4)

        assertTrue(pixmap.getPremulRgbaF16(0, 0, out))
        assertEquals(0.125f, out[0], 0.001f)
        assertEquals(0.25f, out[1], 0.001f)
        assertEquals(0.375f, out[2], 0.001f)
        assertEquals(0.5f, out[3], 0.001f)
        assertTrue(pixmap.getPremulRgbaF16(1, 1, out))
        assertEquals(0.625f, out[0], 0.001f)
        assertEquals(0.75f, out[1], 0.001f)
        assertEquals(0.875f, out[2], 0.001f)
        assertEquals(1f, out[3], 0.001f)
        assertFalse(pixmap.getPremulRgbaF16(2, 0, out))

        val rgba8888 = Pixmap(
            ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB()),
            ByteBuffer.allocate(4),
            4,
        )
        assertThrows<IllegalArgumentException> { rgba8888.getPremulRgbaF16(0, 0, out) }
    }
}
