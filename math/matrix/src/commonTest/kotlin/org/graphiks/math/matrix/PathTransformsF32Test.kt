package org.graphiks.math.matrix

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.RectF32

class PathTransformsF32Test {
    private fun assertNear(expected: Float, actual: Float, epsilon: Float = 1e-4f) {
        assertTrue(abs(expected - actual) <= epsilon, "expected $expected, got $actual")
    }

    @Test
    fun `translation maps every path endpoint and preserves arc metadata`() {
        val source = PathBuilder()
            .moveTo(10f, 0f)
            .arcTo(10f, 20f, 30f, largeArc = true, sweep = false, x = 0f, y = 20f)
            .build()

        val mapped = Matrix3x3F32.translation(100f, 50f).map(source)
        val arc = mapped.segmentAt(1) as PathSegmentF32.ArcTo

        assertEquals(PathSegmentF32.MoveTo(Point2F32(110f, 50f)), mapped.segmentAt(0))
        assertEquals(Point2F32(100f, 70f), arc.point)
        assertEquals(source.segmentAt(1), arc.copy(point = (source.segmentAt(1) as PathSegmentF32.ArcTo).point))
    }

    @Test
    fun `non uniform scale canonicalizes arc ellipse`() {
        val source = PathBuilder()
            .moveTo(10f, 0f)
            .arcTo(10f, 10f, 45f, largeArc = false, sweep = true, x = 0f, y = 10f)
            .build()

        val mapped = Matrix3x3F32.scaling(2f, 1f).map(source)
        val arc = mapped.segmentAt(1) as PathSegmentF32.ArcTo

        assertEquals(Point2F32(20f, 0f), mapped.segmentAt(0).let { (it as PathSegmentF32.MoveTo).point })
        assertNear(20f, arc.radius.x, 0.001f)
        assertNear(10f, arc.radius.y, 0.001f)
        assertNear(0f, arc.xAxisRotation, 0.001f)
        assertTrue(arc.sweep)
        assertEquals(Point2F32(0f, 10f), arc.point)
    }

    @Test
    fun `skew canonicalizes a small arc ellipse`() {
        val radius = 0.0005f
        val source = PathBuilder()
            .moveTo(radius, 0f)
            .arcTo(radius, radius, 0f, largeArc = false, sweep = true, x = 0f, y = radius)
            .build()

        val arc = Matrix3x3F32.skewing(1f, 0f).map(source).segmentAt(1) as PathSegmentF32.ArcTo

        assertNear(0.000809f, arc.radius.x, 0.000001f)
        assertNear(0.000309f, arc.radius.y, 0.000001f)
        assertNear(31.717f, arc.xAxisRotation, 0.001f)
        assertTrue(arc.sweep)
    }

    @Test
    fun `mirroring a path reverses arc sweep without changing source`() {
        val source = PathBuilder()
            .moveTo(5f, 0f)
            .arcTo(5f, 8f, 15f, largeArc = true, sweep = true, x = 4f, y = 6f)
            .build()

        val mapped = Matrix3x3F32.scaling(-1f, 1f).map(source)
        val arc = mapped.segmentAt(1) as PathSegmentF32.ArcTo

        assertFalse(arc.sweep)
        assertTrue((source.segmentAt(1) as PathSegmentF32.ArcTo).sweep)
        assertEquals(Point2F32(-4f, 6f), arc.point)
        assertTrue(arc.largeArc)
    }

    @Test
    fun `perspective maps endpoints with homogeneous division`() {
        val source = PathBuilder()
            .moveTo(1f, 2f)
            .lineTo(3f, 4f)
            .build()
        val matrix = Matrix3x3F32.of(2f, 0f, 4f, 0f, 3f, 6f, 0.1f, 0.2f, 1f)

        val mapped = matrix.map(source)

        assertEquals(Point2F32(6f / 1.5f, 12f / 1.5f), (mapped.segmentAt(0) as PathSegmentF32.MoveTo).point)
        assertEquals(Point2F32(10f / 2.1f, 18f / 2.1f), (mapped.segmentAt(1) as PathSegmentF32.LineTo).point)
    }

    @Test
    fun `rect oval and multiple contours retain structure and fill rule`() {
        val source = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 20f))
            .addOval(RectF32.ofLTRB(20f, 30f, 40f, 50f))
            .build()

        val mapped = Matrix3x3F32.translation(3f, -4f).map(source)

        assertEquals(source.fillRule, mapped.fillRule)
        assertEquals(source.segmentCount, mapped.segmentCount)
        assertEquals(2, mapped.filterIsInstance<PathSegmentF32.MoveTo>().count())
        assertEquals(
            Point2F32(3f, -4f),
            (mapped.segmentAt(0) as PathSegmentF32.MoveTo).point,
        )
        assertEquals(
            Point2F32(43f, 36f),
            (mapped.segmentAt(5) as PathSegmentF32.MoveTo).point,
        )
        assertTrue(mapped.segmentAt(5) is PathSegmentF32.MoveTo)
        assertTrue(mapped.segmentAt(mapped.segmentCount - 1) is PathSegmentF32.Close)
    }

    @Test
    fun `path extension delegates to matrix mapping`() {
        val source = PathBuilder().moveTo(1f, 2f).lineTo(3f, 4f).build()
        val matrix = Matrix3x3F32.scaling(2f, 3f)

        assertEquals(matrix.map(source), source.transformedBy(matrix))
    }
}
