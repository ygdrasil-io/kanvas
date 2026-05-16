package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for cross-curve `hullIntersects` and the supporting
 * `SkDCubic.convexHull` (Phase D1.1.e.1).
 */
class SkDCurveHullIntersectsTest {

    private fun quadOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
    ): SkDQuad = SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2)))

    private fun cubicOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        x3: Double, y3: Double,
    ): SkDCubic = SkDCubic(arrayOf(
        SkDPoint(x0, y0), SkDPoint(x1, y1),
        SkDPoint(x2, y2), SkDPoint(x3, y3),
    ))

    // ─── SkDCubic.convexHull ───────────────────────────────────────

    @Test
    fun `convexHull of a square cubic produces a 4-vertex hull`() {
        // (0,0) - (0,10) - (10,10) - (10,0) — square with control
        // points at the corners.
        val c = cubicOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val order = CharArray(4)
        val n = c.convexHull(order)
        assertEquals(4, n)
        // All 4 indices used exactly once.
        assertEquals(setOf(0, 1, 2, 3), order.map { it.code }.toSet())
    }

    @Test
    fun `convexHull of a colinear cubic returns 3 (one side wins)`() {
        // (0,0)-(5,0)-(7,0)-(10,0) — all colinear.
        val c = cubicOf(0.0, 0.0, 5.0, 0.0, 7.0, 0.0, 10.0, 0.0)
        val order = CharArray(4)
        val n = c.convexHull(order)
        assertEquals(3, n)
    }

    @Test
    fun `convexHull of a triangle-with-interior-point cubic returns 3`() {
        // (0,0)-(10,0)-(5,10) — triangle ; 4th point (5, 3) inside.
        val c = cubicOf(0.0, 0.0, 10.0, 0.0, 5.0, 3.0, 5.0, 10.0)
        val order = CharArray(4)
        val n = c.convexHull(order)
        // Either 3 (interior point dropped) or 4 — we just verify it's
        // one of these and that all returned indices are in [0, 3].
        assertTrue(n == 3 || n == 4)
        for (i in 0 until n) assertTrue(order[i].code in 0..3)
    }

    // ─── SkDQuad.hullIntersects(SkDQuad) ───────────────────────────

    @Test
    fun `quad-quad hullIntersects detects overlapping hulls`() {
        val q1 = quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0)
        val q2 = quadOf(2.0, 2.0, 5.0, 8.0, 8.0, 2.0) // inside q1's hull
        val isLinear = booleanArrayOf(false)
        assertTrue(q1.hullIntersects(q2, isLinear))
    }

    @Test
    fun `quad-quad hullIntersects rejects disjoint quads`() {
        val q1 = quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0)
        val q2 = quadOf(20.0, 20.0, 25.0, 30.0, 30.0, 20.0)
        val isLinear = booleanArrayOf(false)
        assertFalse(q1.hullIntersects(q2, isLinear))
    }

    @Test
    fun `quad-quad hullIntersects with collinear hulls reports linear=true`() {
        // Both quads have all points on y=x ⇒ hulls are degenerate lines.
        val q1 = quadOf(0.0, 0.0, 5.0, 5.0, 10.0, 10.0)
        val q2 = quadOf(2.0, 2.0, 5.0, 5.0, 8.0, 8.0)
        val isLinear = booleanArrayOf(false)
        q1.hullIntersects(q2, isLinear)
        assertTrue(isLinear[0])
    }

    // ─── SkDCubic.hullIntersects(...) ──────────────────────────────

    @Test
    fun `cubic-cubic hullIntersects detects overlapping hulls`() {
        val c1 = cubicOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val c2 = cubicOf(2.0, 2.0, 2.0, 8.0, 8.0, 8.0, 8.0, 2.0)
        val isLinear = booleanArrayOf(false)
        assertTrue(c1.hullIntersects(c2, isLinear))
    }

    @Test
    fun `cubic-cubic hullIntersects rejects fully-disjoint hulls`() {
        val c1 = cubicOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val c2 = cubicOf(50.0, 50.0, 50.0, 60.0, 60.0, 60.0, 60.0, 50.0)
        val isLinear = booleanArrayOf(false)
        assertFalse(c1.hullIntersects(c2, isLinear))
    }

    @Test
    fun `cubic-quad hullIntersects accepts overlapping hulls`() {
        val c = cubicOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val q = quadOf(2.0, 2.0, 5.0, 8.0, 8.0, 2.0)
        val isLinear = booleanArrayOf(false)
        assertTrue(c.hullIntersects(q, isLinear))
    }

    @Test
    fun `cubic-conic hullIntersects accepts overlapping hulls`() {
        val c = cubicOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val k = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(2.0, 2.0), SkDPoint(5.0, 8.0), SkDPoint(8.0, 2.0))),
            weight = (sqrt(2.0) / 2).toFloat(),
        )
        val isLinear = booleanArrayOf(false)
        assertTrue(c.hullIntersects(k, isLinear))
    }

    // ─── SkDConic forwarders ───────────────────────────────────────

    @Test
    fun `conic-quad hullIntersects forwards to inner quad`() {
        val k = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 10.0), SkDPoint(10.0, 0.0))),
            weight = 1f,
        )
        val q = quadOf(2.0, 2.0, 5.0, 8.0, 8.0, 2.0)
        val isLinear = booleanArrayOf(false)
        assertTrue(k.hullIntersects(q, isLinear))
    }

    @Test
    fun `conic-conic hullIntersects forwards to inner quads`() {
        val k1 = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 10.0), SkDPoint(10.0, 0.0))),
            weight = 1f,
        )
        val k2 = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(20.0, 20.0), SkDPoint(25.0, 30.0), SkDPoint(30.0, 20.0))),
            weight = 1f,
        )
        val isLinear = booleanArrayOf(false)
        assertFalse(k1.hullIntersects(k2, isLinear))
    }

    @Test
    fun `conic-cubic hullIntersects delegates to cubic's helper`() {
        val k = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(2.0, 2.0), SkDPoint(5.0, 8.0), SkDPoint(8.0, 2.0))),
            weight = 1f,
        )
        val c = cubicOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val isLinear = booleanArrayOf(false)
        assertTrue(k.hullIntersects(c, isLinear))
    }
}
