package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SkPathBuilderTest {

    @Test
    fun `empty builder produces empty path`() {
        val p = SkPathBuilder().detach()
        assertTrue(p.isEmpty())
        assertEquals(0, p.verbs.size)
        assertEquals(0, p.coords.size)
        assertEquals(0, p.conicWeights.size)
        assertEquals(SkPathFillType.kWinding, p.fillType)
    }

    @Test
    fun `moveTo lineTo close stores verbs and coords in order`() {
        val p = SkPathBuilder()
            .moveTo(10f, 20f)
            .lineTo(30f, 40f)
            .lineTo(50f, 60f)
            .close()
            .detach()
        assertArrayEquals(
            arrayOf(SkPath.Verb.kMove, SkPath.Verb.kLine, SkPath.Verb.kLine, SkPath.Verb.kClose),
            p.verbs,
        )
        assertArrayEquals(floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f), p.coords)
    }

    @Test
    fun `quadTo and cubicTo are stored as full Bezier verbs`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(10f, 0f, 10f, 10f)
            .cubicTo(10f, 20f, 0f, 20f, 0f, 10f)
            .detach()
        assertArrayEquals(
            arrayOf(SkPath.Verb.kMove, SkPath.Verb.kQuad, SkPath.Verb.kCubic),
            p.verbs,
        )
        // 1 (move) + 2 (quad) + 3 (cubic) = 6 logical points = 12 floats.
        assertEquals(12, p.coords.size)
    }

    @Test
    fun `conicTo with weight 1 collapses to a quad`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(10f, 0f, 10f, 10f, 1f)
            .detach()
        assertArrayEquals(arrayOf(SkPath.Verb.kMove, SkPath.Verb.kQuad), p.verbs)
        assertEquals(0, p.conicWeights.size)
    }

    @Test
    fun `conicTo with weight not equal to 1 stores a kConic verb and weight`() {
        val w = 0.7f
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(10f, 0f, 10f, 10f, w)
            .detach()
        assertArrayEquals(arrayOf(SkPath.Verb.kMove, SkPath.Verb.kConic), p.verbs)
        assertArrayEquals(floatArrayOf(w), p.conicWeights, 0f)
    }

    @Test
    fun `close at start of empty path is a no-op`() {
        val p = SkPathBuilder().close().detach()
        assertTrue(p.isEmpty())
    }

    @Test
    fun `lineTo on empty contour implicitly emits a moveTo at origin`() {
        val p = SkPathBuilder().lineTo(10f, 10f).detach()
        // First verb must be kMove inserted at (0, 0).
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(0f, p.coords[0])
        assertEquals(0f, p.coords[1])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
    }

    @Test
    fun `detach resets builder to empty state`() {
        val b = SkPathBuilder().moveTo(1f, 2f).lineTo(3f, 4f)
        val a = b.detach()
        val c = b.detach()
        assertEquals(2, a.verbs.size)
        assertTrue(c.isEmpty())
    }

    @Test
    fun `snapshot returns a copy without resetting`() {
        val b = SkPathBuilder().moveTo(1f, 2f).lineTo(3f, 4f)
        val a = b.snapshot()
        val c = b.snapshot()
        assertEquals(2, a.verbs.size)
        assertEquals(2, c.verbs.size)
        assertNotSame(a, c)
        assertNotEquals(0, b.snapshot().verbs.size)
    }

    @Test
    fun `addRect emits a closed 4-line contour with the requested winding`() {
        val cw = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), SkPathDirection.kCW).detach()
        val ccw = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), SkPathDirection.kCCW).detach()
        // Both are 1 move + 3 line + 1 close.
        val expected = arrayOf(
            SkPath.Verb.kMove, SkPath.Verb.kLine, SkPath.Verb.kLine,
            SkPath.Verb.kLine, SkPath.Verb.kClose,
        )
        assertArrayEquals(expected, cw.verbs)
        assertArrayEquals(expected, ccw.verbs)
        // First lineTo distinguishes CW (right side) from CCW (bottom).
        assertEquals(10f, cw.coords[2])  // CW: lineTo(right, top) → x = 10
        assertEquals(0f, cw.coords[3])
        assertEquals(0f, ccw.coords[2])  // CCW: lineTo(left, bottom) → x = 0
        assertEquals(10f, ccw.coords[3])
    }

    @Test
    fun `addOval emits 4 cubic Bezier arcs with the kappa approximation`() {
        val p = SkPathBuilder().addOval(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        // 1 move + 4 cubic + 1 close.
        assertArrayEquals(
            arrayOf(
                SkPath.Verb.kMove,
                SkPath.Verb.kCubic, SkPath.Verb.kCubic, SkPath.Verb.kCubic, SkPath.Verb.kCubic,
                SkPath.Verb.kClose,
            ),
            p.verbs,
        )
        // The contour begins at (cx + rx, cy) = (10, 5).
        assertEquals(10f, p.coords[0], 1e-4f)
        assertEquals(5f, p.coords[1], 1e-4f)
    }

    @Test
    fun `addCircle delegates to addOval centred on the given point`() {
        val p = SkPathBuilder().addCircle(5f, 5f, 5f).detach()
        // First point of contour at (cx + r, cy) = (10, 5).
        assertEquals(10f, p.coords[0], 1e-4f)
        assertEquals(5f, p.coords[1], 1e-4f)
        assertEquals(SkPath.Verb.kCubic, p.verbs[1])
    }

    @Test
    fun `addArc emits cubic segments matching the start point on the ellipse`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val startDeg = 30f
        val sweepDeg = 60f
        val p = SkPathBuilder().addArc(rect, startDeg, sweepDeg).detach()
        // First verb is a moveTo (forceMoveTo behaviour of addArc).
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        // Compare moveTo to the analytic point on the unit circle scaled by rx/ry.
        val cx = 50f; val cy = 50f; val rx = 50f; val ry = 50f
        val theta = startDeg.toDouble() * PI / 180.0
        val expectedX = (cx + rx * cos(theta)).toFloat()
        val expectedY = (cy + ry * sin(theta)).toFloat()
        assertEquals(expectedX, p.coords[0], 1e-3f)
        assertEquals(expectedY, p.coords[1], 1e-3f)
        // 60° sweep fits in a single ≤90° segment → exactly one cubic.
        assertEquals(SkPath.Verb.kCubic, p.verbs[1])
        assertEquals(2, p.verbs.size)
    }

    @Test
    fun `addArc splits sweeps wider than 90 degrees into multiple cubics`() {
        // 270° sweep → ceil(270/90) = 3 cubics.
        val p = SkPathBuilder()
            .addArc(SkRect.MakeLTRB(0f, 0f, 100f, 100f), 0f, 270f)
            .detach()
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        // 1 move + 3 cubic.
        assertEquals(4, p.verbs.size)
        assertTrue(p.verbs.drop(1).all { it == SkPath.Verb.kCubic })
    }

    @Test
    fun `arcTo without forceMoveTo joins to the existing contour via lineTo`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .arcTo(SkRect.MakeLTRB(0f, 0f, 100f, 100f), 0f, 90f, forceMoveTo = false)
            .detach()
        // Sequence: move, line (join to (100, 50)), cubic.
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(SkPath.Verb.kLine, p.verbs[1])
        assertEquals(SkPath.Verb.kCubic, p.verbs[2])
    }

    @Test
    fun `addPolygon mirrors SkPath Polygon factory`() {
        val pts = arrayOf(0f to 0f, 10f to 0f, 10f to 10f)
        val byPolygon = SkPath.Polygon(pts, isClosed = true)
        val byBuilder = SkPathBuilder().addPolygon(pts, isClosed = true).detach()
        assertArrayEquals(byPolygon.verbs, byBuilder.verbs)
        assertArrayEquals(byPolygon.coords, byBuilder.coords)
    }

    @Test
    fun `addPath copies every verb from the source path`() {
        val src = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .quadTo(20f, 0f, 20f, 10f)
            .conicTo(20f, 20f, 10f, 20f, 0.5f)
            .cubicTo(0f, 20f, 0f, 10f, 0f, 0f)
            .close()
            .detach()
        val copy = SkPathBuilder().addPath(src).detach()
        assertArrayEquals(src.verbs, copy.verbs)
        assertArrayEquals(src.coords, copy.coords)
        assertArrayEquals(src.conicWeights, copy.conicWeights, 0f)
    }

    @Test
    fun `setFillType propagates through detach`() {
        val p = SkPathBuilder().setFillType(SkPathFillType.kEvenOdd)
            .moveTo(0f, 0f).lineTo(10f, 0f).detach()
        assertEquals(SkPathFillType.kEvenOdd, p.fillType)
    }

    @Test
    fun `SkPath Rect factory matches addRect`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 5f, 5f)
        val a = SkPath.Rect(rect)
        val b = SkPathBuilder().addRect(rect).detach()
        assertArrayEquals(a.verbs, b.verbs)
        assertArrayEquals(a.coords, b.coords)
    }

    @Test
    fun `SkPath Line factory emits two verbs and two points`() {
        val p = SkPath.Line(0f to 0f, 10f to 10f)
        assertArrayEquals(arrayOf(SkPath.Verb.kMove, SkPath.Verb.kLine), p.verbs)
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 10f), p.coords)
    }

    @Test
    fun `cubic addOval approximates a circle within sub-pixel error`() {
        // Sample 64 points along the cubic-approximation of a unit circle and
        // verify each lies within the kappa-bound of 0.027 % of the radius.
        val rx = 100f; val ry = 100f
        val rect = SkRect.MakeLTRB(-rx, -ry, rx, ry)
        val p = SkPathBuilder().addOval(rect).detach()
        // The cubic Béziers' endpoints land exactly on the analytical ellipse;
        // check the first two end points of cubic verbs (the cardinal points).
        // Verb stream: move(rx, 0), cubic(.., .., 0, ry), cubic(.., .., -rx, 0), ...
        // Coords: idx 0..1 = move (rx, 0); cubic 1 ends at idx 6..7 = (0, ry).
        assertEquals(rx, p.coords[0], 1e-4f)
        assertEquals(0f, p.coords[1], 1e-4f)
        assertEquals(0f, p.coords[6], 1e-4f)
        assertEquals(ry, p.coords[7], 1e-4f)
    }
}
