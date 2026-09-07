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
    val residualBoundF64 = projectiveUpwardAbsoluteSumF64(
        expansionF64.residualF64,
        expansionF64.uncertaintyF64,
    ) ?: return null
    val minimumF64 = expansionF64.leadingF64 - residualBoundF64
    val maximumF64 = expansionF64.leadingF64 + residualBoundF64
    if (!minimumF64.isFinite() || !maximumF64.isFinite()) return null
    return PathProjectiveIntervalF64(nextDownProjectiveF64(minimumF64), nextUpProjectiveF64(maximumF64))
}

/** A leading W value plus its retained low-order cancellation contribution. */
internal data class ProjectiveCompensatedF64(
    val leadingF64: Double,
    val residualF64: Double,
    /** Directed absolute enclosure for terms below the retained double-double limbs. */
    val uncertaintyF64: Double = 0.0,
)

/**
 * The two fields form a normalized double-double expansion: `leadingF64 + residualF64`, with the
 * residual too small to reverse the leading term.  Do not first collapse it to one [Double] when
 * deciding a denominator's sign: that reintroduces precisely the cancellation this expansion
 * records.
 */
internal fun projectiveCompensatedSignF64(valueF64: ProjectiveCompensatedF64): Int = when {
    !valueF64.leadingF64.isFinite() || !valueF64.residualF64.isFinite() || !valueF64.uncertaintyF64.isFinite() -> 0
    valueF64.uncertaintyF64 == 0.0 && valueF64.leadingF64 > 0.0 -> 1
    valueF64.uncertaintyF64 == 0.0 && valueF64.leadingF64 < 0.0 -> -1
    valueF64.uncertaintyF64 == 0.0 && valueF64.residualF64 > 0.0 -> 1
    valueF64.uncertaintyF64 == 0.0 && valueF64.residualF64 < 0.0 -> -1
    else -> projectiveCompensatedEnclosureSignF64(valueF64)
}

internal fun projectiveCompensatedValueF64(valueF64: ProjectiveCompensatedF64): Double =
    valueF64.leadingF64 + valueF64.residualF64

/** Outward enclosure of a retained double-double value and its unrepresented tail. */
internal fun projectiveCompensatedIntervalF64(valueF64: ProjectiveCompensatedF64): PathProjectiveIntervalF64? {
    if (!valueF64.leadingF64.isFinite() || !valueF64.residualF64.isFinite() || !valueF64.uncertaintyF64.isFinite()) {
        return null
    }
    val estimateF64 = projectiveCompensatedValueF64(valueF64)
    if (!estimateF64.isFinite()) return null
    val lowerF64 = nextDownProjectiveF64(nextDownProjectiveF64(estimateF64) - valueF64.uncertaintyF64)
    val upperF64 = nextUpProjectiveF64(nextUpProjectiveF64(estimateF64) + valueF64.uncertaintyF64)
    if (!lowerF64.isFinite() || !upperF64.isFinite()) return null
    return PathProjectiveIntervalF64(lowerF64, upperF64)
}

/** Decides an uncertain sign from a directed enclosure, never from a nearest-rounded estimate. */
private fun projectiveCompensatedEnclosureSignF64(valueF64: ProjectiveCompensatedF64): Int {
    if (valueF64.leadingF64 == 0.0 && valueF64.residualF64 == 0.0 && valueF64.uncertaintyF64 == 0.0) return 0
    val intervalF64 = projectiveCompensatedIntervalF64(valueF64) ?: return 0
    return when {
        intervalF64.minimumF64 > 0.0 -> 1
        intervalF64.maximumF64 < 0.0 -> -1
        else -> 0
    }
}

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
    val firstProductF64 = projectiveCompensatedProductF64(firstCoefficientF64, pointF64.x) ?: return null
    val secondProductF64 = projectiveCompensatedProductF64(secondCoefficientF64, pointF64.y) ?: return null
    return projectiveCompensatedAddF64(
        projectiveCompensatedAddF64(firstProductF64, secondProductF64) ?: return null,
        ProjectiveCompensatedF64(translationF64, 0.0),
    )
}

