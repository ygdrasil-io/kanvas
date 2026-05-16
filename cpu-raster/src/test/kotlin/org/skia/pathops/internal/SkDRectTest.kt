package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkDRect] (Phase D1.1.a).
 *
 * The curve `setBounds(SkDQuad/Cubic/Conic, ...)` overloads need
 * D1.1.b types and are deferred ; this slice covers `add` /
 * `contains` / `intersects` / `set` / `valid`.
 */
class SkDRectTest {

    @Test
    fun `set from a point collapses bounds to that point`() {
        val r = SkDRect()
        r.set(SkDPoint(3.0, 4.0))
        assertEquals(3.0, r.left); assertEquals(4.0, r.top)
        assertEquals(3.0, r.right); assertEquals(4.0, r.bottom)
        assertEquals(0.0, r.width()); assertEquals(0.0, r.height())
    }

    @Test
    fun `add expands bounds to include the point`() {
        val r = SkDRect()
        r.set(SkDPoint(0.0, 0.0))
        r.add(SkDPoint(10.0, -5.0))
        r.add(SkDPoint(-3.0, 7.0))
        assertEquals(-3.0, r.left); assertEquals(-5.0, r.top)
        assertEquals(10.0, r.right); assertEquals(7.0, r.bottom)
    }

    @Test
    fun `contains accepts interior and boundary points`() {
        val r = SkDRect(left = 0.0, top = 0.0, right = 10.0, bottom = 5.0)
        assertTrue(r.contains(SkDPoint(5.0, 2.5)))
        assertTrue(r.contains(SkDPoint(0.0, 0.0)))
        assertTrue(r.contains(SkDPoint(10.0, 5.0)))
    }

    @Test
    fun `contains rejects points outside even with FLT_EPSILON slack`() {
        val r = SkDRect(left = 0.0, top = 0.0, right = 10.0, bottom = 5.0)
        // FLT_EPSILON slack is allowed (per approximately_between).
        assertTrue(r.contains(SkDPoint(-FLT_EPSILON / 2, 2.5)))
        // But significantly outside is rejected.
        assertFalse(r.contains(SkDPoint(11.0, 2.5)))
        assertFalse(r.contains(SkDPoint(5.0, 6.0)))
    }

    @Test
    fun `intersects detects overlap and rejects disjoint rects`() {
        val a = SkDRect(left = 0.0, top = 0.0, right = 10.0, bottom = 10.0)
        val b = SkDRect(left = 5.0, top = 5.0, right = 15.0, bottom = 15.0)
        val c = SkDRect(left = 20.0, top = 20.0, right = 30.0, bottom = 30.0)
        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
        assertFalse(a.intersects(c))
        assertFalse(c.intersects(a))
    }

    @Test
    fun `intersects accepts touching edges (closed-interval semantics)`() {
        val a = SkDRect(left = 0.0, top = 0.0, right = 10.0, bottom = 10.0)
        val b = SkDRect(left = 10.0, top = 0.0, right = 20.0, bottom = 10.0)
        // Edges coincide at x=10 → intersects per the upstream contract.
        assertTrue(a.intersects(b))
    }

    @Test
    fun `width and height compute the absolute span`() {
        val r = SkDRect(left = -3.0, top = -4.0, right = 7.0, bottom = 2.0)
        assertEquals(10.0, r.width())
        assertEquals(6.0, r.height())
    }

    @Test
    fun `valid rejects mis-sorted bounds`() {
        assertTrue(SkDRect(0.0, 0.0, 10.0, 5.0).valid())
        assertFalse(SkDRect(10.0, 0.0, 0.0, 5.0).valid())
        assertFalse(SkDRect(0.0, 5.0, 10.0, 0.0).valid())
    }

    @Test
    fun `debugInit sets all fields to NaN and renders the rect invalid`() {
        val r = SkDRect(0.0, 0.0, 10.0, 5.0)
        r.debugInit()
        assertTrue(r.left.isNaN())
        assertTrue(r.right.isNaN())
        assertFalse(r.valid())
    }
}
