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
 * from their WGSL definitions (`pow(x, 2.4)` and `pow(x, 1/2.4)`) as
 * `exp2(y * log2(x))`, using arbitrary-precision directed series bounds.
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
        } catch (_: ArithmeticException) {
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

    /**
     * This is the complete two-term closure of the normative SrcOver
     * expression `source + destination * (1 - source.a)`: ordinary evaluation
     * and the only legal multiply/add fusion. There is no third summand to
     * reassociate.
     */
    private fun sourceOver(source: Interval, destination: Interval, inverseAlpha: Interval): Interval =
        sumOfProducts(source, Interval.ONE, destination, inverseAlpha)

    /**
     * The normative coverage expression is
     * `destination + coverage * (blended - destination)`. The direct form is
     * closed over its ordinary and FMA evaluations. The second branch is the
     * fixed-function SrcOver equivalent `destination*(1-coverage) +
     * blended*coverage`, closed over both ordinary and permitted FMA forms.
     */
    private fun applyCoverage(destination: Interval, blended: Interval, coverage: Interval): Interval {
        val inverseCoverage = Interval.ONE - coverage
        return hull(
            sumOfProducts(destination, inverseCoverage, blended, coverage),
            addProduct(destination, coverage, blended - destination),
        )
    }

    /** All legal f32 evaluation forms of `a*b + c*d`. */
    private fun sumOfProducts(a: Interval, b: Interval, c: Interval, d: Interval): Interval {
        val left = a * b
        val right = c * d
        return hull(
            left + right,
            fma(a, b, right),
            fma(c, d, left),
        )
    }

    /** All legal f32 evaluation forms of `addend + multiplier*multiplicand`. */
    private fun addProduct(addend: Interval, multiplier: Interval, multiplicand: Interval): Interval = hull(
        addend + multiplier * multiplicand,
        fma(multiplier, multiplicand, addend),
    )

    private fun fma(a: Interval, b: Interval, c: Interval): Interval = f32Envelope(
        directedTernary(a, b, c),
    )

    private fun toLinear(value: Interval): Interval = piecewiseTransfer(
        value,
        SRGB_BREAK,
        { x -> wgslDivide(Interval.point(x), Interval.point(SRGB_LINEAR_SCALE)) },
        { x -> wgslPow(
            wgslDivide(Interval.point(x) + Interval.point(SRGB_OFFSET), Interval.point(SRGB_ENCODE_SCALE)),
            Interval.point(SRGB_TO_LINEAR_EXPONENT),
        ) },
    )

    private fun toEncoded(value: Interval): Interval = piecewiseTransfer(
        value,
        LINEAR_BREAK,
        { x -> Interval.point(x) * Interval.point(SRGB_LINEAR_SCALE) },
        { x -> wgslPow(
            Interval.point(x),
            wgslDivide(Interval.ONE, Interval.point(SRGB_TO_LINEAR_EXPONENT)),
        ) * Interval.point(SRGB_ENCODE_SCALE) - Interval.point(SRGB_OFFSET) },
    )

    private fun piecewiseTransfer(value: Interval, split: BigDecimal, lower: (BigDecimal) -> Interval, upper: (BigDecimal) -> Interval): Interval = when {
        value.upper <= split -> Interval(lower(value.lower).lower, lower(value.upper).upper)
        value.lower >= split -> upper(value.lower).hull(upper(value.upper))
        else -> hull(Interval(lower(value.lower).lower, lower(split).upper), upper(split).hull(upper(value.upper)))
    }

    /**
     * Directed rational-root bisection is retained as an exact-arithmetic
     * primitive. Ambiguous middle points are never guessed: if the directed
     * powers overlap the target, no narrower proven bracket exists and the
     * caller must report Unbounded.
     */
    private fun nthRoot(value: BigDecimal, degree: Int): Interval? {
        if (value == BigDecimal.ZERO) return Interval.ZERO
        var low = BigDecimal.ZERO
        var high = value.max(BigDecimal.ONE)
        repeat(ROOT_BISECTION_STEPS) {
            val middle = downDivide(downAdd(low, high), BigDecimal.TWO)
            when {
                powUp(middle, degree) <= value -> low = middle
                powDown(middle, degree) >= value -> high = middle
                else -> return null
            }
        }
        return Interval(low, high)
    }

    private fun decodeStoredAttachment(codes: List<Set<Int>>): Array<Interval> = Array(4) { channel ->
        val encoded = Interval(
            downDivide(BigDecimal(codes[channel].minOrNull()!!), UNORM_MAX),
            upDivide(BigDecimal(codes[channel].maxOrNull()!!), UNORM_MAX),
        )
        if (channel < 3) toLinear(encoded) else encoded
    }

    private fun codesFor(value: Interval): Set<Int>? {
        val clamped = value.clamp01()
        // rgba8unorm stores the nearest code: boundaries are k + 1/2, not k.
        val first = downSubtract(downMultiply(clamped.lower, UNORM_MAX), HALF)
            .setScale(0, RoundingMode.CEILING).toInt().coerceIn(0, 255)
        val last = upAdd(upMultiply(clamped.upper, UNORM_MAX), HALF)
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

    private fun directedTernary(a: Interval, b: Interval, c: Interval): Interval {
        val lows = mutableListOf<BigDecimal>()
        val highs = mutableListOf<BigDecimal>()
        listOf(a.lower, a.upper).forEach { x -> listOf(b.lower, b.upper).forEach { y -> listOf(c.lower, c.upper).forEach { z ->
            lows += downAdd(downMultiply(x, y), z)
            highs += upAdd(upMultiply(x, y), z)
        } } }
        return Interval(lows.minOrNull()!!, highs.maxOrNull()!!)
    }

    private fun directedBinary(left: Interval, right: Interval, lower: (BigDecimal, BigDecimal) -> BigDecimal, upper: (BigDecimal, BigDecimal) -> BigDecimal): Interval {
        val pairs = listOf(left.lower to right.lower, left.lower to right.upper, left.upper to right.lower, left.upper to right.upper)
        return Interval(pairs.minOf { lower(it.first, it.second) }, pairs.maxOf { upper(it.first, it.second) })
    }

    private operator fun Interval.plus(other: Interval): Interval = f32Envelope(directedBinary(this, other, ::downAdd, ::upAdd))
    private operator fun Interval.minus(other: Interval): Interval = f32Envelope(directedBinary(this, other, ::downSubtract, ::upSubtract))
    private operator fun Interval.times(other: Interval): Interval = f32Envelope(directedBinary(this, other, ::downMultiply, ::upMultiply))
    private fun Interval.clamp01(): Interval = Interval(lower.max(BigDecimal.ZERO), upper.min(BigDecimal.ONE))
    private fun Interval.hull(other: Interval): Interval = hull(this, other)
    private fun hull(vararg intervals: Interval): Interval = Interval(intervals.minOf { it.lower }, intervals.maxOf { it.upper })

    /** WGSL f32 division has a 2.5-ULP accuracy bound for normal divisors. */
    private fun wgslDivide(left: Interval, right: Interval): Interval {
        require(right.lower > BigDecimal.ZERO || right.upper < BigDecimal.ZERO) {
            "Division crossing zero cannot prove a finite W5a envelope"
        }
        val smallestDivisor = minOf(right.lower.abs(), right.upper.abs())
        require(smallestDivisor >= F32_MIN_NORMAL && right.lower.abs() <= F32_MAX_NORMAL && right.upper.abs() <= F32_MAX_NORMAL) {
            "WGSL division accuracy is unbounded outside its normal-divisor domain"
        }
        return f32Envelope(expandUlps(directedBinary(left, right, ::downDivide, ::upDivide), DIVISION_ULPS))
    }

    /**
     * WGSL specifies `pow(x,y)` as inherited from `exp2(y * log2(x))`.
     * Each built-in below applies the specification's operation-level bound;
     * no implementation-specific transfer-function allowance is assumed.
     */
    private fun wgslPow(base: Interval, exponent: Interval): Interval {
        require(base.lower > BigDecimal.ZERO) { "pow outside its positive finite proof domain" }
        return wgslExp2(exponent * wgslLog2(base))
    }

    private fun wgslLog2(value: Interval): Interval {
        require(value.lower > BigDecimal.ZERO) { "log2 outside its finite proof domain" }
        val exact = exactLog2(value)
        val absolute = f32Envelope(expandAbsolute(exact, LOG2_UNIT_INTERVAL_ABSOLUTE_ERROR))
        val ulps = f32Envelope(expandUlps(exact, LOG2_OUTSIDE_UNIT_INTERVAL_ULPS))
        return when {
            value.lower >= HALF && value.upper <= BigDecimal.TWO -> absolute
            value.upper < HALF || value.lower > BigDecimal.TWO -> ulps
            else -> hull(absolute, ulps)
        }
    }

    private fun wgslExp2(value: Interval): Interval {
        val exact = exactExp2(value)
        // WGSL's exp2 bound is (3 + 2*|x|) ULP at each mathematical result.
        val factor = upAdd(BigDecimal("3"), upMultiply(BigDecimal.TWO, maxOf(value.lower.abs(), value.upper.abs())))
        return f32Envelope(expandUlps(exact, factor))
    }

    private fun expandAbsolute(value: Interval, error: BigDecimal): Interval = Interval(
        downSubtract(value.lower, error),
        upAdd(value.upper, error),
    )

    private fun expandUlps(value: Interval, count: BigDecimal): Interval {
        val ulp = maxOf(f32UlpAt(value.lower), f32UlpAt(value.upper))
        val error = upMultiply(count, ulp)
        return expandAbsolute(value, error)
    }

    /** Exact f32 ULP bracket around an arbitrary-precision real endpoint. */
    private fun f32UlpAt(value: BigDecimal): BigDecimal {
        val rounded = value.toFloat()
        require(rounded.isFinite()) { "An infinite builtin result cannot prove an RGBA8 envelope" }
        val center = decimal(rounded)
        val lower = decimal(java.lang.Math.nextDown(rounded))
        val upper = decimal(java.lang.Math.nextUp(rounded))
        return when {
            value < center -> center.subtract(lower)
            value > center -> upper.subtract(center)
            else -> minOf(center.subtract(lower), upper.subtract(center))
        }
    }

    /** Arbitrary-precision real `log2`, bounded outward at every primitive. */
    private fun exactLog2(value: Interval): Interval {
        val lower = naturalLog(value.lower)
        val upper = naturalLog(value.upper)
        return Interval(
            downDivide(lower.lower, LN_TWO.upper),
            upDivide(upper.upper, LN_TWO.lower),
        )
    }

    private fun naturalLog(value: BigDecimal): Interval {
        require(value > BigDecimal.ZERO)
        var normalized = value
        var exponent = 0
        while (normalized < BigDecimal.ONE) {
            // Scaling by a power of two is exact in BigDecimal; preserving the
            // exact normalization avoids a branch proof depending on rounding.
            normalized = normalized.multiply(BigDecimal.TWO)
            exponent--
        }
        while (normalized >= BigDecimal.TWO) {
            normalized = normalized.divide(BigDecimal.TWO)
            exponent++
        }
        val mantissa = atanhLog(normalized)
        return if (exponent >= 0) Interval(
            downAdd(mantissa.lower, downMultiply(LN_TWO.lower, BigDecimal(exponent))),
            upAdd(mantissa.upper, upMultiply(LN_TWO.upper, BigDecimal(exponent))),
        ) else Interval(
            downSubtract(mantissa.lower, upMultiply(LN_TWO.upper, BigDecimal(-exponent))),
            upSubtract(mantissa.upper, downMultiply(LN_TWO.lower, BigDecimal(-exponent))),
        )
    }

    /** ln(x) for x in [1,2), using ln(x)=2*atanh((x-1)/(x+1)). */
    private fun atanhLog(value: BigDecimal): Interval {
        val z = Interval(
            downDivide(downSubtract(value, BigDecimal.ONE), upAdd(value, BigDecimal.ONE)),
            upDivide(upSubtract(value, BigDecimal.ONE), downAdd(value, BigDecimal.ONE)),
        )
        return atanhSeries(z)
    }

    private fun atanhSeries(z: Interval): Interval {
        require(z.lower >= BigDecimal.ZERO && z.upper < BigDecimal.ONE)
        val zSquared = Interval(downMultiply(z.lower, z.lower), upMultiply(z.upper, z.upper))
        var lowerTerm = z.lower
        var upperTerm = z.upper
        var lowerSum = lowerTerm
        var upperSum = upperTerm
        for (index in 1 until LOG_SERIES_TERMS) {
            lowerTerm = downMultiply(lowerTerm, zSquared.lower)
            upperTerm = upMultiply(upperTerm, zSquared.upper)
            val denominator = BigDecimal(index * 2 + 1)
            lowerSum = downAdd(lowerSum, downDivide(lowerTerm, denominator))
            upperSum = upAdd(upperSum, upDivide(upperTerm, denominator))
        }
        val nextDenominator = BigDecimal(LOG_SERIES_TERMS * 2 + 1)
        val nextTerm = upDivide(upMultiply(upperTerm, zSquared.upper), nextDenominator)
        val remainder = upDivide(nextTerm, downSubtract(BigDecimal.ONE, zSquared.upper))
        return Interval(downMultiply(BigDecimal.TWO, lowerSum), upMultiply(BigDecimal.TWO, upAdd(upperSum, remainder)))
    }

    /** Arbitrary-precision real exp2, via exp(x*ln(2)) with a Taylor remainder. */
    private fun exactExp2(value: Interval): Interval {
        val exponent = directedBinary(value, LN_TWO, ::downMultiply, ::upMultiply)
        return Interval(expLower(exponent.lower), expUpper(exponent.upper))
    }

    private fun expLower(value: BigDecimal): BigDecimal = if (value >= BigDecimal.ZERO) {
        expPositive(value).lower
    } else {
        downDivide(BigDecimal.ONE, expPositive(value.negate()).upper)
    }

    private fun expUpper(value: BigDecimal): BigDecimal = if (value >= BigDecimal.ZERO) {
        expPositive(value).upper
    } else {
        upDivide(BigDecimal.ONE, expPositive(value.negate()).lower)
    }

    private fun expPositive(value: BigDecimal): Interval {
        require(value < BigDecimal(EXP_SERIES_TERMS)) { "exp2 argument exceeds the finite proof budget" }
        var lowerTerm = BigDecimal.ONE
        var upperTerm = BigDecimal.ONE
        var lowerSum = BigDecimal.ONE
        var upperSum = BigDecimal.ONE
        for (index in 1..EXP_SERIES_TERMS) {
            val denominator = BigDecimal(index)
            lowerTerm = downDivide(downMultiply(lowerTerm, value), denominator)
            upperTerm = upDivide(upMultiply(upperTerm, value), denominator)
            lowerSum = downAdd(lowerSum, lowerTerm)
            upperSum = upAdd(upperSum, upperTerm)
        }
        val nextTerm = upDivide(upMultiply(upperTerm, value), BigDecimal(EXP_SERIES_TERMS + 1))
        val ratio = upDivide(value, BigDecimal(EXP_SERIES_TERMS + 2))
        val remainder = upDivide(nextTerm, downSubtract(BigDecimal.ONE, ratio))
        return Interval(lowerSum, upAdd(upperSum, remainder))
    }

    private fun downAdd(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, MC_DOWN)
    private fun upAdd(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, MC_UP)
    private fun downSubtract(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, MC_DOWN)
    private fun upSubtract(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, MC_UP)
    private fun downMultiply(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, MC_DOWN)
    private fun upMultiply(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, MC_UP)
    private fun downDivide(a: BigDecimal, b: BigDecimal): BigDecimal = a.divide(b, MC_DOWN)
    private fun upDivide(a: BigDecimal, b: BigDecimal): BigDecimal = a.divide(b, MC_UP)
    private fun powDown(value: BigDecimal, exponent: Int): BigDecimal = value.pow(exponent, MC_DOWN)
    private fun powUp(value: BigDecimal, exponent: Int): BigDecimal = value.pow(exponent, MC_UP)

    internal data class Interval(val lower: BigDecimal, val upper: BigDecimal) {
        init { require(lower <= upper) }
        companion object {
            val ZERO = Interval(BigDecimal.ZERO, BigDecimal.ZERO)
            val ONE = Interval(BigDecimal.ONE, BigDecimal.ONE)
            fun input(value: Float): Interval {
                require(value.isFinite())
                return Interval(decimal(value), decimal(value))
            }
            fun point(value: BigDecimal): Interval = Interval(value, value)
        }
    }

    private fun decimal(value: Float): BigDecimal {
        require(value.isFinite()) { "An unbounded F32 result cannot prove an RGBA8 set" }
        return BigDecimal.valueOf(value.toDouble())
    }

    private val MC_DOWN = MathContext(160, RoundingMode.FLOOR)
    private val MC_UP = MathContext(160, RoundingMode.CEILING)
    // These are the actual f32 WGSL literals, not decimal source spellings.
    private val SRGB_BREAK = decimal(0.04045f)
    private val LINEAR_BREAK = decimal(0.0031308f)
    private val SRGB_LINEAR_SCALE = decimal(12.92f)
    private val SRGB_OFFSET = decimal(0.055f)
    private val SRGB_ENCODE_SCALE = decimal(1.055f)
    private val SRGB_TO_LINEAR_EXPONENT = decimal(2.4f)
    private val F32_MIN_NORMAL = decimal(java.lang.Float.MIN_NORMAL)
    private val F32_MAX_NORMAL = decimal(Float.MAX_VALUE)
    private val UNORM_MAX = BigDecimal("255")
    private val HALF = BigDecimal("0.5")
    // WGSL 15.7.4.1 operation-level f32 bounds.
    private val DIVISION_ULPS = BigDecimal("2.5")
    private val LOG2_OUTSIDE_UNIT_INTERVAL_ULPS = BigDecimal("3")
    private val LOG2_UNIT_INTERVAL_ABSOLUTE_ERROR = BigDecimal.ONE.divide(BigDecimal.TWO.pow(21), MC_UP)
    private val LN_TWO: Interval by lazy { atanhLog(BigDecimal.TWO) }
    private const val ROOT_BISECTION_STEPS = 512
    private const val LOG_SERIES_TERMS = 512
    private const val EXP_SERIES_TERMS = 512
}