/** Computes `a*x + b*y + c*w` while retaining the denominator as a normalized double-double. */
internal fun projectiveCompensatedHomogeneousCombinationF64(
    firstCoefficientF64: Double,
    firstValueF64: Double,
    secondCoefficientF64: Double,
    secondValueF64: Double,
    thirdCoefficientF64: Double,
    thirdValueF64: Double,
): ProjectiveCompensatedF64? {
    val firstProductF64 = projectiveCompensatedProductF64(firstCoefficientF64, firstValueF64) ?: return null
    val secondProductF64 = projectiveCompensatedProductF64(secondCoefficientF64, secondValueF64) ?: return null
    val thirdProductF64 = projectiveCompensatedProductF64(thirdCoefficientF64, thirdValueF64) ?: return null
    return projectiveCompensatedAddF64(
        projectiveCompensatedAddF64(firstProductF64, secondProductF64) ?: return null,
        thirdProductF64,
    )
}

/**
 * Certifies a real root of a quadratic Bernstein denominator without depending on a dyadic split
 * point.  It intentionally proves only the easy, decisive case: equal nonzero endpoint signs,
 * an interior extremum on the opposite side, and a nonnegative discriminant enclosure.  The
 * caller still subdivides if this certificate cannot decide.
 */
internal fun projectiveQuadraticRootCertificateF64(controlsF64: List<ProjectiveCompensatedF64>): Boolean {
    if (controlsF64.size != 3 || controlsF64.any { it.uncertaintyF64 != 0.0 }) return false
    val endpointSignI32 = projectiveCompensatedSignF64(controlsF64.first())
    if (endpointSignI32 == 0 || endpointSignI32 != projectiveCompensatedSignF64(controlsF64.last())) return false
    val normalizedControlsF64 = if (endpointSignI32 > 0) controlsF64 else controlsF64.map(::projectiveCompensatedNegateF64)
    if (projectiveCompensatedSignF64(normalizedControlsF64[1]) >= 0) return false

    // B(t) = a + b*t + c*t² for quadratic Bernstein weights (a, middle, end).
    val aF64 = normalizedControlsF64[0]
    val bF64 = projectiveCompensatedAddF64(
        projectiveCompensatedScaleF64(normalizedControlsF64[1], 2.0) ?: return false,
        projectiveCompensatedScaleF64(aF64, -2.0) ?: return false,
    ) ?: return false
    val cF64 = projectiveCompensatedAddF64(
        projectiveCompensatedAddF64(
            aF64,
            projectiveCompensatedScaleF64(normalizedControlsF64[1], -2.0) ?: return false,
        ) ?: return false,
        normalizedControlsF64[2],
    ) ?: return false
    if (projectiveCompensatedSignF64(cF64) <= 0) return false

    val minusBF64 = projectiveCompensatedScaleF64(bF64, -1.0) ?: return false
    val twiceCF64 = projectiveCompensatedScaleF64(cF64, 2.0) ?: return false
    // The minimizer -b/(2c) lies strictly inside (0, 1).
    if (projectiveCompensatedSignF64(minusBF64) <= 0 ||
        projectiveCompensatedSignF64(projectiveCompensatedAddF64(minusBF64, projectiveCompensatedNegateF64(twiceCF64))
            ?: return false) >= 0
    ) return false

    val discriminantF64 = projectiveCompensatedAddF64(
        projectiveCompensatedMultiplyF64(bF64, bF64) ?: return false,
        projectiveCompensatedScaleF64(projectiveCompensatedMultiplyF64(aF64, cF64) ?: return false, -4.0)
            ?: return false,
    ) ?: return false
    return projectiveCompensatedSignF64(discriminantF64) > 0 ||
        (discriminantF64.leadingF64 == 0.0 && discriminantF64.residualF64 == 0.0 &&
            discriminantF64.uncertaintyF64 == 0.0)
}

/** Preserves the low-order term while linearly interpolating a W expansion. */
internal fun interpolateProjectiveCompensatedF64(
    firstF64: ProjectiveCompensatedF64,
    secondF64: ProjectiveCompensatedF64,
    parameterF64: Double,
): ProjectiveCompensatedF64? {
    val inverseParameterF64 = 1.0 - parameterF64
    val firstScaledF64 = projectiveCompensatedScaleF64(firstF64, inverseParameterF64) ?: return null
    val secondScaledF64 = projectiveCompensatedScaleF64(secondF64, parameterF64) ?: return null
    return projectiveCompensatedAddF64(firstScaledF64, secondScaledF64)
}

