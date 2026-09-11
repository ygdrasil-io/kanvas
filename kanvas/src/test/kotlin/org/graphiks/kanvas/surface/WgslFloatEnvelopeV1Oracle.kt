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
import org.graphiks.kanvas.paint.BlendMode

/**
 * Independent W5a oracle. All node values are intervals over real arithmetic,
 * rounded outward to the predecessor/successor F32 values. It enumerates both
 * preserved-subnormal and FTZ results. Coverage multiplies the source in the
 * fragment before fixed-function blending, exactly as in the native pipeline.
 * D3D11.3 §17.5 permits target-format precision for UNORM blending, not only F32;
 * the blend envelope therefore includes fixed-point inputs, factors and arithmetic.
 * The source transfer is bounded from WGSL `pow`
 * as `exp2(y * log2(x))`, using arbitrary-precision directed series bounds.
 * The attachment transfer is fixed-function, not WGSL: Metal 4 §8.7.7 permits
 * an encoding error strictly below one RGBA8 code, and a decoding error whose
 * exact re-encoding differs by at most half a code. These bounds include the
 * narrower D3D FLOAT/SRGB conversion bounds; they are propagated separately.
 */
internal object WgslFloatEnvelopeV1Oracle {
    sealed interface DrawResult {
        class Bounded internal constructor(
            internal val channels: List<Set<Int>>,
            internal val state: AttachmentState,
        ) : DrawResult
        data class Unbounded(val reason: String) : DrawResult
    }

    internal class AttachmentState internal constructor(internal val linearPremul: Array<Interval>)

    fun clearAttachment(): AttachmentState = AttachmentState(Array(4) { Interval.ZERO })

    /** Exclusion proof only: deliberately not a DrawResult and never admitted by assertAdmits. */
    class ConservativeExclusion internal constructor(internal val channels: List<Set<Int>>)

    fun sourceOverExclusion(table: MaterialPlanTable, root: MaterialPlanRef, destination: AttachmentState): ConservativeExclusion {
        val values = evaluateProgram(table, root, destination.linearPremul, Interval.ONE)
        return ConservativeExclusion(values.mapIndexed { channel, value ->
            if (channel < 3) codesForSrgbAttachment(value) else codesFor(value)
        })
    }

    /** Interprets the published program/binding graph and produces its direct RGBA8 code sets. */
    fun draw(
        table: MaterialPlanTable,
        root: MaterialPlanRef,
        destination: AttachmentState = clearAttachment(),
        coverageF32: Float = 1f,
    ): DrawResult {
        if (!coverageF32.isFinite()) return DrawResult.Unbounded("Non-finite coverage")
        val encoded = try {
            evaluateProgram(table, root, destination.linearPremul, Interval.input(coverageF32))
        } catch (failure: IllegalArgumentException) {
            return DrawResult.Unbounded(failure.message.orEmpty())
        } catch (failure: ArithmeticException) {
            return DrawResult.Unbounded(failure.message.orEmpty())
        }
        val codes = encoded.mapIndexed { channel, value ->
            if (channel < 3) codesForSrgbAttachment(value) else codesFor(value)
        }
        if (codes.any { it.isEmpty() || it.size > 2 || it.maxOrNull()!! - it.minOrNull()!! > 1 }) {
            return DrawResult.Unbounded("Attachment code sets exceed two adjacent codes: $codes")
        }
        return DrawResult.Bounded(codes, AttachmentState(decodeStoredAttachment(codes)))
    }

    fun nextAttachment(result: DrawResult): AttachmentState? = when (result) {
        is DrawResult.Bounded -> result.state
        is DrawResult.Unbounded -> error("WgslFloatEnvelopeV1 is unbounded: ${result.reason}")
    }

