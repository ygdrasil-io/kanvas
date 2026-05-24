package org.skia.core

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkCanvasReadWritePixelsTest {

    @Test
    fun `readPixels bitmap copies a clipped source rectangle`() {
        val canvas = gradientCanvas(4, 4)
        val dst = SkBitmap(3, 3).also { it.eraseColor(SK_ColorBLACK) }

        assertTrue(canvas.readPixels(dst, srcX = 2, srcY = 1))

        assertEquals(colorAt(2, 1), dst.getPixel(0, 0))
        assertEquals(colorAt(3, 1), dst.getPixel(1, 0))
        assertEquals(SK_ColorBLACK, dst.getPixel(2, 0))
        assertEquals(colorAt(2, 3), dst.getPixel(0, 2))
        assertEquals(colorAt(3, 3), dst.getPixel(1, 2))
        assertEquals(SK_ColorBLACK, dst.getPixel(2, 2))
    }

    @Test
    fun `readPixels bitmap accepts negative source offsets when rectangles overlap`() {
        val canvas = gradientCanvas(4, 4)
        val dst = SkBitmap(3, 3).also { it.eraseColor(SK_ColorBLACK) }

        assertTrue(canvas.readPixels(dst, srcX = -1, srcY = -1))

        assertEquals(SK_ColorBLACK, dst.getPixel(0, 0))
        assertEquals(SK_ColorBLACK, dst.getPixel(1, 0))
        assertEquals(SK_ColorBLACK, dst.getPixel(0, 1))
        assertEquals(colorAt(0, 0), dst.getPixel(1, 1))
        assertEquals(colorAt(1, 1), dst.getPixel(2, 2))
    }

    @Test
    fun `readPixels returns false when source and destination do not overlap`() {
        val canvas = gradientCanvas(4, 4)
        val dst = SkBitmap(3, 3)

        assertFalse(canvas.readPixels(dst, srcX = 4, srcY = 0))
        assertFalse(canvas.readPixels(dst, srcX = -3, srcY = 0))
        assertFalse(canvas.readPixels(dst, srcX = 0, srcY = 4))
        assertFalse(canvas.readPixels(dst, srcX = 0, srcY = -3))
    }

    @Test
    fun `writePixels bitmap clips source pixels to the canvas bounds`() {
        val canvas = SkCanvas(SkBitmap(4, 4).also { it.eraseColor(SK_ColorBLACK) })
        val src = SkBitmap(3, 3)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                src.setPixel(x, y, colorAt(x, y))
            }
        }

        assertTrue(canvas.writePixels(src, dstX = -1, dstY = -1))

        assertEquals(colorAt(1, 1), canvas.bitmap.getPixel(0, 0))
        assertEquals(colorAt(2, 1), canvas.bitmap.getPixel(1, 0))
        assertEquals(colorAt(1, 2), canvas.bitmap.getPixel(0, 1))
        assertEquals(colorAt(2, 2), canvas.bitmap.getPixel(1, 1))
        assertEquals(SK_ColorBLACK, canvas.bitmap.getPixel(2, 2))
    }

    @Test
    fun `writePixels returns false when bitmap source and canvas do not overlap`() {
        val canvas = SkCanvas(SkBitmap(4, 4))
        val src = SkBitmap(2, 2)

        assertFalse(canvas.writePixels(src, dstX = 4, dstY = 0))
        assertFalse(canvas.writePixels(src, dstX = -2, dstY = 0))
        assertFalse(canvas.writePixels(src, dstX = 0, dstY = 4))
        assertFalse(canvas.writePixels(src, dstX = 0, dstY = -2))
    }

    @Test
    fun `readPixels pixmap supports negative source offsets`() {
        val canvas = gradientCanvas(4, 4)
        val info = SkImageInfo.Make(3, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val pixmap = SkPixmap(info, ByteBuffer.allocate(info.minRowBytes() * info.height), info.minRowBytes())
        pixmap.erase(SK_ColorBLACK)

        assertTrue(canvas.readPixels(pixmap, srcX = -1, srcY = -1))

        assertEquals(SK_ColorBLACK, pixmap.getColor(0, 0))
        assertEquals(colorAt(0, 0), pixmap.getColor(1, 1))
        assertEquals(colorAt(1, 1), pixmap.getColor(2, 2))
    }

    @Test
    fun `writePixels pixmap writes through the raw pixel view`() {
        val canvas = SkCanvas(SkBitmap(3, 3).also { it.eraseColor(SK_ColorBLACK) })
        val info = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val pixmap = SkPixmap(
            info,
            ByteBuffer.allocate(info.minRowBytes() * info.height).order(ByteOrder.LITTLE_ENDIAN),
            info.minRowBytes(),
        )
        pixmap.erase(colorAt(7, 9))

        assertTrue(canvas.writePixels(pixmap, dstX = 1, dstY = 1))

        assertEquals(SK_ColorBLACK, canvas.bitmap.getPixel(0, 0))
        assertEquals(colorAt(7, 9), canvas.bitmap.getPixel(1, 1))
        assertEquals(colorAt(7, 9), canvas.bitmap.getPixel(2, 2))
    }

    private fun gradientCanvas(width: Int, height: Int): SkCanvas {
        val bitmap = SkBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, colorAt(x, y))
            }
        }
        return SkCanvas(bitmap)
    }

    private fun colorAt(x: Int, y: Int): Int =
        SkColorSetARGB(0xFF, (x * 53) and 0xFF, (y * 67) and 0xFF, ((x + y) * 29) and 0xFF)
}