/** Error-free product followed by normalization to a non-overlapping double-double. */
private fun projectiveCompensatedProductF64(firstF64: Double, secondF64: Double): ProjectiveCompensatedF64? =
    when {
        !firstF64.isFinite() || !secondF64.isFinite() -> null
        firstF64 == 0.0 || secondF64 == 0.0 -> ProjectiveCompensatedF64(0.0, 0.0)
        abs(firstF64 * secondF64) <= PROJECTIVE_MIN_NORMAL_F64 -> {
            // Dekker residuals below the subnormal floor are not representable.  Keep an outward
            // tail instead of fabricating a sign for MIN*.5 + MIN*.5 - MIN.
            ProjectiveCompensatedF64(firstF64 * secondF64, 0.0, Double.MIN_VALUE)
        }
        else -> projectiveTwoProductF64(firstF64, secondF64)
            ?.let { ProjectiveCompensatedF64(it.leadingF64, it.residualF64) }
    }

/** Multiplies both expansion limbs and retains the cross-limb product before normalization. */
private fun projectiveCompensatedScaleF64(
    valueF64: ProjectiveCompensatedF64,
    factorF64: Double,
): ProjectiveCompensatedF64? {
    val leadingProductF64 = projectiveCompensatedProductF64(valueF64.leadingF64, factorF64) ?: return null
    val residualProductF64 = projectiveCompensatedProductF64(valueF64.residualF64, factorF64) ?: return null
    val scaledF64 = projectiveCompensatedAddF64(leadingProductF64, residualProductF64) ?: return null
    val inheritedUncertaintyF64 = projectiveUpwardProductF64(valueF64.uncertaintyF64, abs(factorF64)) ?: return null
    return scaledF64.copy(
        uncertaintyF64 = projectiveUpwardAbsoluteSumF64(scaledF64.uncertaintyF64, inheritedUncertaintyF64)
            ?: return null,
    )
}

private fun projectiveCompensatedNegateF64(valueF64: ProjectiveCompensatedF64): ProjectiveCompensatedF64 =
    ProjectiveCompensatedF64(-valueF64.leadingF64, -valueF64.residualF64, valueF64.uncertaintyF64)

/** Multiplies two short expansions and carries all products through the same directed tail. */
private fun projectiveCompensatedMultiplyF64(
    firstF64: ProjectiveCompensatedF64,
    secondF64: ProjectiveCompensatedF64,
): ProjectiveCompensatedF64? {
    val productsF64 = listOf(
        projectiveCompensatedProductF64(firstF64.leadingF64, secondF64.leadingF64),
        projectiveCompensatedProductF64(firstF64.leadingF64, secondF64.residualF64),
        projectiveCompensatedProductF64(firstF64.residualF64, secondF64.leadingF64),
        projectiveCompensatedProductF64(firstF64.residualF64, secondF64.residualF64),
    )
    if (productsF64.any { it == null }) return null
    val combinedF64 = productsF64.filterNotNull().fold(ProjectiveCompensatedF64(0.0, 0.0)) { resultF64, productF64 ->
        projectiveCompensatedAddF64(resultF64, productF64) ?: return null
    }
    val firstMagnitudeF64 = projectiveCompensatedMagnitudeUpperF64(firstF64) ?: return null
    val secondMagnitudeF64 = projectiveCompensatedMagnitudeUpperF64(secondF64) ?: return null
    val inheritedUncertaintyF64 = projectiveUpwardAbsoluteSumF64(
        projectiveUpwardProductF64(firstMagnitudeF64, secondF64.uncertaintyF64) ?: return null,
        projectiveUpwardProductF64(secondMagnitudeF64, firstF64.uncertaintyF64) ?: return null,
        projectiveUpwardProductF64(firstF64.uncertaintyF64, secondF64.uncertaintyF64) ?: return null,
    ) ?: return null
    return combinedF64.copy(
        uncertaintyF64 = projectiveUpwardAbsoluteSumF64(combinedF64.uncertaintyF64, inheritedUncertaintyF64)
            ?: return null,
    )
}