    /** Closes SRC_IN against the stored attachment left by the preceding draw. */
    fun drawSrcIn(
        table: MaterialPlanTable,
        root: MaterialPlanRef,
        destination: AttachmentState,
    ): DrawResult {
        val blended = try {
            val source = evaluateMaterialSource(table, root, destination.linearPremul, Interval.ONE)
            Array(4) { channel -> fixedFunctionSrcIn(source[channel].clamp01(), destination.linearPremul[3].clamp01()) }
        } catch (failure: IllegalArgumentException) {
            return DrawResult.Unbounded(failure.message.orEmpty())
        } catch (failure: ArithmeticException) {
            return DrawResult.Unbounded(failure.message.orEmpty())
        }
        val encoded = blended.mapIndexed { channel, value -> if (channel < 3) attachmentEncode(value) else value }
        val codes = encoded.mapIndexed { channel, value -> if (channel < 3) codesForSrgbAttachment(value) else codesFor(value) }
        if (codes.any { it.isEmpty() || it.size > 2 || it.maxOrNull()!! - it.minOrNull()!! > 1 }) {
            return DrawResult.Unbounded("Attachment code sets exceed two adjacent codes: $codes")
        }
        return DrawResult.Bounded(codes, AttachmentState(decodeStoredAttachment(codes)))
    }

    fun assertAdmits(expected: DrawResult, observedRgba8: UByteArray) {
        require(observedRgba8.size == 4)
        val bounded = expected as? DrawResult.Bounded
            ?: error("WgslFloatEnvelopeV1 is unbounded; it cannot prove an RGBA8 envelope: $expected")
        bounded.channels.forEachIndexed { channel, codes ->
            require(observedRgba8[channel].toInt() in codes) {
                "channel=$channel observed=${observedRgba8[channel]} expected=$codes"
            }
        }
    }

    /** Independent shader blend closure. The target writes directly, without fixed blend math. */
    fun drawDestination(
        table: MaterialPlanTable,
        root: MaterialPlanRef,
        destination: AttachmentState,
        mode: org.graphiks.kanvas.paint.BlendMode,
        coverageF32: Float = 1f,
    ): DrawResult {
        val values = try {
            val dst = destination.linearPremul
            val src = evaluateMaterialSource(table, root, dst, Interval.ONE)
            val coverage = Interval.input(coverageF32)
            fun unpremul(value: Interval, alpha: Interval) = if (alpha == Interval.ZERO) Interval.ZERO else wgslDivide(value, alpha)
            val blended = Array(4) { channel ->
                if (mode == BlendMode.PLUS) (src[channel] + dst[channel]).clamp01()
                else if (channel == 3) sourceOver(src[3], dst[3], Interval.ONE - src[3]) else {
                    // Shared destination terms must not lose their correlation through a
                    // wide interval. Directed domain subdivision tightens the enclosure;
                    // it adds no tolerance and covers every original destination value.
                    val partitionChannelI32 = if (mode in NON_SEPARABLE_MODES)
                        (0..2).maxBy { dst[it].upper.subtract(dst[it].lower) } else channel
                    val original = dst[partitionChannelI32]
                    val partitionsI32 = 64
                    val width = upSubtract(original.upper, original.lower)
                    val bounds = (0..partitionsI32).map { partI32 ->
                        if (partI32 == 0) original.lower else if (partI32 == partitionsI32) original.upper else
                            downAdd(original.lower, downDivide(downMultiply(width, BigDecimal(partI32)), BigDecimal(partitionsI32)))
                    }
                    hull(*bounds.zipWithNext().map { (lower, upper) ->
                        val partition = Interval(lower, upper)
                        val destinationChannel = if (channel == partitionChannelI32) partition else dst[channel]
                        val s = unpremul(src[channel], src[3])
                        val d = unpremul(destinationChannel, dst[3])
                        val color = if (mode in NON_SEPARABLE_MODES) artisticNonSeparable(
                            Array(3) { unpremul(src[it], src[3]) },
                            Array(3) { unpremul(if (it == partitionChannelI32) partition else dst[it], dst[3]) }, mode,
                        )[channel] else artisticSeparable(s, d, mode)
                        val left = src[channel] * (Interval.ONE - dst[3])
                        val right = destinationChannel * (Interval.ONE - src[3])
                        val product = hull((src[3] * dst[3]) * color, src[3] * (dst[3] * color))
                        hull((left + right) + product, left + (right + product), (left + product) + right,
                            fma(src[channel], Interval.ONE - dst[3], right + product),
                            fma(destinationChannel, Interval.ONE - src[3], left + product),
                            fma(src[3] * dst[3], color, left + right),
                            fma(src[3], dst[3] * color, left + right))
                    }.toTypedArray())
                }
            }
            Array(4) { channel ->
                val delta = blended[channel] - dst[channel]
                (if (coverageF32 == 1f) blended[channel] else hull(dst[channel] + coverage * delta,
                    fma(coverage, delta, dst[channel]))).clamp01()
            }
        } catch (failure: IllegalArgumentException) {
            return DrawResult.Unbounded(failure.message.orEmpty())
        } catch (failure: ArithmeticException) {
            return DrawResult.Unbounded(failure.message.orEmpty())
        }
        val codes = values.mapIndexed { channel, value -> if (channel < 3) codesForSrgbAttachment(attachmentEncode(value)) else codesFor(value) }
        if (codes.any { it.isEmpty() || it.size > 2 || it.maxOrNull()!! - it.minOrNull()!! > 1 }) {
            return DrawResult.Unbounded("Attachment code sets exceed two adjacent codes: $codes")
        }
        return DrawResult.Bounded(codes, AttachmentState(decodeStoredAttachment(codes)))
    }

