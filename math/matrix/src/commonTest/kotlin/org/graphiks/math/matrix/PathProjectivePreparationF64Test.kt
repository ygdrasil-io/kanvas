package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
        val projected = assertIs<PathProjectivePreparationResult.Ready>(
            Matrix3x3F64(persp0F64 = 0.1).prepareProjectedPathFillInputF64(unitTriangle()),
        )
        val finalized = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = projected.inputF64,
            pathWorkUsageBeforeI64 = projected.pathWorkUsageAfterI64,
            frameWorkUsageBeforeI64 = projected.frameWorkUsageAfterI64,
        )

        val ready = assertIs<org.graphiks.math.geometry.PathFillWithStrokeWorkPreparationResult.Ready>(finalized)
        assertTrue(
            ready.pathWorkUsageI64.attemptedGeometryUnitCountI64 >=
                projected.pathWorkUsageAfterI64.attemptedGeometryUnitCountI64,
        )
        assertTrue(
            ready.frameWorkUsageAfterI64.snapshotByteCountI64 >=
                projected.frameWorkUsageAfterI64.snapshotByteCountI64,
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

    private fun policyWithLimits(
        maxAttemptedGeometryUnitsPerPathI32: Int = 65_536,
        maxAttemptedGeometryUnitsPerFrameI32: Int = 262_144,
        maxSnapshotByteCountPerPathI64: Long = 16L * 1024L * 1024L,
    ): PathStrokePolicyF64 = PathStrokePolicyF64(
        limitsI32 = PathStrokeLimitsI32(
            maxAttemptedGeometryUnitsPerPathI32 = maxAttemptedGeometryUnitsPerPathI32,
            maxAttemptedGeometryUnitsPerFrameI32 = maxAttemptedGeometryUnitsPerFrameI32,
        ),
        limitsI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerPathI64 = maxSnapshotByteCountPerPathI64),
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
