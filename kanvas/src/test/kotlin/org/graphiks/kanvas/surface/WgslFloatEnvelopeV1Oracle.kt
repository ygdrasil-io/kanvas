@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1

/**
 * Independent W5a oracle. All node values are intervals over real arithmetic,
 * rounded outward to the predecessor/successor F32 values. It enumerates both
 * preserved-subnormal and FTZ results, plus fused, unfused, and reassociated
 * blend/coverage expressions. The transfer functions are independently bounded
 * from their WGSL definitions (`pow(x, 2.4)` and `pow(x, 1/2.4)`), using
 * arbitrary-precision bisection for the rational powers.
 */
internal object WgslFloatEnvelopeV1Oracle {
    sealed interface DrawResult {
        class Bounded internal constructor(
            internal val channels: List<Set<Int>>,
            internal val state: AttachmentState,
        ) : DrawResult
        data object Unbounded : DrawResult
    }

    internal class AttachmentState internal constructor(internal val linearPremul: Array<Interval>)

    fun clearAttachment(): AttachmentState = AttachmentState(Array(4) { Interval.ZERO })

    /** Interprets the published program/binding graph and produces its direct RGBA8 code sets. */
    fun draw(
        table: MaterialPlanTable,
        root: MaterialPlanRef,
        destination: AttachmentState = clearAttachment(),
        coverageF32: Float = 1f,
    ): DrawResult {
        if (!coverageF32.isFinite()) return DrawResult.Unbounded
        val encoded = try {
            evaluateProgram(table, root, destination.linearPremul, Interval.input(coverageF32))
        } catch (_: IllegalArgumentException) {
            return DrawResult.Unbounded
        }
        val codes = encoded.map(::codesFor)
        if (codes.any { it == null }) return DrawResult.Unbounded
        val exactCodes = codes.filterNotNull()
        if (exactCodes.any { it.size > 2 || it.maxOrNull()!! - it.minOrNull()!! > 1 }) return DrawResult.Unbounded
        return DrawResult.Bounded(exactCodes, AttachmentState(decodeStoredAttachment(exactCodes)))
    }

    fun nextAttachment(result: DrawResult): AttachmentState? = (result as? DrawResult.Bounded)?.state

    fun assertAdmits(expected: DrawResult, observedRgba8: UByteArray) {
        require(observedRgba8.size == 4)
        val bounded = expected as? DrawResult.Bounded
            ?: error("WgslFloatEnvelopeV1 is unbounded; it cannot prove an RGBA8 envelope")
        bounded.channels.forEachIndexed { channel, codes ->
            require(observedRgba8[channel].toInt() in codes) {
                "channel=$channel observed=${observedRgba8[channel]} expected=$codes"
            }
        }
    }

    private fun evaluateProgram(
        table: MaterialPlanTable,
        root: MaterialPlanRef,
        destination: Array<Interval>,
        coverage: Interval,
    ): Array<Interval> {
        fun materialSource(ref: MaterialPlanRef): Array<Interval> {
            val entry = table.entry(ref)
            val bindings = entry.bindings
            val inputs = Inputs(
                solid = (bindings as? MaterialBindingPlan.SolidRgbaF32V1)?.copyRgbaF32()?.let {
                    arrayOf(Interval.input(it.red), Interval.input(it.green), Interval.input(it.blue), Interval.input(it.alpha))
                },
                material = if (entry.program is MaterialProgramPlan.OpacityV1 && ref.indexI32 > 0) {
                    { materialSource(MaterialPlanRef(ref.indexI32 - 1)) }
                } else null,
                destination = destination,
                coverage = coverage,
                opacity = (bindings as? MaterialBindingPlan.OpacityF32V1)?.alphaF32?.let(Interval::input),
            )
            return evaluate(sourceNode(entry.program.copyNumericOperationGraphV1()), inputs).rgba()
        }

        val entry = table.entry(root)
        val bindings = entry.bindings
        val inputs = Inputs(
            solid = (bindings as? MaterialBindingPlan.SolidRgbaF32V1)?.copyRgbaF32()?.let {
                arrayOf(Interval.input(it.red), Interval.input(it.green), Interval.input(it.blue), Interval.input(it.alpha))
            },
            material = if (entry.program is MaterialProgramPlan.OpacityV1 && root.indexI32 > 0) {
                { materialSource(MaterialPlanRef(root.indexI32 - 1)) }
            } else null,
            destination = destination,
            coverage = coverage,
            opacity = (bindings as? MaterialBindingPlan.OpacityF32V1)?.alphaF32?.let(Interval::input),
        )
        return evaluate(entry.program.copyNumericOperationGraphV1().root, inputs).rgba()
    }

    private data class Inputs(
        val solid: Array<Interval>?,
        val material: (() -> Array<Interval>)?,
        val destination: Array<Interval>,
        val coverage: Interval,
        val opacity: Interval?,
    )

