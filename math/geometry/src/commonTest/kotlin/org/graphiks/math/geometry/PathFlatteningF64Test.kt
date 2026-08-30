package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathFlatteningF64Test {
    @Test
    fun `adaptive flattening is finite and stays within normalized chord tolerance`() {
        val policy = PathFlatteningPolicyF64()

        listOf(1e-5f, 1f, 1e9f).forEach { scale ->
            val path = curvedPathF32(scale)
            val normalization = pathNormalizationF64(listOf(path))
            val contours = PathFlattenerF64.flatten(NormalizedPathF64(path, normalization), policy)

            assertTrue(contours.flatMap { it.points }.all { it.point.x.isFinite() && it.point.y.isFinite() })
            assertTrue(maximumNormalizedChordErrorF64(contours.single(), normalization, scale) <= policy.tolerance)
        }
    }

    @Test
    fun `flattening preserves original endpoints bit for bit`() {
        val path = curvedPathF32(1f)
        val contours = PathFlattenerF64.flatten(NormalizedPathF64(path, pathNormalizationF64(listOf(path))))
        val points = contours.single().points
        val first = requireNotNull(points.first().originalPointF32)
        val last = requireNotNull(points.last().originalPointF32)

        assertEquals(0f.toRawBits(), first.x.toRawBits())
        assertEquals(0f.toRawBits(), first.y.toRawBits())
        assertEquals(0f.toRawBits(), last.x.toRawBits())
        assertEquals(0f.toRawBits(), last.y.toRawBits())
    }

    @Test
    fun `flattening reports deterministic edge and convergence limits`() {
        val path = curvedPathF32(1f)
        val normalized = NormalizedPathF64(path, pathNormalizationF64(listOf(path)))

        val edgeLimit = assertFailsWith<IllegalStateException> {
            PathFlattenerF64.flatten(
                normalized,
                PathFlatteningPolicyF64(limits = PathOpsLimitsI32(maxFlattenedEdgesPerOperand = 1)),
            )
        }
        val convergenceLimit = assertFailsWith<IllegalStateException> {
            PathFlattenerF64.flatten(
                normalized,
                PathFlatteningPolicyF64(limits = PathOpsLimitsI32(maxSubdivisionDepth = 1)),
            )
        }

        assertEquals("path-flattening-limit", edgeLimit.message)
        assertEquals("path-flattening-convergence", convergenceLimit.message)
    }

    @Test
    fun `flattening bounds collinear reentrant quadratic and cubic against their closed chords`() {
        val policy = PathFlatteningPolicyF64()
        val quadratic = PathBuilder().moveTo(0f, 0f).quadTo(2f, 0f, 1f, 0f).build()
        val cubic = PathBuilder().moveTo(0f, 0f).cubicTo(2f, 0f, 2f, 0f, 1f, 0f).build()

        val quadraticNormalization = pathNormalizationF64(listOf(quadratic))
        val quadraticContour = PathFlattenerF64.flatten(NormalizedPathF64(quadratic, quadraticNormalization), policy).single()
        val cubicNormalization = pathNormalizationF64(listOf(cubic))
        val cubicContour = PathFlattenerF64.flatten(NormalizedPathF64(cubic, cubicNormalization), policy).single()

        assertTrue(chordErrorAtF64(quadraticContour, quadraticNormalization, 2.0 / 3.0, ::reentrantQuadraticPointF64) <= policy.tolerance)
        assertTrue(chordErrorAtF64(cubicContour, cubicNormalization, 2.0 - sqrt(2.0), ::reentrantCubicPointF64) <= policy.tolerance)
    }

    @Test
    fun `arc center preserves SVG flags around a diameter and reduces large rotations`() {
        val insideDiameter = Double.fromBits(2.0.toRawBits() - 1L)
        val outsideDiameter = Double.fromBits(2.0.toRawBits() + 1L)

        listOf(1e-5, 1.0, 1e9).forEach { scale ->
            listOf(false, true).forEach { largeArc ->
                listOf(false, true).forEach { sweep ->
                    val center = requireNotNull(
                        arcCenterF64(ArcEndpointF64(Point2F64.Origin, Point2F64(insideDiameter * scale, 0.0), Vector2F64(scale, scale), 0.0, largeArc, sweep)),
                    )
                    assertEquals(sweep, center.sweepAngle > 0.0)
                    assertEquals(largeArc, abs(center.sweepAngle) > PI)
                    assertTrue(abs(center.center.y) > 0.0)
                }
            }
            val corrected = requireNotNull(
                arcCenterF64(ArcEndpointF64(Point2F64.Origin, Point2F64(outsideDiameter * scale, 0.0), Vector2F64(scale, scale), 0.0, false, true)),
            )
            assertTrue(corrected.radiusX > scale)
        }

        val base = requireNotNull(
            arcCenterF64(ArcEndpointF64(Point2F64.Origin, Point2F64(3.0, 0.0), Vector2F64(2.0, 1.0), 30.0, false, true)),
        )
        val rotated = requireNotNull(
            arcCenterF64(ArcEndpointF64(Point2F64.Origin, Point2F64(3.0, 0.0), Vector2F64(2.0, 1.0), 360_000_000_000_030.0, false, true)),
        )
        assertPointNearF64(base.pointAt(0.37), rotated.pointAt(0.37), 1e-12)
    }
}