    /** ONE/ONE/Add includes the same permitted fixed precision schedules as SRC_OVER. */
    fun drawPlus(table: MaterialPlanTable, root: MaterialPlanRef, destination: AttachmentState): DrawResult {
        val src = evaluateMaterialSource(table, root, destination.linearPremul, Interval.ONE)
        val codes = Array(4) { channel ->
            val source = src[channel].clamp01()
            val dst = destination.linearPremul[channel].clamp01()
            val floating = (source + dst).clamp01()
            val fixed = directedBinary(fixedPrecisionEnvelope(source, conversion = true), dst, ::downAdd, ::upAdd).clamp01()
            val value = hull(floating, fixed)
            if (channel < 3) codesForSrgbAttachment(attachmentEncode(value)) else codesFor(value)
        }.toList()
        if (codes.any { it.isEmpty() || it.size > 2 || it.maxOrNull()!! - it.minOrNull()!! > 1 }) {
            return DrawResult.Unbounded("Attachment code sets exceed two adjacent codes: $codes")
        }
        return DrawResult.Bounded(codes, AttachmentState(decodeStoredAttachment(codes)))
    }

    /** W3C blend equations, evaluated only by the independent directed arithmetic above. */
    private fun artisticSeparable(s: Interval, d: Interval, mode: BlendMode): Interval {
        val two = Interval.input(2f)
        fun minimum(a: Interval, b: Interval) = Interval(minOf(a.lower, b.lower), minOf(a.upper, b.upper))
        fun maximum(a: Interval, b: Interval) = Interval(maxOf(a.lower, b.lower), maxOf(a.upper, b.upper))
        fun branch(value: Interval, split: BigDecimal, lower: (Interval) -> Interval, upper: (Interval) -> Interval): Interval = when {
            value.upper <= split -> lower(value)
            value.lower > split -> upper(value)
            else -> hull(lower(Interval(value.lower, split)), upper(Interval(split, value.upper)))
        }
        fun hardLight(source: Interval, backdrop: Interval) = branch(source, HALF,
            { two * it * backdrop }, { Interval.ONE - two * (Interval.ONE - it) * (Interval.ONE - backdrop) })
        return when (mode) {
            BlendMode.MULTIPLY -> s * d
            BlendMode.OVERLAY -> hardLight(d, s)
            BlendMode.DARKEN -> minimum(s, d)
            BlendMode.LIGHTEN -> maximum(s, d)
            BlendMode.COLOR_DODGE -> when {
                d == Interval.ZERO -> Interval.ZERO
                s == Interval.ONE -> Interval.ONE
                s.upper < BigDecimal.ONE -> minimum(Interval.ONE, wgslDivide(d, Interval.ONE - s))
                else -> error("Color dodge source crosses its singularity")
            }
            BlendMode.COLOR_BURN -> when {
                d == Interval.ONE -> Interval.ONE
                s == Interval.ZERO -> Interval.ZERO
                s.lower > BigDecimal.ZERO -> Interval.ONE - minimum(Interval.ONE, wgslDivide(Interval.ONE - d, s))
                else -> error("Color burn source crosses its singularity")
            }
            BlendMode.HARD_LIGHT -> hardLight(s, d)
            BlendMode.SOFT_LIGHT -> branch(s, HALF,
                { source -> d - (Interval.ONE - two * source) * d * (Interval.ONE - d) },
                { source ->
                    val curve = branch(d, BigDecimal("0.25"),
                        { ((Interval.input(16f) * it - Interval.input(12f)) * it + Interval.input(4f)) * it },
                        { value ->
                            // WGSL 15.7.4.1: sqrt inherits 1/inverseSqrt(x); inverseSqrt
                            // admits 2 ULP, and the outer division retains its own 2.5 ULP.
                            val exactInverse = Interval(downDivide(BigDecimal.ONE, value.upper.sqrt(MC_UP)),
                                upDivide(BigDecimal.ONE, value.lower.sqrt(MC_DOWN)))
                            wgslDivide(Interval.ONE, f32Envelope(expandUlps(exactInverse, BigDecimal("2"))))
                        })
                    d + (two * source - Interval.ONE) * (curve - d)
                })
            BlendMode.DIFFERENCE -> (d - s).let {
                Interval(if (it.lower.signum() <= 0 && it.upper.signum() >= 0) BigDecimal.ZERO else minOf(it.lower.abs(), it.upper.abs()),
                    maxOf(it.lower.abs(), it.upper.abs()))
            }
            BlendMode.EXCLUSION -> s + d - two * s * d
            else -> error("No independent separable destination proof for $mode")
        }
    }

