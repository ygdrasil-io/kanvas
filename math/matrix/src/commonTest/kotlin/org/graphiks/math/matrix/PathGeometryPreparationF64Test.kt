package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillFlatteningPolicyF64
import org.graphiks.math.geometry.PathFillWithStrokeWorkPreparationResult
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.InversePathDrawMode
import org.graphiks.math.geometry.InversePathPreparationResult
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDashF64
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeInvalidSceneReason
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeResourceLimitReason
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.preparePathFillGeometryWithStrokeWorkF32

class PathGeometryPreparationF64Test {
    @Test
    fun `transformed inverse fill retains its finite interior and requested scissor domain`() {
        val path = PathBuilder(FillRule.INVERSE_EVEN_ODD)
            .moveTo(0f, 0f)
            .lineTo(2f, 0f)
            .lineTo(0f, 2f)
            .close()
            .build()

        listOf(
            Matrix3x3F64(txF64 = 4.0, tyF64 = 3.0),
            Matrix3x3F64(persp0F64 = 0.25),
        ).forEach { matrixF64 ->
            val ready = assertIs<InversePathPreparationResult.Ready>(
                matrixF64.prepareTransformedInversePathGeometryF32(
                    sourcePathF32 = path,
                    styleF64 = null,
                    mode = InversePathDrawMode.Fill,
                    domainI32 = RectI32(-10, -8, 20, 18),
                    policyF64 = PathStrokePolicyF64(),
                ),
            )

            val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(ready.geometryF32.interiorCoverageF32)
                .copyGeometryF32()
            assertEquals(FillRule.EVEN_ODD, interiorF32.fillRule)
            assertEquals(RectI32(-10, -8, 20, 18), ready.geometryF32.copyDomainI32())
        }
    }

    @Test
    fun `transformed inverse stroke and fill excludes its projected finite outline`() {
        val ready = assertIs<InversePathPreparationResult.Ready>(
            Matrix3x3F64().prepareTransformedInversePathGeometryF32(
                sourcePathF32 = rectanglePathWithFillRule(FillRule.INVERSE_WINDING),
                styleF64 = finiteStyleF64(2.0),
                mode = InversePathDrawMode.StrokeAndFill,
                domainI32 = RectI32(-4, -4, 16, 12),
                policyF64 = PathStrokePolicyF64(),
            ),
        )

        val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(ready.geometryF32.interiorCoverageF32)
            .copyGeometryF32()
        assertEquals(RectI32(1, 1, 9, 7), interiorF32.copyConservativeScissorI32())
    }

