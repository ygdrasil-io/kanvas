package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.ImageAlphaType
import org.graphiks.math.color.ColorARGB
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Typed two-source schedule: entry sRGBA -> linear premul; blend(entry,image); paint alpha.
 * This proof evaluates the selected helper schedule, including eager select operands and
 * guarded division. A failed interval proof is terminal before source/native allocation. */
public class ImageAtlasBlendNumericAuthorityV1 private constructor(
    public val mode: BlendMode, public val color: ColorARGB,
    public val uploadIdentity: String, private val colorAlpha: ImageColorAlphaPlanV1,
    public val maskChild: Boolean, private val finiteOperationCountI64: Long,
    private val maximumMagnitudeF64: Double, private val childSourceIdentity: String?,
    private val operationGraph: BlendFormulaOperationGraphV1,
) {
    public val formulaWgsl: String = operationGraph.sourceWgsl
    public val canonicalIdentity: String = "atlas-source-blend-graph-v1:${BlendFormulaProgramV1.REVISION_I32}:$mode:${color.value}:" +
        "$uploadIdentity:$colorAlpha:decode=${colorAlpha.unpremultiplyOperation}:mask=$maskChild:child=$childSourceIdentity:ops=$finiteOperationCountI64:max=${maximumMagnitudeF64.toRawBits()}:$formulaWgsl"
    public fun copyColorUniformF32(): List<Float> = listOf(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized)
    public fun authenticates(upload: ImageUploadPlanV1, colorAlpha: ImageColorAlphaPlanV1, childSourceIdentity: String?): Boolean =
        upload.contentIdentity == uploadIdentity && colorAlpha == this.colorAlpha && this.childSourceIdentity == childSourceIdentity &&
            formulaWgsl == BlendFormulaProgramV1.selectedAtlasSourceWgsl(mode.name.lowercase())

    internal companion object {
        fun seal(mode: BlendMode, color: ColorARGB, upload: ImageUploadPlanV1,
            colorAlpha: ImageColorAlphaPlanV1, maskChild: Boolean, childSourceIdentity: String?,
            maskColor: ColorARGB?): ImageAtlasBlendNumericAuthorityV1? = try {
            val graph = BlendFormulaOperationGraphV1.read(requireNotNull(BlendFormulaProgramV1.selectedAtlasSourceWgsl(mode.name.lowercase())))
                ?: throw Unbounded()
            val evaluator = Evaluator()
            fun blend(source: Array<Domain>, destination: Array<Domain>) {
                graph.evaluate("w5e_atlas_blend", listOf(source.toList(), destination.toList()), evaluator)
            }
            val source = evaluator.solid(color)
            if (maskChild && maskColor == null) {
                // The child authenticates finiteness, not a tighter source interval.
                // Use that complete domain; no invented alpha floor or [-1,2] bound.
                // A selected mode whose actual arithmetic cannot close is refused.
                blend(source, Array(4) { Domain(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()) })
            } else {
                val bytes = upload.copyLogicalBytes()
                val maskPaint = maskColor?.let(evaluator::solid)
                var offsetI32 = 0
                while (offsetI32 < bytes.size) {
                    val decoded = if (maskPaint == null) evaluator.decode(bytes, offsetI32, colorAlpha) else {
                        val mask = if (colorAlpha.alphaType == ImageAlphaType.OPAQUE) evaluator.one
                            else evaluator.unorm(bytes[offsetI32].toInt() and 255)
                        Array(4) { evaluator.mul(maskPaint[it], mask) }
                    }
                    blend(source, decoded)
                    offsetI32 = Math.addExact(offsetI32, if (maskChild) 1 else 4)
                }
            }
            if (evaluator.maximumMagnitudeF64 >= Float.MAX_VALUE.toDouble() / 4.0) throw Unbounded()
            ImageAtlasBlendNumericAuthorityV1(mode, color, upload.contentIdentity, colorAlpha, maskChild,
                evaluator.operationCountI64, evaluator.maximumMagnitudeF64, childSourceIdentity, graph)
        } catch (_: Unbounded) { null } catch (_: IllegalArgumentException) { null }
    }

    private class Unbounded : RuntimeException()
    private data class Domain(val lowF64: Double, val highF64: Double) {
        val exact: Boolean get() = lowF64 == highF64
        fun contains(value: Double): Boolean = value in lowF64..highF64
    }
    /** Rounded scalar graph interpreter. Vector expressions expand componentwise in WGSL order. */
    private class Evaluator : BlendFormulaOperationGraphV1.Arithmetic<Domain> {
        var operationCountI64: Long = 0L
        var maximumMagnitudeF64: Double = 0.0
        val zero = Domain(0.0, 0.0)
        val one = Domain(1.0, 1.0)
        fun input(value: Float): Domain = Domain(value.toDouble(), value.toDouble())
        fun constant(value: Double): Domain = input(value.toFloat())
        override fun hull(left: Domain, right: Domain): Domain = Domain(min(left.lowF64, right.lowF64), max(left.highF64, right.highF64))
        fun rounded(lowF64: Double, highF64: Double, ulpsI32: Int = 1): Domain {
            operationCountI64 = Math.addExact(operationCountI64, 1L)
            val magnitudeF64 = max(abs(lowF64), abs(highF64))
            val errorF64 = max(Math.ulp(magnitudeF64.toFloat()).toDouble() * ulpsI32, java.lang.Float.MIN_NORMAL.toDouble())
            val low = lowF64 - errorF64
            val high = highF64 + errorF64
            if (!low.isFinite() || !high.isFinite() || max(abs(low), abs(high)) > Float.MAX_VALUE.toDouble()) throw Unbounded()
            maximumMagnitudeF64 = max(maximumMagnitudeF64, max(abs(low), abs(high)))
            return Domain(low, high)
        }
        fun add(a: Domain, b: Domain): Domain = when { a == zero -> b; b == zero -> a; else -> rounded(a.lowF64 + b.lowF64, a.highF64 + b.highF64) }
        fun sub(a: Domain, b: Domain): Domain = when { b == zero -> a; a.exact && b.exact && a == b -> zero; else -> rounded(a.lowF64 - b.highF64, a.highF64 - b.lowF64) }
        fun mul(a: Domain, b: Domain): Domain {
            if (a == zero || b == zero) return zero
            if (a == one) return b
            if (b == one) return a
            val products = listOf(a.lowF64 * b.lowF64, a.lowF64 * b.highF64, a.highF64 * b.lowF64, a.highF64 * b.highF64)
            return rounded(products.min(), products.max())
        }
        fun div(a: Domain, b: Domain): Domain {
            if (b.contains(0.0) || min(abs(b.lowF64), abs(b.highF64)) < java.lang.Float.MIN_NORMAL.toDouble()) throw Unbounded()
            // Unlike exact +0/*1 identities, emitted WGSL division retains its
            // accuracy envelope even for a divisor of one or numerator of zero.
            val quotients = listOf(a.lowF64 / b.lowF64, a.lowF64 / b.highF64, a.highF64 / b.lowF64, a.highF64 / b.highF64)
            return rounded(quotients.min(), quotients.max(), 8)
        }
        fun minimum(a: Domain, b: Domain): Domain = Domain(min(a.lowF64, b.lowF64), min(a.highF64, b.highF64))
        fun maximum(a: Domain, b: Domain): Domain = Domain(max(a.lowF64, b.lowF64), max(a.highF64, b.highF64))
        fun absolute(a: Domain): Domain = Domain(if (a.contains(0.0)) 0.0 else min(abs(a.lowF64), abs(a.highF64)), max(abs(a.lowF64), abs(a.highF64)))
        fun root(a: Domain): Domain {
            if (a.lowF64 < 0.0) throw Unbounded()
            if (a == zero) return zero
            // sqrt inherits reciprocal(inverseSqrt): 2 ULP plus division and rounding.
            return rounded(sqrt(a.lowF64), sqrt(a.highF64), 16)
        }
        fun select(low: Domain, high: Domain, predicate: Domain, thresholdF64: Double): Domain = when {
            predicate.highF64 <= thresholdF64 -> low
            predicate.lowF64 > thresholdF64 -> high
            else -> hull(low, high)
        }
        fun transfer(value: Domain): Domain {
            if (value == zero || value == one) return value
            if (value.highF64 <= .04045) return div(value, constant(12.92))
            val base = div(add(value, constant(.055)), constant(1.055))
            if (base.lowF64 < 0.0) throw Unbounded()
            val powered = power(base, constant(2.4))
            return if (value.lowF64 > .04045) powered else hull(div(value, constant(12.92)), powered)
        }
        fun solid(color: ColorARGB): Array<Domain> {
            val alpha = input(color.alphaNormalized)
            return arrayOf(mul(transfer(input(color.redNormalized)), alpha), mul(transfer(input(color.greenNormalized)), alpha),
                mul(transfer(input(color.blueNormalized)), alpha), alpha)
        }
        fun unorm(valueI32: Int): Domain {
            if (valueI32 == 0) return zero
            if (valueI32 == 255) return one
            val valueF64 = valueI32.toDouble() / 255.0
            return rounded(valueF64, valueF64)
        }
        fun decode(bytes: ByteArray, offsetI32: Int, facts: ImageColorAlphaPlanV1): Array<Domain> {
            fun channel(indexI32: Int): Domain {
                val valueI32 = bytes[offsetI32 + indexI32].toInt() and 255
                return unorm(valueI32)
            }
            val alpha = if (facts.alphaType == ImageAlphaType.OPAQUE) one else channel(3)
            if (alpha == zero) return Array(4) { zero }
            val rgb = Array(3) { channel(if (facts.channelOrder == ImageChannelOrderV1.BGRA) 2 - it else it) }
            // This is the same typed guard emitted by W5eImageTexelEvaluatorV1,
            // not an arithmetic identity for a division that still executes.
            var straight = when (facts.unpremultiplyOperation) {
                ImageNumericOperationGraphV1.TexelOperation.UNIT_ALPHA_GUARDED_UNPREMULTIPLY_SOURCE ->
                    if (alpha == one) rgb else rgb.map { div(it, alpha) }.toTypedArray()
                null -> rgb
                else -> throw Unbounded()
            }
            if (facts.transfer == ImageTransferPlanV1.SRGB) straight = straight.map(::transfer).toTypedArray()
            if (facts.gamut == ImageGamutPlanV1.DISPLAY_P3) straight = arrayOf(
                sub(mul(constant(1.2247455), straight[0]), mul(constant(.2249044), straight[1])),
                add(mul(constant(-.0420581), straight[0]), mul(constant(1.0420810), straight[1])),
                add(sub(mul(constant(-.0196423), straight[0]), mul(constant(.0786549), straight[1])), mul(constant(1.0985372), straight[2])))
            return if (facts.premultiplication == org.graphiks.kanvas.render.ir.ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL)
                arrayOf(straight[0], straight[1], straight[2], alpha)
            else arrayOf(mul(straight[0], alpha), mul(straight[1], alpha), mul(straight[2], alpha), alpha)
        }
        override fun literal(valueF32: Float): Domain = input(valueF32)
        override fun exact(value: Domain): Float? = if (value.exact) value.lowF64.toFloat() else null
        override fun truth(value: Domain): Int = when (value) { zero -> 0; one -> 1; else -> -1 }
        override fun binary(operation: String, left: Domain, right: Domain): Domain = when (operation) {
            "+" -> add(left, right)
            "-" -> sub(left, right)
            "*" -> mul(left, right)
            "/" -> div(left, right)
            else -> {
                val trueAlways = when (operation) {
                    "==" -> left.exact && right.exact && left == right
                    "!=" -> left.highF64 < right.lowF64 || left.lowF64 > right.highF64
                    "<" -> left.highF64 < right.lowF64
                    "<=" -> left.highF64 <= right.lowF64
                    ">" -> left.lowF64 > right.highF64
                    ">=" -> left.lowF64 >= right.highF64
                    "&&" -> truth(left) == 1 && truth(right) == 1
                    "||" -> truth(left) == 1 || truth(right) == 1
                    else -> throw Unbounded()
                }
                val falseAlways = when (operation) {
                    "==" -> left.highF64 < right.lowF64 || left.lowF64 > right.highF64
                    "!=" -> left.exact && right.exact && left == right
                    "<" -> left.lowF64 >= right.highF64
                    "<=" -> left.lowF64 > right.highF64
                    ">" -> left.highF64 <= right.lowF64
                    ">=" -> left.highF64 < right.lowF64
                    "&&" -> truth(left) == 0 || truth(right) == 0
                    "||" -> truth(left) == 0 && truth(right) == 0
                    else -> throw Unbounded()
                }
                when { trueAlways -> one; falseAlways -> zero; else -> Domain(0.0, 1.0) }
            }
        }
        /** WGSL pow inherits exp2(y*log2(x)); retain each builtin's accuracy domain.
         * Double transcendental error is dominated by the outward F32 error used here. */
        private fun power(base: Domain, exponent: Domain): Domain {
            if (base.lowF64 <= 0.0) throw Unbounded()
            val logLow = kotlin.math.log2(base.lowF64)
            val logHigh = kotlin.math.log2(base.highF64)
            // WgslFloatEnvelopeV1: absolute 2^-21 on [0.5,2], 3 ULP outside;
            // retain their union, plus outward rounding, through mul and exp2.
            val logError = max(Math.scalb(1.0, -21), 4 * Math.ulp(max(abs(logLow), abs(logHigh)).toFloat()).toDouble())
            val log = rounded(logLow - logError, logHigh + logError)
            val argument = mul(exponent, log)
            val ulpsI32 = kotlin.math.ceil(4 + 2 * max(abs(argument.lowF64), abs(argument.highF64))).toInt()
            return rounded(2.0.pow(argument.lowF64), 2.0.pow(argument.highF64), ulpsI32)
        }
        override fun builtin(name: String, arguments: List<List<Domain>>): List<Domain> {
            if (name == "vec3f" || name == "vec4f") {
                val widthI32 = if (name == "vec3f") 3 else 4
                val values = arguments.flatten()
                return if (values.size == 1) List(widthI32) { values.single() } else values.also { require(it.size == widthI32) }
            }
            if (name == "dot") {
                require(arguments.size == 2 && arguments[0].size == 3 && arguments[1].size == 3)
                val products = (0..2).map { mul(arguments[0][it], arguments[1][it]) }
                // Include every association and summation order; each nonfused rounding
                // envelope also encloses its exact/FMA alternatives.
                return listOf(listOf(0, 1, 2).flatMap { i -> (0..2).filter { it != i }.map { j ->
                    add(add(products[i], products[j]), products[3 - i - j])
                } }.reduce(::hull))
            }
            val sizeI32 = arguments.maxOf { it.size }
            require(arguments.all { it.size == 1 || it.size == sizeI32 })
            return List(sizeI32) { indexI32 ->
                val values = arguments.map { it[if (it.size == 1) 0 else indexI32] }
                when (name) {
                    "min" -> minimum(values[0], values[1])
                    "max" -> maximum(values[0], values[1])
                    "abs" -> absolute(values.single())
                    "sqrt" -> root(values.single())
                    "pow" -> power(values[0], values[1])
                    "select" -> when (truth(values[2])) { 0 -> values[0]; 1 -> values[1]; else -> hull(values[0], values[1]) }
                    else -> throw Unbounded()
                }
            }
        }
    }
}
