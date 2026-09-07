package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.vector.Vector2F64

class PathStrokeDashPreparationF64Test {
    @Test
    fun `undashed source curves remain parametric F64 spans`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                input(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
                    PathFillSegmentF64.QuadTo(Point2F64(15.0, 10.0), Point2F64(20.0, 0.0)),
                    PathFillSegmentF64.CubicTo(
                        Point2F64(25.0, 10.0),
                        Point2F64(35.0, 10.0),
                        Point2F64(40.0, 0.0),
                    ),
                    PathFillSegmentF64.ArcTo(
                        radius = Vector2F64(5.0, 5.0),
                        xAxisRotationDegreesF64 = 0.0,
                        largeArc = false,
                        sweep = true,
                        point = Point2F64(50.0, 0.0),
                    ),
                ),
                dashF64 = null,
            ),
        )

        val spans = result.centerlineF64.copyContourSpansF64(0)
        assertEquals(4, spans.size)
        assertEquals(0.0, spans[1].startParameterF64)
        assertEquals(1.0, spans[1].endParameterF64)
        assertPointEquals(Point2F64(15.0, 5.0), spans[1].primitiveF64.pointAtF64(0.5))
        assertPointEquals(Point2F64(30.0, 7.5), spans[2].primitiveF64.pointAtF64(0.5))
        assertTrue(abs(spans[3].primitiveF64.pointAtF64(0.5).y) > 0.1)
    }

    @Test
    fun `negative phase wraps and splits an open line in source arclength`() {
        val negative = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(line0To10(), dash2On2Off(-1.0)),
        )
        val wrapped = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(line0To10(), dash2On2Off(3.0)),
        )

        assertEquals(lineIntervals(wrapped.centerlineF64), lineIntervals(negative.centerlineF64))
        assertEquals(
            listOf(
                listOf(1.0 to 3.0),
                listOf(5.0 to 7.0),
                listOf(9.0 to 10.0),
            ),
            lineIntervals(negative.centerlineF64),
        )
    }

    @Test
    fun `dash cuts a nonuniform cubic at its source arclength`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                input(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.CubicTo(
                        Point2F64(0.0, 0.0),
                        Point2F64(0.0, 0.0),
                        Point2F64(10.0, 0.0),
                    ),
                ),
                PathStrokeDashF64.of(doubleArrayOf(2.0, 100.0), phaseF64 = 0.0),
            ),
        )

        val spanF64 = result.centerlineF64.copyContourSpansF64(0).single()
        val endF64 = spanF64.primitiveF64.pointAtF64(spanF64.endParameterF64)
        assertEquals(2.0, endF64.x, absoluteTolerance = 0.0625)
        assertEquals(0.0, endF64.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun `dash cut across cubic primitives stays within the contour arclength error`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                PathFillInputF64.of(
                    FillRule.WINDING,
                    listOf(PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0))) + List(24) { indexI32 ->
                        PathFillSegmentF64.CubicTo(
                            Point2F64(indexI32.toDouble(), 0.0),
                            Point2F64(indexI32.toDouble(), 0.0),
                            Point2F64(indexI32 + 1.0, 0.0),
                        )
                    },
                ),
                PathStrokeDashF64.of(doubleArrayOf(23.5, 100.0), phaseF64 = 0.0),
                PathStrokePolicyF64(
                    limitsI32 = PathStrokeLimitsI32(
                        maxAttemptedGeometryUnitsPerPathI32 = 1_000_000,
                        maxAttemptedGeometryUnitsPerFrameI32 = 1_000_000,
                    ),
                ),
            ),
        )

        val spanF64 = result.centerlineF64.copyContourSpansF64(0).last()
        val endF64 = spanF64.primitiveF64.pointAtF64(spanF64.endParameterF64)
        assertEquals(23.5, endF64.x, absoluteTolerance = 0.0625)
        assertEquals(0.0, endF64.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun `dash retains a collinear cubic retracing through its extrema`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                input(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.CubicTo(
                        Point2F64(10.0, 0.0),
                        Point2F64(-10.0, 0.0),
                        Point2F64(0.0, 0.0),
                    ),
                ),
                PathStrokeDashF64.of(doubleArrayOf(2.0, 100.0), phaseF64 = 0.0),
            ),
        )

        val spanF64 = result.centerlineF64.copyContourSpansF64(0).single()
        val endF64 = spanF64.primitiveF64.pointAtF64(spanF64.endParameterF64)
        assertEquals(2.0, endF64.x, absoluteTolerance = 0.0625)
        assertEquals(0.0, endF64.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun `closed contour keeps its explicit closing source span`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                input(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(10.0, 10.0)),
                    PathFillSegmentF64.Close,
                ),
                dashF64 = null,
            ),
        )

        assertEquals(1, result.centerlineF64.contourCountI32)
        assertTrue(result.centerlineF64.isContourClosed(0))
        val closing = result.centerlineF64.copyContourSpansF64(0).last()
        assertPointEquals(Point2F64(10.0, 10.0), closing.primitiveF64.pointAtF64(closing.startParameterF64))
        assertPointEquals(Point2F64(0.0, 0.0), closing.primitiveF64.pointAtF64(closing.endParameterF64))
    }

    @Test
    fun `closed dash joins only the on run that crosses its closure`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                input(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(10.0, 10.0)),
                    PathFillSegmentF64.LineTo(Point2F64(0.0, 10.0)),
                    PathFillSegmentF64.Close,
                ),
                PathStrokeDashF64.of(doubleArrayOf(6.0, 4.0), phaseF64 = 2.0),
            ),
        )

        assertEquals(4, result.centerlineF64.contourCountI32)
        val closureRunF64 = (0 until result.centerlineF64.contourCountI32)
            .map(result.centerlineF64::copyContourSpansF64)
            .single { spansF64 ->
                spansF64.size == 2 &&
                    spansF64.first().primitiveF64.pointAtF64(spansF64.first().startParameterF64) == Point2F64(0.0, 2.0)
            }
        assertPointEquals(Point2F64(0.0, 2.0), closureRunF64[0].primitiveF64.pointAtF64(closureRunF64[0].startParameterF64))
        assertPointEquals(Point2F64(0.0, 0.0), closureRunF64[0].primitiveF64.pointAtF64(closureRunF64[0].endParameterF64))
        assertPointEquals(Point2F64(0.0, 0.0), closureRunF64[1].primitiveF64.pointAtF64(closureRunF64[1].startParameterF64))
        assertPointEquals(Point2F64(4.0, 0.0), closureRunF64[1].primitiveF64.pointAtF64(closureRunF64[1].endParameterF64))
        assertTrue((0 until result.centerlineF64.contourCountI32).all { !result.centerlineF64.isContourClosed(it) })
    }

    @Test
    fun `zero length source segments emit no centerline`() {
        val result = preparePathStrokeCenterlinesF64(
            input(
                PathFillSegmentF64.MoveTo(Point2F64(2.0, 3.0)),
                PathFillSegmentF64.LineTo(Point2F64(2.0, 3.0)),
            ),
            dashF64 = dash2On2Off(0.0),
        )

        assertIs<PathStrokeCenterlinePreparationResult.Empty>(result)
    }

    @Test
    fun `zero off interval keeps adjacent on fragments distinct`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                line0To10(),
                PathStrokeDashF64.of(doubleArrayOf(2.0, 0.0), phaseF64 = 0.0),
            ),
        )

        assertEquals(
            listOf(
                listOf(0.0 to 2.0),
                listOf(2.0 to 4.0),
                listOf(4.0 to 6.0),
                listOf(6.0 to 8.0),
                listOf(8.0 to 10.0),
            ),
            lineIntervals(result.centerlineF64),
        )
    }

    @Test
    fun `dashed source contours do not merge across move commands`() {
        val result = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                input(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(3.0, 0.0)),
                    PathFillSegmentF64.MoveTo(Point2F64(10.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(13.0, 0.0)),
                ),
                dash2On2Off(0.0),
            ),
        )

        assertEquals(listOf(listOf(0.0 to 2.0), listOf(10.0 to 12.0)), lineIntervals(result.centerlineF64))
        assertTrue((0 until result.centerlineF64.contourCountI32).all { !result.centerlineF64.isContourClosed(it) })
    }

    @Test
    fun `published frame usage makes a second preparation refuse before emission`() {
        val first = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(line0To10(), dash2On2Off(0.0)),
        )
        val usedUnits = first.frameWorkUsageAfterI64.attemptedGeometryUnitCountI64
        assertTrue(usedUnits > 0L)

        val rejected = preparePathStrokeCenterlinesF64(
            inputF64 = line0To10(),
            dashF64 = dash2On2Off(0.0),
            policyF64 = PathStrokePolicyF64(
                limitsI32 = PathStrokeLimitsI32(
                    maxAttemptedGeometryUnitsPerFrameI32 = usedUnits.toInt(),
                ),
            ),
            frameWorkUsageBeforeI64 = first.frameWorkUsageAfterI64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.FrameWorkLimit,
            assertIs<PathStrokeCenterlinePreparationResult.ResourceLimitExceeded>(rejected).reason,
        )
    }

    private fun line0To10(): PathFillInputF64 = input(
        PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
        PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
    )

    private fun dash2On2Off(phaseF64: Double): PathStrokeDashF64 =
        PathStrokeDashF64.of(doubleArrayOf(2.0, 2.0), phaseF64)

    private fun input(vararg segments: PathFillSegmentF64): PathFillInputF64 =
        PathFillInputF64.of(FillRule.WINDING, segments.asList())

    private fun lineIntervals(centerlineF64: PathStrokeCenterlineF64): List<List<Pair<Double, Double>>> =
        (0 until centerlineF64.contourCountI32).map { contourIndexI32 ->
            centerlineF64.copyContourSpansF64(contourIndexI32).map { spanF64 ->
                spanF64.primitiveF64.pointAtF64(spanF64.startParameterF64).x to
                    spanF64.primitiveF64.pointAtF64(spanF64.endParameterF64).x
            }
        }

    private fun assertPointEquals(expected: Point2F64, actual: Point2F64) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, actual.y, absoluteTolerance = 1e-9)
    }
}
