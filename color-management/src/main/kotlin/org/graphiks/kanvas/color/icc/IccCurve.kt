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
            1 -> if (y <= 0f) 0f else (y.pow(1f / g) - values[2]) / values[1]
            2 -> if (y <= values[3]) 0f else {
                ((y - values[3]).pow(1f / g) - values[2]) / values[1]
            }
            3 -> {
                val lowerLimit = values[3] * values[4]
                if (y < lowerLimit && values[3] != 0f) y / values[3] else {
                    (y.pow(1f / g) - values[2]) / values[1]
                }
            }
            else -> {
                val lowerLimit = values[3] * values[4] + values[6]
                if (y < lowerLimit && values[3] != 0f) (y - values[6]) / values[3] else {
                    ((y - values[5]).pow(1f / g) - values[2]) / values[1]
                }
            }
        }
        return if (result.isFinite()) result.coerceIn(0f, 1f) else 0f
    }

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
        var low = 0
        var high = values.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (values[middle] < y) low = middle + 1 else high = middle
        }
        val upper = low
        if (upper == 0) return 0f
        val lower = upper - 1
        val span = values[upper] - values[lower]
        val weight = if (span == 0f) 0f else (y - values[lower]) / span
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
    if (values[0] <= 0f) return "gamma"
    if (type >= 1 && values[1] <= 0f) return "nonlinear scale"

    val threshold = when (type) {
        0 -> 0f
        1, 2 -> -values[2] / values[1]
        else -> values[4]
    }
    if (!threshold.isFinite()) return "threshold"
    if (type >= 3 && threshold !in 0f..1f) return "threshold range"
    if (type >= 3 && values[3] < 0f) return "lower slope"

    if (type >= 1) {
        val nonlinearStart = max(0f, threshold)
        if (nonlinearStart <= 1f && values[1] * nonlinearStart + values[2] < 0f) {
            return "undefined nonlinear branch"
        }
    }

    if (threshold in 0f..1f && type != 0) {
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
        if (!lower.isFinite() || !upper.isFinite() || lower != upper) {
            return "discontinuous branch"
        }
    }

    val start = rawParametricEvaluation(type, values, 0f)
    val end = rawParametricEvaluation(type, values, 1f)
    if (!start.isFinite() || !end.isFinite()) return "non-finite evaluation"
    if (start < 0f || end > 1f || end <= start) {
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
