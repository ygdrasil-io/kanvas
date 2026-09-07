package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PathFillPreparationF64Test {
    @Test
    fun `ledger aware direct fill publishes exact work and snapshot costs`() {
        val ready = assertIs<PathFillWithStrokeWorkPreparationResult.Ready>(
            preparePathFillGeometryWithStrokeWorkF32(triangleInput()),
        )

        assertEquals(3L, ready.pathWorkUsageI64.attemptedGeometryUnitCountI64)
        assertEquals(3L, ready.pathWorkUsageI64.emittedVertexCountI64)
        assertEquals(3L, ready.pathWorkUsageI64.emittedIndexCountI64)
        assertEquals(52L, ready.geometryF32.snapshotByteCostI64)
        assertEquals(52L, ready.pathWorkUsageI64.snapshotByteCountI64)
        assertEquals(ready.pathWorkUsageI64, ready.frameWorkUsageAfterI64)
    }

    @Test
    fun `fill output limits are enforced before its arrays can be published`() {
        val attempted = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = triangleInput(),
            strokePolicyF64 = strokePolicy(maxAttemptedGeometryUnitsPerPathI32 = 2),
        )
        val vertices = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = triangleInput(),
            strokePolicyF64 = strokePolicy(maxEmittedVertexCountPerPathI32 = 2),
        )
        val indices = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = triangleInput(),
            strokePolicyF64 = strokePolicy(maxEmittedIndexCountPerPathI32 = 2),
        )
        val bytes = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = triangleInput(),
            strokePolicyF64 = PathStrokePolicyF64(
                limitsI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerPathI64 = 51L),
            ),
        )

        assertEquals(
            PathStrokeResourceLimitReason.PathWorkLimit,
            assertIs<PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded>(attempted).reason,
        )
        assertEquals(
            PathStrokeResourceLimitReason.VertexLimit,
            assertIs<PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded>(vertices).reason,
        )
        assertEquals(
            PathStrokeResourceLimitReason.IndexLimit,
            assertIs<PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded>(indices).reason,
        )
        assertEquals(
            PathStrokeResourceLimitReason.SnapshotByteLimit,
            assertIs<PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded>(bytes).reason,
        )
    }

    @Test
    fun `returned fill frame snapshot limits the following fill`() {
        val first = assertIs<PathFillWithStrokeWorkPreparationResult.Ready>(
            preparePathFillGeometryWithStrokeWorkF32(triangleInput()),
        )
        val next = preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = triangleInput(),
            strokePolicyF64 = strokePolicy(
                maxAttemptedGeometryUnitsPerFrameI32 =
                    (first.frameWorkUsageAfterI64.attemptedGeometryUnitCountI64 + 1L).toInt(),
            ),
            frameWorkUsageBeforeI64 = first.frameWorkUsageAfterI64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.FrameWorkLimit,
            assertIs<PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded>(next).reason,
        )
    }

    private fun triangleInput(): PathFillInputF64 = PathFillInputF64.of(
        FillRule.WINDING,
        listOf(
            PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(2.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(0.0, 2.0)),
            PathFillSegmentF64.Close,
        ),
    )

    private fun strokePolicy(
        maxAttemptedGeometryUnitsPerPathI32: Int = 65_536,
        maxAttemptedGeometryUnitsPerFrameI32: Int = 262_144,
        maxEmittedVertexCountPerPathI32: Int = 262_144,
        maxEmittedIndexCountPerPathI32: Int = 786_432,
    ): PathStrokePolicyF64 = PathStrokePolicyF64(
        limitsI32 = PathStrokeLimitsI32(
            maxAttemptedGeometryUnitsPerPathI32 = maxAttemptedGeometryUnitsPerPathI32,
            maxAttemptedGeometryUnitsPerFrameI32 = maxAttemptedGeometryUnitsPerFrameI32,
            maxEmittedVertexCountPerPathI32 = maxEmittedVertexCountPerPathI32,
            maxEmittedIndexCountPerPathI32 = maxEmittedIndexCountPerPathI32,
        ),
    )
}
