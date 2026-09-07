package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PathStrokeOutlinePreparationF64Test {
    @Test
    fun `butt cap retains the finite line endpoints while expanding by its half width`() {
        val outline = finiteOutline(
            lineCenterline(),
            finiteStyle(cap = PathStrokeCap.Butt),
        )

        assertEquals(1, outline.contourCountI32)
        assertBoundsEquals(0.0, -2.0, 10.0, 2.0, boundsOf(outline))
        assertAllPointsFinite(outline)
    }

    @Test
    fun `round cap extends an open line by its half width`() {
        val outline = finiteOutline(
            lineCenterline(),
            finiteStyle(cap = PathStrokeCap.Round),
        )

        assertBoundsEquals(-2.0, -2.0, 12.0, 2.0, boundsOf(outline))
    }

    @Test
    fun `square cap extends an open line by its half width`() {
        val outline = finiteOutline(
            lineCenterline(),
            finiteStyle(cap = PathStrokeCap.Square),
        )

        assertBoundsEquals(-2.0, -2.0, 12.0, 2.0, boundsOf(outline))
    }

    @Test
    fun `miter join contains the outer offset intersection when it is within limit`() {
        val outline = finiteOutline(
            rightAngleCenterline(),
            finiteStyle(join = PathStrokeJoin.Miter, miterLimitF64 = 4.0),
        )

        assertTrue(boundaryPoints(outline).any { pointF64 -> pointF64.near(12.0, -2.0) })
    }

    @Test
    fun `miter over limit becomes bevel without refusal`() {
        val outline = finiteOutline(
            rightAngleCenterline(),
            finiteStyle(join = PathStrokeJoin.Miter, miterLimitF64 = 1.0),
        )

        assertTrue(boundaryPoints(outline).none { pointF64 -> pointF64.near(12.0, -2.0) })
        assertAllPointsFinite(outline)
    }

    @Test
    fun `miter limit below one becomes bevel without refusal`() {
        val outline = finiteOutline(
            rightAngleCenterline(),
            finiteStyle(join = PathStrokeJoin.Miter, miterLimitF64 = 0.5),
        )

        assertTrue(boundaryPoints(outline).none { pointF64 -> pointF64.near(12.0, -2.0) })
        assertAllPointsFinite(outline)
    }

    @Test
    fun `bevel join omits the outer offset intersection`() {
        val outline = finiteOutline(
            rightAngleCenterline(),
            finiteStyle(join = PathStrokeJoin.Bevel, miterLimitF64 = 4.0),
        )

        assertTrue(boundaryPoints(outline).none { pointF64 -> pointF64.near(12.0, -2.0) })
    }

    @Test
    fun `round join keeps a circular point between its two outer offsets`() {
        val outline = finiteOutline(
            rightAngleCenterline(),
            finiteStyle(join = PathStrokeJoin.Round),
        )

        assertTrue(boundaryPoints(outline).any { pointF64 ->
            pointF64.x > 10.0 && pointF64.y < 0.0 &&
                abs(pointF64.distanceTo(Point2F64(10.0, 0.0)) - 2.0) <= 1e-9
        })
    }

    @Test
    fun `closed contour has no cap-dependent geometry`() {
        val buttBoundsF64 = boundsOf(
            finiteOutline(closedSquareCenterline(), finiteStyle(cap = PathStrokeCap.Butt)),
        )
        val squareBoundsF64 = boundsOf(
            finiteOutline(closedSquareCenterline(), finiteStyle(cap = PathStrokeCap.Square)),
        )

        assertEquals(buttBoundsF64, squareBoundsF64)
    }

    @Test
    fun `finite curve outline evaluates the centerline normal at its source parameter`() {
        val outline = finiteOutline(
            centerline(
                PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                PathFillSegmentF64.QuadTo(Point2F64(5.0, 10.0), Point2F64(10.0, 0.0)),
            ),
            finiteStyle(widthF64 = 2.0),
        )

        assertTrue(boundaryPoints(outline).any { pointF64 -> pointF64.near(5.0, 6.0) })
        assertAllPointsFinite(outline)
    }

    @Test
    fun `reversal cusp and duplicate points produce a finite outline`() {
        val outline = finiteOutline(
            centerline(
                PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(0.0, 0.0)),
            ),
            finiteStyle(join = PathStrokeJoin.Miter),
        )

        assertAllPointsFinite(outline)
    }

    @Test
    fun `hairline is expanded to one device pixel after projection`() {
        val centerlineF64 = lineCenterline()
        val result = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareProjectedHairlineOutlineF64(
                centerlineF64,
                hairlineStyle(),
                IdentityProjectionF64,
            ),
        )

        assertBoundsEquals(0.0, -0.5, 10.0, 0.5, boundsOf(result.outlineF64))
    }

    @Test
    fun `projection refusal yields an invalid scene before an outline is published`() {
        val result = prepareProjectedHairlineOutlineF64(
            lineCenterline(),
            hairlineStyle(),
            object : PathStrokeProjectionF64 {
                override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
                    PathStrokeProjectionPointResultF64.NonFinite

                override fun certifyOutlineIntervalF64(
                    intervalF64: PathStrokeOutlineIntervalF64,
                ): PathStrokeProjectionIntervalResultF64 = PathStrokeProjectionIntervalResultF64.Bounded(0.0)
            },
        )

        assertEquals(
            PathStrokeInvalidSceneReason.NonFiniteInput,
            assertIs<PathStrokeOutlinePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `published frame outline work refuses the next outline before emission`() {
        val first = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(lineCenterline(), finiteStyle()),
        )
        val usedUnitsI64 = first.frameWorkUsageAfterI64.attemptedGeometryUnitCountI64
        assertTrue(usedUnitsI64 > 0L)

        val rejected = prepareFinitePathStrokeOutlineF64(
            centerlineF64 = lineCenterline(),
            styleF64 = finiteStyle(),
            policyF64 = PathStrokePolicyF64(
                limitsI32 = PathStrokeLimitsI32(
                    maxAttemptedGeometryUnitsPerFrameI32 = usedUnitsI64.toInt(),
                ),
            ),
            frameWorkUsageBeforeI64 = first.frameWorkUsageAfterI64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.FrameWorkLimit,
            assertIs<PathStrokeOutlinePreparationResult.ResourceLimitExceeded>(rejected).reason,
        )
    }

    private fun finiteOutline(
        centerlineF64: PathStrokeCenterlineF64,
        styleF64: PathStrokeStyleF64,
    ): PathStrokeOutlineF64 = assertIs<PathStrokeOutlinePreparationResult.Ready>(
        prepareFinitePathStrokeOutlineF64(centerlineF64, styleF64),
    ).outlineF64

    private fun lineCenterline(): PathStrokeCenterlineF64 = centerline(
        PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
        PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
    )

    private fun rightAngleCenterline(): PathStrokeCenterlineF64 = centerline(
        PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
        PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
        PathFillSegmentF64.LineTo(Point2F64(10.0, 10.0)),
    )

    private fun closedSquareCenterline(): PathStrokeCenterlineF64 = centerline(
        PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
        PathFillSegmentF64.LineTo(Point2F64(10.0, 0.0)),
        PathFillSegmentF64.LineTo(Point2F64(10.0, 10.0)),
        PathFillSegmentF64.LineTo(Point2F64(0.0, 10.0)),
        PathFillSegmentF64.Close,
    )

    private fun centerline(vararg segmentsF64: PathFillSegmentF64): PathStrokeCenterlineF64 =
        assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                PathFillInputF64.of(FillRule.WINDING, segmentsF64.asList()),
                dashF64 = null,
            ),
        ).centerlineF64

    private fun finiteStyle(
        widthF64: Double = 4.0,
        cap: PathStrokeCap = PathStrokeCap.Butt,
        join: PathStrokeJoin = PathStrokeJoin.Miter,
        miterLimitF64: Double = 4.0,
    ): PathStrokeStyleF64 = PathStrokeStyleF64(
        PathStrokeWidthF64.Finite(widthF64),
        cap,
        join,
        miterLimitF64,
    )

    private fun hairlineStyle(): PathStrokeStyleF64 = PathStrokeStyleF64(
        PathStrokeWidthF64.Hairline,
        PathStrokeCap.Butt,
        PathStrokeJoin.Miter,
        4.0,
    )

    private fun boundaryPoints(outlineF64: PathStrokeOutlineF64): List<Point2F64> = buildList {
        repeat(outlineF64.contourCountI32) { contourIndexI32 ->
            outlineF64.copyContourIntervalsF64(contourIndexI32).forEach { intervalF64 ->
                add(intervalF64.primitiveF64.pointAtF64(intervalF64.startParameterF64))
                add(intervalF64.primitiveF64.pointAtF64((intervalF64.startParameterF64 + intervalF64.endParameterF64) * 0.5))
                add(intervalF64.primitiveF64.pointAtF64(intervalF64.endParameterF64))
            }
        }
    }

    private fun boundsOf(outlineF64: PathStrokeOutlineF64): PathStrokeBoundsF64 {
        val pointsF64 = boundaryPoints(outlineF64)
        return PathStrokeBoundsF64(
            leftF64 = pointsF64.minOf(Point2F64::x),
            topF64 = pointsF64.minOf(Point2F64::y),
            rightF64 = pointsF64.maxOf(Point2F64::x),
            bottomF64 = pointsF64.maxOf(Point2F64::y),
        )
    }

    private fun assertAllPointsFinite(outlineF64: PathStrokeOutlineF64) {
        assertTrue(boundaryPoints(outlineF64).all(Point2F64::isFinite))
    }

    private fun assertBoundsEquals(
        leftF64: Double,
        topF64: Double,
        rightF64: Double,
        bottomF64: Double,
        actualF64: PathStrokeBoundsF64,
    ) {
        assertEquals(leftF64, actualF64.leftF64, absoluteTolerance = 1e-9)
        assertEquals(topF64, actualF64.topF64, absoluteTolerance = 1e-9)
        assertEquals(rightF64, actualF64.rightF64, absoluteTolerance = 1e-9)
        assertEquals(bottomF64, actualF64.bottomF64, absoluteTolerance = 1e-9)
    }

    private fun Point2F64.near(xF64: Double, yF64: Double): Boolean =
        abs(x - xF64) <= 1e-9 && abs(y - yF64) <= 1e-9

    private object IdentityProjectionF64 : PathStrokeProjectionF64 {
        override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
            PathStrokeProjectionPointResultF64.Ready(pointF64)

        override fun certifyOutlineIntervalF64(
            intervalF64: PathStrokeOutlineIntervalF64,
        ): PathStrokeProjectionIntervalResultF64 = PathStrokeProjectionIntervalResultF64.Bounded(0.0)
    }
}
