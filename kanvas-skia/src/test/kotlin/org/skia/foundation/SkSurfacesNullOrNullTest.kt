package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.math.SkRect

/**
 * Phase R-suivi.14 — exercises [SkSurfaces.NullOrNull], the nullable
 * variant of [SkSurfaces.Null] that mirrors upstream's
 * `SkSurfaces::Null(width, height)` nullable contract verbatim :
 * returns `null` when either dimension is less than 1, otherwise a
 * discard surface identical to [SkSurfaces.Null]'s output.
 */
class SkSurfacesNullOrNullTest {

    @Test
    fun `NullOrNull returns null for zero width`() {
        assertNull(SkSurfaces.NullOrNull(0, 10))
    }

    @Test
    fun `NullOrNull returns null for zero height`() {
        assertNull(SkSurfaces.NullOrNull(10, 0))
    }

    @Test
    fun `NullOrNull returns null for both zero`() {
        assertNull(SkSurfaces.NullOrNull(0, 0))
    }

    @Test
    fun `NullOrNull returns null for negative width`() {
        assertNull(SkSurfaces.NullOrNull(-5, 10))
    }

    @Test
    fun `NullOrNull returns null for negative height`() {
        assertNull(SkSurfaces.NullOrNull(10, -5))
    }

    @Test
    fun `NullOrNull returns a discard surface for positive dimensions`() {
        val surface = SkSurfaces.NullOrNull(10, 10)
        assertNotNull(surface)
        assertEquals(10, surface!!.width)
        assertEquals(10, surface.height)
    }

    @Test
    fun `NullOrNull surface canvas accepts draws without throwing`() {
        val surface = SkSurfaces.NullOrNull(10, 10)
        assertNotNull(surface)
        // Drawing must not throw — mirrors upstream's SkNoDrawCanvas behaviour.
        surface!!.canvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `NullOrNull snapshot returns a zero-pixel image (matches Null behaviour)`() {
        // Fidelity note (R-suivi.14) : upstream's onNewImageSnapshot
        // returns nullptr ; kanvas-skia returns a zero-pixel image
        // (transparent black) — semantically equivalent.
        val surface = SkSurfaces.NullOrNull(4, 4)
        assertNotNull(surface)
        val snap = surface!!.makeImageSnapshot()
        assertEquals(4, snap.width)
        assertEquals(4, snap.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(0, snap.peekPixel(x, y), "($x, $y) must be transparent black")
        }
    }
}
