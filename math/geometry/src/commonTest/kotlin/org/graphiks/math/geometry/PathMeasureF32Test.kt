package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PathMeasureF32Test {
    @Test
    fun `measures each contour and advances deterministically`() {
        val path = PathBuilder()
            .moveTo(0f, 0f).lineTo(3f, 4f)
            .moveTo(10f, 10f).lineTo(10f, 20f)
            .build()
        val measure = PathMeasureF32(path)

        assertEquals(5f, measure.length)
        assertFalse(measure.isClosed)
        assertTrue(measure.nextContour())
        assertEquals(10f, measure.length)
        assertFalse(measure.nextContour())
    }

    @Test
    fun `position clamps distance and returns a unit tangent`() {
        val measure = PathMeasureF32(PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build())

        assertEquals(PathLocationF32(Point2F32(0f, 0f), Point2F32(1f, 0f)), measure.position(-2f))
        assertEquals(PathLocationF32(Point2F32(5f, 0f), Point2F32(1f, 0f)), measure.position(5f))
        assertEquals(PathLocationF32(Point2F32(10f, 0f), Point2F32(1f, 0f)), measure.position(20f))
    }

    @Test
    fun `force closed adds closing edge and segment returns path geometry`() {
        val source = PathBuilder().moveTo(0f, 0f).lineTo(3f, 4f).build()
        val measure = PathMeasureF32(source, forceClosed = true)

        assertTrue(measure.isClosed)
        assertEquals(10f, measure.length)
        val segment = requireNotNull(measure.segment(2f, 8f))
        assertEquals(2, segment.segmentCount)
        assertNotNull(PathAnalysisF32.line(segment))
    }
}
