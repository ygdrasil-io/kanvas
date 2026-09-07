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
    return projectiveCompensatedIntervalF64(expansionF64)
}

/** A leading W value plus its retained low-order cancellation contribution. */
internal data class ProjectiveCompensatedF64(
    val leadingF64: Double,
    val residualF64: Double,
    /** Symmetric compatibility bound for callers that cannot retain a directed tail. */
    val uncertaintyF64: Double = 0.0,
    /** Inclusive directed tail lower bound. */
    val lowerTailF64: Double = -uncertaintyF64,
    /** Inclusive directed tail upper bound. */
    val upperTailF64: Double = uncertaintyF64,
    /** Exact multiples of [Double.MIN_VALUE] retained when a product underflows its F64 limb. */
    val subnormalUnitsF64: Double = 0.0,
    /** Low-order exact units retained when the subnormal-unit accumulator crosses a binade. */
    val subnormalResidualUnitsF64: Double = 0.0,
)

/** A denominator fact.  Only [Root] is sufficient to publish a horizon. */
internal enum class ProjectiveWSignF64 {
    Positive,
    Negative,
    Root,
    Unknown,
    NonFinite,
}

/**
 * The two fields form a normalized double-double expansion: `leadingF64 + residualF64`, with the
 * residual too small to reverse the leading term.  Do not first collapse it to one [Double] when
 * deciding a denominator's sign: that reintroduces precisely the cancellation this expansion
 * records.
 */
internal fun projectiveCompensatedSignF64(valueF64: ProjectiveCompensatedF64): Int = when (projectiveWSignF64(valueF64)) {
    ProjectiveWSignF64.Positive -> 1
    ProjectiveWSignF64.Negative -> -1
    ProjectiveWSignF64.Root,
    ProjectiveWSignF64.Unknown,
    ProjectiveWSignF64.NonFinite,
    -> 0
}

/** Classifies from an outward enclosure, retaining the difference between root and unknown. */
internal fun projectiveWSignF64(valueF64: ProjectiveCompensatedF64): ProjectiveWSignF64 {
    if (!valueF64.leadingF64.isFinite() || !valueF64.residualF64.isFinite() ||
        !valueF64.uncertaintyF64.isFinite() || !valueF64.lowerTailF64.isFinite() ||
        !valueF64.upperTailF64.isFinite() || valueF64.lowerTailF64 > valueF64.upperTailF64
    ) return ProjectiveWSignF64.NonFinite
    if (valueF64.leadingF64 == 0.0 && valueF64.residualF64 == 0.0 &&
        valueF64.lowerTailF64 == 0.0 && valueF64.upperTailF64 == 0.0 && valueF64.subnormalUnitsF64 == 0.0 &&
            valueF64.subnormalResidualUnitsF64 == 0.0
    ) return ProjectiveWSignF64.Root
    // With no unrepresented tail, evaluate the retained finite expansion at its own binary
    // scale before constructing a Double interval.  Directed addition at the subnormal boundary
    // cannot represent a half-MIN endpoint, but the scaled expansion can still prove its sign.
    if (valueF64.lowerTailF64 == 0.0 && valueF64.upperTailF64 == 0.0 && valueF64.uncertaintyF64 == 0.0) {
        val exponentI32 = projectiveCompensatedMaximumExponentI32(valueF64)
        val scaledF64 = exponentI32?.let { projectiveCompensatedScaledCentralValueF64(valueF64, -it) }
        if (scaledF64 != null) {
            if (scaledF64 > 0.0) return ProjectiveWSignF64.Positive
            if (scaledF64 < 0.0) return ProjectiveWSignF64.Negative
        }
    }
    val intervalF64 = projectiveCompensatedIntervalF64(valueF64) ?: return ProjectiveWSignF64.NonFinite
    return when {
        intervalF64.minimumF64 == 0.0 && intervalF64.maximumF64 == 0.0 -> ProjectiveWSignF64.Root
        intervalF64.minimumF64 > 0.0 -> ProjectiveWSignF64.Positive
        intervalF64.maximumF64 < 0.0 -> ProjectiveWSignF64.Negative
        else -> ProjectiveWSignF64.Unknown
    }
}

internal fun projectiveCompensatedValueF64(valueF64: ProjectiveCompensatedF64): Double =
    valueF64.leadingF64 + valueF64.residualF64 +
        (valueF64.subnormalUnitsF64 + valueF64.subnormalResidualUnitsF64) * Double.MIN_VALUE

