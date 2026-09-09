@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1

/**
 * Independent public W5a numeric oracle. It propagates outward-rounded decimal
 * intervals over the declared DAG; it intentionally does not call the renderer
 * or its colour-transfer helpers. Every arithmetic node contains both the
 * preserved-subnormal and FTZ outcomes. Blend and coverage additionally hull
 * their permitted unfused, reassociated, and fused forms before UNORM8 codes
 * are derived.
 */
internal object WgslFloatEnvelopeV1Oracle {
    data class Inputs(
        val solidSrgbaStraight: FloatArray? = null,
        val materialLinearPremul: FloatArray? = null,
        val destinationLinearPremul: FloatArray,
        val coverageF32: Float,
        val opacityF32: Float = 1f,
    )

    fun evaluateMaterialSource(graph: NumericOperationGraphV1, inputs: Inputs): FloatArray =
        evaluate(sourceNode(graph), inputs).rgbaRepresentative()

    fun evaluateLinearOutput(graph: NumericOperationGraphV1, inputs: Inputs): FloatArray =
        evaluate(coveredNode(graph), inputs).rgbaRepresentative()

    fun evaluateAttachmentOutput(graph: NumericOperationGraphV1, inputs: Inputs): FloatArray {
        val attachment = evaluate(graph.root, inputs).rgba()
        attachment.forEach(::codesFor)
        return attachment.map(Interval::representative).toFloatArray()
    }

    fun admissibleUnorm8(valueF32: Float): IntRange = codesFor(Interval.input(valueF32)).toRange()

    fun assertAdmits(expectedLinearPremul: FloatArray, observedRgba8: UByteArray) {
        require(expectedLinearPremul.size == 4)
        require(observedRgba8.size == 4)
        expectedLinearPremul.indices.forEach { channel ->
            val codes = codesFor(Interval.input(expectedLinearPremul[channel]))
            require(observedRgba8[channel].toInt() in codes) {
                "channel=$channel observed=${observedRgba8[channel]} expected=$codes"
            }
        }
    }

    private sealed interface Value {
        fun rgba(): Array<Interval> = error("Expected RGBA value")
        fun scalar(): Interval = error("Expected scalar value")
    }
    private class Rgba(private val values: Array<Interval>) : Value {
        init { require(values.size == 4) }
        override fun rgba(): Array<Interval> = values.copyOf()
    }
    private data class Scalar(private val value: Interval) : Value { override fun scalar(): Interval = value }

    private fun Value.rgbaRepresentative(): FloatArray = rgba().map(Interval::representative).toFloatArray()

