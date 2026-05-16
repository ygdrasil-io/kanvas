package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for the polymorphic [SkTCurve] interface and the three
 * concrete wrappers [SkTQuad], [SkTConic], [SkTCubic] (Phase D1.1.e.2.a).
 *
 * Verifies : delegation correctness, the polymorphic
 * `hullIntersects(SkTCurve)` dispatcher, `make` factories, and
 * `subDivide` / `setBounds` for each wrapper.
 */
class SkTCurveTest {

    private val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0)))
    private val c = SkDCubic(arrayOf(
        SkDPoint(0.0, 0.0), SkDPoint(0.0, 1000.0),
        SkDPoint(100.0, 1000.0), SkDPoint(100.0, 0.0),
    ))
    private val k = SkDConic(
        pts = SkDQuad(arrayOf(SkDPoint(1.0, 0.0), SkDPoint(1.0, 1.0), SkDPoint(0.0, 1.0))),
        weight = (sqrt(2.0) / 2).toFloat(),
    )

    // ─── SkTQuad delegation ─────────────────────────────────────────

    @Test
    fun `SkTQuad reports correct point counts and isConic`() {
        val tq = SkTQuad(q)
        assertEquals(3, tq.pointCount())
        assertEquals(2, tq.pointLast())
        assertEquals(SkDQuad.kMaxIntersections, tq.maxIntersections())
        assertFalse(tq.isConic())
    }

    @Test
    fun `SkTQuad ptAtT delegates to the wrapped quad`() {
        val tq = SkTQuad(q)
        assertEquals(q.ptAtT(0.5), tq.ptAtT(0.5))
    }

    @Test
    fun `SkTQuad subDivide writes into out parameter`() {
        val tq = SkTQuad(q)
        val out = SkTQuad()
        tq.subDivide(0.25, 0.75, out)
        // Sample comparison : sub at t=0 should equal original at t=0.25.
        assertEquals(q.ptAtT(0.25).x, out.quad.ptAtT(0.0).x, 1e-9)
        assertEquals(q.ptAtT(0.25).y, out.quad.ptAtT(0.0).y, 1e-9)
    }

    @Test
    fun `SkTQuad setBounds writes into out rect`() {
        val tq = SkTQuad(q)
        val rect = SkDRect()
        tq.setBounds(rect)
        // Tight bounds for this peak quad : (0, 0) to (100, 50) — peak at y=50 (since (50,100) controls a peak at (1-0.5)²·0 + 2·0.5·0.5·100 + 0.5²·0 = 50).
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(100.0, rect.right, 1e-9)
        assertEquals(0.0, rect.top, 1e-9)
        assertEquals(50.0, rect.bottom, 1e-9)
    }

    @Test
    fun `SkTQuad make returns a fresh empty SkTQuad of the same type`() {
        val tq = SkTQuad(q)
        val fresh = tq.make()
        assertTrue(fresh is SkTQuad)
        assertNotSame(tq, fresh)
        assertEquals(SkDQuad(), (fresh as SkTQuad).quad) // default-constructed inner quad
    }

    // ─── SkTCubic delegation ────────────────────────────────────────

    @Test
    fun `SkTCubic reports correct point counts and isConic`() {
        val tc = SkTCubic(c)
        assertEquals(4, tc.pointCount())
        assertEquals(3, tc.pointLast())
        assertEquals(SkDCubic.kMaxIntersections, tc.maxIntersections())
        assertFalse(tc.isConic())
    }

    @Test
    fun `SkTCubic ptAtT delegates to the wrapped cubic`() {
        val tc = SkTCubic(c)
        assertEquals(c.ptAtT(0.5), tc.ptAtT(0.5))
    }

    @Test
    fun `SkTCubic subDivide writes into out parameter`() {
        val tc = SkTCubic(c)
        val out = SkTCubic()
        tc.subDivide(0.0, 0.5, out)
        // Sample : sub at t=1 should equal original at t=0.5.
        assertEquals(c.ptAtT(0.5).x, out.cubic.ptAtT(1.0).x, 1e-9)
        assertEquals(c.ptAtT(0.5).y, out.cubic.ptAtT(1.0).y, 1e-9)
    }

    @Test
    fun `SkTCubic make returns a fresh empty SkTCubic`() {
        val tc = SkTCubic(c)
        val fresh = tc.make()
        assertTrue(fresh is SkTCubic)
        assertNotSame(tc, fresh)
    }

    // ─── SkTConic delegation ────────────────────────────────────────

    @Test
    fun `SkTConic reports correct point counts and isConic`() {
        val tk = SkTConic(k)
        assertEquals(3, tk.pointCount())
        assertEquals(2, tk.pointLast())
        assertEquals(SkDConic.kMaxIntersections, tk.maxIntersections())
        assertTrue(tk.isConic())
    }

    @Test
    fun `SkTConic ptAtT delegates to the wrapped conic`() {
        val tk = SkTConic(k)
        assertEquals(k.ptAtT(0.5), tk.ptAtT(0.5))
    }

    @Test
    fun `SkTConic make returns a fresh empty SkTConic`() {
        val tk = SkTConic(k)
        val fresh = tk.make()
        assertTrue(fresh is SkTConic)
        assertNotSame(tk, fresh)
    }

    // ─── Polymorphic hullIntersects(SkTCurve) dispatcher ────────────

    @Test
    fun `polymorphic hullIntersects accepts overlap quad-quad`() {
        val a: SkTCurve = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 10.0), SkDPoint(10.0, 0.0))))
        val b: SkTCurve = SkTQuad(SkDQuad(arrayOf(SkDPoint(2.0, 2.0), SkDPoint(5.0, 8.0), SkDPoint(8.0, 2.0))))
        val isLinear = booleanArrayOf(false)
        assertTrue(a.hullIntersects(b, isLinear))
    }

    @Test
    fun `polymorphic hullIntersects rejects disjoint cubic-quad`() {
        val a: SkTCurve = SkTCubic(c)
        val b: SkTCurve = SkTQuad(SkDQuad(arrayOf(SkDPoint(500.0, 500.0), SkDPoint(550.0, 600.0), SkDPoint(600.0, 500.0))))
        val isLinear = booleanArrayOf(false)
        assertFalse(a.hullIntersects(b, isLinear))
    }

    @Test
    fun `polymorphic hullIntersects accepts overlap cubic-conic`() {
        val a: SkTCurve = SkTCubic(c)
        val b: SkTCurve = SkTConic(SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(20.0, 20.0), SkDPoint(50.0, 200.0), SkDPoint(80.0, 20.0))),
            weight = 0.7f,
        ))
        val isLinear = booleanArrayOf(false)
        assertTrue(a.hullIntersects(b, isLinear))
    }

    // ─── intersectRay polymorphic delegation ───────────────────────

    @Test
    fun `SkTQuad intersectRay delegates to SkIntersections`() {
        val tq = SkTQuad(q)
        val ix = SkIntersections()
        val l = SkDLine(arrayOf(SkDPoint(0.0, 25.0), SkDPoint(100.0, 25.0)))
        // Direct call vs polymorphic call should agree.
        val nDirect = SkIntersections().intersectRay(q, l)
        val nPoly = tq.intersectRay(ix, l)
        assertEquals(nDirect, nPoly)
    }

    @Test
    fun `SkTCubic intersectRay delegates to SkIntersections`() {
        val tc = SkTCubic(c)
        val ix = SkIntersections()
        val l = SkDLine(arrayOf(SkDPoint(0.0, 500.0), SkDPoint(100.0, 500.0)))
        val nDirect = SkIntersections().intersectRay(c, l)
        val nPoly = tc.intersectRay(ix, l)
        assertEquals(nDirect, nPoly)
    }

    @Test
    fun `SkTConic intersectRay delegates to SkIntersections`() {
        val tk = SkTConic(k)
        val ix = SkIntersections()
        val l = SkDLine(arrayOf(SkDPoint(0.0, 0.5), SkDPoint(1.0, 0.5)))
        val nDirect = SkIntersections().intersectRay(k, l)
        val nPoly = tk.intersectRay(ix, l)
        assertEquals(nDirect, nPoly)
    }
}
