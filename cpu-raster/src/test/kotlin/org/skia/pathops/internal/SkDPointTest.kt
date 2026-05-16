package org.skia.pathops.internal



import org.graphiks.math.FLT_EPSILON
import org.graphiks.math.SkDPoint
import org.graphiks.math.SkDVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkPoint

/**
 * Unit tests for [SkDPoint] / [SkDVector] (Phase D1.1.a).
 */
class SkDPointTest {

    // ─── SkDVector ───────────────────────────────────────────────────

    @Test
    fun `SkDVector dot and cross on canonical axes`() {
        val ex = SkDVector(1.0, 0.0)
        val ey = SkDVector(0.0, 1.0)
        assertEquals(0.0, ex.dot(ey))
        assertEquals(1.0, ex.cross(ey))
        assertEquals(-1.0, ey.cross(ex))
    }

    @Test
    fun `SkDVector length and lengthSquared`() {
        val v = SkDVector(3.0, 4.0)
        assertEquals(25.0, v.lengthSquared())
        assertEquals(5.0, v.length(), 1e-12)
    }

    @Test
    fun `SkDVector normalize produces unit length`() {
        val v = SkDVector(3.0, 4.0).normalize()
        assertEquals(1.0, v.length(), 1e-12)
        assertEquals(0.6, v.x, 1e-12)
        assertEquals(0.8, v.y, 1e-12)
    }

    @Test
    fun `SkDVector arithmetic operators`() {
        val v = SkDVector(1.0, 2.0)
        v += SkDVector(3.0, 4.0)
        assertEquals(SkDVector(4.0, 6.0), v)
        v -= SkDVector(2.0, 1.0)
        assertEquals(SkDVector(2.0, 5.0), v)
        v *= 2.0
        assertEquals(SkDVector(4.0, 10.0), v)
        v /= 4.0
        assertEquals(SkDVector(1.0, 2.5), v)
    }

    @Test
    fun `SkDVector crossCheck snaps near-coincident inputs to zero`() {
        // Two vectors that have the same direction up to ULPs.
        val a = SkDVector(1.0, 0.0)
        val b = SkDVector(1.0 + FLT_EPSILON / 4, 0.0)
        // Plain cross : a.x*b.y - a.y*b.x = 0 - 0 = 0 (exact, same direction).
        assertEquals(0.0, a.cross(b))
        // crossCheck preserves the ULPs-snap path.
        assertEquals(0.0, a.crossCheck(b))
    }

    // ─── SkDPoint ────────────────────────────────────────────────────

    @Test
    fun `SkDPoint distance and distanceSquared`() {
        val a = SkDPoint(0.0, 0.0); val b = SkDPoint(3.0, 4.0)
        assertEquals(5.0, a.distance(b), 1e-12)
        assertEquals(25.0, a.distanceSquared(b), 1e-12)
    }

    @Test
    fun `SkDPoint Mid returns the midpoint`() {
        val mid = SkDPoint.Mid(SkDPoint(0.0, 0.0), SkDPoint(10.0, 20.0))
        assertEquals(SkDPoint(5.0, 10.0), mid)
    }

    @Test
    fun `SkDPoint subtract produces a vector`() {
        val v: SkDVector = SkDPoint(5.0, 7.0) - SkDPoint(2.0, 3.0)
        assertEquals(SkDVector(3.0, 4.0), v)
    }

    @Test
    fun `SkDPoint plus and minus a vector returns a new point`() {
        val a = SkDPoint(1.0, 2.0)
        val v = SkDVector(3.0, 4.0)
        assertEquals(SkDPoint(4.0, 6.0), a + v)
        assertEquals(SkDPoint(-2.0, -2.0), a - v)
    }

    @Test
    fun `SkDPoint approximatelyEqual on coincident is true`() {
        val a = SkDPoint(100.0, 100.0)
        val b = SkDPoint(100.0, 100.0)
        assertTrue(a.approximatelyEqual(b))
    }

    @Test
    fun `SkDPoint approximatelyEqual tolerates magnitude-scaled error`() {
        // Two points 1 ULP apart in magnitude 1e6.
        val a = SkDPoint(1e6, 1e6)
        val b = SkDPoint(1e6 + 0.1, 1e6 + 0.1)
        assertTrue(a.approximatelyEqual(b))
    }

    @Test
    fun `SkDPoint approximatelyEqual rejects far-apart points`() {
        val a = SkDPoint(0.0, 0.0)
        val b = SkDPoint(1.0, 1.0)
        assertFalse(a.approximatelyEqual(b))
    }

    @Test
    fun `SkDPoint set from SkPoint copies fX fY`() {
        val p = SkDPoint()
        p.set(SkPoint(fX = 1.5f, fY = 2.5f))
        assertEquals(1.5, p.x, 1e-6)
        assertEquals(2.5, p.y, 1e-6)
    }

    @Test
    fun `SkDPoint asSkPoint round-trips at single precision`() {
        val sp = SkDPoint(3.25, -4.5).asSkPoint()
        assertEquals(3.25f, sp.fX)
        assertEquals(-4.5f, sp.fY)
    }

    @Test
    fun `SkDPoint approximatelyZero accepts near-zero`() {
        assertTrue(SkDPoint(0.0, 0.0).approximatelyZero())
        assertTrue(SkDPoint(FLT_EPSILON / 2, FLT_EPSILON / 2).approximatelyZero())
        assertFalse(SkDPoint(1.0, 0.0).approximatelyZero())
    }
}