    @Test
    fun `transformed inverse hairline excludes its device outline under affine and perspective`() {
        val path = PathBuilder(FillRule.INVERSE_WINDING)
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(10f, 8f).lineTo(0f, 8f).close().build()
        val affineF64 = Matrix3x3F64(sxF64 = 2.0, syF64 = 3.0)
        val fill = assertIs<InversePathPreparationResult.Ready>(
            affineF64.prepareTransformedInversePathGeometryF32(
                path, null, InversePathDrawMode.Fill, RectI32(-4, -4, 24, 28), PathStrokePolicyF64(),
            ),
        )
        val stroked = assertIs<InversePathPreparationResult.Ready>(
            affineF64.prepareTransformedInversePathGeometryF32(
                path, hairlineStyleF64().copy(join = PathStrokeJoin.Bevel), InversePathDrawMode.StrokeAndFill,
                RectI32(-4, -4, 24, 28), preciseInverseStrokePolicyF64(),
            ),
        )

        val fillScissorI32 = assertIs<InverseInteriorCoverageF32.Geometry>(fill.geometryF32.interiorCoverageF32)
            .copyGeometryF32().copyConservativeScissorI32()
        val strokedGeometryF32 = assertIs<InverseInteriorCoverageF32.Geometry>(stroked.geometryF32.interiorCoverageF32)
            .copyGeometryF32()
        assertTrue(strokedGeometryF32.copyConservativeScissorI32().right <= fillScissorI32.right)
        assertTrue(strokedGeometryF32.copyConservativeScissorI32().bottom <= fillScissorI32.bottom)
        assertTrue(
            !assertNotNull(assertIs<InverseInteriorCoverageF32.Geometry>(fill.geometryF32.interiorCoverageF32)
                .copyGeometryF32().copyStencilEdgeFanF32OrNull()).copyVerticesF32().contentEquals(
                assertNotNull(strokedGeometryF32.copyStencilEdgeFanF32OrNull()).copyVerticesF32(),
            ),
        )
        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<InversePathPreparationResult.ResourceLimitExceeded>(
                Matrix3x3F64(persp0F64 = 0.000001).prepareTransformedInversePathGeometryF32(
                    path, hairlineStyleF64(), InversePathDrawMode.StrokeAndFill,
                    RectI32(-4, -4, 24, 28), preciseInverseStrokePolicyF64(),
                ),
            ).reason,
        )
    }

    @Test
    fun `transformed inverse finite stroke and fill retains F minus its projected outline`() {
        val path = rectanglePathWithFillRule(FillRule.INVERSE_WINDING)
        val frameWorkUsageBeforeI64 = PathStrokeWorkUsageI64(
            attemptedGeometryUnitCountI64 = 3L,
            emittedVertexCountI64 = 5L,
            emittedIndexCountI64 = 7L,
            snapshotByteCountI64 = 11L,
        )
        val ready = assertIs<InversePathPreparationResult.Ready>(
            Matrix3x3F64(sxF64 = 2.0, syF64 = 3.0).prepareTransformedInversePathGeometryF32(
                path, finiteStyleF64(2.0).copy(join = PathStrokeJoin.Bevel), InversePathDrawMode.StrokeAndFill,
                RectI32(-4, -4, 24, 28), preciseInverseStrokePolicyF64(), frameWorkUsageBeforeI64,
            ),
        )
        val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(ready.geometryF32.interiorCoverageF32)
            .copyGeometryF32()
        assertTrue(interiorF32.copyConservativeScissorI32().right <= 20)
        assertUsageAddedToFrameI64(frameWorkUsageBeforeI64, ready.pathWorkUsageI64, ready.frameWorkUsageAfterI64)
        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<InversePathPreparationResult.ResourceLimitExceeded>(
                Matrix3x3F64(persp0F64 = 0.001).prepareTransformedInversePathGeometryF32(
                    path, finiteStyleF64(2.0).copy(join = PathStrokeJoin.Bevel), InversePathDrawMode.StrokeAndFill,
                    RectI32(-4, -4, 24, 28), preciseInverseStrokePolicyF64(), frameWorkUsageBeforeI64,
                ),
            ).reason,
        )
    }

    @Test
    fun `finite strokes prepare through rotation skew and bounded perspective`() {
        val path = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build()
        val transforms = listOf(
            Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f),
            Matrix3x3F32(kx = 0.25f),
            Matrix3x3F32(persp0 = 0.05f),
        )

        transforms.forEach { matrixF32 ->
            val result = matrixF32.preparePathStrokeGeometryF32(
                path = path,
                styleF64 = finiteStyleF64(2.0),
                mode = PathStrokeDrawMode.Stroke,
            )

            assertIs<PathStrokePreparationResult.Ready>(result)
            assertTrue(result.geometryF32.copyConservativeBoundsF32().isFinite())
        }
    }

    @Test
    fun `general affine finite stroke maps its source-expanded vertices while hairline stays device thin`() {
        val matrixF64 = Matrix3x3F64(kxF64 = 0.5)
        val path = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build()

        val finite = assertIs<PathStrokePreparationResult.Ready>(matrixF64.preparePathStrokeGeometryF32(
            path = path,
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.Stroke,
        )).geometryF32
        val hairline = assertIs<PathStrokePreparationResult.Ready>(matrixF64.preparePathStrokeGeometryF32(
            path = path,
            styleF64 = hairlineStyleF64(),
            mode = PathStrokeDrawMode.Stroke,
        )).geometryF32

        val finiteBoundsF32 = finite.copyConservativeBoundsF32()
        assertEquals(-0.5f, finiteBoundsF32.left)
        assertEquals(10.5f, finiteBoundsF32.right)
        assertEquals(-1f, finiteBoundsF32.top)
        assertEquals(1f, finiteBoundsF32.bottom)
        val finiteVerticesF32 = assertNotNull(finite.copyFillGeometryF32().copyStencilEdgeFanF32OrNull())
            .copyVerticesF32()
        assertTrue(finiteVerticesF32.hasEdgeEndpointF32(-0.5f, -1f))
        assertTrue(finiteVerticesF32.hasEdgeEndpointF32(10.5f, 1f))

        val hairlineBoundsF32 = hairline.copyConservativeBoundsF32()
        assertEquals(0f, hairlineBoundsF32.left)
        assertEquals(10f, hairlineBoundsF32.right)
        assertEquals(-0.5f, hairlineBoundsF32.top)
        assertEquals(0.5f, hairlineBoundsF32.bottom)
    }

    @Test
    fun `dashed finite stroke remains bounded under perspective`() {
        val result = Matrix3x3F32(persp0 = 0.05f).preparePathStrokeGeometryF32(
            path = PathBuilder().moveTo(0f, 0f).lineTo(12f, 0f).build(),
            styleF64 = finiteStyleF64(
                widthF64 = 2.0,
                dashF64 = PathStrokeDashF64.of(doubleArrayOf(3.0, 1.0), phaseF64 = 0.0),
            ),
            mode = PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `dash intervals remove the final off run from public stroke geometry`() {
        val path = PathBuilder().moveTo(0f, 0f).lineTo(12f, 0f).build()
        val matrixF64 = Matrix3x3F64(kxF64 = 0.25)
        val undashed = assertIs<PathStrokePreparationResult.Ready>(matrixF64.preparePathStrokeGeometryF32(
            path = path,
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.Stroke,
        )).geometryF32
        val dashed = assertIs<PathStrokePreparationResult.Ready>(matrixF64.preparePathStrokeGeometryF32(
            path = path,
            styleF64 = finiteStyleF64(
                widthF64 = 2.0,
                dashF64 = PathStrokeDashF64.of(doubleArrayOf(3.0, 1.0), phaseF64 = 0.0),
            ),
            mode = PathStrokeDrawMode.Stroke,
        )).geometryF32

        assertEquals(12.25f, undashed.copyConservativeBoundsF32().right)
        assertEquals(11.25f, dashed.copyConservativeBoundsF32().right)
        assertEquals(4, undashed.copyFillGeometryF32().emittedNonZeroClosedEdgeCountI32)
        assertEquals(12, dashed.copyFillGeometryF32().emittedNonZeroClosedEdgeCountI32)
    }

    @Test
    fun `hairline expands to one device pixel after perspective projection`() {
        val result = Matrix3x3F32(persp0 = 0.05f).preparePathStrokeGeometryF32(
            path = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build(),
            styleF64 = hairlineStyleF64(),
            mode = PathStrokeDrawMode.Stroke,
        )

        val geometryF32 = assertIs<PathStrokePreparationResult.Ready>(result).geometryF32
        assertEquals(1f, geometryF32.copyConservativeBoundsF32().height())
    }

    @Test
    fun `stroke and fill preserves the axis aligned topology route`() {
        val result = Matrix3x3F32.translation(3f, 5f).preparePathStrokeGeometryF32(
            path = rectanglePath(),
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `stroke and fill dispatches a segment under non trivial perspective`() {
        val result = Matrix3x3F32(persp0 = 0.025f).preparePathStrokeGeometryF32(
            path = PathBuilder().moveTo(0f, 0f).lineTo(8f, 0f).build(),
            styleF64 = finiteStyleF64(1.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
        )

        val geometryF32 = assertIs<PathStrokePreparationResult.Ready>(result).geometryF32
        assertTrue(geometryF32.copyConservativeBoundsF32().isFinite())
    }

    @Test
    fun `closed perspective stroke and fill reports the current topology limit`() {
        val result = Matrix3x3F32(persp0 = 0.025f).preparePathStrokeGeometryF32(
            path = rectanglePath(),
            styleF64 = finiteStyleF64(1.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
        )

        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<PathStrokePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `closed skew stroke and fill reports topology limit rather than leaking a topology exception`() {
        val result = Matrix3x3F64(kxF64 = 0.25).preparePathStrokeGeometryF32(
            path = rectanglePath(),
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
        )

        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<PathStrokePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `perspective horizon stroke retains its horizon refusal reason`() {
        val result = horizonMatrixF32().preparePathStrokeGeometryF32(
            path = horizonPath(),
            styleF64 = finiteStyleF64(1.0),
            mode = PathStrokeDrawMode.Stroke,
        )

        assertEquals(
            PathStrokeInvalidSceneReason.ProjectionHorizonCrossing,
            assertIs<PathStrokePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `perspective horizon stroke and fill retains its horizon refusal reason`() {
        val result = horizonMatrixF32().preparePathStrokeGeometryF32(
            path = horizonPath(),
            styleF64 = finiteStyleF64(1.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
        )

        assertEquals(
            PathStrokeInvalidSceneReason.ProjectionHorizonCrossing,
            assertIs<PathStrokePreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `fill finalization emits vertices and scissor for every typed transform class`() {
        val path = trianglePath()
        val transforms = listOf(
            Matrix3x3F64() to FillExpectationF32(
                verticesF32 = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                scissorI32 = RectI32(0, 0, 2, 2),
            ),
            Matrix3x3F64(sxF64 = 2.0, syF64 = 0.5, txF64 = 3.0, tyF64 = -1.0) to FillExpectationF32(
                verticesF32 = floatArrayOf(3f, -1f, 7f, -1f, 3f, 0f),
                scissorI32 = RectI32(3, -1, 7, 0),
            ),
            Matrix3x3F64(sxF64 = 0.0, kxF64 = -1.0, kyF64 = 1.0, syF64 = 0.0) to FillExpectationF32(
                verticesF32 = floatArrayOf(0f, 0f, 0f, 2f, -2f, 0f),
                scissorI32 = RectI32(-2, 0, 0, 2),
            ),
            Matrix3x3F64(persp0F64 = 0.25) to FillExpectationF32(
                verticesF32 = floatArrayOf(0f, 0f, 1.3333334f, 0f, 0f, 2f),
                scissorI32 = RectI32(0, 0, 2, 2),
            ),
        )

        transforms.forEach { (matrixF64, expectedF32) ->
            val result = matrixF64.preparePathFillGeometryF32(path)

            val geometryF32 = assertIs<PathTransformedFillPreparationResult.Ready>(result).geometryF32
            assertContentEquals(
                expectedF32.verticesF32,
                assertNotNull(geometryF32.copyDirectTriangleF32OrNull()).copyVerticesF32(),
            )
            assertEquals(expectedF32.scissorI32, geometryF32.copyConservativeScissorI32())
        }
    }

    @Test
    fun `perspective fill retains its horizon refusal reason`() {
        val result = Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0).preparePathFillGeometryF32(
            PathBuilder().moveTo(-1f, 0f).lineTo(1f, 0f).lineTo(0f, 1f).close().build(),
        )

        assertEquals(
            PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing,
            assertIs<PathTransformedFillPreparationResult.InvalidScene>(result).reason
                .let { assertIs<PathTransformedFillInvalidSceneReason.Projective>(it).value },
        )
    }

    @Test
    fun `perspective fill facade composes Task 3 and finalization work without resetting a frame`() {
        val frameWorkUsageBeforeI64 = PathStrokeWorkUsageI64(
            attemptedGeometryUnitCountI64 = 7L,
            emittedVertexCountI64 = 11L,
            emittedIndexCountI64 = 13L,
            snapshotByteCountI64 = 17L,
        )
        val matrixF64 = Matrix3x3F64(persp0F64 = 0.025)
        val path = trianglePath()
        val fillPolicyF64 = PathFillFlatteningPolicyF64()
        val strokePolicyF64 = PathStrokePolicyF64()

        val projected = assertIs<PathProjectivePreparationResult.Ready>(matrixF64.prepareProjectedPathFillInputF64(
            path = path,
            policyF64 = fillPolicyF64,
            workPolicyF64 = strokePolicyF64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        ))
        val explicit = assertIs<PathFillWithStrokeWorkPreparationResult.Ready>(
            preparePathFillGeometryWithStrokeWorkF32(
                inputF64 = projected.inputF64,
                fillPolicyF64 = fillPolicyF64,
                strokePolicyF64 = strokePolicyF64,
                pathWorkUsageBeforeI64 = projected.pathWorkUsageAfterI64,
                frameWorkUsageBeforeI64 = projected.frameWorkUsageAfterI64,
            ),
        )

        val result = matrixF64.preparePathFillGeometryF32(
            path = path,
            fillPolicyF64 = fillPolicyF64,
            strokePolicyF64 = strokePolicyF64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        )

        val ready = assertIs<PathTransformedFillPreparationResult.Ready>(result)
        assertContentEquals(
            explicit.geometryF32.copyDirectTriangleF32OrNull()?.copyVerticesF32(),
            ready.geometryF32.copyDirectTriangleF32OrNull()?.copyVerticesF32(),
        )
        assertEquals(explicit.pathWorkUsageI64, ready.pathWorkUsageI64)
        assertEquals(explicit.frameWorkUsageAfterI64, ready.frameWorkUsageAfterI64)
        assertUsageAddedToFrameI64(frameWorkUsageBeforeI64, ready.pathWorkUsageI64, ready.frameWorkUsageAfterI64)
    }

    private fun finiteStyleF64(
        widthF64: Double,
        dashF64: PathStrokeDashF64? = null,
    ): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
        dashF64 = dashF64,
    )

    private fun hairlineStyleF64(): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Hairline,
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private fun preciseInverseStrokePolicyF64(): PathStrokePolicyF64 = PathStrokePolicyF64(
        maximumSagittaErrorF64 = 1.0,
        limitsI32 = PathStrokeLimitsI32(
            maxAttemptedGeometryUnitsPerPathI32 = 1_000_000,
            maxAttemptedGeometryUnitsPerFrameI32 = 2_000_000,
            maxEmittedVertexCountPerPathI32 = 1_000_000,
            maxEmittedVertexCountPerFrameI32 = 2_000_000,
            maxEmittedIndexCountPerPathI32 = 3_000_000,
            maxEmittedIndexCountPerFrameI32 = 6_000_000,
        ),
    )

    private fun rectanglePath() = PathBuilder()
        .moveTo(0f, 0f)
        .lineTo(10f, 0f)
        .lineTo(10f, 8f)
        .lineTo(0f, 8f)
        .close()
        .build()

    private fun rectanglePathWithFillRule(fillRule: FillRule) = PathBuilder(fillRule)
        .moveTo(0f, 0f)
        .lineTo(10f, 0f)
        .lineTo(10f, 8f)
        .lineTo(0f, 8f)
        .close()
        .build()

    private fun trianglePath() = PathBuilder()
        .moveTo(0f, 0f)
        .lineTo(2f, 0f)
        .lineTo(0f, 2f)
        .close()
        .build()

    private fun horizonMatrixF32(): Matrix3x3F32 = Matrix3x3F32(persp0 = 1f, persp2 = 0f)

    private fun horizonPath() = PathBuilder()
        .moveTo(-1f, 0f)
        .lineTo(1f, 0f)
        .lineTo(0f, 1f)
        .close()
        .build()

    private fun FloatArray.hasEdgeEndpointF32(xF32: Float, yF32: Float): Boolean =
        indices.step(6).any { offsetI32 ->
            (this[offsetI32 + 2] == xF32 && this[offsetI32 + 3] == yF32) ||
                (this[offsetI32 + 4] == xF32 && this[offsetI32 + 5] == yF32)
        }

    private fun assertUsageAddedToFrameI64(
        beforeI64: PathStrokeWorkUsageI64,
        pathUsageI64: PathStrokeWorkUsageI64,
        afterI64: PathStrokeWorkUsageI64,
    ) {
        assertEquals(
            beforeI64.attemptedGeometryUnitCountI64 + pathUsageI64.attemptedGeometryUnitCountI64,
            afterI64.attemptedGeometryUnitCountI64,
        )
        assertEquals(
            beforeI64.emittedVertexCountI64 + pathUsageI64.emittedVertexCountI64,
            afterI64.emittedVertexCountI64,
        )
        assertEquals(
            beforeI64.emittedIndexCountI64 + pathUsageI64.emittedIndexCountI64,
            afterI64.emittedIndexCountI64,
        )
        assertEquals(
            beforeI64.snapshotByteCountI64 + pathUsageI64.snapshotByteCountI64,
            afterI64.snapshotByteCountI64,
        )
    }

    private data class FillExpectationF32(
        val verticesF32: FloatArray,
        val scissorI32: RectI32,
    )
}
