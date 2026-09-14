package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32

/** Authenticated expressions, bindings and selected-branch facts; no caller-supplied component box. */
public class ColorSourceProofV1 private constructor(
    public val canonicalIdentity: String,
    public val sourceIdentity: String,
    public val coordinates: SourceCoordinatesV4,
    private val graph: ColorOperationGraphV1,
    internal val bindingOwners: List<MaterialBindingPlan>,
    internal val wordValuesF32: Map<Long, Float>,
    internal val conditionedFacts: List<ColorBranchFactV1>,
    internal val deviceBoundsF32: RectF32,
    private val parentSource: ColorSourceProofV1? = null,
    private val filterExecution: ColorFilterExecutionPlanV1? = null,
    private val childOutputCertificates: List<ColorSourceProofV1> = emptyList(),
) {
    internal val uniformWordCountI64: Long = if (parentSource == null) bindingOwners.fold(0L) { size, binding ->
        Math.addExact(size, if (binding is ColorFilterBindingV4) binding.execution.dynamicByteCountI64 / 4L else 4L)
    } else
        Math.addExact(parentSource.uniformWordCountI64, requireNotNull(filterExecution).dynamicByteCountI64 / 4L)
    public fun copyOperationGraph(): ColorOperationGraphV1 = graph
    public fun authenticates(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: SourceCoordinatesV4): Boolean {
        if (this.coordinates != coordinates || table.sourceIdentity(root) != sourceIdentity) return false
        if (parentSource != null) {
            val binding = table.entry(root).bindings as? ColorFilterBindingV4 ?: return false
            return binding.execution === filterExecution && binding.numericAuthority.outputSourceProof === this &&
                root.indexI32 > 0 && parentSource.authenticates(table, MaterialPlanRef(root.indexI32 - 1), coordinates)
        }
        val firstI32 = root.indexI32 - bindingOwners.lastIndex
        return firstI32 >= 0 && bindingOwners.indices.all {
            table.entry(MaterialPlanRef(firstI32 + it)).bindings === bindingOwners[it]
        }
    }
    internal companion object {
        fun filteredIdentity(source: String, execution: ColorFilterExecutionPlanV1): String =
            "color-filter-source-v4:$source:${execution.canonicalIdentity}"
        fun compose(source: ColorSourceProofV1, execution: ColorFilterExecutionPlanV1): ColorSourceProofV1? {
            val graph = execution.copyOperationGraph().bindInput(source.graph, source.uniformWordCountI64)
            val words = LinkedHashMap(source.wordValuesF32)
            execution.forEachWord { offset, value -> words[Math.addExact(source.uniformWordCountI64, offset)] = value }
            // Compose transports the complete inner output certificate, not its
            // component box, into the outer filter's original lazy alpha guards.
            val composeOutputs = execution.composeChildren?.let { (outer,inner) ->
                val innerOutput = ColorNumericAuthorityV1.seal(inner,source)?.outputSourceProof ?: return null
                val outerOutput = ColorNumericAuthorityV1.seal(outer,innerOutput)?.outputSourceProof ?: return null
                require(outerOutput.graph.canonicalIdentity == graph.canonicalIdentity && outerOutput.wordValuesF32 == words)
                listOf(innerOutput,outerOutput)
            }
            // Both Lerp functions receive this exact original source owner. Their
            // full conditioned certificates remain retained; the final shared DAG
            // proves the rebased branch outputs and rounded premultiplied merge.
            val lerpOutputs = execution.lerpChildren?.let { (dst,src) ->
                listOf(ColorNumericAuthorityV1.seal(dst,source)?.outputSourceProof ?: return null,
                    ColorNumericAuthorityV1.seal(src,source)?.outputSourceProof ?: return null)
            }
            val proof = composeOutputs?.last()?.conditionedFacts ?: ColorRoundedGraphProofV1.prove(graph, words) ?: return null
            val certifiedGraph = composeOutputs?.last()?.graph ?: graph
            val identity = "color-composed-proof-v1:${source.canonicalIdentity}:${execution.canonicalIdentity}:${graph.canonicalIdentity}:${proof.map { Triple(it.taken,it.left,it.right) }}"
            return ColorSourceProofV1(identity, filteredIdentity(source.sourceIdentity,execution), source.coordinates,
                certifiedGraph, source.bindingOwners, java.util.Collections.unmodifiableMap(words), immutableList(proof),
                source.deviceBoundsF32, source, execution,immutableList(composeOutputs ?: lerpOutputs ?: emptyList()))
        }
        fun issue(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: SourceCoordinatesV4,
            boundsF32: RectF32, graph: ColorOperationGraphV1, owners: List<MaterialBindingPlan>,
            words: Map<Long, Float>): ColorSourceProofV1? {
            val proof = ColorRoundedGraphProofV1.prove(graph, words) ?: return null
            val source = table.sourceIdentity(root)
            val identity = "color-source-proof-v1:$source:${coordinates.identityV4()}:" +
                listOf(boundsF32.left, boundsF32.top, boundsF32.right, boundsF32.bottom).joinToString { it.toRawBits().toString() } +
                ":${graph.canonicalIdentity}:${words.entries.joinToString { "${it.key}=${it.value.toRawBits()}" }}:${proof.map { Triple(it.taken,it.left,it.right) }}"
            return ColorSourceProofV1(identity, source, coordinates, graph, immutableList(owners),
                java.util.Collections.unmodifiableMap(LinkedHashMap(words)), immutableList(proof), boundsF32.copy())
        }
    }
}

