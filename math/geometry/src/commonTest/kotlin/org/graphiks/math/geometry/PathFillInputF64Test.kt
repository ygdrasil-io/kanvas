package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.math.vector.Vector2F64

class PathFillInputF64Test {
    @Test
    fun `path fill input snapshots every segment in F64`() {
        val source = mutableListOf<PathFillSegmentF64>(
            PathFillSegmentF64.ArcTo(
                radius = Vector2F64(3.0, 4.0),
                xAxisRotationDegreesF64 = 15.0,
                largeArc = true,
                sweep = false,
                point = Point2F64(5.0, 6.0),
            ),
        )

        val input = PathFillInputF64.of(FillRule.EVEN_ODD, source)
        source[0] = PathFillSegmentF64.Close
        source += PathFillSegmentF64.MoveTo(Point2F64(7.0, 8.0))

        assertEquals(FillRule.EVEN_ODD, input.fillRule)
        assertEquals(1, input.segmentCountI32)
        assertEquals(
            PathFillSegmentF64.ArcTo(
                radius = Vector2F64(3.0, 4.0),
                xAxisRotationDegreesF64 = 15.0,
                largeArc = true,
                sweep = false,
                point = Point2F64(5.0, 6.0),
            ),
            input.segmentAtI32(0),
        )
    }
}
