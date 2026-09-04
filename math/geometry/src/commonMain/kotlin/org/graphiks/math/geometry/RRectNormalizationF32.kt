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
    val materializedRect = RectF32(
        materializedF32(rect.left),
        materializedF32(rect.top),
        materializedF32(rect.right),
        materializedF32(rect.bottom),
    )
    val materializedTopLeft = topLeft.materializedF32()
    val materializedTopRight = topRight.materializedF32()
    val materializedBottomRight = bottomRight.materializedF32()
    val materializedBottomLeft = bottomLeft.materializedF32()
    if (!materializedRect.isFinite()) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.NonFiniteBounds)
    }
    if (!(materializedRect.right > materializedRect.left && materializedRect.bottom > materializedRect.top)) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.EmptyBounds)
    }

    if (!materializedTopLeft.isFiniteF32() || !materializedTopRight.isFiniteF32() ||
        !materializedBottomRight.isFiniteF32() || !materializedBottomLeft.isFiniteF32()
    ) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.NonFiniteRadii)
    }
    if (materializedTopLeft.hasNegativeComponentF32() || materializedTopRight.hasNegativeComponentF32() ||
        materializedBottomRight.hasNegativeComponentF32() || materializedBottomLeft.hasNegativeComponentF32()
    ) {
        return RRectNormalizationF32Result.Rejected(RRectNormalizationF32Rejection.NegativeRadii)
    }

    val canonicalTopLeft = materializedTopLeft.canonicalizedZeroPairF32()
    val canonicalTopRight = materializedTopRight.canonicalizedZeroPairF32()
    val canonicalBottomRight = materializedBottomRight.canonicalizedZeroPairF32()
    val canonicalBottomLeft = materializedBottomLeft.canonicalizedZeroPairF32()
    val widthF64 = materializedRect.right.toDouble() - materializedRect.left.toDouble()
    val heightF64 = materializedRect.bottom.toDouble() - materializedRect.top.toDouble()
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

    val topF32 = correctedConstraintPairF32(topLeftX, topRightX, widthF64)
    topLeftX = topF32.first
    topRightX = topF32.second
    val bottomF32 = correctedConstraintPairF32(bottomLeftX, bottomRightX, widthF64)
    bottomLeftX = bottomF32.first
    bottomRightX = bottomF32.second
    val leftF32 = correctedConstraintPairF32(topLeftY, bottomLeftY, heightF64)
    topLeftY = leftF32.first
    bottomLeftY = leftF32.second
    val rightF32 = correctedConstraintPairF32(topRightY, bottomRightY, heightF64)
    topRightY = rightF32.first
    bottomRightY = rightF32.second

    return RRectNormalizationF32Result.Accepted(
        RRectF32.of(
            rect = materializedRect,
            topLeft = CornerRadiiF32.of(topLeftX, topLeftY).canonicalizedZeroPairF32(),
            topRight = CornerRadiiF32.of(topRightX, topRightY).canonicalizedZeroPairF32(),
            bottomRight = CornerRadiiF32.of(bottomRightX, bottomRightY).canonicalizedZeroPairF32(),
            bottomLeft = CornerRadiiF32.of(bottomLeftX, bottomLeftY).canonicalizedZeroPairF32(),
        ),
    )
}

private fun CornerRadiiF32.isFiniteF32(): Boolean = x.isFinite() && y.isFinite()

private fun CornerRadiiF32.materializedF32(): CornerRadiiF32 =
    CornerRadiiF32.of(materializedF32(x), materializedF32(y))

private fun CornerRadiiF32.hasNegativeComponentF32(): Boolean = x < 0f || y < 0f

private fun CornerRadiiF32.canonicalizedZeroPairF32(): CornerRadiiF32 =
    if (x == 0f || y == 0f) CornerRadiiF32.Zero else this

private fun constraintScaleF64(firstF32: Float, secondF32: Float, sideF64: Double): Double {
    val sumF64 = firstF32.toDouble() + secondF32.toDouble()
    return if (sumF64 > sideF64) sideF64 / sumF64 else 1.0
}

private fun scaledF32(valueF32: Float, scaleF64: Double): Float {
    val roundedF32 = (valueF32.toDouble() * scaleF64).toFloat()
    return materializedF32(roundedF32)
}

private fun materializedF32(valueF32: Float): Float = Float.fromBits(valueF32.toRawBits())

private data class RRectConstraintPairF32(
    val first: Float,
    val second: Float,
)

private fun correctedConstraintPairF32(
    firstF32: Float,
    secondF32: Float,
    sideF64: Double,
): RRectConstraintPairF32 {
    if (firstF32.toDouble() + secondF32.toDouble() <= sideF64) {
        return RRectConstraintPairF32(firstF32, secondF32)
    }
    return if (firstF32.toDouble() <= sideF64) {
        RRectConstraintPairF32(firstF32, limitedF32(secondF32, sideF64 - firstF32.toDouble()))
    } else {
        RRectConstraintPairF32(limitedF32(firstF32, sideF64 - secondF32.toDouble()), secondF32)
    }
}

private fun limitedF32(valueF32: Float, maximumF64: Double): Float {
    if (valueF32.toDouble() <= maximumF64) return valueF32
    if (maximumF64 <= 0.0) return 0f
    val roundedF32 = Float.fromBits(maximumF64.toFloat().toRawBits())
    return if (roundedF32.toDouble() > maximumF64) previousPositiveF32(roundedF32) else roundedF32
}

private fun previousPositiveF32(valueF32: Float): Float {
    if (valueF32 <= 0f) return 0f
    return Float.fromBits(valueF32.toRawBits() - 1)
}
