package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.Point2F64

class PathFillTransformsF64Test {
    @Test
    fun `drawing verb before MoveTo starts at device origin`() {
        val source = PathBuilder().lineTo(2f, 1f).build()

        val mapped = Matrix3x3F32.translation(5f, 7f).mapPathFillInputF64(source)

        assertEquals(2, mapped.segmentCountI32)
        assertEquals(PathFillSegmentF64.MoveTo(Point2F64(5.0, 7.0)), mapped.segmentAtI32(0))
        assertEquals(PathFillSegmentF64.LineTo(Point2F64(7.0, 8.0)), mapped.segmentAtI32(1))
    }

    @Test
    fun `negative axis scale maps points and flips SVG arc sweep`() {
        val source = PathBuilder()
            .moveTo(1f, 2f)
            .arcTo(4f, 5f, 0f, largeArc = true, sweep = true, x = 2f, y = 1f)
            .build()

        val mapped = Matrix3x3F32.scaling(-2f, 3f).mapPathFillInputF64(source)
        val arc = mapped.segmentAtI32(1) as PathFillSegmentF64.ArcTo

        assertEquals(PathFillSegmentF64.MoveTo(Point2F64(-2.0, 6.0)), mapped.segmentAtI32(0))
        assertEquals(Point2F64(-4.0, 3.0), arc.point)
        assertEquals(8.0, arc.radius.x)
        assertEquals(15.0, arc.radius.y)
        assertEquals(180.0, arc.xAxisRotationDegreesF64)
        assertFalse(arc.sweep)
    }

    @Test
    fun `anisotropic scale preserves a rotated arc ellipse within the device tolerance`() {
        val radiusX = 250_000f
        val radiusY = 1_000_000f
        val rotationDegrees = 0.00001f
        val scaleX = 4f
        val scaleY = 1f
        val source = PathBuilder()
            .moveTo(0f, 0f)
            .arcTo(
                radiusX,
                radiusY,
                rotationDegrees,
                largeArc = false,
                sweep = true,
                x = 1f,
                y = 1f,
            )
            .build()

        val arc = Matrix3x3F32.scaling(scaleX, scaleY)
            .mapPathFillInputF64(source)
            .segmentAtI32(1) as PathFillSegmentF64.ArcTo

        val expected = transformedEllipseCovariance(
            radiusX = radiusX.toDouble(),
            radiusY = radiusY.toDouble(),
            rotationDegrees = rotationDegrees.toDouble(),
            scaleX = scaleX.toDouble(),
            scaleY = scaleY.toDouble(),
        )
        val actual = ellipseCovariance(
            radiusX = arc.radius.x,
            radiusY = arc.radius.y,
            rotationDegrees = arc.xAxisRotationDegreesF64,
        )

        val supportErrorPx = abs(diagonalSupport(expected) - diagonalSupport(actual))
        assertTrue(
            supportErrorPx <= 0.25,
            "transformed ellipse support differs by $supportErrorPx px, exceeding 0.25 px",
        )
        assertCovarianceClose(expected, actual, tolerance = 1.0)
    }

    @Test
    fun `F64 transform does not round an intermediate coordinate to F32`() {
        val coordinate = 0.1f
        val scale = 1.0000001f
        val translation = 0.1f
        val source = PathBuilder()
            .moveTo(coordinate, 0f)
            .arcTo(1f, 1f, 0f, largeArc = false, sweep = true, x = coordinate, y = 0f)
            .build()
        val matrix = Matrix3x3F32(sx = scale, sy = 1f, tx = translation)

        val actual = matrix.mapPathFillInputF64(source).segmentAtI32(0) as PathFillSegmentF64.MoveTo
        val expected = canonicalF32(scale) * canonicalF32(coordinate) + canonicalF32(translation)
        val roundedViaPathF32 = Float.fromBits(expected.toFloat().toRawBits()).toDouble()

        assertEquals(expected, actual.point.x)
        assertNotEquals(roundedViaPathF32, actual.point.x)
        val arc = matrix.mapPathFillInputF64(source).segmentAtI32(1) as PathFillSegmentF64.ArcTo
        assertEquals(expected, arc.point.x)
        assertNotEquals(roundedViaPathF32, arc.point.x)
    }