/**
 * Divides two retained expansions after bringing both to the denominator's binary scale.
 *
 * In particular, this must not first round a value such as `1.5 * MIN_VALUE` to one limb: that
 * would turn the exact quotient `MIN_VALUE / (1.5 * MIN_VALUE)` into `1.0`.  The scale is a
 * power of two, so it changes neither the represented real values nor their signs.
 */
internal fun projectiveCompensatedDivideF64(
    numeratorF64: ProjectiveCompensatedF64,
    denominatorF64: ProjectiveCompensatedF64,
): Double? {
    when (projectiveWSignF64(denominatorF64)) {
        ProjectiveWSignF64.Positive,
        ProjectiveWSignF64.Negative,
        -> Unit

        ProjectiveWSignF64.Root,
        ProjectiveWSignF64.Unknown,
        ProjectiveWSignF64.NonFinite,
        -> return null
    }
    val denominatorExponentI32 = projectiveCompensatedMaximumExponentI32(denominatorF64) ?: return null
    val scaleExponentI32 = -denominatorExponentI32
    val scaledNumeratorF64 = projectiveCompensatedScaledCentralValueF64(numeratorF64, scaleExponentI32) ?: return null
    val scaledDenominatorF64 = projectiveCompensatedScaledCentralValueF64(denominatorF64, scaleExponentI32) ?: return null
    if (scaledDenominatorF64 == 0.0 || !scaledNumeratorF64.isFinite() || !scaledDenominatorF64.isFinite()) return null
    val quotientF64 = scaledNumeratorF64 / scaledDenominatorF64
    return (if (quotientF64 == 0.0) 0.0 else quotientF64).takeIf(Double::isFinite)
}

private fun projectiveCompensatedMaximumExponentI32(valueF64: ProjectiveCompensatedF64): Int? {
    val exponentsI32 = listOfNotNull(
        projectiveBinaryExponentI32(valueF64.leadingF64),
        projectiveBinaryExponentI32(valueF64.residualF64),
        projectiveBinaryExponentI32(valueF64.subnormalUnitsF64)?.plus(-1074),
        projectiveBinaryExponentI32(valueF64.subnormalResidualUnitsF64)?.plus(-1074),
    )
    return exponentsI32.maxOrNull()
}

private fun projectiveCompensatedScaledCentralValueF64(
    valueF64: ProjectiveCompensatedF64,
    scaleExponentI32: Int,
): Double? {
    val scaledMinimumF64 = projectiveScalePowerOfTwoF64(Double.MIN_VALUE, scaleExponentI32) ?: return null
    val termsF64 = listOf(
        projectiveScalePowerOfTwoF64(valueF64.leadingF64, scaleExponentI32),
        projectiveScalePowerOfTwoF64(valueF64.residualF64, scaleExponentI32),
        valueF64.subnormalUnitsF64 * scaledMinimumF64,
        valueF64.subnormalResidualUnitsF64 * scaledMinimumF64,
    )
    if (termsF64.any { it == null || !it.isFinite() }) return null
    var sumF64 = 0.0
    termsF64.filterNotNull().forEach { termF64 ->
        val additionF64 = projectiveTwoSumF64(sumF64, termF64) ?: return null
        sumF64 = additionF64.leadingF64 + additionF64.residualF64
    }
    return sumF64.takeIf(Double::isFinite)
}

private fun projectiveBinaryExponentI32(valueF64: Double): Int? {
    if (!valueF64.isFinite() || valueF64 == 0.0) return null
    val bitsI64 = valueF64.toBits() and Long.MAX_VALUE
    val exponentFieldI32 = ((bitsI64 ushr 52) and 0x7ffL).toInt()
    if (exponentFieldI32 != 0) return exponentFieldI32 - 1023
    var fractionI64 = bitsI64 and 0x000f_ffff_ffff_ffffL
    var exponentI32 = -1074
    while (fractionI64 > 1L) {
        fractionI64 = fractionI64 ushr 1
        exponentI32 += 1
    }
    return exponentI32
}

