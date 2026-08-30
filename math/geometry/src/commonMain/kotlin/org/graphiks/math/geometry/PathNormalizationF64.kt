package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector2F64
import kotlin.math.max
import kotlin.math.min

internal data class PathNormalizationF64(
    val origin: Point2F64,
    val scale: Double,
) {
    fun normalize(point: Point2F32): Point2F64 = Point2F64(
        (point.x.toDouble() - origin.x) * scale,
        (point.y.toDouble() - origin.y) * scale,
    )

    fun normalizeVector(vector: Vector2F32): Vector2F64 = Vector2F64(
        vector.x.toDouble() * scale,
        vector.y.toDouble() * scale,
    )

    fun denormalize(point: Point2F64): Point2F32 = Point2F32(
        roundedNormalizedCoordinateF32(point.x / scale + origin.x),
        roundedNormalizedCoordinateF32(point.y / scale + origin.y),
    )
}

// Kotlin/JS represents `Float` values with JavaScript numbers at some call boundaries. Rebuild
// through the raw IEEE-754 payload so the normalization boundary has the same F32 lattice as JVM.
private fun roundedNormalizedCoordinateF32(value: Double): Float = Float.fromBits(value.toFloat().toRawBits())

internal fun pathNormalizationF64(paths: List<PathF32>): PathNormalizationF64 {
    var left = Double.POSITIVE_INFINITY
    var top = Double.POSITIVE_INFINITY
    var right = Double.NEGATIVE_INFINITY
    var bottom = Double.NEGATIVE_INFINITY

    paths.forEach { path ->
        val bounds = PathAnalysisF32.bounds(path) ?: return@forEach
        left = min(left, bounds.left.toDouble())
        top = min(top, bounds.top.toDouble())
        right = max(right, bounds.right.toDouble())
        bottom = max(bottom, bounds.bottom.toDouble())
    }

    if (!left.isFinite()) return PathNormalizationF64(Point2F64.Origin, 1.0)

    val width = right - left
    val height = bottom - top
    val extent = max(width, height)
    return PathNormalizationF64(
        origin = Point2F64((left + right) * 0.5, (top + bottom) * 0.5),
        scale = if (extent > 0.0) 1.0 / extent else 1.0,
    )
}

internal data class NormalizedPathF64(
    val path: PathF32,
    val normalization: PathNormalizationF64,
)
