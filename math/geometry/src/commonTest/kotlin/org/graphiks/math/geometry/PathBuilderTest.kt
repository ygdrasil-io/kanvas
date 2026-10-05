package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PathBuilderTest {
    @Test
    fun `build freezes previous path values`() {
        val builder = PathBuilder().moveTo(1f, 2f).lineTo(3f, 4f)
        val first = builder.build()
        builder.lineTo(5f, 6f)

        assertEquals(2, first.segmentCount)
        assertEquals(PathSegmentF32.LineTo(Point2F32(3f, 4f)), first.segmentAt(1))
    }

    @Test
    fun `add rect appends a closed clockwise contour`() {
        val builder = PathBuilder()
        assertSame(builder, builder.addRect(RectF32.ofLTRB(1f, 2f, 5f, 8f)))

        assertEquals(
            listOf(
                PathSegmentF32.MoveTo(Point2F32(1f, 2f)),
                PathSegmentF32.LineTo(Point2F32(5f, 2f)),
                PathSegmentF32.LineTo(Point2F32(5f, 8f)),
                PathSegmentF32.LineTo(Point2F32(1f, 8f)),
                PathSegmentF32.Close,
            ),
            builder.build().toList(),
        )
    }

    @Test
    fun `add oval appends a closed contour of four cubic curves`() {
        val path = PathBuilder().addOval(RectF32.ofLTRB(2f, 4f, 10f, 12f)).build()

        assertEquals(6, path.segmentCount)
        assertEquals(PathSegmentF32.MoveTo(Point2F32(10f, 8f)), path.segmentAt(0))
        assertEquals(Point2F32(6f, 12f), (path.segmentAt(1) as PathSegmentF32.CubicTo).point)
        assertEquals(Point2F32(2f, 8f), (path.segmentAt(2) as PathSegmentF32.CubicTo).point)
        assertEquals(Point2F32(6f, 4f), (path.segmentAt(3) as PathSegmentF32.CubicTo).point)
        assertEquals(Point2F32(10f, 8f), (path.segmentAt(4) as PathSegmentF32.CubicTo).point)
        assertEquals(PathSegmentF32.Close, path.segmentAt(5))
    }

    @Test
    fun `closed helpers use the same clockwise contour orientation`() {
        val rect = RectF32.ofLTRB(0f, 0f, 10f, 8f)
        val paths = listOf(
            PathBuilder().addRect(rect).build(),
            PathBuilder().addOval(rect).build(),
            PathBuilder().addRRect(RRectF32.of(rect, radius = 2f)).build(),
        )

        paths.forEach { path ->
            assertTrue(signedArea(path) > 0f)
        }
    }

    @Test
    fun `add oval keeps all emitted points finite for extreme finite bounds`() {
        val path = PathBuilder()
            .addOval(RectF32.ofLTRB(-Float.MAX_VALUE, -1f, Float.MAX_VALUE, 1f))
            .build()

        path.flatMap(::pointsIn).forEach { point ->
            assertTrue(point.isFinite())
        }
    }

    @Test
    fun `add rounded rect uses its corner radii to produce arcs`() {
        val path = PathBuilder()
            .addRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 10f, 8f), radius = 2f))
            .build()

        assertEquals(PathSegmentF32.MoveTo(Point2F32(2f, 0f)), path.segmentAt(0))
        assertEquals(PathSegmentF32.LineTo(Point2F32(8f, 0f)), path.segmentAt(1))
        assertEquals(Point2F32(10f, 2f), (path.segmentAt(2) as PathSegmentF32.ArcTo).point)
        assertEquals(Point2F32(8f, 8f), (path.segmentAt(4) as PathSegmentF32.ArcTo).point)
        assertEquals(Point2F32(0f, 6f), (path.segmentAt(6) as PathSegmentF32.ArcTo).point)
        assertEquals(Point2F32(2f, 0f), (path.segmentAt(8) as PathSegmentF32.ArcTo).point)
        assertEquals(PathSegmentF32.Close, path.segmentAt(9))
    }

    @Test
    fun `add rounded rect normalizes extreme finite radii without collapsing them`() {
        val radius = CornerRadiiF32.of(Float.MAX_VALUE)
        val path = PathBuilder()
            .addRRect(
                RRectF32(
                    RectF32.ofLTRB(0f, 0f, 10f, 8f),
                    radius,
                    radius,
                    radius,
                    radius,
                ),
            )
            .build()

        val arcs = path.filterIsInstance<PathSegmentF32.ArcTo>()
        assertEquals(4, arcs.size)
        path.flatMap(::pointsIn).forEach { point -> assertTrue(point.isFinite()) }
        arcs.forEach { arc ->
            assertEquals(4f, arc.radius.x, arc.toString())
            assertEquals(4f, arc.radius.y, arc.toString())
            assertTrue(arc.radius.isFinite())
            assertTrue(arc.point.isFinite())
        }
    }

    @Test
    fun `add path copies all source contours`() {
        val source = PathBuilder().moveTo(1f, 2f).lineTo(3f, 4f).close().build()
        val target = PathBuilder().moveTo(5f, 6f)

        assertSame(target, target.addPath(source))
        assertEquals(
            listOf(
                PathSegmentF32.MoveTo(Point2F32(5f, 6f)),
                PathSegmentF32.MoveTo(Point2F32(1f, 2f)),
                PathSegmentF32.LineTo(Point2F32(3f, 4f)),
                PathSegmentF32.Close,
            ),
            target.build().toList(),
        )
    }

    private fun signedArea(path: PathF32): Float {
        val points = path.flatMap(::pointsIn)
        return points.indices.sumOf { index ->
            val point = points[index]
            val next = points[(index + 1) % points.size]
            (point.x * next.y - point.y * next.x).toDouble()
        }.toFloat() / 2f
    }

    private fun pointsIn(segment: PathSegmentF32): List<Point2F32> = when (segment) {
        is PathSegmentF32.MoveTo -> listOf(segment.point)
        is PathSegmentF32.LineTo -> listOf(segment.point)
        is PathSegmentF32.QuadTo -> listOf(segment.control, segment.point)
        is PathSegmentF32.CubicTo -> listOf(segment.control1, segment.control2, segment.point)
        is PathSegmentF32.ArcTo -> listOf(segment.point)
        PathSegmentF32.Close -> emptyList()
    }
}
