package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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
        assertEquals(Point2F32(6f, 4f), (path.segmentAt(1) as PathSegmentF32.CubicTo).point)
        assertEquals(Point2F32(2f, 8f), (path.segmentAt(2) as PathSegmentF32.CubicTo).point)
        assertEquals(Point2F32(6f, 12f), (path.segmentAt(3) as PathSegmentF32.CubicTo).point)
        assertEquals(Point2F32(10f, 8f), (path.segmentAt(4) as PathSegmentF32.CubicTo).point)
        assertEquals(PathSegmentF32.Close, path.segmentAt(5))
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
}
