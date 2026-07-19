package org.graphiks.kanvas.gpu.renderer.commands

/** Result of normalizing one rounded rectangle to Skia's non-overlapping corner contract. */
sealed interface GPURRectNormalizationResult {
    /** Opaque proof that one exact raw source was normalized by [GPURRectNormalizer]. */
    sealed interface Accepted : GPURRectNormalizationResult {
        val rrect: GPURRect
        val scale: Double
        val wasScaled: Boolean
    }

    data class Refused(
        val code: String,
        val message: String,
    ) : GPURRectNormalizationResult
}

/** Pure, allocation-only implementation of Skia's proportional RRect radius normalization. */
object GPURRectNormalizer {
    fun normalize(rrect: GPURRect): GPURRectNormalizationResult = when (
        val computation = computeRRectNormalization(rrect)
    ) {
        is GPURRectNormalizationComputation.Accepted -> GPURRectNormalizationAccepted(
            rrect = computation.rrect,
            scale = computation.scale,
            wasScaled = computation.wasScaled,
            sourceBoundsBits = rrect.rawBoundsBits(),
            sourceRadiiBits = rrect.rawRadiiBits(),
        )
        is GPURRectNormalizationComputation.Refused -> GPURRectNormalizationResult.Refused(
            computation.code,
            computation.message,
        )
    }
}

/** Private implementation prevents callers from constructing or copying accepted provenance. */
private class GPURRectNormalizationAccepted(
    override val rrect: GPURRect,
    override val scale: Double,
    override val wasScaled: Boolean,
    sourceBoundsBits: IntArray,
    sourceRadiiBits: IntArray,
) : GPURRectNormalizationResult.Accepted {
    private val sourceBoundsBits = sourceBoundsBits.copyOf()
    private val sourceRadiiBits = sourceRadiiBits.copyOf()

    init {
        require(this.sourceBoundsBits.size == 4) {
            "Accepted RRect normalization requires four raw source bounds"
        }
        require(this.sourceRadiiBits.size == 8) {
            "Accepted RRect normalization requires eight raw source radii"
        }
    }

    fun matchesSource(source: GPURRect): Boolean =
        sourceBoundsBits.contentEquals(source.rawBoundsBits()) &&
            sourceRadiiBits.contentEquals(source.rawRadiiBits())

    override fun equals(other: Any?): Boolean = this === other || (
        other is GPURRectNormalizationAccepted &&
            rrect == other.rrect &&
            scale.toRawBits() == other.scale.toRawBits() &&
            wasScaled == other.wasScaled &&
            sourceBoundsBits.contentEquals(other.sourceBoundsBits) &&
            sourceRadiiBits.contentEquals(other.sourceRadiiBits)
        )

    override fun hashCode(): Int {
        var result = rrect.hashCode()
        result = 31 * result + scale.toRawBits().hashCode()
        result = 31 * result + wasScaled.hashCode()
        result = 31 * result + sourceBoundsBits.contentHashCode()
        result = 31 * result + sourceRadiiBits.contentHashCode()
        return result
    }

    override fun toString(): String =
        "Accepted(rrect=$rrect, scale=$scale, wasScaled=$wasScaled)"
}

/** Verifies accepted provenance by raw bits without exposing it on the public opaque interface. */
@JvmSynthetic
internal fun GPURRectNormalizationResult.Accepted.matchesSource(source: GPURRect): Boolean =
    (this as GPURRectNormalizationAccepted).matchesSource(source)

private sealed interface GPURRectNormalizationComputation {
    data class Accepted(
        val rrect: GPURRect,
        val scale: Double,
        val wasScaled: Boolean,
    ) : GPURRectNormalizationComputation

    data class Refused(
        val code: String,
        val message: String,
    ) : GPURRectNormalizationComputation
}