private fun projectiveScalePowerOfTwoF64(valueF64: Double, scaleExponentI32: Int): Double? {
    if (!valueF64.isFinite() || valueF64 == 0.0) return valueF64.takeIf(Double::isFinite)
    var resultF64 = valueF64
    var remainingI32 = scaleExponentI32
    while (remainingI32 > 0) {
        val stepI32 = minOf(remainingI32, 1023)
        resultF64 *= Double.fromBits((stepI32 + 1023).toLong() shl 52)
        if (!resultF64.isFinite()) return null
        remainingI32 -= stepI32
    }
    while (remainingI32 < 0) {
        val stepI32 = maxOf(remainingI32, -1022)
        resultF64 *= Double.fromBits((stepI32 + 1023).toLong() shl 52)
        if (resultF64 == 0.0) return 0.0
        remainingI32 -= stepI32
    }
    return resultF64
}

/** Outward enclosure of a retained double-double value and its unrepresented tail. */
internal fun projectiveCompensatedIntervalF64(valueF64: ProjectiveCompensatedF64): PathProjectiveIntervalF64? {
    if (!valueF64.leadingF64.isFinite() || !valueF64.residualF64.isFinite() || !valueF64.uncertaintyF64.isFinite() ||
        !valueF64.lowerTailF64.isFinite() || !valueF64.upperTailF64.isFinite() ||
        valueF64.lowerTailF64 > valueF64.upperTailF64
    ) {
        return null
    }
    var lowerF64 = projectiveDirectedAddDownF64(
        projectiveDirectedAddDownF64(valueF64.leadingF64, valueF64.residualF64) ?: return null,
        valueF64.lowerTailF64,
    ) ?: return null
    var upperF64 = projectiveDirectedAddUpF64(
        projectiveDirectedAddUpF64(valueF64.leadingF64, valueF64.residualF64) ?: return null,
        valueF64.upperTailF64,
    ) ?: return null
    val subnormalF64 = valueF64.subnormalUnitsF64 * Double.MIN_VALUE
    if (subnormalF64 != 0.0) {
        lowerF64 = projectiveDirectedAddDownF64(lowerF64, subnormalF64) ?: return null
        upperF64 = projectiveDirectedAddUpF64(upperF64, subnormalF64) ?: return null
    } else if (valueF64.subnormalUnitsF64 > 0.0) {
        upperF64 = projectiveDirectedAddUpF64(upperF64, Double.MIN_VALUE) ?: return null
    } else if (valueF64.subnormalUnitsF64 < 0.0) {
        lowerF64 = projectiveDirectedAddDownF64(lowerF64, -Double.MIN_VALUE) ?: return null
    }
    val subnormalResidualF64 = valueF64.subnormalResidualUnitsF64 * Double.MIN_VALUE
    if (subnormalResidualF64 != 0.0) {
        lowerF64 = projectiveDirectedAddDownF64(lowerF64, subnormalResidualF64) ?: return null
        upperF64 = projectiveDirectedAddUpF64(upperF64, subnormalResidualF64) ?: return null
    } else if (valueF64.subnormalResidualUnitsF64 > 0.0) {
        upperF64 = projectiveDirectedAddUpF64(upperF64, Double.MIN_VALUE) ?: return null
    } else if (valueF64.subnormalResidualUnitsF64 < 0.0) {
        lowerF64 = projectiveDirectedAddDownF64(lowerF64, -Double.MIN_VALUE) ?: return null
    }
    if (!lowerF64.isFinite() || !upperF64.isFinite()) return null
    return PathProjectiveIntervalF64(lowerF64, upperF64)
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

/**
 * Certifies roots for the rational Bézier degrees used by path commands.  A cubic is solved only
 * after an exact Bernstein degree reduction; an inconclusive degree never becomes a horizon.
 */
internal fun projectiveBezierRootCertificateF64(controlsF64: List<ProjectiveCompensatedF64>): Boolean = when (controlsF64.size) {
    3 -> projectiveQuadraticRootCertificateF64(controlsF64)
    4 -> projectiveCubicDegreeReducedRootCertificateF64(controlsF64) ||
        projectiveCubicMultipleRootCertificateF64(controlsF64) ||
        projectiveGeneralCubicRootCertificateF64(controlsF64)
    else -> false
}

/**
 * Certifies a non-triple repeated cubic root without materializing its generally non-dyadic
 * parameter.  For `p(t) = a*t^3 + b*t^2 + c*t + d`, a repeated root has
 *
 *     t = (9*a*d - b*c) / (2*(b*b - 3*a*c)).
 *
 * when the denominator is nonzero.  The cubic discriminant proves that this stationary point is
 * a root; strict retained signs of numerator, denominator and their difference prove it lies in
 * `(0, 1)`.  Every decision uses the compensated expansions, so an inexact `Double` such as
 * `1.0 / 3.0` is never substituted for the actual polynomial parameter.
 */
private fun projectiveCubicMultipleRootCertificateF64(controlsF64: List<ProjectiveCompensatedF64>): Boolean {
    if (controlsF64.any {
            it.lowerTailF64 != 0.0 || it.upperTailF64 != 0.0 ||
                it.subnormalUnitsF64 != 0.0 || it.subnormalResidualUnitsF64 != 0.0
        }
    ) return false
    val normalizedControlsF64 = projectiveNormalizeCubicControlsF64(controlsF64) ?: return false
    val coefficientsF64 = projectiveCubicPowerCoefficientsF64(normalizedControlsF64) ?: return false
    val aF64 = coefficientsF64[0]
    val bF64 = coefficientsF64[1]
    val cF64 = coefficientsF64[2]
    val dF64 = coefficientsF64[3]
    if (projectiveCompensatedSignF64(aF64) == 0) return false

    val denominatorHalfF64 = projectiveCompensatedAddF64(
        projectiveCompensatedMultiplyF64(bF64, bF64) ?: return false,
        projectiveCompensatedScaleF64(projectiveCompensatedMultiplyF64(aF64, cF64) ?: return false, -3.0)
            ?: return false,
    ) ?: return false
    val denominatorF64 = projectiveCompensatedScaleF64(denominatorHalfF64, 2.0) ?: return false
    val numeratorF64 = projectiveCompensatedAddF64(
        projectiveCompensatedScaleF64(projectiveCompensatedMultiplyF64(aF64, dF64) ?: return false, 9.0)
            ?: return false,
        projectiveCompensatedNegateF64(projectiveCompensatedMultiplyF64(bF64, cF64) ?: return false),
    ) ?: return false
    val denominatorSignI32 = projectiveCompensatedSignF64(denominatorF64)
    if (denominatorSignI32 == 0 || denominatorSignI32 != projectiveCompensatedSignF64(numeratorF64)) return false
    val remainingF64 = projectiveCompensatedAddF64(
        denominatorF64,
        projectiveCompensatedNegateF64(numeratorF64),
    ) ?: return false
    if (projectiveCompensatedSignF64(remainingF64) != denominatorSignI32) return false

    val discriminantF64 = projectiveCubicDiscriminantF64(aF64, bF64, cF64, dF64) ?: return false
    return projectiveWSignF64(discriminantF64) == ProjectiveWSignF64.Root
}

/** Keeps all cubic invariants near unit scale without using a JVM-only wide-number type. */
private fun projectiveNormalizeCubicControlsF64(
    controlsF64: List<ProjectiveCompensatedF64>,
): List<ProjectiveCompensatedF64>? {
    val maximumExponentI32 = controlsF64.mapNotNull(::projectiveCompensatedMaximumExponentI32).maxOrNull()
        ?: return null
    // A one-limb power of two is enough to keep MAX-scale products finite and to lift MIN-scale
    // products above the underflow boundary.  If a still smaller mixed term disappears, the
    // subsequent sign certificate simply remains inconclusive rather than manufacturing a root.
    val shiftI32 = (-maximumExponentI32).coerceIn(-1022, 1023)
    val factorF64 = Double.fromBits((shiftI32 + 1023).toLong() shl 52)
    return controlsF64.map { projectiveCompensatedScaleF64(it, factorF64) ?: return null }
}

/** Returns power-basis `(a, b, c, d)` from cubic Bernstein controls. */
private fun projectiveCubicPowerCoefficientsF64(
    controlsF64: List<ProjectiveCompensatedF64>,
): List<ProjectiveCompensatedF64>? {
    val dF64 = controlsF64[0]
    val cF64 = projectiveCompensatedScaleF64(
        projectiveCompensatedAddF64(controlsF64[1], projectiveCompensatedNegateF64(controlsF64[0])) ?: return null,
        3.0,
    ) ?: return null
    val bF64 = projectiveCompensatedScaleF64(
        projectiveCompensatedAddF64(
            projectiveCompensatedAddF64(controlsF64[0], projectiveCompensatedScaleF64(controlsF64[1], -2.0) ?: return null)
                ?: return null,
            controlsF64[2],
        ) ?: return null,
        3.0,
    ) ?: return null
    val aF64 = projectiveCompensatedAddF64(
        projectiveCompensatedAddF64(
            projectiveCompensatedAddF64(projectiveCompensatedNegateF64(controlsF64[0]),
                projectiveCompensatedScaleF64(controlsF64[1], 3.0) ?: return null) ?: return null,
            projectiveCompensatedScaleF64(controlsF64[2], -3.0) ?: return null,
        ) ?: return null,
        controlsF64[3],
    ) ?: return null
    return listOf(aF64, bF64, cF64, dF64)
}

/** Exact-sign discriminant of `a*t³ + b*t² + c*t + d`, retaining all product expansions. */
private fun projectiveCubicDiscriminantF64(
    aF64: ProjectiveCompensatedF64,
    bF64: ProjectiveCompensatedF64,
    cF64: ProjectiveCompensatedF64,
    dF64: ProjectiveCompensatedF64,
): ProjectiveCompensatedF64? {
    fun multiply(leftF64: ProjectiveCompensatedF64, rightF64: ProjectiveCompensatedF64): ProjectiveCompensatedF64? =
        projectiveCompensatedMultiplyF64(leftF64, rightF64)
    fun scale(valueF64: ProjectiveCompensatedF64, factorF64: Double): ProjectiveCompensatedF64? =
        projectiveCompensatedScaleF64(valueF64, factorF64)
    fun add(leftF64: ProjectiveCompensatedF64, rightF64: ProjectiveCompensatedF64): ProjectiveCompensatedF64? =
        projectiveCompensatedAddF64(leftF64, rightF64)

    val eighteenAbcdF64 = scale(multiply(multiply(aF64, bF64) ?: return null, multiply(cF64, dF64) ?: return null)
        ?: return null, 18.0) ?: return null
    val fourBCubedF64 = scale(multiply(bF64, multiply(bF64, bF64) ?: return null) ?: return null, -4.0) ?: return null
    val bSquaredCSquaredF64 = multiply(multiply(bF64, bF64) ?: return null, multiply(cF64, cF64) ?: return null) ?: return null
    val fourACubedF64 = scale(multiply(aF64, multiply(cF64, multiply(cF64, cF64) ?: return null) ?: return null)
        ?: return null, -4.0) ?: return null
    val twentySevenASquaredDSquaredF64 = scale(
        multiply(multiply(aF64, aF64) ?: return null, multiply(dF64, dF64) ?: return null) ?: return null,
        -27.0,
    ) ?: return null
    return listOf(
        eighteenAbcdF64,
        fourBCubedF64,
        bSquaredCSquaredF64,
        fourACubedF64,
        twentySevenASquaredDSquaredF64,
    ).fold(ProjectiveCompensatedF64(0.0, 0.0)) { resultF64, termF64 -> add(resultF64, termF64) ?: return null }
}

/**
 * A cubic tangent has no sign variation.  We therefore inspect only the exact stationary points
 * of its power-basis derivative, and publish Horizon only when a retained de Casteljau evaluation
 * proves an exact root.  Floating candidate generation is never itself a root proof.
 */
private fun projectiveGeneralCubicRootCertificateF64(controlsF64: List<ProjectiveCompensatedF64>): Boolean {
    if (controlsF64.any {
            it.lowerTailF64 != 0.0 || it.upperTailF64 != 0.0 ||
                it.subnormalUnitsF64 != 0.0 || it.subnormalResidualUnitsF64 != 0.0
        }
    ) return false
    val rawF64 = controlsF64.map(::projectiveCompensatedValueF64)
    val magnitudeF64 = rawF64.maxOf(::abs)
    if (magnitudeF64 == 0.0 || !magnitudeF64.isFinite()) return false
    val controlsScaledF64 = rawF64.map { it / magnitudeF64 }
    val aF64 = -controlsScaledF64[0] + 3.0 * controlsScaledF64[1] - 3.0 * controlsScaledF64[2] + controlsScaledF64[3]
    val bF64 = 3.0 * controlsScaledF64[0] - 6.0 * controlsScaledF64[1] + 3.0 * controlsScaledF64[2]
    val cF64 = -3.0 * controlsScaledF64[0] + 3.0 * controlsScaledF64[1]
    if (!aF64.isFinite() || !bF64.isFinite() || !cF64.isFinite()) return false
    val candidatesF64 = mutableListOf<Double>()
    val derivativeAF64 = 3.0 * aF64
    val derivativeBF64 = 2.0 * bF64
    when {
        derivativeAF64 == 0.0 && derivativeBF64 != 0.0 -> candidatesF64 += -cF64 / derivativeBF64
        derivativeAF64 != 0.0 -> {
            val discriminantF64 = derivativeBF64 * derivativeBF64 - 4.0 * derivativeAF64 * cF64
            if (discriminantF64 >= 0.0 && discriminantF64.isFinite()) {
                val squareRootF64 = kotlin.math.sqrt(discriminantF64)
                candidatesF64 += (-derivativeBF64 - squareRootF64) / (2.0 * derivativeAF64)
                candidatesF64 += (-derivativeBF64 + squareRootF64) / (2.0 * derivativeAF64)
            }
        }
    }
    return candidatesF64.any { parameterF64 ->
        parameterF64 > 0.0 && parameterF64 < 1.0 &&
            projectiveWSignF64(projectiveEvaluateBezierF64(controlsF64, parameterF64) ?: return@any false) ==
                ProjectiveWSignF64.Root
    }
}

private fun projectiveEvaluateBezierF64(
    controlsF64: List<ProjectiveCompensatedF64>,
    parameterF64: Double,
): ProjectiveCompensatedF64? {
    var levelF64 = controlsF64
    while (levelF64.size > 1) {
        levelF64 = levelF64.zipWithNext { firstF64, secondF64 ->
            interpolateProjectiveCompensatedF64(firstF64, secondF64, parameterF64) ?: return null
        }
    }
    return levelF64.single()
}

private fun projectiveCubicDegreeReducedRootCertificateF64(controlsF64: List<ProjectiveCompensatedF64>): Boolean {
    if (controlsF64.any { it.lowerTailF64 != 0.0 || it.upperTailF64 != 0.0 }) return false
    // Third power-basis coefficient: c3 - 3*c2 + 3*c1 - c0.
    val cubicCoefficientF64 = projectiveCompensatedAddF64(
        projectiveCompensatedAddF64(
            projectiveCompensatedScaleF64(controlsF64[1], 3.0) ?: return false,
            projectiveCompensatedScaleF64(controlsF64[0], -1.0) ?: return false,
        ) ?: return false,
        projectiveCompensatedAddF64(
            controlsF64[3],
            projectiveCompensatedScaleF64(controlsF64[2], -3.0) ?: return false,
        ) ?: return false,
    ) ?: return false
    if (projectiveWSignF64(cubicCoefficientF64) != ProjectiveWSignF64.Root) return false
    // c1 = (q0 + 2*q1)/3 for the equivalent quadratic Bernstein control tuple.
    val middleF64 = projectiveCompensatedScaleF64(
        projectiveCompensatedAddF64(
            projectiveCompensatedScaleF64(controlsF64[1], 3.0) ?: return false,
            projectiveCompensatedNegateF64(controlsF64[0]),
        ) ?: return false,
        0.5,
    ) ?: return false
    return projectiveQuadraticRootCertificateF64(listOf(controlsF64[0], middleF64, controlsF64[3]))
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
        firstF64 == 1.0 -> ProjectiveCompensatedF64(secondF64, 0.0)
        secondF64 == 1.0 -> ProjectiveCompensatedF64(firstF64, 0.0)
        firstF64 == -1.0 -> ProjectiveCompensatedF64(-secondF64, 0.0)
        secondF64 == -1.0 -> ProjectiveCompensatedF64(-firstF64, 0.0)
        else -> {
            val productF64 = firstF64 * secondF64
            if (!productF64.isFinite()) return null
            val positiveF64 = (firstF64 < 0.0) == (secondF64 < 0.0)
            if (abs(productF64) <= PROJECTIVE_MIN_NORMAL_F64) {
                val exactSubnormalUnitsF64 = projectiveSubnormalUnitsF64(firstF64, secondF64)
                if (exactSubnormalUnitsF64 != null) {
                    return projectiveCompensatedDirectedF64(0.0, 0.0, 0.0, 0.0, exactSubnormalUnitsF64)
                }
                // The true product has a known operand sign even where its residual cannot be
                // represented.  Keep that one-sided fact: unknown magnitude is not a root.
                return if (positiveF64) {
                    projectiveCompensatedDirectedF64(productF64, 0.0, 0.0, Double.MIN_VALUE)
                } else {
                    projectiveCompensatedDirectedF64(productF64, 0.0, -Double.MIN_VALUE, 0.0)
                }
            }
            projectiveTwoProductF64(firstF64, secondF64)
                ?.let { ProjectiveCompensatedF64(it.leadingF64, it.residualF64) }
                // Dekker's splitter is intentionally unavailable near MAX.  A directed nearest
                // enclosure still preserves a finite strict sign and permits safe subdivision.
                ?: projectiveCompensatedDirectedF64(
                    productF64,
                    0.0,
                    nextDownProjectiveF64(productF64) - productF64,
                    nextUpProjectiveF64(productF64) - productF64,
                )
        }
    }

/** Multiplies both expansion limbs and retains the cross-limb product before normalization. */
private fun projectiveCompensatedScaleF64(
    valueF64: ProjectiveCompensatedF64,
    factorF64: Double,
): ProjectiveCompensatedF64? {
    val leadingProductF64 = projectiveCompensatedProductF64(valueF64.leadingF64, factorF64) ?: return null
    val residualProductF64 = projectiveCompensatedProductF64(valueF64.residualF64, factorF64) ?: return null
    val scaledF64 = projectiveCompensatedAddF64(leadingProductF64, residualProductF64) ?: return null
    val scaledLowerTailF64 = projectiveDirectedProductDownF64(
        if (factorF64 >= 0.0) valueF64.lowerTailF64 else valueF64.upperTailF64,
        factorF64,
    ) ?: return null
    val scaledUpperTailF64 = projectiveDirectedProductUpF64(
        if (factorF64 >= 0.0) valueF64.upperTailF64 else valueF64.lowerTailF64,
        factorF64,
    ) ?: return null
    return projectiveCompensatedDirectedF64(
        scaledF64.leadingF64,
        scaledF64.residualF64,
        projectiveDirectedAddDownF64(scaledF64.lowerTailF64, scaledLowerTailF64) ?: return null,
        projectiveDirectedAddUpF64(scaledF64.upperTailF64, scaledUpperTailF64) ?: return null,
        scaledF64.subnormalUnitsF64 * factorF64,
        scaledF64.subnormalResidualUnitsF64 * factorF64,
    )
}

private fun projectiveCompensatedNegateF64(valueF64: ProjectiveCompensatedF64): ProjectiveCompensatedF64 =
    projectiveCompensatedDirectedF64(
        -valueF64.leadingF64,
        -valueF64.residualF64,
        -valueF64.upperTailF64,
        -valueF64.lowerTailF64,
        -valueF64.subnormalUnitsF64,
        -valueF64.subnormalResidualUnitsF64,
    )

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
        projectiveUpwardProductF64(firstMagnitudeF64, projectiveTailMagnitudeF64(secondF64)) ?: return null,
        projectiveUpwardProductF64(secondMagnitudeF64, projectiveTailMagnitudeF64(firstF64)) ?: return null,
        projectiveUpwardProductF64(projectiveTailMagnitudeF64(firstF64), projectiveTailMagnitudeF64(secondF64)) ?: return null,
    ) ?: return null
    return projectiveCompensatedDirectedF64(
        combinedF64.leadingF64,
        combinedF64.residualF64,
        projectiveDirectedAddDownF64(combinedF64.lowerTailF64, -inheritedUncertaintyF64) ?: return null,
        projectiveDirectedAddUpF64(combinedF64.upperTailF64, inheritedUncertaintyF64) ?: return null,
        combinedF64.subnormalUnitsF64,
        combinedF64.subnormalResidualUnitsF64,
    )
}

