package org.graphiks.kanvas.color.icc

import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.max
import kotlin.math.pow

public sealed interface IccCurve {
    /** Converts an encoded component to a linear component. */
    public fun evaluate(encoded: Float): Float

    /** Converts a linear component back to an encoded component. */
    public fun inverse(linear: Float): Float
}

internal class ParametricIccCurve(functionType: Int, parameters: FloatArray) : IccCurve {
    private val type: Int = functionType
    private val values: FloatArray = parameters.copyOf()

    init {
        require(parametricCurveValidationError(type, values) == null) { "Invalid ICC parametric curve" }
    }

    override fun evaluate(encoded: Float): Float {
        val x = encoded.finiteUnitValue()
        return rawParametricEvaluation(type, values, x).coerceIn(0f, 1f)
    }

    override fun inverse(linear: Float): Float {
        val y = linear.finiteUnitValue()
        val g = values[0]
        val result = when (type) {
            0 -> y.pow(1f / g)
            1 -> inverseParametricTypeOne(y, g)
            2 -> inverseParametricTypeTwo(y, g)
            3 -> when {
                values[4] <= 0f -> inverseUpperSegment(y, g, 0f)
                values[4] > 1f -> inverseLowerSegment(y, values[3], 0f)
                else -> {
                    val lowerLimit = values[3] * values[4]
                    val upperLimit = (values[1] * values[4] + values[2]).pow(g)
                    when {
                        y < lowerLimit && values[3] != 0f -> y / values[3]
                        y < upperLimit -> values[4]
                        else -> max(values[4], (y.pow(1f / g) - values[2]) / values[1])
                    }
                }
            }
            else -> when {
                values[4] <= 0f -> inverseUpperSegment(y, g, values[5])
                values[4] > 1f -> inverseLowerSegment(y, values[3], values[6])
                else -> {
                    val lowerLimit = values[3] * values[4] + values[6]
                    val upperLimit = (values[1] * values[4] + values[2]).pow(g) + values[5]
                    when {
                        y < lowerLimit && values[3] != 0f -> (y - values[6]) / values[3]
                        y < upperLimit -> values[4]
                        else -> max(values[4], ((y - values[5]).pow(1f / g) - values[2]) / values[1])
                    }
                }
            }
        }
        return if (result.isFinite()) result.coerceIn(0f, 1f) else 0f
    }

    private fun inverseParametricTypeOne(y: Float, g: Float): Float {
        val threshold = -values[2] / values[1]
        val upperLimit = (values[1] * threshold + values[2]).pow(g)
        return if (y < upperLimit) threshold else {
            max(threshold, (y.pow(1f / g) - values[2]) / values[1])
        }
    }

    private fun inverseParametricTypeTwo(y: Float, g: Float): Float {
        val threshold = -values[2] / values[1]
        val upperLimit = (values[1] * threshold + values[2]).pow(g) + values[3]
        return if (y < upperLimit) threshold else {
            max(threshold, ((y - values[3]).pow(1f / g) - values[2]) / values[1])
        }
    }

    private fun inverseUpperSegment(y: Float, g: Float, offset: Float): Float =
        ((y - offset).pow(1f / g) - values[2]) / values[1]

    private fun inverseLowerSegment(y: Float, slope: Float, offset: Float): Float =
        (y - offset) / slope

    fun toTransferFunction(): SkcmsTransferFunction = when (type) {
        0 -> SkcmsTransferFunction(values[0], 1f, 0f, 0f, 0f, 0f, 0f)
        1 -> SkcmsTransferFunction(values[0], values[1], values[2], 0f, -values[2] / values[1], 0f, 0f)
        2 -> SkcmsTransferFunction(
            values[0], values[1], values[2], 0f, -values[2] / values[1], values[3], values[3],
        )
        3 -> SkcmsTransferFunction(values[0], values[1], values[2], values[3], values[4], 0f, 0f)
        else -> SkcmsTransferFunction(values[0], values[1], values[2], values[3], values[4], values[5], values[6])
    }
}

internal class SampledIccCurve(samples: FloatArray) : IccCurve {
    private val values: FloatArray = samples.boundedSampleCopy()

    init {
        require(values.size >= 2) { "A sampled ICC curve requires at least two samples" }
        require(values.all { it.isFinite() && it in 0f..1f }) { "ICC curve samples must be finite and normalized" }
        require((1..values.lastIndex).all { index -> values[index - 1] <= values[index] }) {
            "ICC curve samples must be monotonic"
        }
    }

