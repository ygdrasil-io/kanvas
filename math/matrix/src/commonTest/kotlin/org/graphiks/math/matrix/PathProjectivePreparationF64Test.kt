package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathFillFlatteningPolicyF64
import org.graphiks.math.geometry.PathFillLimitsI32
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeCenterlinePreparationResult
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokeLimitsI64
import org.graphiks.math.geometry.PathStrokeOutlinePreparationResult
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokeProjectionIntervalResultF64
import org.graphiks.math.geometry.PathStrokeProjectionPointResultF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.geometry.prepareFinitePathStrokeOutlineF64
import org.graphiks.math.geometry.preparePathStrokeCenterlinesF64
import org.graphiks.math.geometry.preparePathFillGeometryWithStrokeWorkF32

class PathProjectivePreparationF64Test {
    @Test
    fun `whole interval certificate subdivides the cubic whose unsampled bulge exceeds a quarter pixel`() {
        listOf(-0.011, -0.01, -0.009).forEach { perspectiveXF64 ->
            val result = assertIs<PathProjectivePreparationResult.Ready>(
                Matrix3x3F64(persp0F64 = perspectiveXF64).prepareProjectedPathFillInputF64(unsampledBulgeCubic()),
            )
            val projectedPolylineF64 = projectedPolylineForTest(result.inputF64)
            val maximumDistanceF64 = (0..4_096).maxOf { sampleI32 ->
                val parameterF64 = sampleI32.toDouble() / 4_096.0
                distanceToPolylineForTest(
                    oracleProjectF64(
                        perspectiveXF64,
                        Point2F64(
                            parameterF64,
                            0.32 * parameterF64 * (1.0 - parameterF64) * (4.0 * parameterF64 + 1.0),
                        ),
                    ),
                    projectedPolylineF64,
                )
            }

            assertTrue(projectedPolylineF64.size > 3)
            assertTrue(maximumDistanceF64 <= 0.25)
        }
    }

    @Test
    fun `whole interval certificate uses distance to the emitted segment not its supporting line`() {
        val path = PathBuilder().moveTo(0f, 0f).quadTo(100f, 0f, 1f, 0f).lineTo(0f, 0f).close().build()
        val result = assertIs<PathProjectivePreparationResult.Ready>(
            Matrix3x3F64().prepareProjectedPathFillInputF64(path),
        )
        val projectedPolylineF64 = projectedPolylineForTest(result.inputF64)
        val maximumDistanceF64 = (0..4_096).maxOf { sampleI32 ->
            val parameterF64 = sampleI32.toDouble() / 4_096.0
            distanceToPolylineForTest(
                Point2F64(200.0 * parameterF64 * (1.0 - parameterF64) + parameterF64 * parameterF64, 0.0),
                projectedPolylineF64,
            )
        }

        assertTrue(projectedPolylineF64.size > 3)
        assertTrue(maximumDistanceF64 <= 0.25)
    }

