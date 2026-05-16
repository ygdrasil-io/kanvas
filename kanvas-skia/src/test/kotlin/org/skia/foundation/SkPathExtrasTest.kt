package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Coverage for the Phase 3d additions to [SkPath] / [SkPathBuilder]:
 * relative verbs, tangent-arc `arcTo`, [SkPath.computeBounds], and
 * [SkPath.makeOffset]. Existing unit tests for the Phase 3a/b APIs
 * stay in `SkPathBuilderTest.kt`.
 */
class SkPathExtrasTest {

    // --- Relative verbs ----------------------------------------------------

    @Test
    fun `rMoveTo and rLineTo accumulate from the current pen position`() {
        val p = SkPathBuilder()
            .moveTo(10f, 10f)        // pen = (10, 10)
            .rLineTo(5f, 0f)         // pen = (15, 10)
            .rLineTo(0f, 5f)         // pen = (15, 15)
            .rMoveTo(10f, 0f)        // pen = (25, 15) → starts new contour
            .rLineTo(0f, 5f)         // pen = (25, 20)
            .detach()
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
        assertEquals(SkPath.Verb.kLine, p.verbs[2])
        assertEquals(SkPath.Verb.kMove, p.verbs[3])
        assertEquals(SkPath.Verb.kLine, p.verbs[4])
        // Coords: (10,10)(15,10)(15,15)(25,15)(25,20)
        assertEquals(15f, p.coords[2], 1e-4f)
        assertEquals(10f, p.coords[3], 1e-4f)
        assertEquals(15f, p.coords[4], 1e-4f)
        assertEquals(15f, p.coords[5], 1e-4f)
        assertEquals(25f, p.coords[6], 1e-4f)
        assertEquals(15f, p.coords[7], 1e-4f)
        assertEquals(25f, p.coords[8], 1e-4f)
        assertEquals(20f, p.coords[9], 1e-4f)
    }

    @Test
    fun `rQuadTo and rCubicTo offset all control points by the current pen`() {
        val p = SkPathBuilder()
            .moveTo(10f, 10f)
            .rQuadTo(5f, 0f, 5f, 5f)         // ctrl=(15,10), end=(15,15)
            .rCubicTo(0f, 5f, -5f, 5f, -5f, 10f) // ctrls=(15,20),(10,20), end=(10,25)
            .detach()
        assertEquals(SkPath.Verb.kQuad, p.verbs[1])
        assertEquals(SkPath.Verb.kCubic, p.verbs[2])
        // Quad: control (15,10), end (15,15)
        assertEquals(15f, p.coords[2], 1e-4f)
        assertEquals(10f, p.coords[3], 1e-4f)
        assertEquals(15f, p.coords[4], 1e-4f)
        assertEquals(15f, p.coords[5], 1e-4f)
        // Cubic: c1 (15,20), c2 (10,20), end (10,25)
        assertEquals(15f, p.coords[6], 1e-4f)
        assertEquals(20f, p.coords[7], 1e-4f)
        assertEquals(10f, p.coords[8], 1e-4f)
        assertEquals(20f, p.coords[9], 1e-4f)
        assertEquals(10f, p.coords[10], 1e-4f)
        assertEquals(25f, p.coords[11], 1e-4f)
    }

    @Test
    fun `rConicTo with weight not equal to 1 stores a kConic verb relative to pen`() {
        val p = SkPathBuilder()
            .moveTo(10f, 10f)
            .rConicTo(5f, 0f, 5f, 5f, 0.7f)
            .detach()
        assertEquals(SkPath.Verb.kConic, p.verbs[1])
        assertEquals(15f, p.coords[2], 1e-4f)
        assertEquals(10f, p.coords[3], 1e-4f)
        assertEquals(15f, p.coords[4], 1e-4f)
        assertEquals(15f, p.coords[5], 1e-4f)
        assertEquals(0.7f, p.conicWeights[0], 1e-4f)
    }

    @Test
    fun `rLineTo on an empty contour starts at the origin per the absolute primitive's contract`() {
        // SkPath inherits Skia's behaviour: lineTo on an empty contour first
        // emits an implicit moveTo(0, 0). The relative variant goes through the
        // same primitive, so rLineTo(5, 5) ends up at (5, 5) with the implicit
        // moveTo at (0, 0).
        val p = SkPathBuilder().rLineTo(5f, 5f).detach()
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(0f, p.coords[0])
        assertEquals(0f, p.coords[1])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
        assertEquals(5f, p.coords[2])
        assertEquals(5f, p.coords[3])
    }

    // --- Tangent arcTo ----------------------------------------------------

    @Test
    fun `tangent arcTo on empty path emits a moveTo and returns`() {
        val p = SkPathBuilder().arcTo(10f, 10f, 20f, 10f, 5f).detach()
        assertEquals(1, p.verbs.size)
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(10f, p.coords[0])
        assertEquals(10f, p.coords[1])
    }

