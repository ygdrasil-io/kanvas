package org.graphiks.math.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
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
