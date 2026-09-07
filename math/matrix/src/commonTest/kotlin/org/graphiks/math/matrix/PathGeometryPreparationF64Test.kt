package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDashF64
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeResourceLimitReason
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.PathStrokeWidthF64

class PathGeometryPreparationF64Test {
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
    fun `fill finalization prepares every typed transform class`() {
        val path = rectanglePath()
        val transforms = listOf(
            Matrix3x3F64(),
            Matrix3x3F64(sxF64 = 2.0, syF64 = 0.5, txF64 = 3.0),
            Matrix3x3F64(sxF64 = 0.0, kxF64 = -1.0, kyF64 = 1.0, syF64 = 0.0),
            Matrix3x3F64(persp0F64 = 0.025),
        )

        transforms.forEach { matrixF64 ->
            val result = matrixF64.preparePathFillGeometryF32(path)

            val geometryF32 = assertIs<PathTransformedFillPreparationResult.Ready>(result).geometryF32
            assertTrue(geometryF32.copyConservativeScissorI32().width() > 0)
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
    fun `perspective fill finalization carries its transform work into the frame snapshot`() {
        val frameWorkUsageBeforeI64 = PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 7L)

        val result = Matrix3x3F64(persp0F64 = 0.025).preparePathFillGeometryF32(
            path = rectanglePath(),
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        )

        val ready = assertIs<PathTransformedFillPreparationResult.Ready>(result)
        assertTrue(
            ready.frameWorkUsageAfterI64.attemptedGeometryUnitCountI64 >
                frameWorkUsageBeforeI64.attemptedGeometryUnitCountI64,
        )
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

    private fun rectanglePath() = PathBuilder()
        .moveTo(0f, 0f)
        .lineTo(10f, 0f)
        .lineTo(10f, 8f)
        .lineTo(0f, 8f)
        .close()
        .build()
}
