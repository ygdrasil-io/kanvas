package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.vector.Vector2F64

class PathScaledClosedArcF64Test {
    @Test
    fun `unit closed arc keeps its analytic arc and closing line spans`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                PathFillInputF64.of(
                    FillRule.WINDING,
                    listOf(
                        PathFillSegmentF64.MoveTo(Point2F64(-1.0, 0.0)),
                        PathFillSegmentF64.ArcTo(
                            Vector2F64(1.0, 1.0), 0.0, false, false, Point2F64(1.0, 0.0),
                        ),
                        PathFillSegmentF64.Close,
                    ),
                ),
                dashF64 = null,
            ),
        )

        assertEquals(1, result.centerlineF64.contourCountI32)
        assertTrue(result.centerlineF64.isContourClosed(0))
        val spans = result.centerlineF64.copyContourSpansF64(0)
        assertEquals(2, spans.size)
        spans.forEach { span ->
            assertEquals(0.0, span.startParameterF64)
            assertEquals(1.0, span.endParameterF64)
        }
        assertIs<PathStrokeSvgArcPrimitiveF64>(spans[0].primitiveF64)
        assertIs<PathStrokeLinePrimitiveF64>(spans[1].primitiveF64)
        assertPointEquals(Point2F64(-1.0, 0.0), spans[0].primitiveF64.pointAtF64(0.0))
        assertPointEquals(Point2F64(0.0, 1.0), spans[0].primitiveF64.pointAtF64(0.5))
        assertPointEquals(Point2F64(-0.7071067811865476, 0.7071067811865476), spans[0].primitiveF64.pointAtF64(0.25))
        assertPointEquals(Point2F64(0.7071067811865476, 0.7071067811865476), spans[0].primitiveF64.pointAtF64(0.75))
        assertPointEquals(Point2F64(1.0, 0.0), spans[0].primitiveF64.pointAtF64(1.0))
        assertPointEquals(Point2F64(1.0, 0.0), spans[1].primitiveF64.pointAtF64(0.0))
        assertPointEquals(Point2F64(0.0, 0.0), spans[1].primitiveF64.pointAtF64(0.5))
        assertPointEquals(Point2F64(-1.0, 0.0), spans[1].primitiveF64.pointAtF64(1.0))
    }

    @Test
    fun `device closed arc keeps its analytic arc and closing line spans`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                PathFillInputF64.of(
                    FillRule.WINDING,
                    listOf(
                        PathFillSegmentF64.MoveTo(Point2F64(24.0, 120.0)),
                        PathFillSegmentF64.ArcTo(
                            Vector2F64(96.0, 96.0), 0.0, false, false, Point2F64(216.0, 120.0),
                        ),
                        PathFillSegmentF64.Close,
                    ),
                ),
                dashF64 = null,
            ),
        )

        assertEquals(1, result.centerlineF64.contourCountI32)
        assertTrue(result.centerlineF64.isContourClosed(0))
        val spans = result.centerlineF64.copyContourSpansF64(0)
        assertEquals(2, spans.size)
        spans.forEach { span ->
            assertEquals(0.0, span.startParameterF64)
            assertEquals(1.0, span.endParameterF64)
        }
        assertIs<PathStrokeSvgArcPrimitiveF64>(spans[0].primitiveF64)
        assertIs<PathStrokeLinePrimitiveF64>(spans[1].primitiveF64)
        assertPointEquals(Point2F64(24.0, 120.0), spans[0].primitiveF64.pointAtF64(0.0))
        assertPointEquals(Point2F64(120.0, 216.0), spans[0].primitiveF64.pointAtF64(0.5))
        assertPointEquals(Point2F64(52.11774900609144, 187.88225099390857), spans[0].primitiveF64.pointAtF64(0.25))
        assertPointEquals(Point2F64(187.88225099390857, 187.88225099390857), spans[0].primitiveF64.pointAtF64(0.75))
        assertPointEquals(Point2F64(216.0, 120.0), spans[0].primitiveF64.pointAtF64(1.0))
        assertPointEquals(Point2F64(216.0, 120.0), spans[1].primitiveF64.pointAtF64(0.0))
        assertPointEquals(Point2F64(120.0, 120.0), spans[1].primitiveF64.pointAtF64(0.5))
        assertPointEquals(Point2F64(24.0, 120.0), spans[1].primitiveF64.pointAtF64(1.0))
    }

    private fun assertPointEquals(expected: Point2F64, actual: Point2F64) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, actual.y, absoluteTolerance = 1e-9)
    }
}