private fun projectiveCompensatedMagnitudeUpperF64(valueF64: ProjectiveCompensatedF64): Double? =
    projectiveUpwardAbsoluteSumF64(valueF64.leadingF64, valueF64.residualF64, projectiveTailMagnitudeF64(valueF64))

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
    return projectiveCompensatedDirectedF64(
        normalizedF64.leadingF64,
        normalizedF64.residualF64,
        projectiveDirectedAddDownF64(
            projectiveDirectedAddDownF64(normalizedF64.lowerTailF64, firstF64.lowerTailF64) ?: return null,
            secondF64.lowerTailF64,
        ) ?: return null,
        projectiveDirectedAddUpF64(
            projectiveDirectedAddUpF64(normalizedF64.upperTailF64, firstF64.upperTailF64) ?: return null,
            secondF64.upperTailF64,
        ) ?: return null,
        projectiveTwoSumF64(firstF64.subnormalUnitsF64, secondF64.subnormalUnitsF64)?.leadingF64 ?: return null,
        (projectiveTwoSumF64(firstF64.subnormalUnitsF64, secondF64.subnormalUnitsF64)?.residualF64 ?: return null) +
            firstF64.subnormalResidualUnitsF64 + secondF64.subnormalResidualUnitsF64,
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
    val uncertaintyF64 = projectiveUpwardAbsoluteSumF64(normalizedF64.residualF64, tailF64.residualF64) ?: return null
    return projectiveCompensatedDirectedF64(
        resultWithLowF64.leadingF64,
        resultWithLowF64.residualF64,
        -uncertaintyF64,
        uncertaintyF64,
    ).takeIf { it.leadingF64.isFinite() && it.residualF64.isFinite() &&
        it.lowerTailF64.isFinite() && it.upperTailF64.isFinite() }
}

