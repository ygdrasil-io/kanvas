package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkIPoint
import org.skia.math.SkIRect

/**
 * R-final.9 — sanity tests for [SkImages.MakeWithFilter] and
 * [org.skia.core.SkSurface.makeTemporaryImage].
 */
class SkImagesMakeWithFilterTest {

    private fun solidImage(w: Int, h: Int, argb: Int): SkImage {
        val bm = SkBitmap(w, h)
        for (y in 0 until h) for (x in 0 until w) bm.setPixel(x, y, argb)
        return SkImage.Make(bm)
    }

    @Test
    fun `MakeWithFilter returns null when filter is null`() {
        val img = solidImage(16, 16, 0xFF112233.toInt())
        val out = SkImages.MakeWithFilter(
            img, null,
            SkIRect.MakeWH(16, 16),
            SkIRect.MakeWH(16, 16),
        )
        assertNull(out)
    }

    @Test
    fun `MakeWithFilter through a small blur returns a clipped subset`() {
        val img = solidImage(32, 32, 0xFFAABBCC.toInt())
        val blur = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
        assertNotNull(blur)
        val outSubset = SkIRect(0, 0, 0, 0)
        val outOffset = SkIPoint(0, 0)
        val result = SkImages.MakeWithFilter(
            img, blur,
            SkIRect.MakeWH(32, 32),
            SkIRect.MakeWH(32, 32),
            outSubset,
            outOffset,
        )
        assertNotNull(result)
        // The intersection is the full image, so the offset lands at
        // (0,0) and the subset spans the whole 32x32 footprint.
        assertEquals(0, outOffset.fX)
        assertEquals(0, outOffset.fY)
        assertTrue(outSubset.width() > 0)
        assertTrue(outSubset.height() > 0)
    }

    @Test
    fun `makeTemporaryImage produces a snapshot equivalent`() {
        val info = SkImageInfo.Make(8, 8)
        val surface = org.skia.core.SkSurface.MakeRaster(info)
        // Draw a single non-default pixel into the surface.
        surface.canvas.clear(0xFF112233.toInt())
        val tmp = surface.makeTemporaryImage()
        assertEquals(8, tmp.width)
        assertEquals(8, tmp.height)
        // The snapshot pixel should match the cleared color.
        val px = tmp.pixels[0]
        assertEquals(0xFF112233.toInt(), px)
    }
}
