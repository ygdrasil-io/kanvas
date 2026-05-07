package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkTCoincident] (Phase D1.1.e.2.b).
 */
class SkTCoincidentTest {

    @Test
    fun `init resets perpT to -1 and clears match`() {
        val c = SkTCoincident()
        assertEquals(-1.0, c.perpT())
        assertFalse(c.isMatch())
        assertTrue(c.perpPt().x.isNaN())
    }

    @Test
    fun `markCoincident sets match true and resets perpT`() {
        val c = SkTCoincident()
        c.markCoincident()
        assertTrue(c.isMatch())
        assertEquals(-1.0, c.perpT())
    }

    @Test
    fun `setPerp records a perp hit on the opposing curve`() {
        // Two non-degenerate quads that cross. q1 = peak (0,0)-(50,100)-(100,0).
        // q2 = inverted peak that dips through q1's apex.
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 100.0), SkDPoint(50.0, 0.0), SkDPoint(100.0, 100.0))))
        val c = SkTCoincident()
        c.setPerp(q1, 0.5, q1.ptAtT(0.5), q2)
        // We don't strictly assert isMatch (depends on ULPs precision of
        // the two parabolic peaks). We do assert that a perp hit was
        // recorded (perpT >= 0).
        assertTrue(c.perpT() >= 0) { "expected perpT ≥ 0 (perp hit recorded), got ${c.perpT()}" }
    }

    @Test
    fun `setPerp on disjoint curves leaves match false`() {
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 5.0), SkDPoint(10.0, 10.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(100.0, 100.0), SkDPoint(105.0, 105.0), SkDPoint(110.0, 110.0))))
        val c = SkTCoincident()
        c.setPerp(q1, 0.5, q1.ptAtT(0.5), q2)
        // Either no perp hit (perpT == -1) or hit far from cPt — neither yields a match.
        assertFalse(c.isMatch())
    }
}
