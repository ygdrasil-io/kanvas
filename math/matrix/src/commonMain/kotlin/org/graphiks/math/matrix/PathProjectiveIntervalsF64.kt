package org.graphiks.math.matrix

import kotlin.math.abs
import kotlin.math.max
import org.graphiks.math.geometry.Point2F64

/** Conservative F64 enclosure with outward-rounded finite endpoints. */
internal data class PathProjectiveIntervalF64(
    val minimumF64: Double,
    val maximumF64: Double,
) {
    init {
        require(minimumF64.isFinite() && maximumF64.isFinite())
        require(minimumF64 <= maximumF64)
    }

    fun containsZeroF64(): Boolean = minimumF64 <= 0.0 && maximumF64 >= 0.0

    fun minimumAbsoluteValueF64(): Double = when {
        containsZeroF64() -> 0.0
        minimumF64 > 0.0 -> minimumF64
        else -> -maximumF64
    }

    fun maximumAbsoluteValueF64(): Double = max(abs(minimumF64), abs(maximumF64))
}

internal sealed interface PathProjectiveIntervalResultF64 {
    data class Ready(val intervalF64: PathProjectiveIntervalF64) : PathProjectiveIntervalResultF64

    data object NonFinite : PathProjectiveIntervalResultF64
}

internal fun Matrix3x3F64.projectiveWIntervalForPointF64(pointF64: Point2F64): PathProjectiveIntervalResultF64 {
    if (!pointF64.isFinite()) return PathProjectiveIntervalResultF64.NonFinite
    return directedProjectiveCombinationF64(persp0F64, persp1F64, persp2F64, pointF64.x, pointF64.y)
}

internal fun Matrix3x3F64.projectiveWIntervalForBoundsF64(
    leftF64: Double,
    topF64: Double,
    rightF64: Double,
    bottomF64: Double,
): PathProjectiveIntervalResultF64 {
    if (!leftF64.isFinite() || !topF64.isFinite() || !rightF64.isFinite() || !bottomF64.isFinite() ||
        leftF64 > rightF64 || topF64 > bottomF64
    ) {
        return PathProjectiveIntervalResultF64.NonFinite
    }
    val valuesF64 = listOf(
        directedProjectiveCombinationF64(persp0F64, persp1F64, persp2F64, leftF64, topF64),
        directedProjectiveCombinationF64(persp0F64, persp1F64, persp2F64, leftF64, bottomF64),
        directedProjectiveCombinationF64(persp0F64, persp1F64, persp2F64, rightF64, topF64),
        directedProjectiveCombinationF64(persp0F64, persp1F64, persp2F64, rightF64, bottomF64),
    )
    if (valuesF64.any { it !is PathProjectiveIntervalResultF64.Ready }) {
        return PathProjectiveIntervalResultF64.NonFinite
    }
    val readyF64 = valuesF64.filterIsInstance<PathProjectiveIntervalResultF64.Ready>()
    return PathProjectiveIntervalResultF64.Ready(
        PathProjectiveIntervalF64(
            minimumF64 = readyF64.minOf { it.intervalF64.minimumF64 },
            maximumF64 = readyF64.maxOf { it.intervalF64.maximumF64 },
        ),
    )
}

internal fun Matrix3x3F64.projectiveXYIntervalForBoundsF64(
    leftF64: Double,
    topF64: Double,
    rightF64: Double,
    bottomF64: Double,
): PathProjectiveXYIntervalsF64? {
    val xIntervalF64 = projectiveLinearBoundsF64(sxF64, kxF64, txF64, leftF64, topF64, rightF64, bottomF64)
        ?: return null
    val yIntervalF64 = projectiveLinearBoundsF64(kyF64, syF64, tyF64, leftF64, topF64, rightF64, bottomF64)
        ?: return null
    return PathProjectiveXYIntervalsF64(xIntervalF64, yIntervalF64)
}

internal data class PathProjectiveXYIntervalsF64(
    val xIntervalF64: PathProjectiveIntervalF64,
    val yIntervalF64: PathProjectiveIntervalF64,
)

