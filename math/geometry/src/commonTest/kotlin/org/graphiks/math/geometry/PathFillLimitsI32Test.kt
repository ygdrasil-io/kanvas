package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PathFillLimitsI32Test {
    @Test
    fun `W4c defaults expose the documented flattening bounds`() {
        val limits = PathFillLimitsI32()
        val policy = PathFillFlatteningPolicyF64()

        assertEquals(32, limits.maxSubdivisionDepthI32)
        assertEquals(65_536, limits.maxAttemptedEdgesPerPathI32)
        assertEquals(262_144, limits.maxAttemptedEdgesPerFrameI32)
        assertEquals(0.25, policy.maximumSagittaErrorF64)
        assertEquals(limits, policy.limitsI32)
    }

    @Test
    fun `attempted edge limits accept the boundary and reject the next edge`() {
        val accepted = preparePathFillGeometryF32(
            squareInput(),
            PathFillFlatteningPolicyF64(
                limitsI32 = PathFillLimitsI32(
                    maxAttemptedEdgesPerPathI32 = 4,
                    maxAttemptedEdgesPerFrameI32 = 4,
                ),
            ),
        )
        val rejected = preparePathFillGeometryF32(
            squareInput(),
            PathFillFlatteningPolicyF64(
                limitsI32 = PathFillLimitsI32(
                    maxAttemptedEdgesPerPathI32 = 3,
                    maxAttemptedEdgesPerFrameI32 = 4,
                ),
            ),
        )

        assertEquals(4, assertIs<PathFillPreparationResult.Ready>(accepted).attemptedEdgeCountI32)
        assertEquals(
            PathFillResourceLimitReason.PathAttemptedEdgeLimit,
            assertIs<PathFillPreparationResult.ResourceLimitExceeded>(rejected).reason,
        )
    }

    @Test
    fun `frame attempted edge limit includes attempts before this path`() {
        val policy = PathFillFlatteningPolicyF64(
            limitsI32 = PathFillLimitsI32(
                maxAttemptedEdgesPerPathI32 = 4,
                maxAttemptedEdgesPerFrameI32 = 8,
            ),
        )
        val accepted = preparePathFillGeometryF32(twoEdgeEmptyInput(), policy, frameAttemptedEdgesBeforeI32 = 6)
        val rejected = preparePathFillGeometryF32(twoEdgeEmptyInput(), policy, frameAttemptedEdgesBeforeI32 = 7)

        assertEquals(2, assertIs<PathFillPreparationResult.Empty>(accepted).attemptedEdgeCountI32)
        assertEquals(
            PathFillResourceLimitReason.FrameAttemptedEdgeLimit,
            assertIs<PathFillPreparationResult.ResourceLimitExceeded>(rejected).reason,
        )
    }

    @Test
    fun `bounded subdivision reports non convergence before a partial result`() {
        val result = preparePathFillGeometryF32(
            PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.CubicTo(
                        Point2F64(0.0, 4.0),
                        Point2F64(4.0, 4.0),
                        Point2F64(4.0, 0.0),
                    ),
                    PathFillSegmentF64.Close,
                ),
            ),
            PathFillFlatteningPolicyF64(
                limitsI32 = PathFillLimitsI32(maxSubdivisionDepthI32 = 0),
            ),
        )

        assertEquals(
            PathFillResourceLimitReason.FlatteningDidNotConverge,
            assertIs<PathFillPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `non finite input and projection return distinct invalid scene reasons`() {
        val nonFiniteInput = preparePathFillGeometryF32(
            PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(Double.NaN, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0)),
                ),
            ),
        )
        val nonFiniteProjection = preparePathFillGeometryF32(
            PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(Double.MAX_VALUE, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(0.0, 1.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        )

        assertEquals(
            PathFillInvalidSceneReason.NonFiniteInput,
            assertIs<PathFillPreparationResult.InvalidScene>(nonFiniteInput).reason,
        )
        assertEquals(
            PathFillInvalidSceneReason.NonFiniteProjection,
            assertIs<PathFillPreparationResult.InvalidScene>(nonFiniteProjection).reason,
        )
    }

    @Test
    fun `conservative raster bounds outside I32 return a resource limit`() {
        val result = preparePathFillGeometryF32(
            PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(2_147_483_648.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(2_147_484_160.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(2_147_483_648.0, 512.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        )

        assertEquals(
            PathFillResourceLimitReason.RasterBoundsOverflow,
            assertIs<PathFillPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `winding stencil accepts 255 closed edges and rejects 256`() {
        val accepted = preparePathFillGeometryF32(stencilPolygonInput(edgeCountI32 = 255, fillRule = FillRule.WINDING))
        val rejected = preparePathFillGeometryF32(stencilPolygonInput(edgeCountI32 = 256, fillRule = FillRule.WINDING))

        val geometry = assertIs<PathFillPreparationResult.Ready>(accepted).geometryF32
        assertNull(geometry.copyDirectTriangleF32OrNull())
        assertEquals(255, assertNotNull(geometry.copyStencilEdgeFanF32OrNull()).edgeCountI32)
        assertEquals(
            PathFillResourceLimitReason.WindingStencilEdgeLimit,
            assertIs<PathFillPreparationResult.ResourceLimitExceeded>(rejected).reason,
        )
    }

    @Test
    fun `even odd stencil is not subject to the winding edge limit`() {
        val result = preparePathFillGeometryF32(stencilPolygonInput(edgeCountI32 = 256, fillRule = FillRule.EVEN_ODD))

        val geometry = assertIs<PathFillPreparationResult.Ready>(result).geometryF32
        assertNull(geometry.copyDirectTriangleF32OrNull())
        assertEquals(256, assertNotNull(geometry.copyStencilEdgeFanF32OrNull()).edgeCountI32)
    }

    private fun squareInput(): PathFillInputF64 = PathFillInputF64.of(
        FillRule.WINDING,
        listOf(
            PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(2.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(2.0, 2.0)),
            PathFillSegmentF64.LineTo(Point2F64(0.0, 2.0)),
            PathFillSegmentF64.Close,
        ),
    )

    private fun twoEdgeEmptyInput(): PathFillInputF64 = PathFillInputF64.of(
        FillRule.WINDING,
        listOf(
            PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0)),
        ),
    )

    private fun stencilPolygonInput(edgeCountI32: Int, fillRule: FillRule): PathFillInputF64 {
        val segments = mutableListOf<PathFillSegmentF64>(PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)))
        for (indexI32 in 1 until edgeCountI32) {
            segments += PathFillSegmentF64.LineTo(
                Point2F64(indexI32.toDouble(), if (indexI32 % 2 == 0) 0.0 else 1.0),
            )
        }
        segments += PathFillSegmentF64.Close
        return PathFillInputF64.of(fillRule, segments)
    }
}
