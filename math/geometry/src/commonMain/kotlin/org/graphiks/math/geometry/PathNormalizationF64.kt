package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector2F64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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
private fun roundedNormalizedCoordinateF32(value: Double): Float {
    val maximumF32AsF64 = Float.MAX_VALUE.toDouble()
    if (!value.isFinite() || value < -maximumF32AsF64 || value > maximumF32AsF64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val roundedF32 = Float.fromBits(value.toFloat().toRawBits())
    if (!roundedF32.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
    return roundedF32
}

/**
 * The source flattener must not resolve a carrier more finely than the F32 lattice that will
 * embed it.  Otherwise two F64-only micro sections can round to a spurious F32 endpoint contact
 * away from their exact witness.  The bound comes solely from the maximum observable F32 spacing
 * over this normalization rectangle: `2 * ulpF32(worldBound) * scale`.
 *
 * The lower bound keeps the established identity/small-scale precision.  The upper bound limits
 * the approximation even when a finite F32 input is so far from the origin that its lattice is
 * coarse; a material projected collapse is then still rejected by the hybrid guard.
 */
internal fun PathNormalizationF64.projectionLatticeFlatteningToleranceF64(): Double {
    if (!origin.x.isFinite() || !origin.y.isFinite() || !scale.isFinite() || scale <= 0.0) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val halfExtentF64 = 0.5 / scale
    if (!halfExtentF64.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
    val maximumWorldMagnitudeF64 = max(
        abs(origin.x) + halfExtentF64,
        abs(origin.y) + halfExtentF64,
    )
    val latticeStepF64 = f32LatticeStepAtMagnitudeF64(maximumWorldMagnitudeF64)
    return (latticeStepF64 * scale * 2.0)
        .coerceAtLeast(2.0.pow(-23))
        .coerceAtMost(2.0.pow(-12))
}

private fun f32LatticeStepAtMagnitudeF64(magnitudeF64: Double): Double {
    if (!magnitudeF64.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
    // The spacing is an observable F32-domain property.  A finite normalization envelope can
    // exceed Float.MAX_VALUE while adding origin and half-extent in F64; clamp only this lookup
    // to the outer finite lattice cell, never an emitted coordinate.
    val boundedMagnitudeF64 = magnitudeF64.coerceIn(0.0, Float.MAX_VALUE.toDouble())
    val roundedF32 = Float.fromBits(boundedMagnitudeF64.toFloat().toRawBits())
    val roundedBitsI32 = roundedF32.toRawBits()
    val adjacentF32 = if (roundedBitsI32 == Float.MAX_VALUE.toRawBits()) {
        Float.fromBits(roundedBitsI32 - 1)
    } else {
        Float.fromBits(roundedBitsI32 + 1)
    }
    return abs(adjacentF32.toDouble() - roundedF32.toDouble())
}

internal fun pathNormalizationF64(paths: List<PathF32>): PathNormalizationF64 {
    var left = Double.POSITIVE_INFINITY
    var top = Double.POSITIVE_INFINITY
    var right = Double.NEGATIVE_INFINITY
    var bottom = Double.NEGATIVE_INFINITY

    paths.forEach { path ->
        val bounds = PathAnalysisF32.bounds(path) ?: return@forEach
        // Kotlin/JS keeps Float-shaped intermediate bounds as JS numbers. Restore the public F32
        // payload before it contributes to a shared F64 normalization envelope, otherwise a
        // sub-ULP analytic extremum can select a backend-specific scale and subdivision depth.
        left = min(left, canonicalInputCoordinateF64(bounds.left))
        top = min(top, canonicalInputCoordinateF64(bounds.top))
        right = max(right, canonicalInputCoordinateF64(bounds.right))
        bottom = max(bottom, canonicalInputCoordinateF64(bounds.bottom))
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

/** Reconstructs the logical F32 payload at a Kotlin/JS-to-F64 boundary. */
private fun canonicalInputCoordinateF64(valueF32: Float): Double = Float.fromBits(valueF32.toRawBits()).toDouble()

internal data class NormalizedPathF64(
    val path: PathF32,
    val normalization: PathNormalizationF64,
)
