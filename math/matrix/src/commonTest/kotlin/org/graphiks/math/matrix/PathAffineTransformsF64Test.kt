package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.vector.Vector2F64

class PathAffineTransformsF64Test {
    @Test
    fun `affine F64 mapping applies an arbitrary rotation to lines and controls`() {
        val matrixF64 = Matrix3x3F64(
            sxF64 = 0.6,
            kxF64 = -0.8,
            txF64 = 7.0,
            kyF64 = 0.8,
            syF64 = 0.6,
            tyF64 = -3.0,
        )
        val inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(1.0, 2.0)),
                PathFillSegmentF64.QuadTo(Point2F64(3.0, 5.0), Point2F64(8.0, -1.0)),
                PathFillSegmentF64.CubicTo(Point2F64(-4.0, 9.0), Point2F64(11.0, 6.0), Point2F64(2.0, 3.0)),
            ),
        )

        val mappedF64 = matrixF64.mapAffinePathFillInputF64(inputF64, PathTransformWorkDebitI64 { })

        assertEquals(
            PathFillSegmentF64.MoveTo(oraclePointF64(matrixF64, Point2F64(1.0, 2.0))),
            mappedF64.segmentAtI32(0),
        )
        val quadF64 = mappedF64.segmentAtI32(1) as PathFillSegmentF64.QuadTo
        assertEquals(oraclePointF64(matrixF64, Point2F64(3.0, 5.0)), quadF64.control)
        assertEquals(oraclePointF64(matrixF64, Point2F64(8.0, -1.0)), quadF64.point)
        val cubicF64 = mappedF64.segmentAtI32(2) as PathFillSegmentF64.CubicTo
        assertEquals(oraclePointF64(matrixF64, Point2F64(-4.0, 9.0)), cubicF64.control1)
        assertEquals(oraclePointF64(matrixF64, Point2F64(11.0, 6.0)), cubicF64.control2)
        assertEquals(oraclePointF64(matrixF64, Point2F64(2.0, 3.0)), cubicF64.point)
    }

    @Test
    fun `tiny nonzero skew keeps a rotated SVG arc on its affine covariance`() {
        val matrixF64 = Matrix3x3F64(kxF64 = 1e-12)
        val sourceRadiusF64 = Vector2F64(250_000.0, 1_000_000.0)
        val rotationDegreesF64 = 0.00001
        val inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64.Origin),
                PathFillSegmentF64.ArcTo(
                    radius = sourceRadiusF64,
                    xAxisRotationDegreesF64 = rotationDegreesF64,
                    largeArc = false,
                    sweep = true,
                    point = Point2F64(1.0, 1.0),
                ),
            ),
        )

        val actualArcF64 = (matrixF64.mapAffinePathFillInputF64(inputF64, PathTransformWorkDebitI64 { })
            .segmentAtI32(1) as PathFillSegmentF64.ArcTo)
        val expectedCovarianceF64 = oracleTransformedCovarianceF64(matrixF64, sourceRadiusF64, rotationDegreesF64)
        val actualCovarianceF64 = ellipseCovarianceF64(
            actualArcF64.radius,
            actualArcF64.xAxisRotationDegreesF64,
        )

        assertCovarianceWithinDeviceToleranceF64(expectedCovarianceF64, actualCovarianceF64)
    }

    @Test
    fun `reflection and anisotropy map an arc endpoint with its reversed sweep`() {
        val matrixF64 = Matrix3x3F64(sxF64 = -2.0, syF64 = 3.0, txF64 = 5.0, tyF64 = -7.0)
        val sourcePointF64 = Point2F64(8.0, 3.0)
        val inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(1.0, 2.0)),
                PathFillSegmentF64.ArcTo(
                    radius = Vector2F64(4.0, 5.0),
                    xAxisRotationDegreesF64 = 15.0,
                    largeArc = true,
                    sweep = true,
                    point = sourcePointF64,
                ),
            ),
        )

        val mappedArcF64 = matrixF64.mapAffinePathFillInputF64(inputF64, PathTransformWorkDebitI64 { })
            .segmentAtI32(1) as PathFillSegmentF64.ArcTo

        assertEquals(oraclePointF64(matrixF64, sourcePointF64), mappedArcF64.point)
        assertTrue(!mappedArcF64.sweep)
        assertCovarianceWithinDeviceToleranceF64(
            oracleTransformedCovarianceF64(matrixF64, Vector2F64(4.0, 5.0), 15.0),
            ellipseCovarianceF64(mappedArcF64.radius, mappedArcF64.xAxisRotationDegreesF64),
        )
    }

    @Test
    fun `public stroke facade maps a finite outline through a 90 degree rotation`() {
        val result = Matrix3x3F32(
            sx = 0f,
            kx = -1f,
            ky = 1f,
            sy = 0f,
        ).preparePathStrokeGeometryF32(
            path = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build(),
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.Stroke,
        )

        val geometry = assertIs<PathStrokePreparationResult.Ready>(result).geometryF32
        assertEquals(-1f, geometry.copyConservativeBoundsF32().left)
        assertEquals(1f, geometry.copyConservativeBoundsF32().right)
        assertEquals(0f, geometry.copyConservativeBoundsF32().top)
        assertEquals(10f, geometry.copyConservativeBoundsF32().bottom)
    }

    @Test
    fun `public stroke facade expands a finite source outline before general affine projection`() {
        val result = Matrix3x3F32(sx = 4f, kx = 1f, ky = 1f, sy = 2f).preparePathStrokeGeometryF32(
            path = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).build(),
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.Stroke,
        )

        val boundsF32 = assertIs<PathStrokePreparationResult.Ready>(result).geometryF32.copyConservativeBoundsF32()
        assertEquals(-1f, boundsF32.left)
        assertEquals(41f, boundsF32.right)
        assertEquals(-2f, boundsF32.top)
        assertEquals(12f, boundsF32.bottom)
    }

    @Test
    fun `public fill facade maps a rotated SVG arc from the F64 affine seam`() {
        val matrixF32 = Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f)
        val source = PathBuilder().moveTo(1f, 2f).arcTo(4f, 5f, 15f, false, true, 8f, 3f).build()

        val mappedArcF64 = matrixF32.mapPathFillInputF64(source).segmentAtI32(1) as PathFillSegmentF64.ArcTo

        assertEquals(Point2F64(-3.0, 8.0), mappedArcF64.point)
        assertCovarianceWithinDeviceToleranceF64(
            oracleTransformedCovarianceF64(matrixF32.toMatrix3x3F64(), Vector2F64(4.0, 5.0), 15.0),
            ellipseCovarianceF64(mappedArcF64.radius, mappedArcF64.xAxisRotationDegreesF64),
        )
    }

    @Test
    fun `transform debit is observed before a finite input overflows during mapping`() {
        var debitBeforeFailureI64 = PathStrokeWorkUsageI64()
        val inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(PathFillSegmentF64.MoveTo(Point2F64(Double.MAX_VALUE, 0.0))),
        )

        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F64(sxF64 = 2.0).mapAffinePathFillInputF64(
                inputF64,
                PathTransformWorkDebitI64 { deltaI64 -> debitBeforeFailureI64 = addUsageForTestI64(debitBeforeFailureI64, deltaI64) },
            )
        }

        assertTrue(debitBeforeFailureI64.attemptedGeometryUnitCountI64 > 0L)
        assertTrue(debitBeforeFailureI64.snapshotByteCountI64 > 0L)
    }

    private fun finiteStyleF64(widthF64: Double): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private fun oraclePointF64(matrixF64: Matrix3x3F64, pointF64: Point2F64): Point2F64 = Point2F64(
        matrixF64.sxF64 * pointF64.x + matrixF64.kxF64 * pointF64.y + matrixF64.txF64,
        matrixF64.kyF64 * pointF64.x + matrixF64.syF64 * pointF64.y + matrixF64.tyF64,
    )

    private fun oracleTransformedCovarianceF64(
        matrixF64: Matrix3x3F64,
        radiusF64: Vector2F64,
        rotationDegreesF64: Double,
    ): EllipseCovarianceF64 {
        val angleF64 = rotationDegreesF64 * PI / 180.0
        val xAxisXF64 = cos(angleF64) * radiusF64.x
        val xAxisYF64 = sin(angleF64) * radiusF64.x
        val yAxisXF64 = -sin(angleF64) * radiusF64.y
        val yAxisYF64 = cos(angleF64) * radiusF64.y
        val mappedXAxisXF64 = matrixF64.sxF64 * xAxisXF64 + matrixF64.kxF64 * xAxisYF64
        val mappedXAxisYF64 = matrixF64.kyF64 * xAxisXF64 + matrixF64.syF64 * xAxisYF64
        val mappedYAxisXF64 = matrixF64.sxF64 * yAxisXF64 + matrixF64.kxF64 * yAxisYF64
        val mappedYAxisYF64 = matrixF64.kyF64 * yAxisXF64 + matrixF64.syF64 * yAxisYF64
        return covarianceFromAxesF64(mappedXAxisXF64, mappedXAxisYF64, mappedYAxisXF64, mappedYAxisYF64)
    }

    private fun ellipseCovarianceF64(
        radiusF64: Vector2F64,
        rotationDegreesF64: Double,
    ): EllipseCovarianceF64 {
        val angleF64 = rotationDegreesF64 * PI / 180.0
        return covarianceFromAxesF64(
            radiusF64.x * cos(angleF64),
            radiusF64.x * sin(angleF64),
            -radiusF64.y * sin(angleF64),
            radiusF64.y * cos(angleF64),
        )
    }

    private fun covarianceFromAxesF64(
        xAxisXF64: Double,
        xAxisYF64: Double,
        yAxisXF64: Double,
        yAxisYF64: Double,
    ): EllipseCovarianceF64 = EllipseCovarianceF64(
        xxF64 = xAxisXF64 * xAxisXF64 + yAxisXF64 * yAxisXF64,
        xyF64 = xAxisXF64 * xAxisYF64 + yAxisXF64 * yAxisYF64,
        yyF64 = xAxisYF64 * xAxisYF64 + yAxisYF64 * yAxisYF64,
    )

    private fun assertCovarianceWithinDeviceToleranceF64(
        expectedF64: EllipseCovarianceF64,
        actualF64: EllipseCovarianceF64,
    ) {
        val expectedDiagonalSupportF64 = sqrt((expectedF64.xxF64 + 2.0 * expectedF64.xyF64 + expectedF64.yyF64) / 2.0)
        val actualDiagonalSupportF64 = sqrt((actualF64.xxF64 + 2.0 * actualF64.xyF64 + actualF64.yyF64) / 2.0)
        assertTrue(abs(expectedDiagonalSupportF64 - actualDiagonalSupportF64) <= 0.25)
        assertTrue(abs(expectedF64.xxF64 - actualF64.xxF64) <= 1.0)
        assertTrue(abs(expectedF64.xyF64 - actualF64.xyF64) <= 1.0)
        assertTrue(abs(expectedF64.yyF64 - actualF64.yyF64) <= 1.0)
    }

    private fun addUsageForTestI64(
        firstI64: PathStrokeWorkUsageI64,
        secondI64: PathStrokeWorkUsageI64,
    ): PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(
        attemptedGeometryUnitCountI64 = firstI64.attemptedGeometryUnitCountI64 + secondI64.attemptedGeometryUnitCountI64,
        emittedVertexCountI64 = firstI64.emittedVertexCountI64 + secondI64.emittedVertexCountI64,
        emittedIndexCountI64 = firstI64.emittedIndexCountI64 + secondI64.emittedIndexCountI64,
        snapshotByteCountI64 = firstI64.snapshotByteCountI64 + secondI64.snapshotByteCountI64,
    )

    private data class EllipseCovarianceF64(
        val xxF64: Double,
        val xyF64: Double,
        val yyF64: Double,
    )
}