private fun projectiveCompensatedDirectedF64(
    leadingF64: Double,
    residualF64: Double,
    lowerTailF64: Double,
    upperTailF64: Double,
    subnormalUnitsF64: Double = 0.0,
    subnormalResidualUnitsF64: Double = 0.0,
): ProjectiveCompensatedF64 = ProjectiveCompensatedF64(
    leadingF64 = leadingF64,
    residualF64 = residualF64,
    uncertaintyF64 = max(abs(lowerTailF64), abs(upperTailF64)),
    lowerTailF64 = lowerTailF64,
    upperTailF64 = upperTailF64,
    subnormalUnitsF64 = subnormalUnitsF64,
    subnormalResidualUnitsF64 = subnormalResidualUnitsF64,
)

/** Exact in the useful subnormal family: MIN_VALUE times a finite binary factor. */
private fun projectiveSubnormalUnitsF64(firstF64: Double, secondF64: Double): Double? = when {
    abs(firstF64) == Double.MIN_VALUE -> secondF64
    abs(secondF64) == Double.MIN_VALUE -> firstF64
    else -> null
}

private fun projectiveTailMagnitudeF64(valueF64: ProjectiveCompensatedF64): Double =
    max(abs(valueF64.lowerTailF64), abs(valueF64.upperTailF64))

