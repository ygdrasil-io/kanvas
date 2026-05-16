package org.skia.pathops.internal


import org.skia.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for the curve-tight `SkDRect.setBounds` overloads added
 * in Phase D1.1.b.
 *
 * The fixtures match the existing `SkPathGeometryHelpersTest` cases
 * to anchor parity with the float-precision `SkPath.computeTightBounds`.
 */
class SkDRectCurveBoundsTest {

    // ─── SkDQuad bounds ─────────────────────────────────────────────

    @Test
    fun `setBounds on a y-peak quad excludes the control-point bulge`() {
        // Same fixture as SkPathGeometryHelpersTest : y-peak at 500, not 1000.
        val q = SkDQuad(arrayOf(
            SkDPoint(0.0, 0.0),
            SkDPoint(50.0, 1000.0),
            SkDPoint(100.0, 0.0),
        ))
        val rect = SkDRect()
        rect.setBounds(q)
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(100.0, rect.right, 1e-9)
        assertEquals(0.0, rect.top, 1e-9)
        assertEquals(500.0, rect.bottom, 1e-9)
    }

    @Test
    fun `setBounds on a monotonic quad returns the chord box`() {
        // X- and Y-monotonic → bounds are exactly the endpoint box.
        val q = SkDQuad(arrayOf(
            SkDPoint(0.0, 0.0),
            SkDPoint(5.0, 5.0),
            SkDPoint(10.0, 10.0),
        ))
        val rect = SkDRect()
        rect.setBounds(q)
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(0.0, rect.top, 1e-9)
        assertEquals(10.0, rect.right, 1e-9)
        assertEquals(10.0, rect.bottom, 1e-9)
    }

    @Test
    fun `setBounds with sub range tightens to the sampled subinterval`() {
        val q = SkDQuad(arrayOf(
            SkDPoint(0.0, 0.0),
            SkDPoint(50.0, 1000.0),
            SkDPoint(100.0, 0.0),
        ))
        // Restrict to t ∈ [0.25, 0.75].
        val sub = q.subDivide(0.25, 0.75)
        val rect = SkDRect()
        rect.setBounds(q, sub, 0.25, 0.75)
        // Endpoints of the subinterval :
        //   q(0.25) = (25, 375),   q(0.75) = (75, 375).
        // Peak at t=0.5 still inside [0.25, 0.75] → y reaches 500.
        assertEquals(25.0, rect.left, 1e-9)
        assertEquals(75.0, rect.right, 1e-9)
        assertEquals(375.0, rect.top, 1e-9)
        assertEquals(500.0, rect.bottom, 1e-9)
    }

    // ─── SkDCubic bounds ────────────────────────────────────────────

    @Test
    fun `setBounds on a symmetric cubic peak gives 750 not 1000`() {
        // Same fixture as SkPathGeometryHelpersTest cubic case.
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0),
            SkDPoint(0.0, 1000.0),
            SkDPoint(100.0, 1000.0),
            SkDPoint(100.0, 0.0),
        ))
        val rect = SkDRect()
        rect.setBounds(c)
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(100.0, rect.right, 1e-9)
        assertEquals(0.0, rect.top, 1e-9)
        assertEquals(750.0, rect.bottom, 1e-7)
    }

    @Test
    fun `setBounds on a monotonic cubic returns the chord box`() {
        // Straight line 0 → 1 → 2 → 3 ; bounds = (0, 0, 3, 3).
        val c = SkDCubic(arrayOf(
            SkDPoint(0.0, 0.0),
            SkDPoint(1.0, 1.0),
            SkDPoint(2.0, 2.0),
            SkDPoint(3.0, 3.0),
        ))
        val rect = SkDRect()
        rect.setBounds(c)
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(0.0, rect.top, 1e-9)
        assertEquals(3.0, rect.right, 1e-9)
        assertEquals(3.0, rect.bottom, 1e-9)
    }

    // ─── SkDConic bounds ────────────────────────────────────────────

    @Test
    fun `setBounds on a quarter-arc conic equals the unit-axis box`() {
        // Standard quarter-arc (1, 0) → (1, 1) → (0, 1) with √2/2 weight.
        // Tight bounds = (0, 0, 1, 1).
        val arcWeight = (sqrt(2.0) / 2).toFloat()
        val k = SkDConic(
            pts = SkDQuad(arrayOf(
                SkDPoint(1.0, 0.0),
                SkDPoint(1.0, 1.0),
                SkDPoint(0.0, 1.0),
            )),
            weight = arcWeight,
        )
        val rect = SkDRect()
        rect.setBounds(k)
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(0.0, rect.top, 1e-9)
        assertEquals(1.0, rect.right, 1e-9)
        assertEquals(1.0, rect.bottom, 1e-9)
    }
}
