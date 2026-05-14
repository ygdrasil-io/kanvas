package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.core.SkSurface

/**
 * S7-C — verifies that [SkSurfaceProps] round-trips through the
 * [SkSurface] / [SkCanvas] surface-props plumbing :
 *
 *  - default [SkSurface.MakeRaster] surfaces report a zero-filled
 *    [SkSurfaceProps] (`flags = 0`, `pixelGeometry = kUnknown`).
 *  - explicit props passed to [SkSurface.MakeRaster] survive on
 *    [SkSurface.props] **and** on the canvas's [SkCanvas.surfaceProps].
 *  - data-class equality is value-based.
 */
class SkSurfacePropsTest {

    @Test
    fun `default-constructed SkSurfaceProps matches upstream zero-fill`() {
        val p = SkSurfaceProps()
        assertEquals(0, p.flags)
        assertEquals(SkSurfaceProps.SkPixelGeometry.kUnknown, p.pixelGeometry)
    }

    @Test
    fun `MakeRaster without props reports zero-fill props`() {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(20, 20))
        val props = surface.props()
        assertEquals(0, props.flags)
        assertEquals(SkSurfaceProps.SkPixelGeometry.kUnknown, props.pixelGeometry)
    }

    @Test
    fun `MakeRaster with explicit props round-trips through surface and canvas`() {
        val custom = SkSurfaceProps(
            flags = SkSurfaceProps.kUseDeviceIndependentFonts_Flag,
            pixelGeometry = SkSurfaceProps.SkPixelGeometry.kRGB_H,
        )
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(20, 20), custom)
        assertEquals(custom, surface.props())
        // The canvas inherits from the surface — same value, possibly
        // a copy (we assert value-equality, not identity).
        assertEquals(custom, surface.canvas.surfaceProps())
    }

    @Test
    fun `data-class equality is value-based`() {
        val a = SkSurfaceProps(0, SkSurfaceProps.SkPixelGeometry.kBGR_V)
        val b = SkSurfaceProps(0, SkSurfaceProps.SkPixelGeometry.kBGR_V)
        val c = SkSurfaceProps(1, SkSurfaceProps.SkPixelGeometry.kBGR_V)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `SkPixelGeometry enum carries all five upstream values`() {
        // Names match upstream `k…_SkPixelGeometry` exactly.
        val expected = setOf(
            SkSurfaceProps.SkPixelGeometry.kUnknown,
            SkSurfaceProps.SkPixelGeometry.kRGB_H,
            SkSurfaceProps.SkPixelGeometry.kBGR_H,
            SkSurfaceProps.SkPixelGeometry.kRGB_V,
            SkSurfaceProps.SkPixelGeometry.kBGR_V,
        )
        val actual = SkSurfaceProps.SkPixelGeometry.values().toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `surface props returns a stable value across calls`() {
        val custom = SkSurfaceProps(0, SkSurfaceProps.SkPixelGeometry.kRGB_V)
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(8, 8), custom)
        val a = surface.props()
        val b = surface.props()
        assertEquals(a, b)
        // Same canvas instance returns same surfaceProps() value.
        assertSame(surface.canvas, surface.canvas)
    }
}