private fun curvedPathF32(scale: Float): PathF32 = PathBuilder()
    .moveTo(0f, 0f)
    .cubicTo(0f, scale, scale, scale, scale, 0f)
    .arcTo(scale / 2f, scale / 2f, 0f, false, true, 0f, 0f)
    .build()

private fun maximumNormalizedChordErrorF64(
    contour: FlattenedContourF64,
    normalization: PathNormalizationF64,
    scale: Float,
): Double = contour.points.zipWithNext().maxOf { (first, second) ->
    if (first.sourceSegmentIndex != second.sourceSegmentIndex) 0.0 else {
        val sourcePoint = when (first.sourceSegmentIndex) {
            1 -> cubicPointF64((first.t + second.t) * 0.5, scale.toDouble())
            2 -> Point2F64(
                scale.toDouble() * (0.5 + 0.5 * kotlin.math.cos(PI * (first.t + second.t) * 0.5)),
                scale.toDouble() * 0.5 * kotlin.math.sin(PI * (first.t + second.t) * 0.5),
            )
            else -> error("Unexpected source segment")
        }
        pointToSegmentDistanceF64(normalization.normalize(sourcePoint.toPoint2F32()), first.point, second.point)
    }
}

private fun cubicPointF64(t: Double, scale: Double): Point2F64 {
    val u = 1.0 - t
    return Point2F64(
        scale * (3.0 * u * t * t + t * t * t),
        scale * (3.0 * u * u * t + 3.0 * u * t * t),
    )
}

private fun pointToSegmentDistanceF64(point: Point2F64, start: Point2F64, end: Point2F64): Double {
    val deltaX = end.x - start.x
    val deltaY = end.y - start.y
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    if (lengthSquared == 0.0) return stableHypotF64(point.x - start.x, point.y - start.y)
    val t = (((point.x - start.x) * deltaX + (point.y - start.y) * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
    return stableHypotF64(point.x - (start.x + deltaX * t), point.y - (start.y + deltaY * t))
}

private fun stableHypotF64(x: Double, y: Double): Double {
    val scale = max(abs(x), abs(y))
    return if (scale == 0.0) 0.0 else scale * sqrt((x / scale) * (x / scale) + (y / scale) * (y / scale))
}

private fun reentrantQuadraticPointF64(t: Double): Point2F64 = Point2F64(4.0 * t - 3.0 * t * t, 0.0)

private fun reentrantCubicPointF64(t: Double): Point2F64 = Point2F64(6.0 * t - 6.0 * t * t + t * t * t, 0.0)

private fun chordErrorAtF64(
    contour: FlattenedContourF64,
    normalization: PathNormalizationF64,
    t: Double,
    evaluate: (Double) -> Point2F64,
): Double {
    val (first, second) = contour.points.zipWithNext().first { (first, second) ->
        first.sourceSegmentIndex == 1 && second.sourceSegmentIndex == 1 && t in first.t..second.t
    }
    val point = evaluate(t)
    val normalizedPoint = Point2F64(
        (point.x - normalization.origin.x) * normalization.scale,
        (point.y - normalization.origin.y) * normalization.scale,
    )
    return pointToSegmentDistanceF64(normalizedPoint, first.point, second.point)
}

private fun assertPointNearF64(expected: Point2F64, actual: Point2F64, tolerance: Double) {
    assertTrue(abs(expected.x - actual.x) <= tolerance)
    assertTrue(abs(expected.y - actual.y) <= tolerance)
}