    private fun sourceNode(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node =
        coveredNode(graph).inputs[1].inputs[0]

    private fun coveredNode(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node =
        graph.root.inputs.single().inputs.single().inputs.single()

    private fun evaluate(node: NumericOperationGraphV1.Node, inputs: Inputs): Value = when (node.operation) {
        NumericOperationGraphV1.Operation.INPUT_SOLID_SRGBA_STRAIGHT -> Rgba(intervals(requireNotNull(inputs.solidSrgbaStraight)))
        NumericOperationGraphV1.Operation.INPUT_MATERIAL_LINEAR_PREMUL -> Rgba(intervals(requireNotNull(inputs.materialLinearPremul)))
        NumericOperationGraphV1.Operation.INPUT_DESTINATION_LINEAR_PREMUL -> Rgba(intervals(inputs.destinationLinearPremul))
        NumericOperationGraphV1.Operation.INPUT_COVERAGE_F32 -> Scalar(Interval.input(inputs.coverageF32))
        NumericOperationGraphV1.Operation.CONSTANT_TRANSPARENT -> Rgba(Array(4) { Interval.ZERO })
        NumericOperationGraphV1.Operation.SRGB_TO_LINEAR -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(arrayOf(toLinear(value[0]), toLinear(value[1]), toLinear(value[2]), value[3]))
        }
        NumericOperationGraphV1.Operation.PREMULTIPLY -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(arrayOf(value[0] * value[3], value[1] * value[3], value[2] * value[3], value[3]))
        }
        NumericOperationGraphV1.Operation.OPACITY_F32 -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            val opacity = Interval.input(inputs.opacityF32)
            Rgba(Array(4) { channel -> value[channel] * opacity })
        }
        NumericOperationGraphV1.Operation.SRC_OVER -> {
            val source = evaluate(node.inputs[0], inputs).rgba()
            val destination = evaluate(node.inputs[1], inputs).rgba()
            val inverseAlpha = Interval.ONE - source[3]
            Rgba(Array(4) { channel -> sourceOver(source[channel], destination[channel], inverseAlpha) })
        }
        NumericOperationGraphV1.Operation.APPLY_COVERAGE_F32 -> {
            val destination = evaluate(node.inputs[0], inputs).rgba()
            val blended = evaluate(node.inputs[1], inputs).rgba()
            val coverage = evaluate(node.inputs[2], inputs).scalar()
            Rgba(Array(4) { channel -> applyCoverage(destination[channel], blended[channel], coverage) })
        }
        NumericOperationGraphV1.Operation.LINEAR_TO_SRGB_ATTACHMENT -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(arrayOf(toEncoded(value[0]), toEncoded(value[1]), toEncoded(value[2]), value[3]))
        }
        NumericOperationGraphV1.Operation.CLAMP_01 -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            Rgba(Array(4) { value[it].clamp01() })
        }
        NumericOperationGraphV1.Operation.QUANTIZE_UNORM8 -> evaluate(node.inputs.single(), inputs)
    }

    /** Enumerates source + destination * inverseAlpha with FMA and separate rounding. */
    private fun sourceOver(source: Interval, destination: Interval, inverseAlpha: Interval): Interval = hull(
        source + (destination * inverseAlpha),
        fma(destination, inverseAlpha, source),
    )

    /** Enumerates direct, reassociated, and fused coverage forms. */
    private fun applyCoverage(destination: Interval, blended: Interval, coverage: Interval): Interval = hull(
        destination + coverage * (blended - destination),
        (Interval.ONE - coverage) * destination + coverage * blended,
        fma(coverage, blended - destination, destination),
    )

    private fun fma(a: Interval, b: Interval, c: Interval): Interval = f32Envelope(
        exactBinary(exactBinary(a, b) { x, y -> x.multiply(y, MC) }, c) { x, y -> x.add(y, MC) },
    )

    private fun toLinear(value: Interval): Interval = f32Envelope(transfer(value) { channel ->
        if (channel <= SRGB_BREAK) channel.divide(SRGB_LINEAR_SCALE, MC)
        else decimalFromDouble(StrictMath.pow(channel.add(SRGB_OFFSET, MC).divide(SRGB_ENCODE_SCALE, MC).toDouble(), 2.4))
    })

    private fun toEncoded(value: Interval): Interval = f32Envelope(transfer(value) { channel ->
        if (channel <= LINEAR_BREAK) channel.multiply(SRGB_LINEAR_SCALE, MC)
        else decimalFromDouble(StrictMath.pow(channel.toDouble(), 1.0 / 2.4)).multiply(SRGB_ENCODE_SCALE, MC).subtract(SRGB_OFFSET, MC)
    })

    private fun transfer(value: Interval, transform: (BigDecimal) -> BigDecimal): Interval = Interval(
        outwardDown(transform(value.lower)),
        outwardUp(transform(value.upper)),
    )

    private fun intervals(values: FloatArray): Array<Interval> {
        require(values.size == 4)
        return Array(4) { Interval.input(values[it]) }
    }

    /** Exact decimal interval followed by both normal F32 and FTZ branches. */
    private fun f32Envelope(exact: Interval): Interval = hull(preserveF32(exact), flushSubnormalToZero(exact))

    private fun preserveF32(exact: Interval): Interval = Interval(
        outwardDown(BigDecimal.valueOf(exact.lower.toFloat().toDouble())),
        outwardUp(BigDecimal.valueOf(exact.upper.toFloat().toDouble())),
    )

    private fun flushSubnormalToZero(exact: Interval): Interval = if (
        exact.lower.abs() < F32_MIN_NORMAL_DECIMAL || exact.upper.abs() < F32_MIN_NORMAL_DECIMAL
    ) hull(preserveF32(exact), Interval.ZERO) else preserveF32(exact)

    private fun exactBinary(left: Interval, right: Interval, operation: (BigDecimal, BigDecimal) -> BigDecimal): Interval {
        val values = listOf(
            operation(left.lower, right.lower), operation(left.lower, right.upper),
            operation(left.upper, right.lower), operation(left.upper, right.upper),
        )
        return Interval(values.minOrNull()!!, values.maxOrNull()!!)
    }

    private operator fun Interval.plus(other: Interval): Interval = f32Envelope(exactBinary(this, other) { a, b -> a.add(b, MC) })
    private operator fun Interval.minus(other: Interval): Interval = f32Envelope(exactBinary(this, other) { a, b -> a.subtract(b, MC) })
    private operator fun Interval.times(other: Interval): Interval = f32Envelope(exactBinary(this, other) { a, b -> a.multiply(b, MC) })
    private fun Interval.clamp01(): Interval = Interval(lower.max(BigDecimal.ZERO), upper.min(BigDecimal.ONE))

    private fun hull(vararg intervals: Interval): Interval = Interval(
        intervals.minOf { it.lower }, intervals.maxOf { it.upper },
    )

    private fun codesFor(value: Interval): Set<Int> {
        val clamped = value.clamp01()
        val first = floor(clamped.lower.multiply(UNORM_MAX, MC).toDouble()).toInt().coerceIn(0, 255)
        val last = ceil(clamped.upper.multiply(UNORM_MAX, MC).toDouble()).toInt().coerceIn(0, 255)
        val codes = (first..last).toSet()
        require(codes.size <= 2 && (codes.maxOrNull()!! - codes.minOrNull()!! <= 1)) {
            "WgslFloatEnvelopeV1 produced non-adjacent or more than two UNORM8 codes: $codes from $value"
        }
        return codes
    }

    private fun Set<Int>.toRange(): IntRange = minOrNull()!!..maxOrNull()!!

    private data class Interval(val lower: BigDecimal, val upper: BigDecimal) {
        init { require(lower <= upper) }
        fun representative(): Float = lower.add(upper, MC).divide(BigDecimal("2"), MC).toFloat()
        companion object {
            val ZERO: Interval = Interval(BigDecimal.ZERO, BigDecimal.ZERO)
            val ONE: Interval = Interval(BigDecimal.ONE, BigDecimal.ONE)
            fun input(value: Float): Interval {
                require(value.isFinite()) { "W5a inputs must be finite F32 values" }
                val decimal = BigDecimal.valueOf(value.toDouble())
                return Interval(decimal, decimal)
            }
        }
    }

    private fun decimalFromDouble(value: Double): BigDecimal = BigDecimal.valueOf(value)
    private fun outwardDown(value: BigDecimal): BigDecimal = BigDecimal.valueOf(Math.nextDown(value.toDouble()))
    private fun outwardUp(value: BigDecimal): BigDecimal = BigDecimal.valueOf(Math.nextUp(value.toDouble()))

    private val MC: MathContext = MathContext(80, RoundingMode.HALF_EVEN)
    private val SRGB_BREAK = BigDecimal("0.04045")
    private val LINEAR_BREAK = BigDecimal("0.0031308")
    private val SRGB_LINEAR_SCALE = BigDecimal("12.92")
    private val SRGB_OFFSET = BigDecimal("0.055")
    private val SRGB_ENCODE_SCALE = BigDecimal("1.055")
    private val F32_MIN_NORMAL_DECIMAL = BigDecimal("1.17549435e-38")
    private val UNORM_MAX = BigDecimal("255")
}
