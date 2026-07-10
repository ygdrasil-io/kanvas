package org.graphiks.kanvas.color.icc

import kotlin.ConsistentCopyVisibility

/** An immutable, normalized ICC colour lookup table with one to four input dimensions. */
@ConsistentCopyVisibility
public data class IccClut private constructor(
    public val inputChannels: Int,
    public val outputChannels: Int,
    private val gridPointStorage: IntArrayValue,
    private val valueStorage: FloatArrayValue,
) {
    public constructor(
        inputChannels: Int,
        outputChannels: Int,
        gridPoints: IntArray,
        values: FloatArray,
    ) : this(
        inputChannels = inputChannels,
        outputChannels = outputChannels,
        gridPointStorage = IntArrayValue.copyOf(gridPoints),
        valueStorage = FloatArrayValue.copyOf(values),
    )

    init {
        require(inputChannels in 1..MAX_CHANNELS) { "inputChannels must be in 1..$MAX_CHANNELS" }
        require(outputChannels in 1..MAX_CHANNELS) { "outputChannels must be in 1..$MAX_CHANNELS" }
        require(gridPointStorage.size == inputChannels) { "gridPoints must contain one entry per input channel" }
        require(gridPointStorage.all { it >= MIN_GRID_POINTS }) { "Each CLUT dimension requires at least two points" }
        var expectedValues = outputChannels.toLong()
        gridPointStorage.forEach { points ->
            require(expectedValues <= Int.MAX_VALUE.toLong() / points) { "CLUT dimensions exceed addressable storage" }
            expectedValues *= points
        }
        require(valueStorage.size == expectedValues.toInt()) {
            "CLUT value count does not match its dimensions"
        }
        require(valueStorage.all { it.isFinite() && it in 0f..1f }) {
            "CLUT values must be finite and normalized"
        }
    }

    /** Returns a fresh copy so callers cannot mutate the interpolation grid. */
    public val gridPoints: IntArray
        get() = gridPointStorage.copy()

    /** Returns a fresh copy so callers cannot mutate the normalized samples. */
    public val values: FloatArray
        get() = valueStorage.copy()

    /** Performs bounded multilinear interpolation. [input] and [output] may be the same array. */
    public fun interpolate(input: FloatArray, output: FloatArray) {
        require(input.size >= inputChannels) { "input must contain at least $inputChannels channels" }
        require(output.size >= outputChannels) { "output must contain at least $outputChannels channels" }

        val lower = IntArray(inputChannels)
        val upper = IntArray(inputChannels)
        val upperWeight = FloatArray(inputChannels)
        repeat(inputChannels) { channel ->
            val normalized = if (input[channel].isFinite()) input[channel].coerceIn(0f, 1f) else 0f
            val last = gridPointStorage[channel] - 1
            val position = normalized * last
            val low = position.toInt().coerceAtMost(last - 1)
            lower[channel] = low
            upper[channel] = low + 1
            upperWeight[channel] = position - low
        }

        val result = FloatArray(outputChannels)
        repeat(1 shl inputChannels) { corner ->
            var pointIndex = 0
            var weight = 1f
            repeat(inputChannels) { channel ->
                val useUpper = corner and (1 shl channel) != 0
                val coordinate = if (useUpper) upper[channel] else lower[channel]
                val dimensionWeight = if (useUpper) upperWeight[channel] else 1f - upperWeight[channel]
                pointIndex = pointIndex * gridPointStorage[channel] + coordinate
                weight *= dimensionWeight
            }
            if (weight != 0f) {
                val base = pointIndex * outputChannels
                repeat(outputChannels) { channel -> result[channel] += weight * valueStorage[base + channel] }
            }
        }
        result.copyInto(output, endIndex = outputChannels)
    }

    private companion object {
        const val MAX_CHANNELS: Int = 4
        const val MIN_GRID_POINTS: Int = 2
    }
}

private class IntArrayValue private constructor(private val values: IntArray) {
    val size: Int get() = values.size
    operator fun get(index: Int): Int = values[index]
    fun all(predicate: (Int) -> Boolean): Boolean = values.all(predicate)
    fun forEach(action: (Int) -> Unit) = values.forEach(action)
    fun copy(): IntArray = values.copyOf()

    override fun equals(other: Any?): Boolean = other is IntArrayValue && values.contentEquals(other.values)
    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        fun copyOf(values: IntArray): IntArrayValue = IntArrayValue(values.copyOf())
    }
}

private class FloatArrayValue private constructor(private val values: FloatArray) {
    val size: Int get() = values.size
    operator fun get(index: Int): Float = values[index]
    fun all(predicate: (Float) -> Boolean): Boolean = values.all(predicate)
    fun copy(): FloatArray = values.copyOf()

    override fun equals(other: Any?): Boolean = other is FloatArrayValue && values.contentEquals(other.values)
    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        fun copyOf(values: FloatArray): FloatArrayValue = FloatArrayValue(values.copyOf())
    }
}
