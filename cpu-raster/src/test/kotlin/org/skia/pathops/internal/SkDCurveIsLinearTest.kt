package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for `isLinear` and the pinned-endpoint `subDivide`
 * variants on [SkDQuad] / [SkDCubic] / [SkDConic] (Phase D1.1.c).
 */
class SkDCurveIsLinearTest {

    // ─── isLinear ───────────────────────────────────────────────────

    @Test
    fun `quad isLinear true for collinear points`() {
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 5.0), SkDPoint(10.0, 10.0)))
        assertTrue(q.isLinear(0, 2))
    }

    @Test
    fun `quad isLinear false for control point off the chord`() {
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 100.0), SkDPoint(10.0, 0.0)))
        assertFalse(q.isLinear(0, 2))
    }

    @Test
    fun `cubic isLinear true for collinear points`() {
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(1.0, 1.0),
            SkDPoint(2.0, 2.0), SkDPoint(3.0, 3.0),
        ))
        assertTrue(c.isLinear(0, 3))
    }

    @Test
    fun `cubic isLinear false for control points off the chord`() {
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(0.0, 100.0),
            SkDPoint(100.0, 100.0), SkDPoint(100.0, 0.0),
        ))
        assertFalse(c.isLinear(0, 3))
    }

    @Test
    fun `conic isLinear delegates to the inner quad`() {
        val k = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 5.0), SkDPoint(10.0, 10.0))),
            weight = 0.5f,
        )
        assertTrue(k.isLinear(0, 2))
        val k2 = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 100.0), SkDPoint(10.0, 0.0))),
            weight = 0.5f,
        )
        assertFalse(k2.isLinear(0, 2))
    }

    // ─── Pinned subDivide ───────────────────────────────────────────

    @Test
    fun `quad pinned subDivide reproduces original middle on full range`() {
        // Pin the endpoints to the original — pinned middle must coincide
        // (within ULPs) with the original middle.
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0)))
        val a = q[0]; val c = q[2]
        val mid = q.subDivide(a, c, 0.0, 1.0)
        assertEquals(50.0, mid.x, 1e-9)
        assertEquals(100.0, mid.y, 1e-9)
    }

    @Test
    fun `quad pinned subDivide on half range produces sub-quad middle that matches subDivide`() {
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0)))
        val sub = q.subDivide(0.0, 0.5)
        val pinnedMid = q.subDivide(sub[0], sub[2], 0.0, 0.5)
        // Pinned middle should match the regular sub-divide middle.
        assertEquals(sub[1].x, pinnedMid.x, 1e-9)
        assertEquals(sub[1].y, pinnedMid.y, 1e-9)
    }

    @Test
    fun `cubic pinned subDivide reproduces original controls on full range`() {
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(0.0, 100.0),
            SkDPoint(100.0, 100.0), SkDPoint(100.0, 0.0),
        ))
        val a = c[0]; val d = c[3]
        val dst = arrayOf(SkDPoint(), SkDPoint())
        c.subDivide(a, d, 0.0, 1.0, dst)
        assertEquals(c[1].x, dst[0].x, 1e-9); assertEquals(c[1].y, dst[0].y, 1e-9)
        assertEquals(c[2].x, dst[1].x, 1e-9); assertEquals(c[2].y, dst[1].y, 1e-9)
    }

    @Test
    fun `conic pinned subDivide writes weight and returns middle`() {
        val k = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(1.0, 0.0), SkDPoint(1.0, 1.0), SkDPoint(0.0, 1.0))),
            weight = 0.7071f,
        )
        val a = k[0]; val c = k[2]
        val w = FloatArray(1)
        val mid = k.subDivide(a, c, 0.0, 1.0, w)
        // Full range : weight unchanged, middle is the original middle.
        assertEquals(0.7071f, w[0])
        assertEquals(1.0, mid.x, 1e-9)
        assertEquals(1.0, mid.y, 1e-9)
    }
}
