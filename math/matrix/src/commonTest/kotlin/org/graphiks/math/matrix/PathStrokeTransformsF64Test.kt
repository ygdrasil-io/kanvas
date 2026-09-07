package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeResourceLimitReason
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64

class PathStrokeTransformsF64Test {
    @Test
    fun `finite stroke expands before anisotropic scale but hairline stays one device pixel`() {
        val matrix = Matrix3x3F32(sx = 4f, sy = 2f)

        val finite = assertReady(
            matrix.preparePathStrokeGeometryF32(
                path = linePath(),
                styleF64 = finiteStyle(2.0),
                mode = PathStrokeDrawMode.Stroke,
            ),
        )
        val hairline = assertReady(
            matrix.preparePathStrokeGeometryF32(
                path = linePath(),
                styleF64 = hairlineStyle(),
                mode = PathStrokeDrawMode.Stroke,
            ),
        )

        assertEquals(4f, finite.copyConservativeBoundsF32().height())
        assertEquals(1f, hairline.copyConservativeBoundsF32().height())
    }

    @Test
    fun `finite quadratic with a rapid normal turn is subdivided at the device tolerance`() {
        val result = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().moveTo(0f, 0f).quadTo(10f, 0f, 0f, 4f).build(),
            finiteStyle(5.0),
            PathStrokeDrawMode.Stroke,
        )
        assertTrue(result is PathStrokePreparationResult.Ready, "$result")
        val geometry = assertReady(result)
        val fan = assertIs<org.graphiks.math.geometry.PathStencilEdgeFanF32>(
            geometry.copyFillGeometryF32().copyStencilEdgeFanF32OrNull(),
        )