private fun computeRRectNormalization(rrect: GPURRect): GPURRectNormalizationComputation {
    val rect = rrect.rect
    if (!rect.left.isFinite() || !rect.top.isFinite() ||
        !rect.right.isFinite() || !rect.bottom.isFinite()
    ) {
        return refused(
            "unsupported.geometry.rrect_bounds",
            "Rounded rectangle bounds must be finite.",
        )
    }
    val width = rect.right.toDouble() - rect.left.toDouble()
    val height = rect.bottom.toDouble() - rect.top.toDouble()
    if (width <= 0.0 || height <= 0.0) {
        return refused(
            "unsupported.geometry.rrect_bounds",
            "Rounded rectangle bounds must have positive width and height.",
        )
    }

    val radii = floatArrayOf(
        rrect.topLeft.x,
        rrect.topLeft.y,
        rrect.topRight.x,
        rrect.topRight.y,
        rrect.bottomRight.x,
        rrect.bottomRight.y,
        rrect.bottomLeft.x,
        rrect.bottomLeft.y,
    )
    if (radii.any { !it.isFinite() }) {
        return refused(
            "unsupported.geometry.rrect_radii_non_finite",
            "Rounded rectangle radii must be finite.",
        )
    }
    if (radii.any { it < 0f }) {
        return refused(
            "unsupported.geometry.rrect_radii_negative",
            "Rounded rectangle radii must be non-negative.",
        )
    }

    clampSquareCorners(radii)
    var scale = 1.0
    scale = minimumSideScale(radii[0], radii[2], width, scale)
    scale = minimumSideScale(radii[3], radii[5], height, scale)
    scale = minimumSideScale(radii[4], radii[6], width, scale)
    scale = minimumSideScale(radii[7], radii[1], height, scale)

    flushIndistinguishablePair(radii, 0, 2)
    flushIndistinguishablePair(radii, 3, 5)
    flushIndistinguishablePair(radii, 4, 6)
    flushIndistinguishablePair(radii, 7, 1)

    if (scale < 1.0) {
        adjustSide(radii, 0, 2, width, scale)
        adjustSide(radii, 3, 5, height, scale)
        adjustSide(radii, 4, 6, width, scale)
        adjustSide(radii, 7, 1, height, scale)
    }
    clampSquareCorners(radii)

    return GPURRectNormalizationComputation.Accepted(
        rrect = GPURRect(
            rect = rect,
            topLeft = GPURRectCornerRadii(radii[0], radii[1]),
            topRight = GPURRectCornerRadii(radii[2], radii[3]),
            bottomRight = GPURRectCornerRadii(radii[4], radii[5]),
            bottomLeft = GPURRectCornerRadii(radii[6], radii[7]),
        ),
        scale = scale,
        wasScaled = scale < 1.0,
    )
}

private fun minimumSideScale(
    first: Float,
    second: Float,
    limit: Double,
    current: Double,
): Double {
    val sum = first.toDouble() + second.toDouble()
    return if (sum > limit) minOf(current, limit / sum) else current
}

private fun clampSquareCorners(radii: FloatArray) {
    for (corner in 0 until 4) {
        val x = corner * 2
        val y = x + 1
        if (radii[x] <= 0f || radii[y] <= 0f) {
            radii[x] = 0f
            radii[y] = 0f
        }
    }
}

private fun flushIndistinguishablePair(radii: FloatArray, first: Int, second: Int) {
    val sum = radii[first] + radii[second]
    if (sum == radii[first]) {
        radii[second] = 0f
    } else if (sum == radii[second]) {
        radii[first] = 0f
    }
}

private fun adjustSide(
    radii: FloatArray,
    first: Int,
    second: Int,
    limit: Double,
    scale: Double,
) {
    radii[first] = (radii[first].toDouble() * scale).toFloat()
    radii[second] = (radii[second].toDouble() * scale).toFloat()
    if (radii[first] + radii[second] <= limit) return

    val minimumIndex: Int
    val maximumIndex: Int
    if (radii[first] <= radii[second]) {
        minimumIndex = first
        maximumIndex = second
    } else {
        minimumIndex = second
        maximumIndex = first
    }
    val minimum = radii[minimumIndex]
    var maximum = (limit - minimum.toDouble()).toFloat()
    while (maximum + minimum > limit) {
        maximum = Math.nextAfter(maximum, 0.0)
    }
    radii[maximumIndex] = maximum
}

private fun refused(code: String, message: String): GPURRectNormalizationComputation.Refused =
    GPURRectNormalizationComputation.Refused(code, message)

private fun GPURRect.rawBoundsBits(): IntArray = intArrayOf(
    rect.left.toRawBits(),
    rect.top.toRawBits(),
    rect.right.toRawBits(),
    rect.bottom.toRawBits(),
)

private fun GPURRect.rawRadiiBits(): IntArray = intArrayOf(
    topLeft.x.toRawBits(),
    topLeft.y.toRawBits(),
    topRight.x.toRawBits(),
    topRight.y.toRawBits(),
    bottomRight.x.toRawBits(),
    bottomRight.y.toRawBits(),
    bottomLeft.x.toRawBits(),
    bottomLeft.y.toRawBits(),
)