    private fun artisticNonSeparable(source: Array<Interval>, destination: Array<Interval>, mode: BlendMode): Array<Interval> {
        fun minimum(color: Array<Interval>) = Interval(color.minOf { it.lower }, color.minOf { it.upper })
        fun maximum(color: Array<Interval>) = Interval(color.maxOf { it.lower }, color.maxOf { it.upper })
        fun luminosity(color: Array<Interval>): Interval {
            val weights = arrayOf(Interval.input(.3f), Interval.input(.59f), Interval.input(.11f))
            return hull(*(0..2).flatMap { a -> (0..2).filter { it != a }.flatMap { b ->
                val c = 3 - a - b
                val first = color[a] * weights[a]
                val second = color[b] * weights[b]
                val third = color[c] * weights[c]
                listOf((first + second) + third, first + (second + third),
                    fma(color[a], weights[a], second + third),
                    fma(color[a], weights[a], fma(color[b], weights[b], third)))
            } }.toTypedArray())
        }
        fun saturation(color: Array<Interval>) = maximum(color) - minimum(color)
        fun setSaturation(color: Array<Interval>, saturation: Interval): Array<Interval> {
            val lo = minimum(color)
            val hi = maximum(color)
            if (hi == lo) return Array(3) { Interval.ZERO }
            require(hi.lower > lo.upper) { "Ambiguous nonseparable saturation denominator" }
            return Array(3) { wgslDivide((color[it] - lo) * saturation, hi - lo) }
        }
        fun setLuminosity(color: Array<Interval>, lum: Interval): Array<Interval> {
            val delta = lum - luminosity(color)
            val shifted = Array(3) { color[it] + delta }
            val l = luminosity(shifted)
            val n = minimum(shifted)
            val x = maximum(shifted)
            var result = shifted
            if (n.lower < BigDecimal.ZERO) {
                val clipped = Array(3) { l + wgslDivide((shifted[it] - l) * l, l - n) }
                result = if (n.upper < BigDecimal.ZERO) clipped else Array(3) { hull(result[it], clipped[it]) }
            }
            if (x.upper > BigDecimal.ONE) {
                val clipped = Array(3) { l + wgslDivide((result[it] - l) * (Interval.ONE - l), x - l) }
                result = if (x.lower > BigDecimal.ONE) clipped else Array(3) { hull(result[it], clipped[it]) }
            }
            return result
        }
        return when (mode) {
            BlendMode.HUE -> setLuminosity(setSaturation(source, saturation(destination)), luminosity(destination))
            BlendMode.SATURATION -> setLuminosity(setSaturation(destination, saturation(source)), luminosity(destination))
            BlendMode.COLOR -> setLuminosity(source, luminosity(destination))
            BlendMode.LUMINOSITY -> setLuminosity(destination, luminosity(source))
            else -> error("No independent nonseparable destination proof for $mode")
        }
    }

    private val NON_SEPARABLE_MODES = setOf(BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY)

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