    private sealed interface Value {
        fun rgba(): Array<Interval> = error("Expected RGBA")
        fun scalar(): Interval = error("Expected scalar")
    }
    private class Rgba(private val value: Array<Interval>) : Value {
        override fun rgba(): Array<Interval> = value.copyOf()
    }
    private data class Scalar(private val value: Interval) : Value { override fun scalar(): Interval = value }

    private fun sourceNode(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node =
        graph.root.inputs.single().inputs.single().inputs.single().inputs[1].inputs[0]

    private fun evaluate(node: NumericOperationGraphV1.Node, inputs: Inputs): Value = when (node.operation) {
        NumericOperationGraphV1.Operation.INPUT_SOLID_SRGBA_STRAIGHT -> Rgba(requireNotNull(inputs.solid))
        NumericOperationGraphV1.Operation.INPUT_MATERIAL_LINEAR_PREMUL -> Rgba(requireNotNull(inputs.material).invoke())
        NumericOperationGraphV1.Operation.INPUT_DESTINATION_LINEAR_PREMUL -> Rgba(inputs.destination)
        NumericOperationGraphV1.Operation.INPUT_COVERAGE_F32 -> Scalar(inputs.coverage)
        NumericOperationGraphV1.Operation.CONSTANT_TRANSPARENT -> Rgba(Array(4) { Interval.ZERO })
        NumericOperationGraphV1.Operation.SRGB_TO_LINEAR -> evaluate(node.inputs.single(), inputs).rgba().let {
            Rgba(arrayOf(toLinear(it[0]), toLinear(it[1]), toLinear(it[2]), it[3]))
        }
        NumericOperationGraphV1.Operation.PREMULTIPLY -> evaluate(node.inputs.single(), inputs).rgba().let {
            Rgba(arrayOf(it[0] * it[3], it[1] * it[3], it[2] * it[3], it[3]))
        }
        NumericOperationGraphV1.Operation.OPACITY_F32 -> evaluate(node.inputs.single(), inputs).rgba().let { value ->
            val opacity = requireNotNull(inputs.opacity)
            Rgba(Array(4) { value[it] * opacity })
        }
        NumericOperationGraphV1.Operation.SRC_OVER -> {
            val src = evaluate(node.inputs[0], inputs).rgba()
            val dst = evaluate(node.inputs[1], inputs).rgba()
            val inverseAlpha = Interval.ONE - src[3]
            Rgba(Array(4) { sourceOver(src[it], dst[it], inverseAlpha) })
        }
        NumericOperationGraphV1.Operation.APPLY_COVERAGE_F32 -> {
            val dst = evaluate(node.inputs[0], inputs).rgba()
            val blended = evaluate(node.inputs[1], inputs).rgba()
            val coverage = evaluate(node.inputs[2], inputs).scalar()
            Rgba(Array(4) { applyCoverage(dst[it], blended[it], coverage) })
        }
        NumericOperationGraphV1.Operation.LINEAR_TO_SRGB_ATTACHMENT -> evaluate(node.inputs.single(), inputs).rgba().let {
            Rgba(arrayOf(toEncoded(it[0]), toEncoded(it[1]), toEncoded(it[2]), it[3]))
        }
        NumericOperationGraphV1.Operation.CLAMP_01 -> evaluate(node.inputs.single(), inputs).rgba().let {
            Rgba(Array(4) { index -> it[index].clamp01() })
        }
        NumericOperationGraphV1.Operation.QUANTIZE_UNORM8 -> evaluate(node.inputs.single(), inputs)
    }

    private fun sourceOver(source: Interval, destination: Interval, inverseAlpha: Interval): Interval = hull(
        source + destination * inverseAlpha,
        fma(destination, inverseAlpha, source),
    )

    private fun applyCoverage(destination: Interval, blended: Interval, coverage: Interval): Interval = hull(
        destination + coverage * (blended - destination),
        (Interval.ONE - coverage) * destination + coverage * blended,
        fma(coverage, blended - destination, destination),
    )

    private fun fma(a: Interval, b: Interval, c: Interval): Interval = f32Envelope(
        exactBinary(exactBinary(a, b, BigDecimal::multiply), c, BigDecimal::add),
    )

    private fun toLinear(value: Interval): Interval = f32Envelope(piecewiseTransfer(
        value,
        SRGB_BREAK,
        { x -> x.divide(SRGB_LINEAR_SCALE, MC) },
        { x -> rationalPower(x.add(SRGB_OFFSET, MC).divide(SRGB_ENCODE_SCALE, MC), 12, 5) },
    ))

    private fun toEncoded(value: Interval): Interval = f32Envelope(piecewiseTransfer(
        value,
        LINEAR_BREAK,
        { x -> x.multiply(SRGB_LINEAR_SCALE, MC) },
        { x -> rationalPower(x, 5, 12).let { power ->
            Interval(
                power.lower.multiply(SRGB_ENCODE_SCALE, MC).subtract(SRGB_OFFSET, MC),
                power.upper.multiply(SRGB_ENCODE_SCALE, MC).subtract(SRGB_OFFSET, MC),
            )
        } },
    ))

    private fun piecewiseTransfer(value: Interval, split: BigDecimal, lower: (BigDecimal) -> BigDecimal, upper: (BigDecimal) -> Interval): Interval = when {
        value.upper <= split -> Interval(lower(value.lower), lower(value.upper))
        value.lower >= split -> upper(value.lower).hull(upper(value.upper))
        else -> hull(Interval(lower(value.lower), lower(split)), upper(split).hull(upper(value.upper)))
    }

    /** Positive rational powers are enclosed with BigDecimal bisection, not Double math. */
    private fun rationalPower(value: BigDecimal, numerator: Int, denominator: Int): Interval {
        require(value >= BigDecimal.ZERO)
        val root = nthRoot(value, denominator)
        return Interval(root.lower.pow(numerator, MC), root.upper.pow(numerator, MC))
    }

    private fun nthRoot(value: BigDecimal, degree: Int): Interval {
        if (value == BigDecimal.ZERO) return Interval.ZERO
        var low = BigDecimal.ZERO
        var high = value.max(BigDecimal.ONE)
        repeat(260) {
            val middle = low.add(high, MC).divide(BigDecimal.TWO, MC)
            if (middle.pow(degree, MC) <= value) low = middle else high = middle
        }
        return Interval(low, high)
    }

    private fun decodeStoredAttachment(codes: List<Set<Int>>): Array<Interval> = Array(4) { channel ->
        val encoded = Interval(
            BigDecimal(codes[channel].minOrNull()!!).divide(UNORM_MAX, MC),
            BigDecimal(codes[channel].maxOrNull()!!).divide(UNORM_MAX, MC),
        )
        if (channel < 3) toLinear(encoded) else encoded
    }

    private fun codesFor(value: Interval): Set<Int>? {
        val clamped = value.clamp01()
        // rgba8unorm stores the nearest code: boundaries are k + 1/2, not k.
        val first = clamped.lower.multiply(UNORM_MAX, MC).subtract(HALF, MC)
            .setScale(0, RoundingMode.CEILING).toInt().coerceIn(0, 255)
        val last = clamped.upper.multiply(UNORM_MAX, MC).add(HALF, MC)
            .setScale(0, RoundingMode.FLOOR).toInt().coerceIn(0, 255)
        return (first..last).toSet().takeIf { it.size <= 2 && it.maxOrNull()!! - it.minOrNull()!! <= 1 }
    }

    private fun f32Envelope(exact: Interval): Interval = hull(preserveF32(exact), flushSubnormal(exact))

    /** Containment expands rounded endpoints to adjacent F32 values, never a Double ULP. */
    private fun preserveF32(exact: Interval): Interval = Interval(
        decimal(java.lang.Math.nextDown(exact.lower.toFloat())),
        decimal(java.lang.Math.nextUp(exact.upper.toFloat())),
    )

    private fun flushSubnormal(exact: Interval): Interval = if (
        exact.lower.abs() < F32_MIN_NORMAL || exact.upper.abs() < F32_MIN_NORMAL
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
    private fun Interval.hull(other: Interval): Interval = hull(this, other)
    private fun hull(vararg intervals: Interval): Interval = Interval(intervals.minOf { it.lower }, intervals.maxOf { it.upper })

    internal data class Interval(val lower: BigDecimal, val upper: BigDecimal) {
        init { require(lower <= upper) }
        companion object {
            val ZERO = Interval(BigDecimal.ZERO, BigDecimal.ZERO)
            val ONE = Interval(BigDecimal.ONE, BigDecimal.ONE)
            fun input(value: Float): Interval {
                require(value.isFinite())
                return Interval(decimal(value), decimal(value))
            }
        }
    }

    private fun decimal(value: Float): BigDecimal {
        require(value.isFinite()) { "An unbounded F32 result cannot prove an RGBA8 set" }
        return BigDecimal.valueOf(value.toDouble())
    }

    private val MC = MathContext(120, RoundingMode.HALF_EVEN)
    private val SRGB_BREAK = BigDecimal("0.04045")
    private val LINEAR_BREAK = BigDecimal("0.0031308")
    private val SRGB_LINEAR_SCALE = BigDecimal("12.92")
    private val SRGB_OFFSET = BigDecimal("0.055")
    private val SRGB_ENCODE_SCALE = BigDecimal("1.055")
    private val F32_MIN_NORMAL = BigDecimal("1.17549435e-38")
    private val UNORM_MAX = BigDecimal("255")
    private val HALF = BigDecimal("0.5")
}
