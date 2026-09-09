package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.toEncoded

/**
 * W5a's public numeric gate: each channel must be a singleton or two adjacent
 * UNORM8 codes after the explicitly modeled F32 arithmetic.
 */
internal object WgslFloatEnvelopeV1Oracle {
    data class Inputs(
        val solidSrgbaStraight: FloatArray? = null,
        val materialLinearPremul: FloatArray? = null,
        val destinationLinearPremul: FloatArray,
        val coverageF32: Float,
        val opacityF32: Float = 1f,
    )

    /** Evaluates the graph's material portion with F32 operations only. */
    fun evaluateMaterialSource(graph: NumericOperationGraphV1, inputs: Inputs): FloatArray =
        evaluate(sourceNode(graph), inputs).rgba()

    /** Evaluates the graph through source-over and post-raster coverage, before attachment encoding. */
    fun evaluateLinearOutput(graph: NumericOperationGraphV1, inputs: Inputs): FloatArray =
        evaluate(coveredNode(graph), inputs).rgba()

    /** Evaluates the declared attachment tail; quantization remains an outward envelope. */
    fun evaluateAttachmentOutput(graph: NumericOperationGraphV1, inputs: Inputs): FloatArray =
        evaluate(graph.root, inputs).rgba()

    fun admissibleUnorm8(valueF32: Float): IntRange {
        val scaled = valueF32.coerceIn(0f, 1f) * 255f
        val lower = floor(scaled.toDouble()).toInt().coerceIn(0, 255)
        val upper = ceil(scaled.toDouble()).toInt().coerceIn(0, 255)
        require(upper - lower <= 1) { "WgslFloatEnvelopeV1 must be singleton or adjacent" }
        return lower..upper
    }

    fun assertAdmits(expectedLinearPremul: FloatArray, observedRgba8: UByteArray) {
        require(expectedLinearPremul.size == 4)
        require(observedRgba8.size == 4)
        expectedLinearPremul.indices.forEach { channel ->
            require(observedRgba8[channel].toInt() in admissibleUnorm8(expectedLinearPremul[channel])) {
                "channel=$channel observed=${observedRgba8[channel]} expected=${admissibleUnorm8(expectedLinearPremul[channel])}"
            }
        }
    }

    private sealed interface Value {
        fun rgba(): FloatArray = error("Expected RGBA value")
        fun scalar(): Float = error("Expected scalar value")
    }
    private class Rgba(private val values: FloatArray) : Value {
        init { require(values.size == 4) }
        override fun rgba(): FloatArray = values.copyOf()
    }
    private data class Scalar(private val value: Float) : Value { override fun scalar(): Float = value }

    private fun sourceNode(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node {
        val covered = coveredNode(graph)
        return covered.inputs[1].inputs[0]
    }

    private fun coveredNode(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node =
        graph.root.inputs.single().inputs.single().inputs.single()

    private fun evaluate(node: NumericOperationGraphV1.Node, inputs: Inputs): Value = when (node.operation) {
        NumericOperationGraphV1.Operation.INPUT_SOLID_SRGBA_STRAIGHT -> Rgba(requireNotNull(inputs.solidSrgbaStraight))
        NumericOperationGraphV1.Operation.INPUT_MATERIAL_LINEAR_PREMUL -> Rgba(requireNotNull(inputs.materialLinearPremul))
        NumericOperationGraphV1.Operation.INPUT_DESTINATION_LINEAR_PREMUL -> Rgba(inputs.destinationLinearPremul)
        NumericOperationGraphV1.Operation.INPUT_COVERAGE_F32 -> Scalar(inputs.coverageF32)
        NumericOperationGraphV1.Operation.CONSTANT_TRANSPARENT -> Rgba(floatArrayOf(0f, 0f, 0f, 0f))
        NumericOperationGraphV1.Operation.SRGB_TO_LINEAR -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(floatArrayOf(
                f32(ColorTransferFunction.sRgb.toLinear(value[0])),
                f32(ColorTransferFunction.sRgb.toLinear(value[1])),
                f32(ColorTransferFunction.sRgb.toLinear(value[2])),
                f32(value[3]),
            ))
        }
        NumericOperationGraphV1.Operation.PREMULTIPLY -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(floatArrayOf(f32(value[0] * value[3]), f32(value[1] * value[3]), f32(value[2] * value[3]), value[3]))
        }
        NumericOperationGraphV1.Operation.OPACITY_F32 -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(FloatArray(4) { channel -> f32(value[channel] * inputs.opacityF32) })
        }
        NumericOperationGraphV1.Operation.SRC_OVER -> {
            val source = evaluate(node.inputs[0], inputs).rgba()
            val destination = evaluate(node.inputs[1], inputs).rgba()
            Rgba(FloatArray(4) { channel -> f32(source[channel] + destination[channel] * (1f - source[3])) })
        }
        NumericOperationGraphV1.Operation.APPLY_COVERAGE_F32 -> {
            val destination = evaluate(node.inputs[0], inputs).rgba()
            val blended = evaluate(node.inputs[1], inputs).rgba()
            val coverage = evaluate(node.inputs[2], inputs).scalar()
            Rgba(FloatArray(4) { channel -> f32(destination[channel] + coverage * (blended[channel] - destination[channel])) })
        }
        NumericOperationGraphV1.Operation.LINEAR_TO_SRGB_ATTACHMENT -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(floatArrayOf(
                f32(ColorTransferFunction.sRgb.toEncoded(value[0])),
                f32(ColorTransferFunction.sRgb.toEncoded(value[1])),
                f32(ColorTransferFunction.sRgb.toEncoded(value[2])),
                value[3],
            ))
        }
        NumericOperationGraphV1.Operation.CLAMP_01 -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(FloatArray(4) { channel -> value[channel].coerceIn(0f, 1f) })
        }
        NumericOperationGraphV1.Operation.QUANTIZE_UNORM8 -> evaluate(node.inputs.single(), inputs)
    }

    /** JVM Float gives the mandatory F32 rounding; zero is an allowed FTZ result. */
    private fun f32(value: Float): Float = if (value != 0f && kotlin.math.abs(value) < F32_MIN_NORMAL) 0f else value

    private const val F32_MIN_NORMAL: Float = 1.17549435E-38f
}
