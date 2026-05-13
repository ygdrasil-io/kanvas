package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect

/**
 * Phase R1-C — point-in-path tests for [SkPath.contains]. Mirrors Skia's
 * `SkPath::contains` test suite (`tests/PathTest.cpp::test_contains`).
 *
 * Covers : axis-aligned rectangle, triangle (winding rule), even-odd
 * concentric rings, oval, and edge-cases (point on boundary, empty
 * path, inverse fill).
 */
class SkPathContainsTest {

    @Test
    fun `contains inside a rectangle returns true`() {
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 20f, 50f, 60f))
        assertTrue(path.contains(30f, 40f), "center inside")
        assertTrue(path.contains(15f, 25f), "near-corner inside")
    }

    @Test
    fun `contains outside a rectangle returns false`() {
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 20f, 50f, 60f))
        assertFalse(path.contains(0f, 0f), "well outside top-left")
        assertFalse(path.contains(100f, 100f), "well outside bottom-right")
        assertFalse(path.contains(5f, 40f), "outside left edge")
        assertFalse(path.contains(60f, 40f), "outside right edge")
    }

    @Test
    fun `contains on an oval matches geometric inside`() {
        val path = SkPath.Oval(SkRect.MakeLTRB(0f, 0f, 100f, 100f))
        // Center of the ellipse — clearly inside.
        assertTrue(path.contains(50f, 50f), "center of oval")
        // Near the cardinal — still inside.
        assertTrue(path.contains(50f, 10f), "near-top cardinal interior")
        // Corner of the bbox — *outside* the oval (lies in the corner gap).
        assertFalse(path.contains(2f, 2f), "bbox top-left corner is outside oval")
    }

    @Test
    fun `contains on a triangle obeys the winding rule`() {
        // CCW triangle (0,0)-(100,0)-(50,100) — kWinding by default.
        val path = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(100f, 0f).lineTo(50f, 100f).close()
            .detach()
        assertTrue(path.contains(50f, 30f), "interior of CCW triangle")
        assertFalse(path.contains(0f, 100f), "outside CCW triangle")
    }

    @Test
    fun `contains on an even-odd concentric rect pair excludes the inner hole`() {
        // Outer rect + inner rect, both CW — under kEvenOdd the inner
        // pixels are *outside* (one crossing each from the outer + inner
        // edges => parity 0 inside the inner rect).
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f))
            .addRect(SkRect.MakeLTRB(40f, 40f, 60f, 60f))
            .detach()
        assertTrue(path.contains(20f, 20f), "outer ring is inside")
        assertFalse(path.contains(50f, 50f), "inner hole is outside under even-odd")
    }

    @Test
    fun `contains on an empty path returns false (non-inverse)`() {
        val path = SkPathBuilder().detach()
        assertFalse(path.contains(0f, 0f))
    }

    @Test
    fun `contains on an inverse fill type returns true for points outside the geometry`() {
        // Outer rect + inverse winding => "inside" set is the complement.
        val path = SkPath.Rect(
            SkRect.MakeLTRB(10f, 20f, 50f, 60f),
            SkPathFillType.kInverseWinding,
        )
        assertFalse(path.contains(30f, 40f), "inside the geometry => OUT of inverse fill")
        assertTrue(path.contains(0f, 0f), "outside the geometry => IN of inverse fill")
    }
}
