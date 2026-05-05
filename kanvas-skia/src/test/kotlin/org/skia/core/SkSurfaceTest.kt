package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

/**
 * Unit tests for [SkSurface] — host-owned raster destination + canvas
 * vendor + immutable image snapshot.
 *
 * Coverage :
 *  - [SkSurface.MakeRaster] allocates a fresh bitmap matching the
 *    requested [SkImageInfo].
 *  - [SkSurface.MakeRasterDirect] wraps an externally owned bitmap
 *    without copy (writes through `surface.canvas` mutate the supplied
 *    bitmap).
 *  - [SkSurface.canvas] returns the same instance across calls.
 *  - [SkSurface.makeImageSnapshot] captures the current pixel state and
 *    survives subsequent mutation.
 *  - [SkSurface.draw] composites this surface's snapshot onto another
 *    canvas at the supplied offset.
 */
class SkSurfaceTest {

    @Test
    fun `MakeRaster allocates a fresh bitmap matching the SkImageInfo`() {
        val info = SkImageInfo.MakeN32Premul(20, 30)
        val surface = SkSurface.MakeRaster(info)
        assertEquals(20, surface.width)
        assertEquals(30, surface.height)
        assertEquals(info, surface.imageInfo())
    }

    @Test
    fun `MakeRasterDirect writes go through to the supplied bitmap`() {
        val bitmap = SkBitmap(8, 8).also { it.eraseColor(SK_ColorWHITE) }
        val surface = SkSurface.MakeRasterDirect(bitmap)
        surface.canvas.drawRect(SkRect.MakeWH(8f, 8f), SkPaint(SK_ColorRED))
        for (i in bitmap.pixels.indices) {
            assertEquals(SK_ColorRED, bitmap.pixels[i], "pixel $i")
        }
    }

    @Test
    fun `canvas property returns the same SkCanvas instance across calls`() {
        val surface = SkSurface.MakeRasterN32Premul(4, 4)
        assertSame(surface.canvas, surface.canvas)
    }

    @Test
    fun `makeImageSnapshot captures current state and survives subsequent mutation`() {
        val bitmap = SkBitmap(4, 4).also { it.eraseColor(SK_ColorRED) }
        val surface = SkSurface.MakeRasterDirect(bitmap)
        // Snapshot the RED state.
        val snapshot = surface.makeImageSnapshot()
        // Now overwrite the surface in BLUE — the snapshot must keep RED.
        surface.canvas.drawRect(SkRect.MakeWH(4f, 4f), SkPaint(SK_ColorBLUE))
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(SK_ColorRED, snapshot.peekPixel(x, y), "snapshot ($x,$y) should still be RED")
                assertEquals(SK_ColorBLUE, bitmap.getPixel(x, y), "live ($x,$y) should now be BLUE")
            }
        }
    }

    @Test
    fun `successive snapshots are independent SkImage instances`() {
        val surface = SkSurface.MakeRasterN32Premul(4, 4)
        val a = surface.makeImageSnapshot()
        val b = surface.makeImageSnapshot()
        assertNotSame(a, b)
    }

    @Test
    fun `draw composites this surface onto another canvas at the supplied offset`() {
        // Source surface : 4×4 RED.
        val src = SkSurface.MakeRasterN32Premul(4, 4)
        src.canvas.drawRect(SkRect.MakeWH(4f, 4f), SkPaint(SK_ColorRED))

        // Destination surface : 10×10 WHITE.
        val dstBitmap = SkBitmap(10, 10).also { it.eraseColor(SK_ColorWHITE) }
        val dst = SkSurface.MakeRasterDirect(dstBitmap)

        // Composite src onto dst at (3, 4).
        src.draw(dst.canvas, x = 3f, y = 4f, paint = null)

        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected = if (x in 3 until 7 && y in 4 until 8) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, dstBitmap.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `MakeRasterN32Premul is equivalent to MakeRaster with N32 premul info`() {
        val a = SkSurface.MakeRasterN32Premul(8, 8)
        val b = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(8, 8))
        assertEquals(a.imageInfo(), b.imageInfo())
        // Different surface instances though.
        assertNotEquals(a, b)
    }
}
