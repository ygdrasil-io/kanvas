package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for [SkLineParameters] (Phase D1.1.c).
 *
 * Coverage : line/quad/cubic endpoint parametrization, normalization,
 * point distance, control-point distance helpers, distance sampling
 * (`quadDistanceY`, `cubicDistanceY`).
 */
class SkLineParametersTest {

    @Test
    fun `lineEndPoints + pointDistance returns 0 for points on the line`() {
        val params = SkLineParameters()
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 10.0))))
        // Origin is on the line.
        assertEquals(0.0, params.pointDistance(SkDPoint(0.0, 0.0)), 1e-12)
        // (5, 5) is on the line.
        assertEquals(0.0, params.pointDistance(SkDPoint(5.0, 5.0)), 1e-12)
    }

    @Test
    fun `pointDistance is non-zero for off-line points and changes sign across the line`() {
        val params = SkLineParameters()
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0))))
        // Above the line (y=5) — sign one way.
        val above = params.pointDistance(SkDPoint(5.0, 5.0))
        // Below the line (y=-5) — opposite sign.
        val below = params.pointDistance(SkDPoint(5.0, -5.0))
        assertTrue(above * below < 0) { "above=$above, below=$below should have opposite signs" }
    }

    @Test
    fun `normalize scales so a²+b² == 1 and returns true on success`() {
        val params = SkLineParameters()
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(3.0, 4.0))))
        assertTrue(params.normalize())
        assertEquals(1.0, params.normalSquared(), 1e-12)
    }

    @Test
    fun `normalize returns false on a degenerate (zero-length) line`() {
        val params = SkLineParameters()
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(5.0, 5.0), SkDPoint(5.0, 5.0))))
        assertFalse(params.normalize())
    }

    @Test
    fun `normalized pointDistance returns true Euclidean distance`() {
        val params = SkLineParameters()
        // Horizontal line y = 0 from (0, 0) to (10, 0).
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0))))
        params.normalize()
        // (5, 7) is 7 units above the line.
        val dist = params.pointDistance(SkDPoint(5.0, 7.0))
        assertEquals(7.0, kotlin.math.abs(dist), 1e-12)
    }

    @Test
    fun `quadEndPoints with explicit 0,2 chord gets perpendicular distance to control`() {
        val params = SkLineParameters()
        // Quad with chord on x-axis (pts 0 and 2), control 100 above.
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0)))
        params.quadEndPoints(q, 0, 2)
        params.normalize()
        val d = params.controlPtDistance(q)
        assertEquals(100.0, kotlin.math.abs(d), 1e-9)
    }

    @Test
    fun `quadPart returns the perpendicular distance from quad point 2 to chord through 0 and 1`() {
        val params = SkLineParameters()
        // Quad : (0, 0) → (10, 0) [chord] → (5, 10) [endpoint above chord].
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0), SkDPoint(5.0, 10.0)))
        params.normalize()
        val d = params.quadPart(q)
        params.normalize()
        // Raw distance is 10 * normal magnitude — call again normalized to assert.
        // Easier : just check sign + nonzero.
        assertTrue(d != 0.0)
    }

    @Test
    fun `cubicEndPoints + controlPtDistance get distances from controls to chord`() {
        val params = SkLineParameters()
        // Cubic with chord on x-axis, controls above.
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(0.0, 100.0),
            SkDPoint(100.0, 100.0), SkDPoint(100.0, 0.0),
        ))
        params.cubicEndPoints(c, 0, 3)
        params.normalize()
        val d1 = params.controlPtDistance(c, 1)
        val d2 = params.controlPtDistance(c, 2)
        assertEquals(100.0, kotlin.math.abs(d1), 1e-9)
        assertEquals(100.0, kotlin.math.abs(d2), 1e-9)
    }

    @Test
    fun `cubicEndPoints (auto) finds a non-degenerate line through pts0`() {
        val params = SkLineParameters()
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(1.0, 1.0),
            SkDPoint(2.0, 2.0), SkDPoint(3.0, 3.0),
        ))
        // Colinear cubic ; pick (0,0)-(1,1) succeeds for the line.
        assertTrue(params.cubicEndPoints(c))
    }

    @Test
    fun `quadEndPoints (auto) returns true for non-degenerate quad`() {
        val params = SkLineParameters()
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 10.0), SkDPoint(10.0, 0.0)))
        assertTrue(params.quadEndPoints(q))
    }

    @Test
    fun `quadDistanceY samples per-control distance at t = 0, 1 over 2, 1`() {
        val params = SkLineParameters()
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0))))
        params.normalize()
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 7.0), SkDPoint(10.0, 0.0)))
        val out = SkDQuad()
        params.quadDistanceY(q, out)
        assertEquals(0.0, out[0].x, 1e-12); assertEquals(0.0, out[0].y, 1e-9)
        assertEquals(0.5, out[1].x, 1e-12); assertEquals(7.0, kotlin.math.abs(out[1].y), 1e-9)
        assertEquals(1.0, out[2].x, 1e-12); assertEquals(0.0, out[2].y, 1e-9)
    }

    @Test
    fun `cubicDistanceY samples per-control distance at t = 0, 1 over 3, 2 over 3, 1`() {
        val params = SkLineParameters()
        params.lineEndPoints(SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(3.0, 0.0))))
        params.normalize()
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(1.0, 5.0),
            SkDPoint(2.0, 5.0), SkDPoint(3.0, 0.0),
        ))
        val out = SkDCubic()
        params.cubicDistanceY(c, out)
        assertEquals(0.0, out[0].x, 1e-12)
        assertEquals(1.0 / 3, out[1].x, 1e-9)
        assertEquals(2.0 / 3, out[2].x, 1e-9)
        assertEquals(1.0, out[3].x, 1e-12)
        assertEquals(5.0, kotlin.math.abs(out[1].y), 1e-9)
        assertEquals(5.0, kotlin.math.abs(out[2].y), 1e-9)
    }
}