    @Test
    fun `non finite matrix or mapped coordinate is rejected`() {
        val finitePath = PathBuilder().moveTo(1f, 2f).build()
        val nonFiniteMatrices = listOf(
            Matrix3x3F32(sx = Float.NaN),
            Matrix3x3F32(kx = Float.POSITIVE_INFINITY),
            Matrix3x3F32(tx = Float.NEGATIVE_INFINITY),
            Matrix3x3F32(ky = Float.NaN),
            Matrix3x3F32(sy = Float.POSITIVE_INFINITY),
            Matrix3x3F32(ty = Float.NEGATIVE_INFINITY),
            Matrix3x3F32(persp0 = Float.NaN),
            Matrix3x3F32(persp1 = Float.POSITIVE_INFINITY),
            Matrix3x3F32(persp2 = Float.NEGATIVE_INFINITY),
        )

        nonFiniteMatrices.forEach { matrix ->
            assertFailsWith<IllegalArgumentException> { matrix.mapPathFillInputF64(finitePath) }
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { coordinate ->
            val nonFinitePath = PathBuilder().moveTo(coordinate, 0f).build()
            assertFailsWith<IllegalArgumentException> {
                Matrix3x3F32.Identity.mapPathFillInputF64(nonFinitePath)
            }
        }
    }

    @Test
    fun `runtime F32 matrix coefficients are canonicalized before validation and perspective classification`() {
        val emptyPath = PathBuilder().build()
        val runtimeTwo = listOf(2f).single()
        val runtimeHalf = listOf(0.5f).single()
        val overflowF32 = Float.MAX_VALUE * runtimeTwo
        val zeroF32 = Float.MIN_VALUE * runtimeHalf

        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32(sx = overflowF32).mapPathFillInputF64(emptyPath)
        }
        assertEquals(
            0,
            Matrix3x3F32(persp0 = zeroF32).mapPathFillInputF64(emptyPath).segmentCountI32,
        )
    }

    private fun canonicalF32(value: Float): Double = Float.fromBits(value.toRawBits()).toDouble()

    private fun transformedEllipseCovariance(
        radiusX: Double,
        radiusY: Double,
        rotationDegrees: Double,
        scaleX: Double,
        scaleY: Double,
    ): EllipseCovariance {
        val angle = rotationDegrees * PI / 180.0
        return covarianceFromAxes(
            xAxisX = scaleX * radiusX * cos(angle),
            xAxisY = scaleY * radiusX * sin(angle),
            yAxisX = -scaleX * radiusY * sin(angle),
            yAxisY = scaleY * radiusY * cos(angle),
        )
    }

    private fun ellipseCovariance(
        radiusX: Double,
        radiusY: Double,
        rotationDegrees: Double,
    ): EllipseCovariance {
        val angle = rotationDegrees * PI / 180.0
        return covarianceFromAxes(
            xAxisX = radiusX * cos(angle),
            xAxisY = radiusX * sin(angle),
            yAxisX = -radiusY * sin(angle),
            yAxisY = radiusY * cos(angle),
        )
    }

    private fun covarianceFromAxes(
        xAxisX: Double,
        xAxisY: Double,
        yAxisX: Double,
        yAxisY: Double,
    ): EllipseCovariance = EllipseCovariance(
        xx = xAxisX * xAxisX + yAxisX * yAxisX,
        xy = xAxisX * xAxisY + yAxisX * yAxisY,
        yy = xAxisY * xAxisY + yAxisY * yAxisY,
    )

    private fun diagonalSupport(covariance: EllipseCovariance): Double = sqrt(
        (covariance.xx + 2.0 * covariance.xy + covariance.yy) / 2.0,
    )

    private fun assertCovarianceClose(
        expected: EllipseCovariance,
        actual: EllipseCovariance,
        tolerance: Double,
    ) {
        assertTrue(abs(expected.xx - actual.xx) <= tolerance)
        assertTrue(abs(expected.xy - actual.xy) <= tolerance)
        assertTrue(abs(expected.yy - actual.yy) <= tolerance)
    }

    private data class EllipseCovariance(
        val xx: Double,
        val xy: Double,
        val yy: Double,
    )
}