private fun projectiveCompensatedMagnitudeUpperF64(valueF64: ProjectiveCompensatedF64): Double? =
    projectiveUpwardAbsoluteSumF64(valueF64.leadingF64, valueF64.residualF64, valueF64.uncertaintyF64)

/**
 * Adds two normalized double-doubles using only error-free transforms before the final
 * normalization.  In particular this does not accumulate residuals with ordinary `+`, which
 * could discard a small positive denominator after cancellation.
 */
private fun projectiveCompensatedAddF64(
    firstF64: ProjectiveCompensatedF64,
    secondF64: ProjectiveCompensatedF64,
): ProjectiveCompensatedF64? {
    val leadingSumF64 = projectiveTwoSumF64(firstF64.leadingF64, secondF64.leadingF64) ?: return null
    val residualSumF64 = projectiveTwoSumF64(firstF64.residualF64, secondF64.residualF64) ?: return null
    val middleSumF64 = projectiveTwoSumF64(leadingSumF64.residualF64, residualSumF64.leadingF64) ?: return null
    val normalizedLeadingF64 = projectiveTwoSumF64(leadingSumF64.leadingF64, middleSumF64.leadingF64) ?: return null
    val normalizedF64 = projectiveCompensatedNormalizeF64(
        normalizedLeadingF64.leadingF64,
        normalizedLeadingF64.residualF64,
        middleSumF64.residualF64,
        residualSumF64.residualF64,
    ) ?: return null
    return normalizedF64.copy(
        uncertaintyF64 = projectiveUpwardAbsoluteSumF64(
            normalizedF64.uncertaintyF64,
            firstF64.uncertaintyF64,
            secondF64.uncertaintyF64,
        ) ?: return null,
    )
}

/** Compresses the remaining low-order expansion terms and encloses any third limb outward. */
private fun projectiveCompensatedNormalizeF64(
    leadingF64: Double,
    firstResidualF64: Double,
    secondResidualF64: Double,
    thirdResidualF64: Double,
): ProjectiveCompensatedF64? {
    val firstSumF64 = projectiveTwoSumF64(firstResidualF64, secondResidualF64) ?: return null
    val secondSumF64 = projectiveTwoSumF64(firstSumF64.leadingF64, thirdResidualF64) ?: return null
    val lowSumF64 = projectiveTwoSumF64(firstSumF64.residualF64, secondSumF64.residualF64) ?: return null
    val leadingWithLowF64 = projectiveTwoSumF64(leadingF64, secondSumF64.leadingF64) ?: return null
    val finalF64 = projectiveTwoSumF64(leadingWithLowF64.residualF64, lowSumF64.leadingF64) ?: return null
    val resultF64 = projectiveTwoSumF64(leadingWithLowF64.leadingF64, finalF64.leadingF64) ?: return null
    val tailF64 = projectiveTwoSumF64(finalF64.residualF64, lowSumF64.residualF64) ?: return null
    val normalizedF64 = projectiveTwoSumF64(resultF64.residualF64, tailF64.leadingF64) ?: return null
    val resultWithLowF64 = projectiveTwoSumF64(resultF64.leadingF64, normalizedF64.leadingF64) ?: return null
    return ProjectiveCompensatedF64(
        leadingF64 = resultWithLowF64.leadingF64,
        residualF64 = resultWithLowF64.residualF64,
        uncertaintyF64 = projectiveUpwardAbsoluteSumF64(normalizedF64.residualF64, tailF64.residualF64)
            ?: return null,
    ).takeIf { it.leadingF64.isFinite() && it.residualF64.isFinite() && it.uncertaintyF64.isFinite() }
}

private fun projectiveUpwardProductF64(firstF64: Double, secondF64: Double): Double? {
    if (!firstF64.isFinite() || !secondF64.isFinite()) return null
    if (firstF64 == 0.0 || secondF64 == 0.0) return 0.0
    val productF64 = firstF64 * secondF64
    if (!productF64.isFinite()) return null
    return if (productF64 == 0.0) Double.MIN_VALUE else nextUpProjectiveF64(productF64)
}

private val PROJECTIVE_MIN_NORMAL_F64: Double = Double.fromBits(0x0010_0000_0000_0000L)

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
        if (valueF64 == 0.0) return@forEach
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