    override fun evaluate(encoded: Float): Float {
        val position = encoded.finiteUnitValue() * (values.size - 1)
        val lower = position.toInt().coerceAtMost(values.lastIndex)
        val upper = (lower + 1).coerceAtMost(values.lastIndex)
        val weight = position - lower
        return values[lower] + (values[upper] - values[lower]) * weight
    }

    override fun inverse(linear: Float): Float {
        val finiteLinear = if (linear.isFinite()) linear else values.first()
        val y = finiteLinear.coerceIn(values.first(), values.last())
        var first = 0
        var high = values.lastIndex
        while (first < high) {
            val middle = (first + high) ushr 1
            if (values[middle] < y) first = middle + 1 else high = middle
        }
        if (values[first] == y) {
            var last = first
            while (last < values.lastIndex && values[last + 1] == y) last++
            val plateauIndex = if (last == values.lastIndex) first else last
            return plateauIndex.toFloat() / values.lastIndex
        }
        val lower = first - 1
        val span = values[first] - values[lower]
        val weight = (y - values[lower]) / span
        return (lower + weight) / (values.size - 1)
    }
}

private fun FloatArray.boundedSampleCopy(): FloatArray {
    require(size <= MAX_DIRECT_SAMPLED_CURVE_ENTRIES) { "Sampled ICC curve is too large" }
    return copyOf()
}

internal fun parametricCurveValidationError(type: Int, values: FloatArray): String? {
    if (type !in PARAMETRIC_PARAMETER_COUNTS.indices) return "function type"
    if (values.size != PARAMETRIC_PARAMETER_COUNTS[type]) return "parameter count"
    if (!values.all(Float::isFinite)) return "non-finite parameter"
    val threshold = when (type) {
        0 -> 0f
        1, 2 -> -values[2] / values[1]
        else -> values[4]
    }
    if (!threshold.isFinite()) return "threshold"
    val lowerSelected = type != 0 && threshold > 0f
    val nonlinearSelected = type != 0 && threshold <= 1f
    if (type == 0 && values[0] <= 0f) return "gamma"
    if (type in 1..2 && (values[0] <= 0f || values[1] <= 0f)) return "nonlinear scale"
    if (type >= 3 && nonlinearSelected && values[0] <= 0f) return "gamma"
    if (type >= 3 && nonlinearSelected && values[1] <= 0f) return "nonlinear scale"
    if (type >= 3 && lowerSelected && values[3] < 0f) return "lower slope"

    if (nonlinearSelected) {
        val nonlinearStart = max(0f, threshold)
        if (values[1] * nonlinearStart + values[2] < 0f) {
            return "undefined nonlinear branch"
        }
    }

    if (lowerSelected && nonlinearSelected) {
        val lower = when (type) {
            1 -> 0f
            2 -> values[3]
            3 -> values[3] * threshold
            else -> values[3] * threshold + values[6]
        }
        val upper = when (type) {
            1 -> 0f
            2 -> values[3]
            3 -> (values[1] * threshold + values[2]).pow(values[0])
            else -> (values[1] * threshold + values[2]).pow(values[0]) + values[5]
        }
        val clippedLower = lower.coerceIn(0f, 1f)
        val clippedUpper = upper.coerceIn(0f, 1f)
        if (!clippedLower.isFinite() || !clippedUpper.isFinite()) return "non-finite branch"
        if (clippedUpper < clippedLower) {
            return "downward jump"
        }
    }

    val rawStart = rawParametricEvaluation(type, values, 0f)
    val rawEnd = rawParametricEvaluation(type, values, 1f)
    val start = rawStart.coerceIn(0f, 1f)
    val end = rawEnd.coerceIn(0f, 1f)
    if (!start.isFinite() || !end.isFinite() || end <= start) {
        return "non-monotonic range"
    }
    return null
}

private fun rawParametricEvaluation(type: Int, values: FloatArray, x: Float): Float {
    val g = values[0]
    return when (type) {
        0 -> x.pow(g)
        1 -> if (x >= -values[2] / values[1]) {
            (values[1] * x + values[2]).pow(g)
        } else {
            0f
        }
        2 -> if (x >= -values[2] / values[1]) {
            (values[1] * x + values[2]).pow(g) + values[3]
        } else {
            values[3]
        }
        3 -> if (x >= values[4]) {
            (values[1] * x + values[2]).pow(g)
        } else {
            values[3] * x
        }
        else -> if (x >= values[4]) {
            (values[1] * x + values[2]).pow(g) + values[5]
        } else {
            values[3] * x + values[6]
        }
    }
}

private fun Float.finiteUnitValue(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

private const val MAX_DIRECT_SAMPLED_CURVE_ENTRIES: Int = 65_536
private val PARAMETRIC_PARAMETER_COUNTS: IntArray = intArrayOf(1, 3, 4, 5, 7)
