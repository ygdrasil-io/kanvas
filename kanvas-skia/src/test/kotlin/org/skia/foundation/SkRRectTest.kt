package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkVector

class SkRRectTest {

    private fun rect(l: Float, t: Float, r: Float, b: Float): SkRect = SkRect.MakeLTRB(l, t, r, b)

    @Test
    fun `MakeEmpty has type kEmpty`() {
        val rr = SkRRect.MakeEmpty()
        assertTrue(rr.isEmpty())
        assertEquals(SkRRect.Type.kEmpty_Type, rr.getType())
        assertEquals(0f, rr.width())
        assertEquals(0f, rr.height())
    }

    @Test
    fun `MakeRect with non-empty rect classifies as kRect`() {
        val rr = SkRRect.MakeRect(rect(0f, 0f, 10f, 5f))
        assertTrue(rr.isRect())
        assertEquals(SkRRect.Type.kRect_Type, rr.getType())
        assertEquals(10f, rr.width())
        assertEquals(5f, rr.height())
        // All radii zeroed
        for (corner in SkRRect.Corner.entries) {
            assertEquals(SkPoint(0f, 0f), rr.radii(corner))
        }
    }

    @Test
    fun `MakeRect with empty rect degenerates to kEmpty`() {
        val rr = SkRRect.MakeRect(rect(5f, 5f, 5f, 10f)) // zero width
        assertTrue(rr.isEmpty())
    }

    @Test
    fun `MakeOval sets all radii to half the rect dims`() {
        val rr = SkRRect.MakeOval(rect(0f, 0f, 10f, 6f))
        assertTrue(rr.isOval())
        assertEquals(SkVector(5f, 3f), rr.getSimpleRadii())
        for (corner in SkRRect.Corner.entries) {
            assertEquals(SkVector(5f, 3f), rr.radii(corner))
        }
    }

    @Test
    fun `MakeRectXY with both radii zero classifies as kRect`() {
        val rr = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 5f), 0f, 0f)
        assertTrue(rr.isRect())
    }

    @Test
    fun `MakeRectXY with single corner radius is kSimple`() {
        val rr = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 5f), 1f, 2f)
        assertTrue(rr.isSimple())
        assertEquals(SkVector(1f, 2f), rr.getSimpleRadii())
    }

    @Test
    fun `MakeRectXY with radii covering the rect is kOval`() {
        val rr = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 6f), 5f, 3f)
        assertTrue(rr.isOval())
    }

    @Test
    fun `MakeRectRadii with axis-aligned ninepatch pattern is kNinePatch`() {
        // Canonical 9-patch: 4 scalars (l, r, t, b) determining the four
        // corners as (l,t), (r,t), (r,b), (l,b).
        val l = 2f; val r = 5f; val t = 3f; val b = 4f
        val radii = arrayOf(
            SkVector(l, t),  // top-left
            SkVector(r, t),  // top-right
            SkVector(r, b),  // bottom-right
            SkVector(l, b),  // bottom-left
        )
        val rr = SkRRect.MakeRectRadii(rect(0f, 0f, 20f, 20f), radii)
        assertTrue(rr.isNinePatch())
    }

    @Test
    fun `MakeRectRadii with truly varied radii is kComplex`() {
        val radii = arrayOf(
            SkVector(1f, 2f),
            SkVector(3f, 4f),
            SkVector(5f, 6f),
            SkVector(7f, 8f),
        )
        val rr = SkRRect.MakeRectRadii(rect(0f, 0f, 50f, 50f), radii)
        assertTrue(rr.isComplex())
    }

    @Test
    fun `setRectRadii negative values clamp to zero`() {
        val radii = arrayOf(
            SkVector(-1f, -2f),
            SkVector(0f, 0f),
            SkVector(0f, 0f),
            SkVector(0f, 0f),
        )
        val rr = SkRRect.MakeRectRadii(rect(0f, 0f, 10f, 10f), radii)
        assertTrue(rr.isRect()) // all clamped to zero
    }

    @Test
    fun `scaleRadii clamps overlapping radii to fit the rect`() {
        // Radii sum 30 across a 10-wide rect → scale ~ 1/3. After scaling
        // all radii equal → kSimple (or kOval if covering the rect).
        val radii = arrayOf(
            SkVector(15f, 15f),
            SkVector(15f, 15f),
            SkVector(15f, 15f),
            SkVector(15f, 15f),
        )
        val rr = SkRRect.MakeRectRadii(rect(0f, 0f, 10f, 10f), radii)
        // After scaling, each radius = 15 * (10/30) = 5 → covers half the rect → kOval.
        assertTrue(rr.isOval())
        assertEquals(5f, rr.getSimpleRadii().fX, 1e-5f)
        assertEquals(5f, rr.getSimpleRadii().fY, 1e-5f)
    }

    @Test
    fun `setNinePatch builds a ninepatch when sides differ`() {
        val rr = SkRRect()
        rr.setNinePatch(rect(0f, 0f, 20f, 20f), leftRad = 1f, topRad = 2f, rightRad = 3f, bottomRad = 4f)
        assertTrue(rr.isNinePatch())
        assertEquals(SkVector(1f, 2f), rr.radii(SkRRect.Corner.kUpperLeft_Corner))
        assertEquals(SkVector(3f, 2f), rr.radii(SkRRect.Corner.kUpperRight_Corner))
        assertEquals(SkVector(3f, 4f), rr.radii(SkRRect.Corner.kLowerRight_Corner))
        assertEquals(SkVector(1f, 4f), rr.radii(SkRRect.Corner.kLowerLeft_Corner))
    }

    @Test
    fun `radii returns defensive copies`() {
        val rr = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 1f, 2f)
        val r = rr.radii(SkRRect.Corner.kUpperLeft_Corner)
        r.set(99f, 99f) // mutate the copy
        // Internal radii unchanged
        assertEquals(SkVector(1f, 2f), rr.radii(SkRRect.Corner.kUpperLeft_Corner))
    }

    @Test
    fun `setRect sorts unsorted input rects`() {
        val rr = SkRRect()
        rr.setRect(rect(10f, 20f, 0f, 5f)) // l > r, t > b
        assertTrue(rr.isRect())
        assertEquals(0f, rr.rect().left)
        assertEquals(5f, rr.rect().top)
        assertEquals(10f, rr.rect().right)
        assertEquals(20f, rr.rect().bottom)
    }

    @Test
    fun `setEmpty resets state`() {
        val rr = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 2f)
        assertFalse(rr.isEmpty())
        rr.setEmpty()
        assertTrue(rr.isEmpty())
        for (corner in SkRRect.Corner.entries) {
            assertEquals(SkVector(0f, 0f), rr.radii(corner))
        }
    }

    @Test
    fun `equals compares rect plus all four radii`() {
        val a = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 3f)
        val b = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 3f)
        val c = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 4f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