/** Correctly directed IEEE addition; exact additions are not widened across zero. */
private fun projectiveDirectedAddDownF64(firstF64: Double, secondF64: Double): Double? {
    if (secondF64 == 0.0) return firstF64.takeIf(Double::isFinite)
    if (firstF64 == 0.0) return secondF64.takeIf(Double::isFinite)
    val sumF64 = projectiveTwoSumF64(firstF64, secondF64) ?: return null
    return (if (sumF64.residualF64 < 0.0) nextDownProjectiveF64(sumF64.leadingF64) else sumF64.leadingF64)
        .takeIf(Double::isFinite)
}

/** Correctly directed IEEE addition; exact additions are not widened across zero. */
private fun projectiveDirectedAddUpF64(firstF64: Double, secondF64: Double): Double? {
    if (secondF64 == 0.0) return firstF64.takeIf(Double::isFinite)
    if (firstF64 == 0.0) return secondF64.takeIf(Double::isFinite)
    val sumF64 = projectiveTwoSumF64(firstF64, secondF64) ?: return null
    return (if (sumF64.residualF64 > 0.0) nextUpProjectiveF64(sumF64.leadingF64) else sumF64.leadingF64)
        .takeIf(Double::isFinite)
}

private fun projectiveDirectedProductDownF64(firstF64: Double, secondF64: Double): Double? {
    if (firstF64 == 0.0 || secondF64 == 0.0) return 0.0
    val productF64 = firstF64 * secondF64
    return productF64.takeIf(Double::isFinite)?.let(::nextDownProjectiveF64)
}

private fun projectiveDirectedProductUpF64(firstF64: Double, secondF64: Double): Double? {
    if (firstF64 == 0.0 || secondF64 == 0.0) return 0.0
    val productF64 = firstF64 * secondF64
    return productF64.takeIf(Double::isFinite)?.let(::nextUpProjectiveF64)
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
