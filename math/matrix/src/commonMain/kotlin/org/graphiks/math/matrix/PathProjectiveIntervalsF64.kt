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
    val cornersF64 = listOf(
        Point2F64(leftF64, topF64),
        Point2F64(leftF64, bottomF64),
        Point2F64(rightF64, topF64),
        Point2F64(rightF64, bottomF64),
    )
    val cornerIntervalsF64 = cornersF64.map { pointF64 ->
        dependencyAwareProjectiveCombinationF64(persp0F64, persp1F64, persp2F64, pointF64)
            ?: return PathProjectiveIntervalResultF64.NonFinite
    }
    return PathProjectiveIntervalResultF64.Ready(
        PathProjectiveIntervalF64(
            // W is affine over a box, so its extrema are precisely at the four corners.
            minimumF64 = cornerIntervalsF64.minOf { it.minimumF64 },
            maximumF64 = cornerIntervalsF64.maxOf { it.maximumF64 },
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

/**
 * Encloses a three-term dot product while retaining its cancellation dependency.  Each product
 * and sum is represented as a leading value plus a rounding residual; the final interval adds a
 * directed absolute-residual bound only once.  This differs materially from widening each term
 * before cancellation, which can manufacture a zero for a constant positive W.
 */
private fun dependencyAwareProjectiveCombinationF64(
    firstCoefficientF64: Double,
    secondCoefficientF64: Double,
    translationF64: Double,
    pointF64: Point2F64,
): PathProjectiveIntervalF64? {
    val expansionF64 = projectiveCompensatedCombinationF64(
        firstCoefficientF64,
        secondCoefficientF64,
        translationF64,
        pointF64,
    ) ?: return directedProjectiveCombinationF64(
        firstCoefficientF64,
        secondCoefficientF64,
        translationF64,
        pointF64.x,
        pointF64.y,
    ).let { it as? PathProjectiveIntervalResultF64.Ready }?.intervalF64
    val residualBoundF64 = projectiveUpwardAbsoluteSumF64(expansionF64.residualF64) ?: return null
    val minimumF64 = expansionF64.leadingF64 - residualBoundF64
    val maximumF64 = expansionF64.leadingF64 + residualBoundF64
    if (!minimumF64.isFinite() || !maximumF64.isFinite()) return null
    return PathProjectiveIntervalF64(nextDownProjectiveF64(minimumF64), nextUpProjectiveF64(maximumF64))
}

/** A leading W value plus its retained low-order cancellation contribution. */
internal data class ProjectiveCompensatedF64(
    val leadingF64: Double,
    val residualF64: Double,
)

/**
 * Computes `a*x + b*y + c` as a short expansion.  Fill keeps this representation through
 * de Casteljau subdivision so a tiny positive `c` cannot disappear from an initially cancelling
 * Bernstein control tuple.
 */
internal fun projectiveCompensatedCombinationF64(
    firstCoefficientF64: Double,
    secondCoefficientF64: Double,
    translationF64: Double,
    pointF64: Point2F64,
): ProjectiveCompensatedF64? {
    val firstProductF64 = projectiveTwoProductF64(firstCoefficientF64, pointF64.x) ?: return null
    val secondProductF64 = projectiveTwoProductF64(secondCoefficientF64, pointF64.y) ?: return null
    val productSumF64 = projectiveTwoSumF64(firstProductF64.leadingF64, secondProductF64.leadingF64) ?: return null
    val totalF64 = projectiveTwoSumF64(productSumF64.leadingF64, translationF64) ?: return null
    val firstResidualSumF64 = projectiveTwoSumF64(firstProductF64.residualF64, secondProductF64.residualF64)
        ?: return null
    val secondResidualSumF64 = projectiveTwoSumF64(firstResidualSumF64.leadingF64, productSumF64.residualF64)
        ?: return null
    val thirdResidualSumF64 = projectiveTwoSumF64(secondResidualSumF64.leadingF64, totalF64.residualF64)
        ?: return null
    val residualF64 = thirdResidualSumF64.leadingF64 + firstResidualSumF64.residualF64 +
        secondResidualSumF64.residualF64 + thirdResidualSumF64.residualF64
    return ProjectiveCompensatedF64(totalF64.leadingF64, residualF64)
        .takeIf { it.leadingF64.isFinite() && it.residualF64.isFinite() }
}

/** Preserves the low-order term while linearly interpolating a W expansion. */
internal fun interpolateProjectiveCompensatedF64(
    firstF64: ProjectiveCompensatedF64,
    secondF64: ProjectiveCompensatedF64,
    parameterF64: Double,
): ProjectiveCompensatedF64? {
    val inverseParameterF64 = 1.0 - parameterF64
    val firstLeadingF64 = projectiveTwoProductF64(firstF64.leadingF64, inverseParameterF64) ?: return null
    val secondLeadingF64 = projectiveTwoProductF64(secondF64.leadingF64, parameterF64) ?: return null
    val leadingSumF64 = projectiveTwoSumF64(firstLeadingF64.leadingF64, secondLeadingF64.leadingF64) ?: return null
    val firstResidualF64 = projectiveTwoProductF64(firstF64.residualF64, inverseParameterF64) ?: return null
    val secondResidualF64 = projectiveTwoProductF64(secondF64.residualF64, parameterF64) ?: return null
    val residualF64 = projectiveCompensatedSumF64(
        firstLeadingF64.residualF64,
        secondLeadingF64.residualF64,
        leadingSumF64.residualF64,
        firstResidualF64.leadingF64,
        firstResidualF64.residualF64,
        secondResidualF64.leadingF64,
        secondResidualF64.residualF64,
    ) ?: return null
    return ProjectiveCompensatedF64(leadingSumF64.leadingF64, residualF64)
        .takeIf { it.leadingF64.isFinite() && it.residualF64.isFinite() }
}

private fun projectiveCompensatedSumF64(vararg valuesF64: Double): Double? {
    var leadingF64 = 0.0
    var residualF64 = 0.0
    valuesF64.forEach { valueF64 ->
        val sumF64 = projectiveTwoSumF64(leadingF64, valueF64) ?: return null
        leadingF64 = sumF64.leadingF64
        residualF64 += sumF64.residualF64
        if (!residualF64.isFinite()) return null
    }
    return (leadingF64 + residualF64).takeIf(Double::isFinite)
}

private data class ProjectiveTwoTermF64(
    val leadingF64: Double,
    val residualF64: Double,
)

private fun projectiveTwoSumF64(firstF64: Double, secondF64: Double): ProjectiveTwoTermF64? {
    val sumF64 = firstF64 + secondF64
    if (!sumF64.isFinite()) return null
    val secondVirtualF64 = sumF64 - firstF64
    val firstVirtualF64 = sumF64 - secondVirtualF64
    val secondResidualF64 = secondF64 - secondVirtualF64
    val firstResidualF64 = firstF64 - firstVirtualF64
    val residualF64 = firstResidualF64 + secondResidualF64
    return ProjectiveTwoTermF64(sumF64, residualF64).takeIf { residualF64.isFinite() }
}

private fun projectiveTwoProductF64(firstF64: Double, secondF64: Double): ProjectiveTwoTermF64? {
    if (!firstF64.isFinite() || !secondF64.isFinite()) return null
    if (firstF64 == 0.0 || secondF64 == 0.0) return ProjectiveTwoTermF64(0.0, 0.0)
    val productF64 = firstF64 * secondF64
    // Dekker splitting must not overflow while forming splitter * operand.
    if (!productF64.isFinite() || abs(firstF64) > Double.MAX_VALUE / 134_217_729.0 ||
        abs(secondF64) > Double.MAX_VALUE / 134_217_729.0
    ) return null
    val firstSplitF64 = 134_217_729.0 * firstF64
    val firstHighF64 = firstSplitF64 - (firstSplitF64 - firstF64)
    val firstLowF64 = firstF64 - firstHighF64
    val secondSplitF64 = 134_217_729.0 * secondF64
    val secondHighF64 = secondSplitF64 - (secondSplitF64 - secondF64)
    val secondLowF64 = secondF64 - secondHighF64
    val residualF64 = ((firstHighF64 * secondHighF64 - productF64) + firstHighF64 * secondLowF64 +
        firstLowF64 * secondHighF64) + firstLowF64 * secondLowF64
    return ProjectiveTwoTermF64(productF64, residualF64).takeIf { residualF64.isFinite() }
}

private fun projectiveUpwardAbsoluteSumF64(vararg valuesF64: Double): Double? {
    var resultF64 = 0.0
    valuesF64.forEach { valueF64 ->
        if (!valueF64.isFinite()) return null
        resultF64 = nextUpProjectiveF64(resultF64 + abs(valueF64))
        if (!resultF64.isFinite()) return null
    }
    return resultF64
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
