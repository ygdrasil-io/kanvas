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

    // ── Iso-alignment with Skia ─────────────────────────────────────────

    @Test
    fun `offset translates rect leaves radii unchanged`() {
        val a = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 3f)
        a.offset(5f, 7f)
        assertEquals(SkRect.MakeLTRB(5f, 7f, 15f, 17f), a.rect())
        assertEquals(SkPoint(2f, 3f), a.getSimpleRadii())
    }

    @Test
    fun `makeOffset returns a copy untouched source`() {
        val a = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 3f)
        val b = a.makeOffset(5f, 7f)
        assertEquals(SkRect.MakeLTRB(5f, 7f, 15f, 17f), b.rect())
        assertEquals(SkRect.MakeLTRB(0f, 0f, 10f, 10f), a.rect())
    }

    @Test
    fun `inset shrinks bounds and radii`() {
        val a = SkRRect.MakeRectXY(rect(0f, 0f, 100f, 100f), 20f, 20f)
        a.inset(5f, 5f)
        assertEquals(SkRect.MakeLTRB(5f, 5f, 95f, 95f), a.rect())
        // 20 - 5 = 15, both axes
        assertEquals(SkPoint(15f, 15f), a.getSimpleRadii())
    }

    @Test
    fun `outset is the inverse of inset for non-degenerate cases`() {
        val a = SkRRect.MakeRectXY(rect(0f, 0f, 100f, 100f), 20f, 20f)
        a.inset(5f, 5f)
        a.outset(5f, 5f)
        assertEquals(SkRect.MakeLTRB(0f, 0f, 100f, 100f), a.rect())
    }

    @Test
    fun `inset that collapses width yields kEmpty_Type`() {
        val a = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 2f)
        a.inset(20f, 0f)
        assertTrue(a.isEmpty())
    }

    @Test
    fun `inset preserves zero radii (square corners stay square)`() {
        // Skia's inset only subtracts dx/dy from non-zero radii. A
        // square corner should remain square after inset.
        val r = rect(0f, 0f, 100f, 100f)
        val radii = arrayOf<SkVector>(
            SkPoint(0f, 0f),    // UL: square
            SkPoint(20f, 20f),  // UR: rounded
            SkPoint(20f, 20f),  // LR: rounded
            SkPoint(0f, 0f),    // LL: square
        )
        val a = SkRRect.MakeRectRadii(r, radii)
        a.inset(5f, 5f)
        assertEquals(SkPoint(0f, 0f), a.radii(SkRRect.Corner.kUpperLeft_Corner))
        assertEquals(SkPoint(15f, 15f), a.radii(SkRRect.Corner.kUpperRight_Corner))
    }

    @Test
    fun `contains rect inside fully rounded outer`() {
        val outer = SkRRect.MakeRectXY(rect(0f, 0f, 100f, 100f), 10f, 10f)
        // Centered rect well inside the corner curves — should be contained.
        assertTrue(outer.contains(rect(20f, 20f, 80f, 80f)))
    }

    @Test
    fun `contains rect that pokes into rounded corner is rejected`() {
        val outer = SkRRect.MakeRectXY(rect(0f, 0f, 100f, 100f), 50f, 50f)
        // A square rect spanning the full outer bounds will hit each
        // corner's rounded arc — not contained.
        assertFalse(outer.contains(rect(0f, 0f, 100f, 100f)))
    }

    @Test
    fun `contains rect on plain SkRect-type rrect short-circuits`() {
        val outer = SkRRect.MakeRect(rect(0f, 0f, 100f, 100f))
        assertTrue(outer.contains(rect(10f, 10f, 90f, 90f)))
        assertFalse(outer.contains(rect(50f, 50f, 110f, 90f)))
    }

    @Test
    fun `getBounds is alias for rect`() {
        val a = SkRRect.MakeRectXY(rect(1f, 2f, 9f, 8f), 1f, 1f)
        assertEquals(a.rect(), a.getBounds())
    }

    @Test
    fun `setRectXY non-finite radii falls back to plain rect`() {
        val rr = SkRRect()
        rr.setRectXY(rect(0f, 0f, 10f, 10f), Float.NaN, 5f)
        assertTrue(rr.isRect())
        assertEquals(SkPoint(0f, 0f), rr.getSimpleRadii())
    }

    @Test
    fun `setRectRadii non-finite radii falls back to plain rect`() {
        val rr = SkRRect()
        rr.setRectRadii(rect(0f, 0f, 10f, 10f), arrayOf(
            SkPoint(2f, 2f), SkPoint(2f, 2f),
            SkPoint(2f, Float.POSITIVE_INFINITY), SkPoint(2f, 2f),
        ))
        assertTrue(rr.isRect())
    }

    @Test
    fun `setRectXY too-large radii scale down and stay simple`() {
        // Rect is 10x10 but radii are 8 each — sum 16 > 10, so scaleRadii
        // applies factor 10/16 = 0.625 → radii become 5,5 → kSimple_Type.
        val rr = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 8f, 8f)
        // Skia's setRectXY does the scale inline before falling through.
        // After scale, rx == 5 == w/2, ry == 5 == h/2 ⇒ should report kOval_Type.
        assertTrue(rr.isOval(), "radii saturating to half-side should mark kOval, got ${rr.getType()}")
    }

    @Test
    fun `isValid catches manual mismatches`() {
        // A correctly built rrect is valid.
        val good = SkRRect.MakeRectXY(rect(0f, 0f, 10f, 10f), 2f, 3f)
        assertTrue(good.isValid())
        assertTrue(SkRRect.MakeEmpty().isValid())
        assertTrue(SkRRect.MakeRect(rect(0f, 0f, 10f, 10f)).isValid())
        assertTrue(SkRRect.MakeOval(rect(0f, 0f, 10f, 10f)).isValid())
    }

    @Test
    fun `AreRectAndRadiiValid rejects out-of-bounds radii`() {
        val r = rect(0f, 0f, 10f, 10f)
        assertTrue(SkRRect.AreRectAndRadiiValid(r, arrayOf(
            SkPoint(2f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f),
        )))
        // Negative radius rejected
        assertFalse(SkRRect.AreRectAndRadiiValid(r, arrayOf(
            SkPoint(-1f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f),
        )))
        // Radius > side rejected
        assertFalse(SkRRect.AreRectAndRadiiValid(r, arrayOf(
            SkPoint(20f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f),
        )))
        // Non-finite rect rejected
        assertFalse(SkRRect.AreRectAndRadiiValid(rect(0f, 0f, Float.NaN, 10f), arrayOf(
            SkPoint(2f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f), SkPoint(2f, 2f),
        )))
    }
}
