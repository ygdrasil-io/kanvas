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

    @Test
    fun `measures a large finite diagonal without overflow`() {
        val measure = PathMeasureF32(PathBuilder().moveTo(0f, 0f).lineTo(2e19f, 2e19f).build())

        assertTrue(measure.length.isFinite())
        assertTrue(measure.length > 2.8e19f)
    }

    @Test
    fun `curve position uses the analytic tangent`() {
        val measure = PathMeasureF32(
            PathBuilder().moveTo(0f, 0f).quadTo(1f, 1f, 2f, 0f).build(),
        )

        val location = requireNotNull(measure.position(measure.length / 2f))

        assertTrue(PathPredicatesF32.almostEqualUlps(location.point.x, 1f, 4))
        assertTrue(PathPredicatesF32.almostEqualUlps(location.point.y, 0.5f, 4))
        assertTrue(PathPredicatesF32.almostEqualUlps(location.tangent.x, 1f, 4))
        assertTrue(PathPredicatesF32.almostEqualUlps(location.tangent.y, 0f, 4))
    }

    @Test
    fun `segment orders its clamped distance interval`() {
        val measure = PathMeasureF32(PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build())

        assertEquals(measure.segment(2f, 8f), measure.segment(8f, 2f))
    }

    @Test
    fun `segment preserves the F64 clamped endpoint after a large first edge`() {
        val measure = PathMeasureF32(
            PathBuilder().moveTo(0f, 0f).lineTo(1e8f, 0f).lineTo(1e8f, 10f).build(),
        )

        val segment = requireNotNull(measure.segment(0f, Float.POSITIVE_INFINITY))

        assertEquals(Point2F32(1e8f, 10f), requireNotNull(PathAnalysisF32.line(segment)).end)
    }
}
