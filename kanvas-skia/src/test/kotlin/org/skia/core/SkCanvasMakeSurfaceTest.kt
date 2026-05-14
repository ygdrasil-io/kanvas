package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSurfaceProps

/**
 * S7-C — verifies [SkCanvas.makeSurface] :
 *
 *  - returns a fresh raster surface with the requested [SkImageInfo].
 *  - sub-surface inherits the canvas's [SkSurfaceProps] when no
 *    override is passed.
 *  - explicit `props` argument overrides the inherited value.
 *  - empty image-info returns `null` (mirrors upstream's contract).
 */
class SkCanvasMakeSurfaceTest {

    @Test
    fun `makeSurface returns a fresh raster surface with the requested info`() {
        val parent = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(40, 40))
        val sub = parent.canvas.makeSurface(SkImageInfo.MakeN32Premul(10, 10))
        assertNotNull(sub)
        assertEquals(10, sub!!.width)
        assertEquals(10, sub.height)
    }

    @Test
    fun `sub-surface inherits parent canvas surfaceProps when override is null`() {
        val custom = SkSurfaceProps(
            flags = 0,
            pixelGeometry = SkSurfaceProps.SkPixelGeometry.kRGB_H,
        )
        val parent = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(40, 40), custom)
        val sub = parent.canvas.makeSurface(SkImageInfo.MakeN32Premul(10, 10))
        assertNotNull(sub)
        assertEquals(custom, sub!!.props())
    }

    @Test
    fun `explicit props override the inherited value`() {
        val parentProps = SkSurfaceProps(0, SkSurfaceProps.SkPixelGeometry.kRGB_H)
        val parent = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(40, 40), parentProps)
        val overrideProps = SkSurfaceProps(0, SkSurfaceProps.SkPixelGeometry.kBGR_V)
        val sub = parent.canvas.makeSurface(
            SkImageInfo.MakeN32Premul(10, 10),
            overrideProps,
        )
        assertNotNull(sub)
        assertEquals(overrideProps, sub!!.props())
    }

    @Test
    fun `empty info returns null`() {
        val parent = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(40, 40))
        val sub = parent.canvas.makeSurface(SkImageInfo.MakeN32Premul(0, 0))
        assertNull(sub)
    }

    @Test
    fun `default canvas with no props yields zero-fill props on sub-surface`() {
        val parent = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(40, 40))
        val sub = parent.canvas.makeSurface(SkImageInfo.MakeN32Premul(8, 8))
        assertNotNull(sub)
        assertEquals(SkSurfaceProps(), sub!!.props())
    }
}
