package org.graphiks.math.geometry

/** The outcome of normalizing a rounded rectangle for analytic filling. */
public sealed interface RRectNormalizationF32Result {
    /** A rounded rectangle whose bounds and corner radii satisfy analytic-fill constraints. */
    public data class Accepted(public val shape: RRectF32) : RRectNormalizationF32Result

    /** A rounded rectangle rejected before analytic-fill normalization. */
    public data class Rejected(
        public val rejection: RRectNormalizationF32Rejection,
    ) : RRectNormalizationF32Result
}

/** The reason an [RRectF32] cannot be normalized for analytic filling. */
public enum class RRectNormalizationF32Rejection {
    NonFiniteBounds,
    EmptyBounds,
    NonFiniteRadii,
    NegativeRadii,
}

/**
 * Validates and normalizes this rounded rectangle for backend-neutral analytic filling.
 *
 * The result has finite, non-empty bounds and non-negative F32 radii that meet each side
 * constraint when summed in F64.
 */
public fun RRectF32.normalizeForAnalyticFillF32(): RRectNormalizationF32Result {
    if (!rect.isFinite()) return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.NonFiniteBounds)
    if (!(rect.right > rect.left && rect.bottom > rect.top)) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.EmptyBounds)
    }

    if (!topLeft.isFiniteF32() || !topRight.isFiniteF32() ||
        !bottomRight.isFiniteF32() || !bottomLeft.isFiniteF32()
    ) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.NonFiniteRadii)
    }
    if (topLeft.hasNegativeComponentF32() || topRight.hasNegativeComponentF32() ||
        bottomRight.hasNegativeComponentF32() || bottomLeft.hasNegativeComponentF32()
    ) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.NegativeRadii)
    }

    val canonicalTopLeft = topLeft.canonicalizedZeroPairF32()
    val canonicalTopRight = topRight.canonicalizedZeroPairF32()
    val canonicalBottomRight = bottomRight.canonicalizedZeroPairF32()
    val canonicalBottomLeft = bottomLeft.canonicalizedZeroPairF32()
    val widthF64 = rect.right.toDouble() - rect.left.toDouble()
    val heightF64 = rect.bottom.toDouble() - rect.top.toDouble()
    val scaleF64 = minOf(
        1.0,
        constraintScaleF64(canonicalTopLeft.x, canonicalTopRight.x, widthF64),
        constraintScaleF64(canonicalBottomLeft.x, canonicalBottomRight.x, widthF64),
        constraintScaleF64(canonicalTopLeft.y, canonicalBottomLeft.y, heightF64),
        constraintScaleF64(canonicalTopRight.y, canonicalBottomRight.y, heightF64),
    )

    var topLeftX = scaledF32(canonicalTopLeft.x, scaleF64)
    var topLeftY = scaledF32(canonicalTopLeft.y, scaleF64)
    var topRightX = scaledF32(canonicalTopRight.x, scaleF64)
    var topRightY = scaledF32(canonicalTopRight.y, scaleF64)
    var bottomRightX = scaledF32(canonicalBottomRight.x, scaleF64)
    var bottomRightY = scaledF32(canonicalBottomRight.y, scaleF64)
    var bottomLeftX = scaledF32(canonicalBottomLeft.x, scaleF64)
    var bottomLeftY = scaledF32(canonicalBottomLeft.y, scaleF64)

    while (topLeftX.toDouble() + topRightX.toDouble() > widthF64) {
        topRightX = previousPositiveF32(topRightX)
    }
    while (bottomLeftX.toDouble() + bottomRightX.toDouble() > widthF64) {
        bottomRightX = previousPositiveF32(bottomRightX)
    }
    while (topLeftY.toDouble() + bottomLeftY.toDouble() > heightF64) {
        bottomLeftY = previousPositiveF32(bottomLeftY)
    }
    while (topRightY.toDouble() + bottomRightY.toDouble() > heightF64) {
        bottomRightY = previousPositiveF32(bottomRightY)
    }

    return RRectNormalizationF32Result.Accepted(
        RRectF32.of(
            rect = rect,
            topLeft = CornerRadiiF32.of(topLeftX, topLeftY).canonicalizedZeroPairF32(),
            topRight = CornerRadiiF32.of(topRightX, topRightY).canonicalizedZeroPairF32(),
            bottomRight = CornerRadiiF32.of(bottomRightX, bottomRightY).canonicalizedZeroPairF32(),
            bottomLeft = CornerRadiiF32.of(bottomLeftX, bottomLeftY).canonicalizedZeroPairF32(),
        ),
    )
}

private fun CornerRadiiF32.isFiniteF32(): Boolean = x.isFinite() && y.isFinite()

private fun CornerRadiiF32.hasNegativeComponentF32(): Boolean = x < 0f || y < 0f

private fun CornerRadiiF32.canonicalizedZeroPairF32(): CornerRadiiF32 =
    if (x == 0f || y == 0f) CornerRadiiF32.Zero else this

private fun constraintScaleF64(firstF32: Float, secondF32: Float, sideF64: Double): Double {
    val sumF64 = firstF32.toDouble() + secondF32.toDouble()
    return if (sumF64 > sideF64) sideF64 / sumF64 else 1.0
}

private fun scaledF32(valueF32: Float, scaleF64: Double): Float {
    val roundedF32 = (valueF32.toDouble() * scaleF64).toFloat()
    return Float.fromBits(roundedF32.toRawBits())
}

private fun previousPositiveF32(valueF32: Float): Float {
    if (valueF32 <= 0f) return 0f
    return Float.fromBits(valueF32.toRawBits() - 1)
}
