package org.graphiks.kanvas.color.icc

/** Immutable sequence of parsed ICC transform stages. */
public class IccTransformPipeline internal constructor(
    stages: List<IccTransformStage>,
    fingerprint: ByteArray,
) {
    private val stages: List<IccTransformStage> = stages.toList()
    private val fingerprint: ByteArray = fingerprint.copyOf()

    init {
        require(this.stages.isNotEmpty()) { "An ICC transform pipeline requires at least one stage" }
        require(this.stages.first().inputChannels == RGB_CHANNELS)
        require(this.stages.last().outputChannels == RGB_CHANNELS)
        this.stages.zipWithNext().forEach { (first, second) ->
            require(first.outputChannels == second.inputChannels) { "Adjacent ICC stages have incompatible channels" }
        }
    }

    /** Applies this directional pipeline to three components in place. */
    public fun apply(rgb: FloatArray, offset: Int) {
        require(offset >= 0 && offset <= rgb.size - RGB_CHANNELS) { "rgb must contain three channels at offset" }
        val output = FloatArray(RGB_CHANNELS)
        apply(rgb, offset, output, 0)
        output.copyInto(rgb, offset)
    }

    internal fun apply(input: FloatArray, inputOffset: Int, output: FloatArray, outputOffset: Int) {
        require(inputOffset >= 0 && inputOffset <= input.size - RGB_CHANNELS)
        require(outputOffset >= 0 && outputOffset <= output.size - RGB_CHANNELS)
        var current = FloatArray(MAX_CHANNELS)
        var next = FloatArray(MAX_CHANNELS)
        repeat(RGB_CHANNELS) { current[it] = input[inputOffset + it] }
        var channels = RGB_CHANNELS
        stages.forEach { stage ->
            check(stage.inputChannels == channels)
            stage.apply(current, next)
            channels = stage.outputChannels
            val swap = current
            current = next
            next = swap
        }
        check(channels == RGB_CHANNELS)
        repeat(RGB_CHANNELS) { output[outputOffset + it] = current[it] }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is IccTransformPipeline && fingerprint.contentEquals(other.fingerprint)

    override fun hashCode(): Int = fingerprint.contentHashCode()

    private companion object {
        const val RGB_CHANNELS: Int = 3
        const val MAX_CHANNELS: Int = 4
    }
}

internal sealed interface IccTransformStage {
    val inputChannels: Int
    val outputChannels: Int
    fun apply(input: FloatArray, output: FloatArray)
}

internal class IccCurveStage(curves: List<IccCurve>) : IccTransformStage {
    private val curves: List<IccCurve> = curves.toList()
    override val inputChannels: Int = curves.size
    override val outputChannels: Int = curves.size

    init {
        require(curves.isNotEmpty())
    }

    override fun apply(input: FloatArray, output: FloatArray) {
        repeat(outputChannels) { channel -> output[channel] = curves[channel].evaluate(input[channel]) }
    }
}

internal class IccMatrixStage(matrix: FloatArray) : IccTransformStage {
    private val matrix: FloatArray = matrix.copyOf()
    override val inputChannels: Int = 3
    override val outputChannels: Int = 3

    init {
        require(this.matrix.size == MATRIX_VALUE_COUNT && this.matrix.all(Float::isFinite))
    }

    override fun apply(input: FloatArray, output: FloatArray) {
        val x = input[0]
        val y = input[1]
        val z = input[2]
        repeat(3) { row ->
            val base = row * 3
            output[row] = matrix[base] * x + matrix[base + 1] * y + matrix[base + 2] * z + matrix[9 + row]
        }
    }

    private companion object {
        const val MATRIX_VALUE_COUNT: Int = 12
    }
}

internal class IccClutStage(private val clut: IccClut) : IccTransformStage {
    override val inputChannels: Int = clut.inputChannels
    override val outputChannels: Int = clut.outputChannels

    override fun apply(input: FloatArray, output: FloatArray) {
        clut.interpolate(input, output)
    }
}

internal class IccScaleStage(scale: FloatArray) : IccTransformStage {
    private val scale: FloatArray = scale.copyOf()
    override val inputChannels: Int = scale.size
    override val outputChannels: Int = scale.size

    init {
        require(this.scale.isNotEmpty() && this.scale.size <= 4 && this.scale.all(Float::isFinite))
    }

    override fun apply(input: FloatArray, output: FloatArray) {
        repeat(outputChannels) { channel -> output[channel] = input[channel] * scale[channel] }
    }
}

internal class IccClampStage(
    override val inputChannels: Int,
) : IccTransformStage {
    override val outputChannels: Int = inputChannels

    init {
        require(inputChannels in 1..4)
    }

    override fun apply(input: FloatArray, output: FloatArray) {
        repeat(outputChannels) { channel ->
            output[channel] = if (input[channel].isFinite()) input[channel].coerceIn(0f, 1f) else 0f
        }
    }
}