private fun projectiveLinearBoundsF64(
    xCoefficientF64: Double,
    yCoefficientF64: Double,
    translationF64: Double,
    leftF64: Double,
    topF64: Double,
    rightF64: Double,
    bottomF64: Double,
): PathProjectiveIntervalF64? {
    val valuesF64 = listOf(
        directedProjectiveCombinationF64(xCoefficientF64, yCoefficientF64, translationF64, leftF64, topF64),
        directedProjectiveCombinationF64(xCoefficientF64, yCoefficientF64, translationF64, leftF64, bottomF64),
        directedProjectiveCombinationF64(xCoefficientF64, yCoefficientF64, translationF64, rightF64, topF64),
        directedProjectiveCombinationF64(xCoefficientF64, yCoefficientF64, translationF64, rightF64, bottomF64),
    )
    if (valuesF64.any { it !is PathProjectiveIntervalResultF64.Ready }) return null
    val readyF64 = valuesF64.filterIsInstance<PathProjectiveIntervalResultF64.Ready>()
    return PathProjectiveIntervalF64(
        minimumF64 = readyF64.minOf { it.intervalF64.minimumF64 },
        maximumF64 = readyF64.maxOf { it.intervalF64.maximumF64 },
    )
}

private fun directedProjectiveCombinationF64(
    firstCoefficientF64: Double,
    secondCoefficientF64: Double,
    translationF64: Double,
    xF64: Double,
    yF64: Double,
): PathProjectiveIntervalResultF64 {
    val firstProductF64 = outwardProjectiveMultiplyF64(firstCoefficientF64, xF64)
        ?: return PathProjectiveIntervalResultF64.NonFinite
    val secondProductF64 = outwardProjectiveMultiplyF64(secondCoefficientF64, yF64)
        ?: return PathProjectiveIntervalResultF64.NonFinite
    val partialSumF64 = outwardProjectiveAddF64(firstProductF64, secondProductF64)
        ?: return PathProjectiveIntervalResultF64.NonFinite
    val resultF64 = outwardProjectiveAddF64(
        partialSumF64,
        PathProjectiveIntervalF64(translationF64, translationF64),
    ) ?: return PathProjectiveIntervalResultF64.NonFinite
    return PathProjectiveIntervalResultF64.Ready(resultF64)
}

private fun outwardProjectiveMultiplyF64(firstF64: Double, secondF64: Double): PathProjectiveIntervalF64? {
    if (firstF64 == 0.0 || secondF64 == 0.0) return PathProjectiveIntervalF64(0.0, 0.0)
    val productF64 = firstF64 * secondF64
    if (!productF64.isFinite()) return null
    return PathProjectiveIntervalF64(nextDownProjectiveF64(productF64), nextUpProjectiveF64(productF64))
}

private fun outwardProjectiveAddF64(
    firstF64: PathProjectiveIntervalF64,
    secondF64: PathProjectiveIntervalF64,
): PathProjectiveIntervalF64? {
    if (secondF64.minimumF64 == 0.0 && secondF64.maximumF64 == 0.0) return firstF64
    if (firstF64.minimumF64 == 0.0 && firstF64.maximumF64 == 0.0) return secondF64
    val minimumF64 = firstF64.minimumF64 + secondF64.minimumF64
    val maximumF64 = firstF64.maximumF64 + secondF64.maximumF64
    if (!minimumF64.isFinite() || !maximumF64.isFinite()) return null
    return PathProjectiveIntervalF64(nextDownProjectiveF64(minimumF64), nextUpProjectiveF64(maximumF64))
}

internal fun projectiveHullIntervalF64(valuesF64: Collection<PathProjectiveIntervalF64>): PathProjectiveIntervalF64? {
    if (valuesF64.isEmpty()) return null
    return PathProjectiveIntervalF64(
        minimumF64 = valuesF64.minOf { it.minimumF64 },
        maximumF64 = valuesF64.maxOf { it.maximumF64 },
    )
}

internal fun nextDownProjectiveF64(valueF64: Double): Double = when {
    valueF64.isNaN() || valueF64 == Double.NEGATIVE_INFINITY -> valueF64
    valueF64 == 0.0 -> -Double.MIN_VALUE
    valueF64 > 0.0 -> Double.fromBits(valueF64.toBits() - 1L)
    else -> Double.fromBits(valueF64.toBits() + 1L)
}

internal fun nextUpProjectiveF64(valueF64: Double): Double = when {
    valueF64.isNaN() || valueF64 == Double.POSITIVE_INFINITY -> valueF64
    valueF64 == 0.0 -> Double.MIN_VALUE
    valueF64 > 0.0 -> Double.fromBits(valueF64.toBits() + 1L)
    else -> Double.fromBits(valueF64.toBits() - 1L)
}