    private fun evaluateMaterialSource(
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
        return materialSource(root)
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
            val coverage = evaluate(node.inputs[2], inputs).scalar()
            val blend = node.inputs[1]
            require(blend.operation == NumericOperationGraphV1.Operation.SRC_OVER && blend.inputs[1] == node.inputs[0])
            val source = evaluate(blend.inputs[0], inputs).rgba()
            Rgba(Array(4) { blendAndCoverage(source[it], source[3], dst[it], coverage) })
        }
        NumericOperationGraphV1.Operation.LINEAR_TO_SRGB_ATTACHMENT -> evaluate(node.inputs.single(), inputs).rgba().let {
            Rgba(arrayOf(attachmentEncode(it[0]), attachmentEncode(it[1]), attachmentEncode(it[2]), it[3]))
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

    /** The authenticated native partition emits coverage*source in WGSL, then One/InvSrcAlpha. */
    private fun blendAndCoverage(source: Interval, alpha: Interval, destination: Interval, coverage: Interval): Interval {
        val coveredSource = if (source == Interval.ZERO || coverage == Interval.ZERO) Interval.ZERO
            else (source * coverage).clamp01()
        val coveredAlpha = if (alpha == Interval.ZERO || coverage == Interval.ZERO) Interval.ZERO
            else (alpha * coverage).clamp01()
        val clampedDestination = destination.clamp01()
        val floating = sourceOver(coveredSource, clampedDestination, Interval.ONE - coveredAlpha).clamp01()
        return floating.hull(fixedFunctionSourceOver(coveredSource, coveredAlpha, clampedDestination))
    }

    /**
     * D3D11.3 §17.5 (UNORM8 precision floor), §3.2.3.6 and §3.2.4.1 (input
     * conversion <=0.6 destination LSB). The coarsest allowed quantum is 1/255;
     * binary fixed-point with eight fractional bits is finer. Input and factor
     * precision may differ from arithmetic precision, so enclose all finer
     * conversions instead of sampling only one implementation's bit allocation.
     *
     * Fixed-point ADD/SUB are integer-exact at a common precision (§3.2.4).
     * Product rescaling can discard at most one fractional LSB; this enclosure
     * includes truncation and both adjacent roundings. Fused multiply/add skips
     * that intermediate rounding and is included separately. No empirical error.
     */
    private fun fixedFunctionSourceOver(source: Interval, alpha: Interval, destination: Interval): Interval {
        fun exactAdd(a: Interval, b: Interval) = directedBinary(a, b, ::downAdd, ::upAdd)
        fun exactMultiply(a: Interval, b: Interval) = directedBinary(a, b, ::downMultiply, ::upMultiply)
        fun product(a: Interval, b: Interval): Interval = when {
            a == Interval.ZERO || b == Interval.ZERO -> Interval.ZERO
            a == Interval.ONE -> b
            b == Interval.ONE -> a
            else -> fixedPrecisionEnvelope(exactMultiply(a, b), conversion = false)
        }
        // §17.5 permits target-format precision for the blend operation itself.
        // Consequently the source term, including its ONE factor, has a legal
        // fixed-point product schedule. This is distinct from a prior
        // FLOAT->UNORM attachment conversion: zero and one stay exact, while a
        // non-trivial source is quantized only as the product result. The
        // destination remains the same correlated value throughout its term.
        val sourceTimesOne = if (source == Interval.ZERO || source == Interval.ONE) source
            else fixedPrecisionEnvelope(source, conversion = true)
        val inverseAlpha = fixedPrecisionEnvelope(
            directedBinary(Interval.ONE, alpha, ::downSubtract, ::upSubtract),
            conversion = true,
        )
        return exactAdd(sourceTimesOne, product(destination, inverseAlpha)).clamp01()
    }

    /** SRC_IN is source * destination.a; the ZERO destination term admits no extra fusion. */
    private fun fixedFunctionSrcIn(source: Interval, destinationAlpha: Interval): Interval {
        if (source == Interval.ZERO || destinationAlpha == Interval.ZERO) return Interval.ZERO
        val floating = source * destinationAlpha
        val fixedSource = fixedPrecisionEnvelope(source, conversion = true)
        val fixedAlpha = fixedPrecisionEnvelope(destinationAlpha, conversion = true)
        val fixed = when {
            fixedAlpha == Interval.ONE -> fixedSource
            fixedSource == Interval.ONE -> fixedAlpha
            else -> fixedPrecisionEnvelope(
                directedBinary(fixedSource, fixedAlpha, ::downMultiply, ::upMultiply), conversion = false,
            )
        }
        return floating.hull(fixed).clamp01()
    }

    /**
     * Keep the coarse quantization lattice explicit: replacing its rounding by
     * an unconditional +/-LSB would invent errors for exact zero and one.
     * Enumerate UNORM and binary fixed-point precisions 8..24; all finer grids
     * are enclosed by the directed 1/(2^25-1) remainder, and F32 is unioned by the
     * caller. The finite cut changes only enclosure tightness, never admission.
     */
    private fun fixedPrecisionEnvelope(value: Interval, conversion: Boolean): Interval {
        val clamped = value.clamp01()
        // A preserved or flushed subnormal is still below the least UNORM8 code;
        // it therefore quantizes exactly to zero rather than acquiring a generic
        // FLOAT-to-UNORM error interval.
        if (clamped.upper <= F32_MIN_NORMAL) return Interval.ZERO
        if (clamped == Interval.ZERO || clamped == Interval.ONE) return clamped
        var result = clamped
        for (bits in 8..24) {
            val binary = BigDecimal(1L shl bits)
            for (denominator in listOf(binary - BigDecimal.ONE, binary)) {
                val low = downMultiply(clamped.lower, denominator)
                val high = upMultiply(clamped.upper, denominator)
                val first = if (conversion) downSubtract(low, UNORM_CODE_ERROR).setScale(0, RoundingMode.CEILING)
                    else low.setScale(0, RoundingMode.FLOOR)
                val last = if (conversion) upAdd(high, UNORM_CODE_ERROR).setScale(0, RoundingMode.FLOOR)
                    else high.setScale(0, RoundingMode.CEILING)
                result = result.hull(Interval(downDivide(first.max(BigDecimal.ZERO), denominator),
                    upDivide(last.min(denominator), denominator)))
            }
        }
        val finerQuantum = upDivide(BigDecimal.ONE, BigDecimal((1L shl 25) - 1L))
        val finerError = if (conversion) upMultiply(UNORM_CODE_ERROR, finerQuantum) else finerQuantum
        return result.hull(expandAbsolute(clamped, finerError)).clamp01()
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

    /** Exact real sRGB reference, before the attachment's documented integer-code error. */
    private fun attachmentEncode(value: Interval): Interval = piecewiseTransfer(
        value.clamp01(),
        BigDecimal("0.0031308"),
        { x -> Interval(downMultiply(x, BigDecimal("12.92")), upMultiply(x, BigDecimal("12.92"))) },
        { x -> exactRationalPower(x, 5, 12).let { power -> Interval(
            downSubtract(downMultiply(BigDecimal("1.055"), power.lower), BigDecimal("0.055")),
            upSubtract(upMultiply(BigDecimal("1.055"), power.upper), BigDecimal("0.055")),
        ) } },
    )

    private fun attachmentDecode(value: Interval): Interval = piecewiseTransfer(
        value.clamp01(),
        BigDecimal("0.04045"),
        { x -> Interval(downDivide(x, BigDecimal("12.92")), upDivide(x, BigDecimal("12.92"))) },
        { x ->
            val base = Interval(downDivide(downAdd(x, BigDecimal("0.055")), BigDecimal("1.055")),
                upDivide(upAdd(x, BigDecimal("0.055")), BigDecimal("1.055")))
            Interval(exactRationalPower(base.lower, 12, 5).lower,
                exactRationalPower(base.upper, 12, 5).upper)
        },
    )

    private fun exactRationalPower(value: BigDecimal, numerator: Int, denominator: Int): Interval =
        Interval(requireNotNull(nthRoot(powDown(value, numerator), denominator)).lower,
            requireNotNull(nthRoot(powUp(value, numerator), denominator)).upper)

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
        val lowerCode = BigDecimal(codes[channel].minOrNull()!!)
        val upperCode = BigDecimal(codes[channel].maxOrNull()!!)
        if (channel < 3) {
            // Invert Metal's exact re-encoding bound |255*encode(decoded)-storedCode| <= 0.5.
            attachmentDecode(Interval(downDivide(downSubtract(lowerCode, HALF), UNORM_MAX),
                upDivide(upAdd(upperCode, HALF), UNORM_MAX)))
        } else f32Envelope(Interval(downDivide(lowerCode, UNORM_MAX), upDivide(upperCode, UNORM_MAX)))
    }

    /**
     * Metal 4 (2026-06-04), §8.7.7, p381:
     * https://developer.apple.com/metal/Metal-Shading-Language-Specification.pdf
     * The total fixed-function encode/quantize error is |reference-code| < 1,
     * not nearest rounding plus another one-code allowance. Strict endpoints
     * are retained, and a three-code result still fails the public proof gate.
     */
    private fun codesForSrgbAttachment(value: Interval): Set<Int> {
        val clamped = value.clamp01()
        val first = downSubtract(downMultiply(clamped.lower, UNORM_MAX), BigDecimal.ONE)
            .setScale(0, RoundingMode.FLOOR).toInt().plus(1).coerceIn(0, 255)
        val last = upAdd(upMultiply(clamped.upper, UNORM_MAX), BigDecimal.ONE)
            .setScale(0, RoundingMode.CEILING).toInt().minus(1).coerceIn(0, 255)
        return (first..last).toSet()
    }

    private fun codesFor(value: Interval): Set<Int> {
        val clamped = value.clamp01()
        // D3D 11.3 §3.2.3.6 permits <=0.6 code error for FLOAT -> UNORM;
        // this also contains Metal's correctly-rounded (<=0.5) linear alpha.
        val first = downSubtract(downMultiply(clamped.lower, UNORM_MAX), UNORM_CODE_ERROR)
            .setScale(0, RoundingMode.CEILING).toInt().coerceIn(0, 255)
        val last = upAdd(upMultiply(clamped.upper, UNORM_MAX), UNORM_CODE_ERROR)
            .setScale(0, RoundingMode.FLOOR).toInt().coerceIn(0, 255)
        return (first..last).toSet()
    }

    private fun f32Envelope(exact: Interval): Interval = hull(preserveF32(exact), flushSubnormal(exact))

    /** Containment expands rounded endpoints to adjacent F32 values, never a Double ULP. */
    private fun preserveF32(exact: Interval): Interval {
        if (exact.lower.compareTo(exact.upper) == 0) {
            if (exact.lower.signum() == 0) return Interval.ZERO
            if (exact.lower.compareTo(BigDecimal.ONE) == 0) return Interval.ONE
            val candidate = exact.lower.toFloat()
            // IEEE arithmetic cannot change a representable exact result under
            // any rounding mode. BigDecimal(Double) checks the exact binary value,
            // not the shortest decimal display spelling of that value.
            if (candidate.isFinite() && BigDecimal(candidate.toDouble()).compareTo(exact.lower) == 0) return exact
        }
        return Interval(decimal(java.lang.Math.nextDown(exact.lower.toFloat())),
            decimal(java.lang.Math.nextUp(exact.upper.toFloat())))
    }

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
    private fun Interval.clamp01(): Interval = Interval(lower.max(BigDecimal.ZERO).min(BigDecimal.ONE),
        upper.max(BigDecimal.ZERO).min(BigDecimal.ONE))
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
        // ln(x) is negative below one, so a fixed lower/upper denominator
        // choice is not sign-safe. Directed pair evaluation selects the
        // outward quotient across the full ln(x) and ln(2) intervals.
        return directedBinary(
            Interval(lower.lower, upper.upper),
            LN_TWO,
            ::downDivide,
            ::upDivide,
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
    private val SRGB_LINEAR_SCALE = decimal(12.92f)
    private val SRGB_OFFSET = decimal(0.055f)
    private val SRGB_ENCODE_SCALE = decimal(1.055f)
    private val SRGB_TO_LINEAR_EXPONENT = decimal(2.4f)
    private val F32_MIN_NORMAL = decimal(java.lang.Float.MIN_NORMAL)
    private val F32_MAX_NORMAL = decimal(Float.MAX_VALUE)
    private val UNORM_MAX = BigDecimal("255")
    private val UNORM_CODE_ERROR = BigDecimal("0.6")
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