    @Test
    fun `tangent arcTo with radius 0 degenerates to lineTo`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .arcTo(10f, 0f, 10f, 10f, 0f)
            .detach()
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
        assertEquals(10f, p.coords[2])
        assertEquals(0f, p.coords[3])
    }

    @Test
    fun `tangent arcTo on a 90 degree corner inserts a lineTo to T0 then a conic arc (Skia parity)`() {
        // L-shape (0,0) → (10,0) → (10,10) with radius 2.
        // For a right angle: cosh = 0, sinh = 1.
        // dist = |r * (1 - 0) / 1| = 2.
        // T0 = (10,0) - 2 * (1, 0) = (8, 0).
        // T1 = (10,0) + 2 * (0, 1) = (10, 2).
        // weight = sqrt(0.5 + 0) = √2/2 ≈ 0.7071.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .arcTo(10f, 0f, 10f, 10f, 2f)
            .detach()
        // Mirrors src/core/SkPathBuilder.cpp:477-511 — exactly 1 line + 1 conic.
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
        assertEquals(8f, p.coords[2], 1e-3f)
        assertEquals(0f, p.coords[3], 1e-3f)
        assertEquals(SkPath.Verb.kConic, p.verbs[2])
        assertEquals(3, p.verbs.size)
        // Conic control = p1 = (10, 0); end = T1 = (10, 2).
        // Coords: move(2) + line(2) + conic(control(2) + end(2)) = 8 floats.
        assertEquals(10f, p.coords[4], 1e-3f)
        assertEquals(0f,  p.coords[5], 1e-3f)
        assertEquals(10f, p.coords[6], 1e-3f)
        assertEquals(2f,  p.coords[7], 1e-3f)
        // Weight = √2/2.
        val expectedWeight = kotlin.math.sqrt(2.0).toFloat() * 0.5f
        assertEquals(1, p.conicWeights.size)
        assertEquals(expectedWeight, p.conicWeights[0], 1e-4f)
    }

    @Test
    fun `tangent arcTo on collinear points degenerates to lineTo at p1`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .arcTo(10f, 0f, 20f, 0f, 5f)   // P0, p1, p2 all on Y=0 → collinear
            .detach()
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
        assertEquals(10f, p.coords[2])
        assertEquals(0f, p.coords[3])
        assertEquals(2, p.verbs.size)
    }

    @Test
    fun `tangent arcTo center lies on the angle bisector at the right distance`() {
        // 60° corner: P0=(1, 0), P1=(0, 0), P2=(cos60°, sin60°) = (0.5, sqrt(3)/2).
        // Angle θ at P1 = 60°; θ/2 = 30°.
        // d = r / tan(30°) = r * sqrt(3).
        // |P1 − C| = r / sin(30°) = 2r.
        val r = 1f
        val sqrt3 = kotlin.math.sqrt(3.0).toFloat()
        val p2x = (cos(PI / 3.0)).toFloat()
        val p2y = (sin(PI / 3.0)).toFloat()
        val p = SkPathBuilder()
            .moveTo(1f, 0f)
            .arcTo(0f, 0f, p2x, p2y, r)
            .detach()
        // After moveTo(1,0), arcTo emits lineTo(T0). T0 = (0,0) + d * (1, 0) = (d, 0).
        assertEquals(sqrt3, p.coords[2], 1e-3f, "T0.x = d = r*sqrt(3)")
        assertEquals(0f, p.coords[3], 1e-3f, "T0.y = 0")
    }

    // --- SkPath.computeBounds --------------------------------------------

    @Test
    fun `computeBounds on an empty path is the origin rect`() {
        val empty = SkPathBuilder().detach()
        val b = empty.computeBounds()
        assertEquals(0f, b.left); assertEquals(0f, b.top)
        assertEquals(0f, b.right); assertEquals(0f, b.bottom)
    }

    @Test
    fun `computeBounds on a polygonal path returns its enclosing rect`() {
        val p = SkPath.Polygon(arrayOf(0f to 0f, 10f to 5f, 5f to 10f), isClosed = true)
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f)
        assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f)
        assertEquals(10f, b.bottom, 1e-4f)
    }

    @Test
    fun `computeBounds on a Bezier path expands to control points (conservative)`() {
        // Quadratic curve with a control point bulging well outside the chord.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(50f, 1000f, 100f, 0f)
            .detach()
        val b = p.computeBounds()
        // Includes control point (50, 1000), even though the actual curve
        // peaks at y=500. This is the expected `getBounds()` semantics.
        assertEquals(0f, b.left, 1e-4f)
        assertEquals(0f, b.top, 1e-4f)
        assertEquals(100f, b.right, 1e-4f)
        assertEquals(1000f, b.bottom, 1e-4f)
    }

    // --- SkPath.makeOffset -----------------------------------------------

    @Test
    fun `makeOffset with zero deltas returns the same instance`() {
        val p = SkPath.Rect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val q = p.makeOffset(0f, 0f)
        assertSame(p, q)
    }

    @Test
    fun `makeOffset shifts every coordinate without touching verbs or weights`() {
        val src = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .quadTo(10f, 10f, 20f, 10f)
            .conicTo(20f, 20f, 10f, 20f, 0.5f)
            .close()
            .detach()
        val moved = src.makeOffset(5f, 7f)
        assertTrue(src.verbs contentEquals moved.verbs)
        // Coords shifted by (5, 7).
        for (i in src.coords.indices step 2) {
            assertEquals(src.coords[i] + 5f, moved.coords[i], 1e-4f)
            assertEquals(src.coords[i + 1] + 7f, moved.coords[i + 1], 1e-4f)
        }
        // Conic weight unchanged.
        assertEquals(src.conicWeights[0], moved.conicWeights[0], 1e-4f)
        // Bounds shift accordingly.
        val sb = src.computeBounds()
        val mb = moved.computeBounds()
        assertEquals(sb.left + 5f, mb.left, 1e-4f)
        assertEquals(sb.top + 7f, mb.top, 1e-4f)
        assertEquals(sb.right + 5f, mb.right, 1e-4f)
        assertEquals(sb.bottom + 7f, mb.bottom, 1e-4f)
    }

    @Test
    fun `makeOffset preserves fill type`() {
        val src = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .moveTo(0f, 0f).lineTo(10f, 10f)
            .detach()
        val moved = src.makeOffset(1f, 1f)
        assertEquals(SkPathFillType.kEvenOdd, moved.fillType)
    }
}
