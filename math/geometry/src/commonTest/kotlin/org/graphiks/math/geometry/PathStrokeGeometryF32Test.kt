package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PathStrokeGeometryF32Test {
    @Test
    fun `stroke work usage rejects negative debits before geometry can be published`() {
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(emittedVertexCountI64 = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(emittedIndexCountI64 = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(snapshotByteCountI64 = -1L)
        }
    }

    @Test
    fun `stroke limits reject non-positive budgets and negative subdivision depth`() {
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxSubdivisionDepthI32 = -1) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxAttemptedGeometryUnitsPerPathI32 = 0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxEmittedVertexCountPerFrameI32 = 0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxEmittedIndexCountPerPathI32 = 0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI64(maxSnapshotByteCountPerPathI64 = 0L) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI64(maxSnapshotByteCountPerFrameI64 = 0L) }
    }

    @Test
    fun `programming exception from a public projection remains visible to the caller`() {
        assertFailsWith<IllegalStateException> {
            prepareProjectedPathStrokeGeometryF32(
                inputF64 = lineInputF64(),
                styleF64 = finiteStyleF64(),
                mode = PathStrokeDrawMode.Stroke,
                projectionF64 = object : PathStrokeProjectionF64 {
                    override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
                        PathStrokeProjectionPointResultF64.Ready(pointF64)

                    override fun certifyOutlineIntervalF64(
                        intervalF64: PathStrokeOutlineIntervalF64,
                    ): PathStrokeProjectionIntervalResultF64 =
                        error("programming failure from projection authority")
                },
            )
        }
    }

    @Test
    fun `public projected stroke entry point publishes a device geometry snapshot`() {
        val result = prepareProjectedPathStrokeGeometryF32(
            inputF64 = lineInputF64(),
            styleF64 = finiteStyleF64(),
            mode = PathStrokeDrawMode.Stroke,
            projectionF64 = identityProjectionF64,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `stroke and fill publishes a device geometry through the union lane`() {
        val result = prepareProjectedPathStrokeGeometryF32(
            inputF64 = lineInputF64(),
            styleF64 = finiteStyleF64(),
            mode = PathStrokeDrawMode.StrokeAndFill,
            projectionF64 = identityProjectionF64,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `overflowing device scissor returns atomically without a geometry snapshot`() {
        val result = prepareProjectedPathStrokeGeometryF32(
            inputF64 = lineInputF64(
                startX = 2_147_483_904.0,
                endX = 2_147_484_928.0,
            ),
            styleF64 = finiteStyleF64(),
            mode = PathStrokeDrawMode.Stroke,
            projectionF64 = identityProjectionF64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.RasterBoundsOverflow,
            assertIs<PathStrokePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    private fun lineInputF64(startX: Double = 0.0, endX: Double = 10.0): PathFillInputF64 = PathFillInputF64.of(
        FillRule.WINDING,
        listOf(
            PathFillSegmentF64.MoveTo(Point2F64(startX, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(endX, 0.0)),
        ),
    )

    private fun finiteStyleF64(): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(2.0),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private val identityProjectionF64: PathStrokeProjectionF64 = object : PathStrokeProjectionF64 {
        override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
            PathStrokeProjectionPointResultF64.Ready(pointF64)

        override fun certifyOutlineIntervalF64(
            intervalF64: PathStrokeOutlineIntervalF64,
        ): PathStrokeProjectionIntervalResultF64 = PathStrokeProjectionIntervalResultF64.Bounded(
            intervalF64.sourceSagittaUpperBoundF64,
        )
    }
}