public sealed interface ColorSourceProofResultV1 {
    public data class Ready(public val source: ColorSourceProofV1) : ColorSourceProofResultV1
    public data class Refused(public val diagnosticCode: String) : ColorSourceProofResultV1
}

internal data class ColorBoundsV1(val lowerF64: Double, val upperF64: Double) {
    init { require(lowerF64.isFinite() && upperF64.isFinite() && lowerF64 <= upperF64) }
}
internal data class ColorBranchFactV1(val predicate: ColorOperationGraphV1.Predicate, val taken: Boolean,
    val left: ColorBoundsV1, val right: ColorBoundsV1)

/** Finite operation enclosure under the pinned F32 envelope, including FTZ and reassociation. */
internal object ColorRoundedGraphProofV1 {
    private val normalF64 = java.lang.Float.MIN_NORMAL.toDouble()
    // A full binary32 ULP relative bound (2^-23), deliberately covering either
    // directed rounding choice. gamma(n) bounds any tree of n rounded operations.
    private fun gamma(operationCountI32: Int): Double {
        val productF64 = Math.nextUp(operationCountI32 * Math.scalb(1.0, -23))
        require(productF64 < 1.0)
        return Math.nextUp(productF64 / Math.nextDown(1.0 - productF64))
    }
    private fun exact(valueF32: Float) = ColorBoundsV1(valueF32.toDouble(), valueF32.toDouble())
    private fun rounded(lowF64: Double, highF64: Double, ulpsF64: Double = 1.0): ColorBoundsV1 {
        require(lowF64.isFinite() && highF64.isFinite() && kotlin.math.abs(lowF64) < Float.MAX_VALUE &&
            kotlin.math.abs(highF64) < Float.MAX_VALUE)
        if (lowF64 == highF64 && lowF64.toFloat().toDouble() == lowF64 && ulpsF64 == 1.0 &&
            (lowF64 == 0.0 || kotlin.math.abs(lowF64) >= normalF64)) return exact(lowF64.toFloat())
        val spacingF64 = maxOf(Math.ulp(lowF64.toFloat()).toDouble(), Math.ulp(highF64.toFloat()).toDouble())
        val low = Math.nextDown(lowF64 - ulpsF64 * spacingF64)
        val high = Math.nextUp(highF64 + ulpsF64 * spacingF64)
        require(low >= -Float.MAX_VALUE.toDouble() && high <= Float.MAX_VALUE.toDouble())
        return ColorBoundsV1(if (low < normalF64 && high > -normalF64) minOf(0.0, low) else low,
            if (low < normalF64 && high > -normalF64) maxOf(0.0, high) else high)
    }
    fun prove(graph: ColorOperationGraphV1, words: Map<Long, Float>): List<ColorBranchFactV1>? = try {
        val facts = mutableListOf<ColorBranchFactV1>()
        val cache = java.util.IdentityHashMap<ColorOperationGraphV1.Scalar,
            java.util.IdentityHashMap<Map<ColorOperationGraphV1.Scalar, ColorBoundsV1>, ColorBoundsV1>>()
        fun hull(a: ColorBoundsV1, b: ColorBoundsV1) = ColorBoundsV1(minOf(a.lowerF64,b.lowerF64), maxOf(a.upperF64,b.upperF64))
        fun evaluate(node: ColorOperationGraphV1.Scalar, conditions: Map<ColorOperationGraphV1.Scalar, ColorBoundsV1>): ColorBoundsV1 {
            conditions[node]?.let { return it }
            cache[node]?.get(conditions)?.let { return it }
            fun value(n: ColorOperationGraphV1.Scalar) = evaluate(n, conditions)
            val result = when (node) {
                is ColorOperationGraphV1.Scalar.InputLinearPremul -> error("Unbound source input")
                is ColorOperationGraphV1.Scalar.DynamicF32 -> exact(requireNotNull(words[node.wordOffsetU32]))
                is ColorOperationGraphV1.Scalar.ConstantF32 -> exact(Float.fromBits(node.bitsI32))
                is ColorOperationGraphV1.Scalar.Clamp01 -> value(node.value).let {
                    ColorBoundsV1(it.lowerF64.coerceIn(0.0,1.0), it.upperF64.coerceIn(0.0,1.0)) }
                is ColorOperationGraphV1.Scalar.LazyBranch -> {
                    val a = when (val p = node.predicate) { is ColorOperationGraphV1.Predicate.Equal -> p.a; is ColorOperationGraphV1.Predicate.LessEqual -> p.a }
                    val b = when (val p = node.predicate) { is ColorOperationGraphV1.Predicate.Equal -> p.b; is ColorOperationGraphV1.Predicate.LessEqual -> p.b }
                    val left = value(a); val right = value(b)
                    val equal = node.predicate is ColorOperationGraphV1.Predicate.Equal
                    val yesPossible = if (equal) left.lowerF64 <= right.upperF64 && right.lowerF64 <= left.upperF64 else left.lowerF64 <= right.upperF64
                    val noPossible = if (equal) left.lowerF64 != left.upperF64 || right.lowerF64 != right.upperF64 || left != right else left.upperF64 > right.lowerF64
                    fun branch(taken: Boolean): ColorBoundsV1 {
                        facts += ColorBranchFactV1(node.predicate, taken, left, right)
                        val bounds = if (taken && equal) ColorBoundsV1(maxOf(left.lowerF64,right.lowerF64),minOf(left.upperF64,right.upperF64))
                        else if (!equal) if (taken) ColorBoundsV1(left.lowerF64,minOf(left.upperF64,right.upperF64))
                            else ColorBoundsV1(maxOf(left.lowerF64,right.lowerF64),left.upperF64) else null
                        val restricted = if (bounds == null) conditions else java.util.IdentityHashMap(conditions).apply { put(a,bounds) }
                        return evaluate(if (taken) node.yes else node.no, restricted)
                    }
                    when { !yesPossible -> branch(false); !noPossible -> branch(true); else -> hull(branch(true),branch(false)) }
                }
                is ColorOperationGraphV1.Scalar.Pow -> {
                    val base = value(node.a); val exponent = value(node.b)
                    // pow inherits log2, rounded multiply and exp2. Throughout the
                    // guard's base range [2^-126,1.001], real log2 lies [-126,.002].
                    // Its largest binary32 ULP is 2^-17: the outside-[.5,2] 3ULP
                    // error plus one rounding ULP is <=4*2^-17=2^-15. This also
                    // covers the alternative absolute2^-21 error on [.5,2].
                    // The exact exponent is 2.4F32=2.400000095367431640625<2.401.
                    // Multiplication adds at most 2^-15 (one ULP at magnitude303):
                    // t is in (-303,.005), since (.002+2^-15)*2.401+2^-15<.005.
                    // exp2's (3+2*abs(t))ULP error, plus one rounding ULP, is
                    // <=610*2^-23 globally here; 2^.005+610*2^-23<1.004<2.
                    // The lower bound uses the actual sealed EOTF graph, not just
                    // that broad guard: its selected power branch has x>.04045F32,
                    // so rounded (x+.055F32)/1.055F32>.09 and t>-9. Thus even
                    // 2^-9-610*2^-23>0; no exp2 underflow is reachable. Flushing
                    // a tiny log2/multiply result to zero remains in these bounds.
                    // [0,2] is therefore safe for EOTF, not a generic pow certificate.
                    require(base.lowerF64 >= normalF64 && base.upperF64 <= 1.001 && exponent == exact(2.4f))
                    ColorBoundsV1(0.0,2.0)
                }
                is ColorOperationGraphV1.Scalar.Divide -> {
                    val a = value(node.a); val b = value(node.b)
                    require(b.lowerF64 >= normalF64 || b.upperF64 <= -normalF64)
                    val corners = listOf(a.lowerF64 / b.lowerF64, a.lowerF64 / b.upperF64,
                        a.upperF64 / b.lowerF64, a.upperF64 / b.upperF64)
                    rounded(corners.min(),corners.max(), 3.5)
                }
                is ColorOperationGraphV1.Scalar.Subtract -> {
                    val a = value(node.a); val b = value(node.b)
                    rounded(Math.nextDown(a.lowerF64-b.upperF64),Math.nextUp(a.upperF64-b.lowerF64))
                }
                is ColorOperationGraphV1.Scalar.Add -> {
                    val terms = mutableListOf<ColorOperationGraphV1.Scalar>()
                    fun flatten(n: ColorOperationGraphV1.Scalar) { if (n is ColorOperationGraphV1.Scalar.Add) { flatten(n.a); flatten(n.b) } else terms += n }
                    flatten(node)
                    // Sum absolute bounds encloses every ordered/reassociated tree and FMA.
                    // Preserve exact representable sums where all operands are points.
                    val values = terms.map(::value)
                    val low = values.fold(0.0) { sum, term -> Math.nextDown(sum + term.lowerF64) }
                    val high = values.fold(0.0) { sum, term -> Math.nextUp(sum + term.upperF64) }
                    val magnitude = values.fold(0.0) { sum, term -> Math.nextUp(sum + maxOf(kotlin.math.abs(term.lowerF64),kotlin.math.abs(term.upperF64))) }
                    val growth = gamma(terms.size)
                    // FTZ contributes at most one minimum-normal absolute error
                    // per addition, subsequently amplified by the remaining tree.
                    val error = Math.nextUp(magnitude * growth + terms.size * normalF64 * (1.0 + growth))
                    require(Math.nextUp(magnitude + error) < Float.MAX_VALUE.toDouble())
                    if (values.all { it.lowerF64 == it.upperF64 } && values.all { it.lowerF64 == 0.0 } ) exact(0f)
                    else rounded(Math.nextDown(low-error),Math.nextUp(high+error))
                }
                is ColorOperationGraphV1.Scalar.Multiply -> {
                    val factors = mutableListOf<ColorOperationGraphV1.Scalar>()
                    fun flatten(n: ColorOperationGraphV1.Scalar) {
                        if (n is ColorOperationGraphV1.Scalar.Multiply) { flatten(n.a); flatten(n.b) }
                        else factors += n
                    }
                    flatten(node)
                    val values = factors.map(::value)
                    // Every subset product is bounded by this product of max(1,|x|).
                    // This also bounds amplification after an intermediate FTZ, even
                    // when a tiny factor is reassociated before a large coefficient.
                    val amplification = values.fold(1.0) { product, factor -> Math.nextUp(product *
                        maxOf(1.0,kotlin.math.abs(factor.lowerF64),kotlin.math.abs(factor.upperF64))) }
                    val growth = gamma(factors.size)
                    require(Math.nextUp(amplification * (1.0 + growth)) < Float.MAX_VALUE.toDouble())
                    if (values.any { it == exact(0f) }) exact(0f)
                    else if (values.all { it == exact(1f) }) exact(1f)
                    else {
                        val product = values.fold(ColorBoundsV1(1.0,1.0)) { a,b ->
                            val corners = listOf(a.lowerF64*b.lowerF64,a.lowerF64*b.upperF64,a.upperF64*b.lowerF64,a.upperF64*b.upperF64)
                            ColorBoundsV1(Math.nextDown(corners.min()),Math.nextUp(corners.max()))
                        }
                        val magnitude = maxOf(kotlin.math.abs(product.lowerF64),kotlin.math.abs(product.upperF64))
                        val error = Math.nextUp(magnitude * growth + factors.size * normalF64 * amplification * (1.0 + growth))
                        rounded(Math.nextDown(product.lowerF64-error),Math.nextUp(product.upperF64+error))
                    }
                }
            }
            cache.getOrPut(node) { java.util.IdentityHashMap() }[conditions] = result
            return result
        }
        graph.outputs.forEach { evaluate(it, emptyMap()) }
        facts
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
}
