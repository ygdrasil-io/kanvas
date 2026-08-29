package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PathF32Test {
    @Test
    fun `paths retain every segment verb across multiple contours`() {
        val path = PathBuilder()
            .moveTo(1f, 2f)
            .lineTo(3f, 4f)
            .quadTo(5f, 6f, 7f, 8f)
            .cubicTo(9f, 10f, 11f, 12f, 13f, 14f)
            .arcTo(15f, 16f, 17f, largeArc = true, sweep = false, x = 18f, y = 19f)
            .close()
            .moveTo(20f, 21f)
            .lineTo(22f, 23f)
            .build()

        assertEquals(
            listOf(
                PathSegmentF32.MoveTo(Point2F32(1f, 2f)),
                PathSegmentF32.LineTo(Point2F32(3f, 4f)),
                PathSegmentF32.QuadTo(Point2F32(5f, 6f), Point2F32(7f, 8f)),
                PathSegmentF32.CubicTo(Point2F32(9f, 10f), Point2F32(11f, 12f), Point2F32(13f, 14f)),
                PathSegmentF32.ArcTo(Vector2F32(15f, 16f), 17f, largeArc = true, sweep = false, Point2F32(18f, 19f)),
                PathSegmentF32.Close,
                PathSegmentF32.MoveTo(Point2F32(20f, 21f)),
                PathSegmentF32.LineTo(Point2F32(22f, 23f)),
            ),
            path.toList(),
        )
    }

    @Test
    fun `builders preserve each fill rule in the built path`() {
        val paths = FillRule.entries.map { fillRule ->
            PathBuilder(fillRule).moveTo(0f, 0f).build()
        }

        assertEquals(FillRule.entries.toList(), paths.map(PathF32::fillRule))
    }

    @Test
    fun `paths use structural equality for fill rule and segments`() {
        val first = PathBuilder(FillRule.EVEN_ODD).moveTo(1f, 2f).lineTo(3f, 4f).build()
        val same = PathBuilder(FillRule.EVEN_ODD).moveTo(1f, 2f).lineTo(3f, 4f).build()
        val differentRule = PathBuilder(FillRule.WINDING).moveTo(1f, 2f).lineTo(3f, 4f).build()
        val differentSegments = PathBuilder(FillRule.EVEN_ODD).moveTo(1f, 2f).lineTo(5f, 6f).build()

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertEquals(first.toString(), same.toString())
        assertNotEquals(first, differentRule)
        assertNotEquals(first, differentSegments)
    }
}
