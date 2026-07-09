package org.graphiks.kanvas.color.icc

import org.graphiks.math.SkcmsTransferFunction
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
        require(type in PARAMETER_COUNTS.indices) { "ICC parametric curve type must be in 0..4" }
        require(values.size == PARAMETER_COUNTS[type]) { "Wrong parameter count for ICC parametric curve type $type" }
        require(values.all(Float::isFinite)) { "ICC parametric curve parameters must be finite" }
        require(values[0] > 0f) { "ICC parametric curve gamma must be positive" }
        if (type >= 1) require(values[1] > 0f) { "ICC parametric curve scale must be positive" }
    }

    override fun evaluate(encoded: Float): Float {
        val x = encoded.coerceIn(0f, 1f)
        val g = values[0]
        return when (type) {
            0 -> x.pow(g)
            1 -> if (x >= -values[2] / values[1]) (values[1] * x + values[2]).pow(g) else 0f
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
                (values[1] * x + values[2]).pow(g) + values[3]
            } else {
                values[5] * x + values[6]
            }
        }
    }

    override fun inverse(linear: Float): Float {
        val y = linear.coerceIn(0f, 1f)
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
                val lowerLimit = values[5] * values[4] + values[6]
                if (y < lowerLimit && values[5] != 0f) (y - values[6]) / values[5] else {
                    ((y - values[3]).coerceAtLeast(0f).pow(1f / g) - values[2]) / values[1]
                }
            }
        }
        return result.coerceIn(0f, 1f)
    }

    fun toTransferFunction(): SkcmsTransferFunction = when (type) {
        0 -> SkcmsTransferFunction(values[0], 1f, 0f, 0f, 0f, 0f, 0f)
        1 -> SkcmsTransferFunction(values[0], values[1], values[2], 0f, -values[2] / values[1], 0f, 0f)
        2 -> SkcmsTransferFunction(
            values[0], values[1], values[2], 0f, -values[2] / values[1], values[3], values[3],
        )
        3 -> SkcmsTransferFunction(values[0], values[1], values[2], values[3], values[4], 0f, 0f)
        else -> SkcmsTransferFunction(
            values[0], values[1], values[2], values[5], values[4], values[3], values[6],
        )
    }

    private companion object {
        val PARAMETER_COUNTS: IntArray = intArrayOf(1, 3, 4, 5, 7)
    }
}

internal class SampledIccCurve(samples: FloatArray) : IccCurve {
    private val values: FloatArray = samples.copyOf()

    init {
        require(values.size >= 2) { "A sampled ICC curve requires at least two samples" }
        require(values.all { it.isFinite() && it in 0f..1f }) { "ICC curve samples must be finite and normalized" }
        require((1..values.lastIndex).all { index -> values[index - 1] <= values[index] }) {
            "ICC curve samples must be monotonic"
        }
    }

    override fun evaluate(encoded: Float): Float {
        val position = encoded.coerceIn(0f, 1f) * (values.size - 1)
        val lower = position.toInt().coerceAtMost(values.lastIndex)
        val upper = (lower + 1).coerceAtMost(values.lastIndex)
        val weight = position - lower
        return values[lower] + (values[upper] - values[lower]) * weight
    }

    override fun inverse(linear: Float): Float {
        val y = linear.coerceIn(values.first(), values.last())
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
