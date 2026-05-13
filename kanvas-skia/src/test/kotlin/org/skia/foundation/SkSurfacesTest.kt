package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect
import java.nio.ByteBuffer

/**
 * Phase R2 batch3-B — unit tests for [SkSurfaces] static factories.
 *
 * Coverage :
 *  - [SkSurfaces.Raster] allocates a fresh raster surface and rejects
 *    invalid image-info.
 *  - [SkSurfaces.WrapPixels] reads pixels out of a `ByteBuffer` honouring
 *    `rowBytes`, invokes the release proc once, and produces a writable
 *    surface that mutates a fresh backing bitmap (no shared ByteBuffer).
 *  - [SkSurfaces.Null] returns a discard surface whose canvas swallows
 *    draws and whose snapshot is all-zero.
 *  - The `SkPixmap`-overload of [SkSurfaces.WrapPixels] stubs out until
 *    R2 batch3-A lands the type.
 */
class SkSurfacesTest {

    @Test
    fun `Raster allocates a fresh surface matching the SkImageInfo`() {
        val info = SkImageInfo.MakeN32Premul(20, 30)
        val surface = SkSurfaces.Raster(info)
        assertNotNull(surface)
        assertEquals(20, surface!!.width)
        assertEquals(30, surface.height)
        assertEquals(info, surface.imageInfo())
    }

    @Test
    fun `Raster rejects empty SkImageInfo`() {
        val info = SkImageInfo.MakeN32Premul(0, 30)
        assertNull(SkSurfaces.Raster(info))
    }

    @Test
    fun `Raster rejects rowBytes smaller than minRowBytes`() {
        val info = SkImageInfo.MakeN32Premul(4, 4)
        // minRowBytes for 4×4 8888 = 16. rowBytes=8 is invalid.
        assertNull(SkSurfaces.Raster(info, rowBytes = 8))
    }

    @Test
    fun `WrapPixels reads a ByteBuffer into a writable raster surface`() {
        val width = 4
        val height = 4
        val rowBytes = 16
        val buf = ByteBuffer.allocate(rowBytes * height)
        for (y in 0 until height) for (x in 0 until width) {
            val off = y * rowBytes + x * 4
            buf.put(off, 0x11.toByte()) // R
            buf.put(off + 1, 0x22.toByte()) // G
            buf.put(off + 2, 0x33.toByte()) // B
            buf.put(off + 3, 0xFF.toByte()) // A
        }
        val info = SkImageInfo.Make(width, height, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)

        var released = false
        val surface = SkSurfaces.WrapPixels(info, buf, rowBytes) { released = true }
        assertNotNull(surface)
        assertEquals(width, surface!!.width)
        assertEquals(height, surface.height)
        // The release proc fires immediately (eager-copy semantics).
        assertTrue(released, "releaseProc must be invoked after copying")

        // Snapshot the initial state — the (0x11, 0x22, 0x33, 0xFF) pixels
        // must match what the buffer contained.
        val snapshot = surface.makeImageSnapshot()
        val expected = (0xFF shl 24) or (0x11 shl 16) or (0x22 shl 8) or 0x33
        for (y in 0 until height) for (x in 0 until width) {
            assertEquals(expected, snapshot.peekPixel(x, y), "($x, $y)")
        }

        // The surface is writable — draw a fully-opaque red rectangle
        // and verify the live pixels change (proves WrapPixels really
        // produces a draw-able raster surface, not a no-op like Null).
        val redPaint = SkPaint(SK_ColorRED).apply { blendMode = SkBlendMode.kSrc }
        surface.canvas.drawRect(SkRect.MakeWH(width.toFloat(), height.toFloat()), redPaint)
        val snapshotAfter = surface.makeImageSnapshot()
        for (y in 0 until height) for (x in 0 until width) {
            assertEquals(SK_ColorRED, snapshotAfter.peekPixel(x, y), "($x, $y) after draw")
        }
    }

    @Test
    fun `WrapPixels rejects undersized ByteBuffer`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = ByteBuffer.allocate(8)
        assertNull(SkSurfaces.WrapPixels(info, buf, rowBytes = 16))
    }

    @Test
    fun `WrapPixels rejects rowBytes smaller than one row`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = ByteBuffer.allocate(4 * 16)
        assertNull(SkSurfaces.WrapPixels(info, buf, rowBytes = 8))
    }

    @Test
    fun `Null returns a discard surface whose snapshot is all-zero`() {
        val surface = SkSurfaces.Null(8, 8)
        assertEquals(8, surface.width)
        assertEquals(8, surface.height)
        // The canvas accepts draw calls — exercising it must not throw.
        surface.canvas.drawRect(SkRect.MakeWH(8f, 8f), SkPaint(0xFFFFFFFF.toInt()))
        // The snapshot is a fresh all-zero image (matches upstream "no
        // useful pixels" semantics, collapsed onto a non-null sentinel).
        val snapshot = surface.makeImageSnapshot()
        assertEquals(8, snapshot.width)
        for (y in 0 until 8) for (x in 0 until 8) {
            assertEquals(0, snapshot.peekPixel(x, y), "($x, $y) must be all-zero")
        }
    }

    @Test
    fun `Null rejects non-positive dimensions`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkSurfaces.Null(0, 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkSurfaces.Null(8, -1)
        }
    }

    @Test
    fun `WrapPixels pixmap overload stub throws TODO until SkPixmap lands`() {
        assertThrows(NotImplementedError::class.java) {
            SkSurfaces.WrapPixels(Any())
        }
    }
}