    @Test
    fun `constant positive w after cancellation is ready for fill and bounded for stroke`() {
        val matrix = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = -0.9999999999999999)
        val path = PathBuilder().moveTo(1f, 0f).lineTo(1f, 1f).lineTo(1f, 2f).close().build()
        val fill = matrix.prepareProjectedPathFillInputF64(path)
        val centerline = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(PathFillInputF64.fromPathF32(path), dashF64 = null),
        ).centerlineF64
        val interval = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(
                centerline,
                PathStrokeStyleF64(
                    widthF64 = PathStrokeWidthF64.Finite(1e-20),
                    cap = PathStrokeCap.Butt,
                    join = PathStrokeJoin.Miter,
                    miterLimitF64 = 4.0,
                ),
            ),
        ).outlineF64.copyContourIntervalsF64(0).first()

        assertIs<PathProjectivePreparationResult.Ready>(fill)
        assertIs<PathStrokeProjectionIntervalResultF64.Bounded>(matrix.toPathStrokeProjectionF64().certifyOutlineIntervalF64(interval))
        assertIs<PathStrokeProjectionIntervalResultF64.Bounded>(
            Matrix3x3F64(persp2F64 = -1.0 / (1L shl 53).toDouble()).toPathStrokeProjectionF64()
                .certifyOutlineIntervalF64(interval),
        )
    }

    @Test
    fun `subnormal denominator cancellation remains a horizon rather than a ready snapshot`() {
        val result = Matrix3x3F64(
            persp0F64 = Double.MIN_VALUE,
            persp1F64 = Double.MIN_VALUE,
            persp2F64 = -Double.MIN_VALUE,
        ).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(0.5f, 0.5f).lineTo(0.5f, 0.75f).lineTo(0.5f, 0.5f).close().build(),
        )

        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `strictly positive subnormal denominator is not promoted to a horizon`() {
        val minimumF64 = Double.MIN_VALUE
        val result = Matrix3x3F64(
            sxF64 = 0.0,
            syF64 = 0.0,
            txF64 = minimumF64,
            persp0F64 = minimumF64,
            persp2F64 = minimumF64,
        ).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(0.5f, 0f).lineTo(0.25f, 0f).lineTo(0f, 0f).close().build(),
        )

        val ready = assertIs<PathProjectivePreparationResult.Ready>(result)
        assertEquals(
            2.0 / 3.0,
            assertIs<PathFillSegmentF64.MoveTo>(ready.inputF64.first()).point.x,
        )
    }

    @Test
    fun `scaled subnormal stroke quotient remains finite and non horizon`() {
        val unitsF64 = 4_503_599_627_370_496.0
        val projectionF64 = Matrix3x3F64(
            sxF64 = 0.0,
            syF64 = 0.0,
            txF64 = Double.MIN_VALUE,
            persp0F64 = Double.MIN_VALUE,
            persp1F64 = Double.MIN_VALUE,
            persp2F64 = -Double.fromBits(0x0020_0000_0000_0000L),
        ).toPathStrokeProjectionF64()

        val point = projectionF64.projectPointF64(Point2F64(unitsF64, unitsF64 - 0.5))
        assertEquals(Point2F64(-2.0, 0.0), assertIs<PathStrokeProjectionPointResultF64.Ready>(point).pointF64)
    }

    @Test
    fun `large finite denominator survives bezier splitting`() {
        val result = Matrix3x3F64(
            sxF64 = 2e300,
            syF64 = 2e300,
            persp0F64 = 1e300,
            persp2F64 = 1e300,
        ).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(1f, 0f).quadTo(1f, 1f, 1f, 0f).lineTo(1f, 0f).close().build(),
        )

        assertIs<PathProjectivePreparationResult.Ready>(result)
    }

    @Test
    fun `maximum finite homogeneous denominator remains projectable`() {
        val result = Matrix3x3F64(
            sxF64 = 1.0,
            syF64 = 1.0,
            persp0F64 = Double.MAX_VALUE,
            persp2F64 = 0.0,
        ).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(1f, 0f).lineTo(1f, 1f).lineTo(1f, 0f).close().build(),
        )

        assertIs<PathProjectivePreparationResult.Ready>(result)
    }

    @Test
    fun `double double denominator retains the nonzero product residual after cancellation`() {
        val result = Matrix3x3F64(
            sxF64 = 1e-20,
            syF64 = 1e-20,
            persp0F64 = 1.0 / 3.0,
            persp2F64 = -1.0,
        ).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(3f, 0f).lineTo(3f, 1f).lineTo(3f, 0f).close().build(),
        )

        assertIs<PathProjectivePreparationResult.Ready>(result)
    }

    @Test
    fun `stroke gradient norm does not underflow past an interior quadratic horizon`() {
        val path = PathBuilder().moveTo(1f, 0f).quadTo(-3f, 1f, 1f, 0f).build()
        val largeCoordinatePolicyF64 = PathStrokePolicyF64(
            maximumSagittaErrorF64 = 1e39,
            maximumDashArcLengthErrorF64 = 1e39,
        )
        val centerline = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                PathFillInputF64.fromPathF32(path),
                dashF64 = null,
                policyF64 = largeCoordinatePolicyF64,
            ),
        ).centerlineF64
        val outlineF64 = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(
                centerline,
                finiteStrokeStyleForTest(),
                policyF64 = largeCoordinatePolicyF64,
            ),
        ).outlineF64
        val projectionF64 = Matrix3x3F64(persp0F64 = 1e-162, persp2F64 = 0.0).toPathStrokeProjectionF64()

        assertTrue(
            (0 until outlineF64.contourCountI32)
                .flatMap { outlineF64.copyContourIntervalsF64(it) }
                .any { projectionF64.certifyOutlineIntervalF64(it) !is PathStrokeProjectionIntervalResultF64.Bounded },
        )
    }

    @Test
    fun `large coordinate quadratic outline interval exposes its interior horizon`() {
        val inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(1e162, 0.0)),
                PathFillSegmentF64.QuadTo(Point2F64(-3e162, 1e162), Point2F64(1e162, 0.0)),
            ),
        )
        val largeCoordinatePolicyF64 = PathStrokePolicyF64(
            maximumSagittaErrorF64 = 1e163,
            maximumDashArcLengthErrorF64 = 1e163,
        )
        val centerline = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(inputF64, dashF64 = null, policyF64 = largeCoordinatePolicyF64),
        ).centerlineF64
        val outlineF64 = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(
                centerline,
                finiteStrokeStyleForTest(),
                policyF64 = largeCoordinatePolicyF64,
            ),
        ).outlineF64
        val projectionF64 = Matrix3x3F64(persp0F64 = 1e-162, persp2F64 = 0.0).toPathStrokeProjectionF64()
        val certificatesF64 = (0 until outlineF64.contourCountI32)
            .flatMap { contourIndexI32 -> outlineF64.copyContourIntervalsF64(contourIndexI32) }
            .map(projectionF64::certifyOutlineIntervalF64)

        assertEquals(6, certificatesF64.size)
        assertEquals(
            listOf("HorizonCrossing", "HorizonCrossing", "Bounded", "HorizonCrossing", "HorizonCrossing", "Bounded"),
            certificatesF64.map { it::class.simpleName },
        )
    }

    @Test
    fun `scaled stroke gradient bounds the exact curved interval that naive hypot would miss`() {
        val inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                PathFillSegmentF64.QuadTo(Point2F64(-4e162, 1e162), Point2F64(0.0, 0.0)),
            ),
        )
        val policyF64 = PathStrokePolicyF64(
            maximumSagittaErrorF64 = 1e163,
            maximumDashArcLengthErrorF64 = 1e163,
        )
        val centerlineF64 = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(inputF64, dashF64 = null, policyF64 = policyF64),
        ).centerlineF64
        val outlineF64 = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(centerlineF64, finiteStrokeStyleForTest(), policyF64 = policyF64),
        ).outlineF64
        val projectionF64 = Matrix3x3F64(persp0F64 = 1e-162, persp2F64 = 5.0).toPathStrokeProjectionF64()
        val curvedIntervalsF64 = (0 until outlineF64.contourCountI32)
            .flatMap { outlineF64.copyContourIntervalsF64(it) }
            .filter { it.sourceSagittaUpperBoundF64 > 0.0 }

        assertEquals(4, curvedIntervalsF64.size)
        curvedIntervalsF64.forEach { intervalF64 ->
            // Both endpoints have W=5, but the retained sagitta can consume that margin only
            // when the 1e-162 projective gradient is measured with a scaled hypotenuse.
            assertIs<PathStrokeProjectionIntervalResultF64.Unbounded>(projectionF64.certifyOutlineIntervalF64(intervalF64))
        }
    }

    @Test
    fun `stroke interval preserves correlated diagonal w instead of crossing its AABB`() {
        val path = PathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).build()
        val centerline = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(PathFillInputF64.fromPathF32(path), dashF64 = null),
        ).centerlineF64
        val outlineF64 = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(
                centerline,
                PathStrokeStyleF64(
                    widthF64 = PathStrokeWidthF64.Finite(0.01),
                    cap = PathStrokeCap.Butt,
                    join = PathStrokeJoin.Miter,
                    miterLimitF64 = 4.0,
                ),
            ),
        ).outlineF64
        val projectionF64 = Matrix3x3F64(persp0F64 = 1.0, persp1F64 = -1.0, persp2F64 = 0.1)
            .toPathStrokeProjectionF64()

        repeat(outlineF64.contourCountI32) { contourIndexI32 ->
            outlineF64.copyContourIntervalsF64(contourIndexI32).forEach { intervalF64 ->
                assertIs<PathStrokeProjectionIntervalResultF64.Bounded>(projectionF64.certifyOutlineIntervalF64(intervalF64))
            }
        }
    }

    @Test
    fun `negative w stroke certificates cover source offsets reversed segments arcs joins and caps`() {
        val paths = listOf(
            "reversed source segment" to PathBuilder().moveTo(2f, 0f).lineTo(0f, 0f).build(),
            "forward source segment" to PathBuilder().moveTo(0f, 0f).lineTo(2f, 0f).build(),
            "offset join" to PathBuilder().moveTo(0f, 0f).lineTo(2f, 0f).lineTo(2f, 2f).build(),
            "source arc" to PathBuilder().moveTo(1f, 0f).arcTo(1f, 1f, 0f, false, true, -1f, 0f).build(),
        )
        val styles = listOf(
            PathStrokeCap.Butt to PathStrokeJoin.Miter,
            PathStrokeCap.Round to PathStrokeJoin.Round,
            PathStrokeCap.Square to PathStrokeJoin.Bevel,
        )
        val expectedIntervalCountsI32 = listOf(
            listOf(4, 4, 8),  // reversed source: butt/miter, round/round, square/bevel
            listOf(4, 4, 8),  // forward source: proves independent source direction handling
            listOf(10, 9, 13), // offset join: retains every join family
            listOf(4, 4, 8),  // source arc: retains the parametric arc and its caps
        )
        val projectionF64 = Matrix3x3F64(persp0F64 = -0.01, persp2F64 = -1.0).toPathStrokeProjectionF64()

        paths.forEachIndexed { pathIndexI32, (sourceNameF64, path) ->
            val centerlineF64 = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
                preparePathStrokeCenterlinesF64(PathFillInputF64.fromPathF32(path), dashF64 = null),
            ).centerlineF64
            styles.forEachIndexed { styleIndexI32, (capF64, joinF64) ->
                val outlineF64 = assertIs<PathStrokeOutlinePreparationResult.Ready>(
                    prepareFinitePathStrokeOutlineF64(
                        centerlineF64,
                        PathStrokeStyleF64(
                            widthF64 = PathStrokeWidthF64.Finite(0.25),
                            cap = capF64,
                            join = joinF64,
                            miterLimitF64 = 4.0,
                        ),
                    ),
                ).outlineF64
                val intervalsF64 = (0 until outlineF64.contourCountI32)
                    .flatMap { outlineF64.copyContourIntervalsF64(it) }
                assertEquals(
                    expectedIntervalCountsI32[pathIndexI32][styleIndexI32],
                    intervalsF64.size,
                    "$sourceNameF64 $capF64/$joinF64 must retain its own outline primitive family",
                )
                intervalsF64.forEach { intervalF64 ->
                    assertIs<PathStrokeProjectionIntervalResultF64.Bounded>(
                        projectionF64.certifyOutlineIntervalF64(intervalF64),
                    )
                }
            }
        }
    }

    @Test
    fun `snapshot counter overflow is not classified as raster overflow`() {
        val result = Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
            path = unitTriangle(),
            workPolicyF64 = PathStrokePolicyF64(
                limitsI64 = PathStrokeLimitsI64(Long.MAX_VALUE, Long.MAX_VALUE),
            ),
            pathWorkUsageBeforeI64 = PathStrokeWorkUsageI64(snapshotByteCountI64 = Long.MAX_VALUE - 8L),
            frameWorkUsageBeforeI64 = PathStrokeWorkUsageI64(snapshotByteCountI64 = Long.MAX_VALUE - 8L),
        )

        assertEquals(
            PathProjectiveResourceLimitReason.SnapshotByteLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `source command budget is debited before inspecting a later non finite command`() {
        val result = Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
            path = PathBuilder().moveTo(0f, 0f).lineTo(Float.NaN, 1f).build(),
            workPolicyF64 = policyWithLimits(maxAttemptedGeometryUnitsPerPathI32 = 1),
        )

        assertEquals(
            PathProjectiveResourceLimitReason.PathWorkLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `positive and negative w lines quads cubics and arcs project to finite device input`() {
        val paths = listOf(
            PathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).lineTo(0f, 1f).close().build(),
            PathBuilder().moveTo(0f, 0f).quadTo(1f, 2f, 2f, 0f).lineTo(0f, 0f).close().build(),
            PathBuilder().moveTo(0f, 0f).cubicTo(1f, 3f, 3f, -1f, 4f, 1f).lineTo(0f, 0f).close().build(),
            PathBuilder().moveTo(1f, 0f).arcTo(1f, 1f, 0f, false, true, -1f, 0f).lineTo(1f, 0f).close().build(),
        )

        paths.forEach { path ->
            val positive = Matrix3x3F64(persp0F64 = 0.05).prepareProjectedPathFillInputF64(path)
            val negative = Matrix3x3F64(persp0F64 = 0.05, persp2F64 = -2.0)
                .prepareProjectedPathFillInputF64(path)

            assertIs<PathProjectivePreparationResult.Ready>(positive).inputF64.forEach { segment ->
                assertTrue(segmentIsFiniteForTest(segment))
            }
            assertIs<PathProjectivePreparationResult.Ready>(negative).inputF64.forEach { segment ->
                assertTrue(segmentIsFiniteForTest(segment))
            }
        }
    }

    @Test
    fun `independent parametric oracles keep every line quad cubic and arc within the public tolerance`() {
        val cases = listOf(
            ProjectiveOracleCase(
                path = PathBuilder().moveTo(0f, 0f).lineTo(1f, 0.75f).lineTo(0f, 0f).close().build(),
                pointAtF64 = { tF64 -> Point2F64(tF64, 0.75 * tF64) },
            ),
            ProjectiveOracleCase(
                path = PathBuilder().moveTo(0f, 0f).quadTo(0.5f, 1f, 1f, 0f).lineTo(0f, 0f).close().build(),
                pointAtF64 = { tF64 ->
                    Point2F64(
                        2.0 * (1.0 - tF64) * tF64 * 0.5 + tF64 * tF64,
                        2.0 * (1.0 - tF64) * tF64,
                    )
                },
            ),
            ProjectiveOracleCase(
                path = unsampledBulgeCubic(),
                pointAtF64 = { tF64 ->
                    Point2F64(tF64, 0.32 * tF64 * (1.0 - tF64) * (4.0 * tF64 + 1.0))
                },
            ),
            ProjectiveOracleCase(
                path = PathBuilder().moveTo(1f, 0f).arcTo(1f, 1f, 0f, false, true, 0f, 1f)
                    .lineTo(1f, 0f).close().build(),
                pointAtF64 = { tF64 ->
                    Point2F64(kotlin.math.cos(PI * tF64 * 0.5), kotlin.math.sin(PI * tF64 * 0.5))
                },
            ),
        )
        val matrices = listOf(
            Matrix3x3F64(sxF64 = 1.2, kxF64 = 0.15, kyF64 = -0.1, syF64 = 0.9, persp0F64 = 0.08, persp1F64 = 0.03),
            Matrix3x3F64(sxF64 = 1.2, kxF64 = 0.15, kyF64 = -0.1, syF64 = 0.9, persp0F64 = 0.08, persp1F64 = 0.03, persp2F64 = -2.0),
        )

        cases.forEach { case ->
            matrices.forEach { matrix ->
                val inputF64 = assertIs<PathProjectivePreparationResult.Ready>(
                    matrix.prepareProjectedPathFillInputF64(case.path),
                ).inputF64
                val polylineF64 = projectedPolylineForTest(inputF64)
                val maximumDeviationF64 = (0..1_024).maxOf { sampleI32 ->
                    val parameterF64 = sampleI32.toDouble() / 1_024.0
                    distanceToPolylineForTest(
                        oracleMatrixProjectF64(matrix, case.pointAtF64(parameterF64)),
                        polylineF64,
                    )
                }

                assertTrue(maximumDeviationF64 <= 0.25 + 1e-12)
            }
        }
    }

    @Test
    fun `line projection uses the homogeneous divide independently of an affine approximation`() {
        val result = assertIs<PathProjectivePreparationResult.Ready>(
            Matrix3x3F64(persp0F64 = 0.5).prepareProjectedPathFillInputF64(
                PathBuilder().moveTo(0f, 0f).lineTo(2f, 0f).lineTo(0f, 1f).close().build(),
            ),
        )

        val endpoint = result.inputF64
            .asSequence()
            .filterIsInstance<PathFillSegmentF64.LineTo>()
            .first().point
        assertEquals(1.0, endpoint.x)
        assertEquals(0.0, endpoint.y)
    }

    @Test
    fun `projected quadratic retains post divide extrema through adaptive midpoint splitting`() {
        val result = assertIs<PathProjectivePreparationResult.Ready>(
            Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
                PathBuilder().moveTo(0f, 0f).quadTo(10f, 4f, 0f, 0f).lineTo(0f, 1f).close().build(),
            ),
        )
        val maximumProjectedXF64 = result.inputF64.asSequence()
            .mapNotNull { (it as? PathFillSegmentF64.LineTo)?.point }
            .maxOf { it.x }

        // x(0.5) / w(0.5) = 5 / 1.5; an affine chord would miss this interior maximum.
        assertTrue(maximumProjectedXF64 >= 10.0 / 3.0 - 0.25)
    }

    @Test
    fun `near zero separated w is admitted while a real horizon crossing is classified`() {
        val nearZero = Matrix3x3F64(persp2F64 = 1e-12).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(1e-12f, 0f).lineTo(2e-12f, 0f).lineTo(1e-12f, 1e-12f).close().build(),
        )
        val crossing = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(-1f, 0f).lineTo(1f, 0f).lineTo(1f, 1f).close().build(),
        )

        assertIs<PathProjectivePreparationResult.Ready>(nearZero)
        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(crossing).reason,
        )
    }

    @Test
    fun `positive w hidden by a cancelling quadratic control tuple remains ready`() {
        val twoToMinusEightyF64 = (1.0 / (1L shl 40).toDouble()) / (1L shl 40).toDouble()
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = twoToMinusEightyF64)
            .prepareProjectedPathFillInputF64(
                PathBuilder().moveTo(0.25f, 0f).quadTo(-0.25f, 0f, 0.25f, 0f)
                    .lineTo(0.25f, 1f).close().build(),
            )

        assertIs<PathProjectivePreparationResult.Ready>(result)
    }

    @Test
    fun `non dyadic quadratic w root is a horizon rather than a subdivision exhaustion`() {
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = -0.3).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(0f, 0f).quadTo(0.5f, 1f, 1f, 0f).lineTo(0f, 0f).close().build(),
        )

        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `inconclusive alternating w controls at depth limit are not a fabricated horizon`() {
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
            path = PathBuilder().moveTo(1f, 0f).quadTo(-0.1f, 0f, 1f, 0f)
                .lineTo(1f, 1f).close().build(),
            policyF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0)),
        )

        assertEquals(
            PathProjectiveResourceLimitReason.FlatteningDidNotConverge,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `non dyadic quadratic tangent horizon is certified before the subdivision limit`() {
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
            path = PathBuilder().moveTo(1f, 0f).quadTo(-2f, 0f, 4f, 0f)
                .lineTo(1f, 1f).close().build(),
            policyF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0)),
        )

        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `degree reduced cubic tangent horizon is certified before the subdivision limit`() {
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
            path = PathBuilder().moveTo(1f, 0f).cubicTo(-1f, 0f, 0f, 0f, 4f, 0f)
                .lineTo(1f, 0f).close().build(),
            policyF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0)),
        )

        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `general cubic root certificates ignore internal zero controls and prove crossings and tangencies`() {
        val policyF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0))
        fun classify(vararg controlsF64: Float): PathProjectivePreparationResult =
            Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
                PathBuilder().moveTo(controlsF64[0], 0f)
                    .cubicTo(controlsF64[1], 0f, controlsF64[2], 0f, controlsF64[3], 0f)
                    .lineTo(controlsF64[0], 1f).close().build(),
                policyF64 = policyF64,
            )

        val strictInternalZero = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(1f, 0f).cubicTo(0f, 0f, -0.25f, 0f, 1f, 0f)
                .lineTo(1f, 1f).close().build(),
        )
        val crossing = classify(1f, 0f, -1f, -1f)
        val tangency = classify(1f, -0.25f, -0.5f, 1.25f)

        assertIs<PathProjectivePreparationResult.Ready>(strictInternalZero)
        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(crossing).reason,
        )
        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(tangency).reason,
        )
    }

    @Test
    fun `cubic tangent at one third is certified without rounding its stationary point`() {
        fun classify(lastControlF32: Float, policyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64()):
            PathProjectivePreparationResult =
            Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
                PathBuilder().moveTo(1f, 0f).cubicTo(-0.5f, 0f, -2f, 0f, lastControlF32, 0f)
                    .lineTo(1f, 1f).close().build(),
                policyF64 = policyF64,
            )

        val depthZeroF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0))
        listOf(PathFillFlatteningPolicyF64(), depthZeroF64).forEach { policyF64 ->
            assertEquals(
                PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
                assertIs<PathProjectivePreparationResult.InvalidScene>(classify(10f, policyF64)).reason,
            )
        }

        assertIs<PathProjectivePreparationResult.Ready>(classify(10.25f))
        assertEquals(
            PathProjectiveResourceLimitReason.FlatteningDidNotConverge,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(classify(10.25f, depthZeroF64)).reason,
        )
    }

    @Test
    fun `long arc horizon is classified before a zero subdivision budget is exhausted`() {
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).prepareProjectedPathFillInputF64(
            path = PathBuilder().moveTo(1f, 0f).arcTo(1f, 1f, 0f, false, true, -1f, 0f)
                .lineTo(1f, 0f).close().build(),
            policyF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0)),
        )

        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathProjectivePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `long arc cancellation remains separated when its exact denominator is nonzero`() {
        val matrixF64 = Matrix3x3F64(
            sxF64 = 1e-20,
            syF64 = 1e-20,
            persp0F64 = 1.0 / 3.0,
            persp2F64 = -1.0,
        )
        val path = PathBuilder().moveTo(3f, 0f).arcTo(1f, 1f, 0f, false, true, 1f, 0f)
            .lineTo(3f, 0f).close().build()

        val atDepthLimit = matrixF64.prepareProjectedPathFillInputF64(
            path,
            policyF64 = PathFillFlatteningPolicyF64(limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0)),
        )
        val normalBudget = matrixF64.prepareProjectedPathFillInputF64(path)

        assertEquals(
            PathProjectiveResourceLimitReason.FlatteningDidNotConverge,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(atDepthLimit).reason,
        )
        assertIs<PathProjectivePreparationResult.Ready>(normalBudget)
    }

    @Test
    fun `non finite matrix and projection remain distinct invalid scene facts`() {
        val nonFiniteMatrix = Matrix3x3F64(sxF64 = Double.NaN).prepareProjectedPathFillInputF64(unitTriangle())
        val nonFiniteProjection = Matrix3x3F64(sxF64 = Double.MAX_VALUE, persp0F64 = 1.0)
            .prepareProjectedPathFillInputF64(
                PathBuilder().moveTo(2f, 0f).lineTo(3f, 0f).lineTo(2f, 1f).close().build(),
            )

        assertEquals(
            PathProjectiveInvalidSceneReason.NonFiniteMatrix,
            assertIs<PathProjectivePreparationResult.InvalidScene>(nonFiniteMatrix).reason,
        )
        assertEquals(
            PathProjectiveInvalidSceneReason.NonFiniteProjection,
            assertIs<PathProjectivePreparationResult.InvalidScene>(nonFiniteProjection).reason,
        )
    }

    @Test
    fun `depth and each projection ledger budget axis are reported before publication`() {
        val curvedPath = PathBuilder().moveTo(0f, 0f).quadTo(10f, 5f, 0f, 0f).lineTo(0f, 1f).close().build()
        val perspective = Matrix3x3F64(persp0F64 = 0.1)
        val depth = perspective.prepareProjectedPathFillInputF64(
            path = curvedPath,
            policyF64 = PathFillFlatteningPolicyF64(
                maximumSagittaErrorF64 = 0.25,
                limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0),
            ),
        )
        val pathWork = perspective.prepareProjectedPathFillInputF64(
            path = unitTriangle(),
            workPolicyF64 = policyWithLimits(maxAttemptedGeometryUnitsPerPathI32 = 1),
        )
        val frameWork = perspective.prepareProjectedPathFillInputF64(
            path = unitTriangle(),
            workPolicyF64 = policyWithLimits(maxAttemptedGeometryUnitsPerFrameI32 = 1),
        )
        val snapshotBytes = perspective.prepareProjectedPathFillInputF64(
            path = unitTriangle(),
            workPolicyF64 = policyWithLimits(maxSnapshotByteCountPerPathI64 = 1L),
        )

        assertEquals(
            PathProjectiveResourceLimitReason.FlatteningDidNotConverge,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(depth).reason,
        )
        assertEquals(
            PathProjectiveResourceLimitReason.PathWorkLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(pathWork).reason,
        )
        assertEquals(
            PathProjectiveResourceLimitReason.FrameWorkLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(frameWork).reason,
        )
        assertEquals(
            PathProjectiveResourceLimitReason.SnapshotByteLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(snapshotBytes).reason,
        )
    }

    @Test
    fun `projection ledger admits exact boundaries and rejects the immediately smaller path frame and snapshot budgets`() {
        val exactPolicy = policyWithLimits(
            maxAttemptedGeometryUnitsPerPathI32 = 13,
            maxAttemptedGeometryUnitsPerFrameI32 = 13,
            maxSnapshotByteCountPerPathI64 = 208L,
            maxSnapshotByteCountPerFrameI64 = 208L,
        )
        val ready = assertIs<PathProjectivePreparationResult.Ready>(
            Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(unitTriangle(), workPolicyF64 = exactPolicy),
        )

        assertEquals(13L, ready.pathWorkUsageAfterI64.attemptedGeometryUnitCountI64)
        assertEquals(208L, ready.pathWorkUsageAfterI64.snapshotByteCountI64)
        assertEquals(ready.pathWorkUsageAfterI64, ready.frameWorkUsageAfterI64)
        assertEquals(
            PathProjectiveResourceLimitReason.PathWorkLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(
                Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
                    unitTriangle(),
                    workPolicyF64 = policyWithLimits(maxAttemptedGeometryUnitsPerPathI32 = 12),
                ),
            ).reason,
        )
        assertEquals(
            PathProjectiveResourceLimitReason.FrameWorkLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(
                Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
                    unitTriangle(),
                    workPolicyF64 = policyWithLimits(
                        maxAttemptedGeometryUnitsPerPathI32 = 13,
                        maxAttemptedGeometryUnitsPerFrameI32 = 12,
                    ),
                ),
            ).reason,
        )
        assertEquals(
            PathProjectiveResourceLimitReason.SnapshotByteLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(
                Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
                    unitTriangle(),
                    workPolicyF64 = policyWithLimits(maxSnapshotByteCountPerPathI64 = 207L),
                ),
            ).reason,
        )
        assertEquals(
            PathProjectiveResourceLimitReason.SnapshotByteLimit,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(
                Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
                    unitTriangle(),
                    workPolicyF64 = policyWithLimits(
                        maxSnapshotByteCountPerPathI64 = 208L,
                        maxSnapshotByteCountPerFrameI64 = 207L,
                    ),
                ),
            ).reason,
        )
    }

    @Test
    fun `raster overflow is rejected before a non representable projected snapshot is published`() {
        val result = Matrix3x3F64(sxF64 = 2.0, persp0F64 = Double.MIN_VALUE).prepareProjectedPathFillInputF64(
            PathBuilder().moveTo(0f, 0f).lineTo(Float.MAX_VALUE, 0f).lineTo(0f, 1f).close().build(),
        )

        assertEquals(
            PathProjectiveResourceLimitReason.RasterBoundsOverflow,
            assertIs<PathProjectivePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `ready projection hands both immutable work snapshots to fill finalization without reset`() {
        val pathBeforeI64 = PathStrokeWorkUsageI64(
            attemptedGeometryUnitCountI64 = 5L,
            emittedVertexCountI64 = 7L,
            emittedIndexCountI64 = 11L,
            snapshotByteCountI64 = 13L,
        )
        val frameBeforeI64 = PathStrokeWorkUsageI64(
            attemptedGeometryUnitCountI64 = 17L,
            emittedVertexCountI64 = 19L,
            emittedIndexCountI64 = 23L,
            snapshotByteCountI64 = 29L,
        )
        val projected = assertIs<PathProjectivePreparationResult.Ready>(
            Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(
                unitTriangle(),
                pathWorkUsageBeforeI64 = pathBeforeI64,
                frameWorkUsageBeforeI64 = frameBeforeI64,
            ),
        )
        val finalized = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = projected.inputF64,
            pathWorkUsageBeforeI64 = projected.pathWorkUsageAfterI64,
            frameWorkUsageBeforeI64 = projected.frameWorkUsageAfterI64,
        )

        val ready = assertIs<org.graphiks.math.geometry.PathFillWithStrokeWorkPreparationResult.Ready>(finalized)
        assertEquals(
            PathStrokeWorkUsageI64(18L, 7L, 11L, 221L),
            projected.pathWorkUsageAfterI64,
        )
        assertEquals(
            PathStrokeWorkUsageI64(30L, 19L, 23L, 237L),
            projected.frameWorkUsageAfterI64,
        )
        assertEquals(
            PathStrokeWorkUsageI64(22L, 10L, 14L, 273L),
            ready.pathWorkUsageI64,
        )
        assertEquals(
            PathStrokeWorkUsageI64(34L, 22L, 26L, 289L),
            ready.frameWorkUsageAfterI64,
        )
    }

    @Test
    fun `stroke projection exposes point and interval facts without choosing subdivisions`() {
        val projection = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 2.0).toPathStrokeProjectionF64()
        val point = projection.projectPointF64(Point2F64(1.0, 2.0))
        val centerline = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
            preparePathStrokeCenterlinesF64(
                PathFillInputF64.fromPathF32(PathBuilder().moveTo(-3f, 0f).lineTo(1f, 0f).build()),
                dashF64 = null,
            ),
        ).centerlineF64
        val interval = assertIs<PathStrokeOutlinePreparationResult.Ready>(
            prepareFinitePathStrokeOutlineF64(
                centerlineF64 = centerline,
                styleF64 = PathStrokeStyleF64(
                    widthF64 = PathStrokeWidthF64.Finite(1.0),
                    cap = PathStrokeCap.Butt,
                    join = PathStrokeJoin.Miter,
                    miterLimitF64 = 4.0,
                ),
            ),
        ).outlineF64.copyContourIntervalsF64(0).first()

        assertEquals(
            Point2F64(1.0 / 3.0, 2.0 / 3.0),
            assertIs<PathStrokeProjectionPointResultF64.Ready>(point).pointF64,
        )
        assertIs<PathStrokeProjectionIntervalResultF64.HorizonCrossing>(projection.certifyOutlineIntervalF64(interval))
    }

    private fun unitTriangle() = PathBuilder().moveTo(0f, 0f).lineTo(2f, 0f).lineTo(0f, 2f).close().build()

    private fun unsampledBulgeCubic() = PathBuilder()
        .moveTo(0f, 0f)
        .cubicTo(1f / 3f, 0.32f / 3f, 2f / 3f, 0.32f * 5f / 3f, 1f, 0f)
        .lineTo(0f, 0f)
        .close()
        .build()

    private fun finiteStrokeStyleForTest(): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(1.0),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private fun oracleProjectF64(perspectiveXF64: Double, sourceF64: Point2F64): Point2F64 {
        val wF64 = 1.0 + perspectiveXF64 * sourceF64.x
        return Point2F64(sourceF64.x / wF64, sourceF64.y / wF64)
    }

    private fun oracleMatrixProjectF64(matrixF64: Matrix3x3F64, sourceF64: Point2F64): Point2F64 {
        val wF64 = matrixF64.persp0F64 * sourceF64.x + matrixF64.persp1F64 * sourceF64.y + matrixF64.persp2F64
        return Point2F64(
            (matrixF64.sxF64 * sourceF64.x + matrixF64.kxF64 * sourceF64.y + matrixF64.txF64) / wF64,
            (matrixF64.kyF64 * sourceF64.x + matrixF64.syF64 * sourceF64.y + matrixF64.tyF64) / wF64,
        )
    }

    private fun projectedPolylineForTest(inputF64: PathFillInputF64): List<Point2F64> = buildList {
        inputF64.forEach { segmentF64 ->
            when (segmentF64) {
                is PathFillSegmentF64.MoveTo -> add(segmentF64.point)
                is PathFillSegmentF64.LineTo -> add(segmentF64.point)
                else -> Unit
            }
        }
    }

    private fun distanceToPolylineForTest(pointF64: Point2F64, polylineF64: List<Point2F64>): Double =
        polylineF64.zipWithNext().minOf { (startF64, endF64) ->
            val dxF64 = endF64.x - startF64.x
            val dyF64 = endF64.y - startF64.y
            val lengthSquaredF64 = dxF64 * dxF64 + dyF64 * dyF64
            val parameterF64 = if (lengthSquaredF64 == 0.0) 0.0 else {
                (((pointF64.x - startF64.x) * dxF64 + (pointF64.y - startF64.y) * dyF64) / lengthSquaredF64)
                    .coerceIn(0.0, 1.0)
            }
            val nearestXF64 = startF64.x + dxF64 * parameterF64
            val nearestYF64 = startF64.y + dyF64 * parameterF64
            kotlin.math.sqrt((pointF64.x - nearestXF64) * (pointF64.x - nearestXF64) +
                (pointF64.y - nearestYF64) * (pointF64.y - nearestYF64))
        }

    private fun policyWithLimits(
        maxAttemptedGeometryUnitsPerPathI32: Int = 65_536,
        maxAttemptedGeometryUnitsPerFrameI32: Int = 262_144,
        maxSnapshotByteCountPerPathI64: Long = 16L * 1024L * 1024L,
        maxSnapshotByteCountPerFrameI64: Long = 64L * 1024L * 1024L,
    ): PathStrokePolicyF64 = PathStrokePolicyF64(
        limitsI32 = PathStrokeLimitsI32(
            maxAttemptedGeometryUnitsPerPathI32 = maxAttemptedGeometryUnitsPerPathI32,
            maxAttemptedGeometryUnitsPerFrameI32 = maxAttemptedGeometryUnitsPerFrameI32,
        ),
        limitsI64 = PathStrokeLimitsI64(
            maxSnapshotByteCountPerPathI64 = maxSnapshotByteCountPerPathI64,
            maxSnapshotByteCountPerFrameI64 = maxSnapshotByteCountPerFrameI64,
        ),
    )

private data class ProjectiveOracleCase(
        val path: org.graphiks.math.geometry.PathF32,
        val pointAtF64: (Double) -> Point2F64,
    )

    private fun segmentIsFiniteForTest(segment: PathFillSegmentF64): Boolean = when (segment) {
        is PathFillSegmentF64.MoveTo -> segment.point.isFinite()
        is PathFillSegmentF64.LineTo -> segment.point.isFinite()
        is PathFillSegmentF64.QuadTo -> segment.control.isFinite() && segment.point.isFinite()
        is PathFillSegmentF64.CubicTo -> segment.control1.isFinite() && segment.control2.isFinite() && segment.point.isFinite()
        is PathFillSegmentF64.ArcTo -> segment.radius.isFinite() && segment.point.isFinite()
        PathFillSegmentF64.Close -> true
    }
}