        assertTrue(
            fan.edgeCountI32 > 128,
            "expected certified finite-outline subdivision, got ${fan.edgeCountI32} edges",
        )
    }

    @Test
    fun `finite cubic with collinear forward derivative controls converges`() {
        val result = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().moveTo(0f, 0f).cubicTo(1f, 0f, 3f, 0f, 6f, 0f).build(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertTrue(result is PathStrokePreparationResult.Ready, "$result")
    }

    @Test
    fun `finite cubic splits a rounded double cusp before certification`() {
        val result = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().moveTo(0f, 0f).cubicTo(9f, 0f, -3f, 0f, 13f, 0f).build(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `finite quadratic with an endpoint tangent zero converges`() {
        val result = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().moveTo(0f, 0f).quadTo(1f, 0f, 1f, 0f).build(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `tiny finite endpoint tangent retains its analytic normal side`() {
        val geometry = assertReady(
            Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
                PathBuilder().moveTo(0f, 0f).quadTo(0.0000000002f, 0f, 0.0000000002f, 0f).build(),
                finiteStyle(2.0),
                PathStrokeDrawMode.Stroke,
            ),
        )
        val fan = assertIs<org.graphiks.math.geometry.PathStencilEdgeFanF32>(
            geometry.copyFillGeometryF32().copyStencilEdgeFanF32OrNull(),
        )
        val vertices = fan.copyVerticesF32()

        assertTrue(vertices.all(Float::isFinite), "finite source derivatives must publish finite device geometry")
        assertTrue(
            vertices.indices.step(6).none { offsetI32 ->
                val startX = vertices[offsetI32 + 2]
                val startY = vertices[offsetI32 + 3]
                val endX = vertices[offsetI32 + 4]
                val endY = vertices[offsetI32 + 5]
                startX != endX && startY * endY < 0f
            },
            "finite offset must not join opposite normal sides diagonally",
        )
    }

    @Test
    fun `finite cubic cusp pieces with endpoint tangent zeros converge`() {
        val result = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().moveTo(-1f, 0f).cubicTo(1f, 0f, -1f, 0f, 1f, 0f).build(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `finite quadratic with a tiny derivative hull near the origin converges conservatively`() {
        val result = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().moveTo(0f, 0f).quadTo(0.0000005f, 0.000000005f, 0.0000005f, 0.00000001f).build(),
            finiteStyle(0.5),
            PathStrokeDrawMode.Stroke,
        )

        val geometry = assertReady(result)
        val fan = assertIs<org.graphiks.math.geometry.PathStencilEdgeFanF32>(
            geometry.copyFillGeometryF32().copyStencilEdgeFanF32OrNull(),
        )
        assertTrue(fan.edgeCountI32 > 16, "expected a conservative normal bound, got ${fan.edgeCountI32} edges")
    }

    @Test
    fun `axis aligned identity translation and negative scales retain finite stroke coverage`() {
        val source = linePath()

        val identity = assertReady(
            Matrix3x3F32.Identity.preparePathStrokeGeometryF32(source, finiteStyle(2.0), PathStrokeDrawMode.Stroke),
        )
        val translated = assertReady(
            Matrix3x3F32.translation(5f, 7f).preparePathStrokeGeometryF32(
                source,
                finiteStyle(2.0),
                PathStrokeDrawMode.Stroke,
            ),
        )
        val mirrored = assertReady(
            Matrix3x3F32.scaling(-2f, -3f).preparePathStrokeGeometryF32(
                source,
                finiteStyle(2.0),
                PathStrokeDrawMode.Stroke,
            ),
        )

        assertEquals(-1f, identity.copyConservativeBoundsF32().top)
        assertEquals(1f, identity.copyConservativeBoundsF32().bottom)
        assertEquals(5f, translated.copyConservativeBoundsF32().left)
        assertEquals(15f, translated.copyConservativeBoundsF32().right)
        assertEquals(6f, translated.copyConservativeBoundsF32().top)
        assertEquals(8f, translated.copyConservativeBoundsF32().bottom)
        assertEquals(-20f, mirrored.copyConservativeBoundsF32().left)
        assertEquals(0f, mirrored.copyConservativeBoundsF32().right)
        assertEquals(-3f, mirrored.copyConservativeBoundsF32().top)
        assertEquals(3f, mirrored.copyConservativeBoundsF32().bottom)
    }

    @Test
    fun `one negative scale reflects a finite stroke without changing its coverage`() {
        val geometry = assertReady(
            Matrix3x3F32.scaling(-2f, 3f).preparePathStrokeGeometryF32(
                linePath(),
                finiteStyle(2.0),
                PathStrokeDrawMode.Stroke,
            ),
        )

        assertEquals(-20f, geometry.copyConservativeBoundsF32().left)
        assertEquals(0f, geometry.copyConservativeBoundsF32().right)
        assertEquals(-3f, geometry.copyConservativeBoundsF32().top)
        assertEquals(3f, geometry.copyConservativeBoundsF32().bottom)
    }

    @Test
    fun `empty path stays empty and unsupported or non finite matrices are rejected`() {
        val empty = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            PathBuilder().build(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )
        val skew = Matrix3x3F32(kx = 1f).preparePathStrokeGeometryF32(
            linePath(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )
        val nonFinite = Matrix3x3F32(sx = Float.NaN).preparePathStrokeGeometryF32(
            linePath(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.Empty>(empty)
        assertIs<PathStrokePreparationResult.InvalidScene>(skew)
        assertIs<PathStrokePreparationResult.InvalidScene>(nonFinite)
    }

    @Test
    fun `negative zero matrix coefficients are canonicalized before axis classification`() {
        val result = Matrix3x3F32(kx = -0f, ky = -0f, persp0 = -0f, persp1 = -0f).preparePathStrokeGeometryF32(
            linePath(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.Ready>(result)
    }

    @Test
    fun `device F32 overflow is rejected before a geometry snapshot is published`() {
        val result = Matrix3x3F32(sx = Float.MAX_VALUE).preparePathStrokeGeometryF32(
            PathBuilder().moveTo(0f, 0f).lineTo(2f, 0f).build(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
        )

        assertIs<PathStrokePreparationResult.InvalidScene>(result)
    }

    @Test
    fun `next stroke observes the exact returned frame work snapshot`() {
        val first = assertIs<PathStrokePreparationResult.Ready>(
            Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
                linePath(),
                finiteStyle(2.0),
                PathStrokeDrawMode.Stroke,
            ),
        )
        val policy = PathStrokePolicyF64(
            limitsI32 = PathStrokeLimitsI32(
                maxAttemptedGeometryUnitsPerFrameI32 =
                    (first.frameWorkUsageAfterI64.attemptedGeometryUnitCountI64 + 1L).toInt(),
            ),
        )
        val next = Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            linePath(),
            finiteStyle(2.0),
            PathStrokeDrawMode.Stroke,
            policy,
            first.frameWorkUsageAfterI64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.FrameWorkLimit,
            assertIs<PathStrokePreparationResult.ResourceLimitExceeded>(next).reason,
        )
    }

    @Test
    fun `stroke snapshots remain isolated from mutable copies`() {
        val geometry = assertReady(
            Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
                linePath(),
                finiteStyle(2.0),
                PathStrokeDrawMode.Stroke,
            ),
        )

        val bounds = geometry.copyConservativeBoundsF32()
        val fill = geometry.copyFillGeometryF32()
        val fan = assertIs<org.graphiks.math.geometry.PathStencilEdgeFanF32>(fill.copyStencilEdgeFanF32OrNull())
        val vertices = fan.copyVerticesF32()
        bounds.top = 99f
        vertices[2] = 99f

        assertEquals(-1f, geometry.copyConservativeBoundsF32().top)
        assertEquals(0f, assertIs<org.graphiks.math.geometry.PathStencilEdgeFanF32>(
            geometry.copyFillGeometryF32().copyStencilEdgeFanF32OrNull(),
        ).copyVerticesF32()[2])
    }

    private fun assertReady(result: PathStrokePreparationResult): PathStrokeGeometryF32 =
        assertIs<PathStrokePreparationResult.Ready>(result).geometryF32

    private fun linePath() = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build()

    private fun finiteStyle(widthF64: Double): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private fun hairlineStyle(): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Hairline,
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )
}
