package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.render.ir.ImmutableUBytes

/** Authenticated expressions, bindings and selected-branch facts; no caller-supplied component box. */
public class ColorSourceProofV1 private constructor(
    public val canonicalIdentity: String,
    public val sourceIdentity: String,
    public val coordinates: SourceCoordinatesV4,
    private val graph: ColorOperationGraphV1,
    internal val bindingOwners: List<MaterialBindingPlan>,
    internal val numericWordBits: Map<Long, Int>,
    internal val tableRecords: Map<Long, ImmutableUBytes>,
    internal val conditionedFacts: List<ColorBranchFactV1>,
    internal val deviceBoundsF32: RectF32,
    private val parentSource: ColorSourceProofV1? = null,
    private val filterExecution: ColorFilterExecutionPlanV1? = null,
    private val childOutputCertificates: List<ColorSourceProofV1> = emptyList(),
    internal val integerWordValuesU32: Map<Long, UInt> = emptyMap(),
    public val gradientStopSlab: GradientStopSlabPlanV1? = null,
    internal val preparedDefinition: PreparedSourceDefinitionV4? = null,
) {
    internal val uniformWordCountI64: Long = if (parentSource == null) bindingOwners.fold(
        if (bindingOwners.isEmpty()) preparedDefinition?.uniformWordCountI64 ?: 0L else 0L) { size, binding ->
        Math.addExact(size, binding.colorUniformWordCountV4())
    } else
        Math.addExact(parentSource.uniformWordCountI64, requireNotNull(filterExecution).dynamicByteCountI64 / 4L)
    internal val sourceUniformWordCountI64: Long = if (parentSource != null) parentSource.sourceUniformWordCountI64
        else bindingOwners.filterNot { it is ColorFilterBindingV4 }.fold(
            if (bindingOwners.isEmpty()) preparedDefinition?.uniformWordCountI64 ?: 0L else 0L) { size,binding ->
            Math.addExact(size,binding.colorUniformWordCountV4())
        }
    public fun copyOperationGraph(): ColorOperationGraphV1 = graph
    public fun authenticates(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: SourceCoordinatesV4): Boolean {
        if (this.coordinates != coordinates || table.sourceIdentity(root) != sourceIdentity) return false
        if (parentSource != null) {
            val binding = table.entry(root).bindings as? ColorFilterBindingV4 ?: return false
            return binding.execution === filterExecution && binding.numericAuthority.outputSourceProof === this &&
                root.indexI32 > 0 && parentSource.authenticates(table, MaterialPlanRef(root.indexI32 - 1), coordinates)
        }
        if (bindingOwners.isEmpty() && preparedDefinition != null) {
            val binding = table.entry(root).bindings as? GradientInterpolationBindingV4 ?: return false
            return binding.definition === preparedDefinition && binding.sourceProof === this &&
                table.gradientStopSlab === preparedDefinition.slab
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
            val words = LinkedHashMap(source.numericWordBits)
            val tables = LinkedHashMap(source.tableRecords)
            execution.forEachWord { offset, value -> words[Math.addExact(source.uniformWordCountI64, offset)] = value }
            execution.forEachTable { offset, table -> tables[Math.addExact(source.uniformWordCountI64,offset)] = table }
            // Compose transports the complete inner output certificate, not its
            // component box, into the outer filter's original lazy alpha guards.
            val composeOutputs = execution.composeChildren?.let { (outer,inner) ->
                val innerOutput = ColorNumericAuthorityV1.seal(inner,source)?.outputSourceProof ?: return null
                val outerOutput = ColorNumericAuthorityV1.seal(outer,innerOutput)?.outputSourceProof ?: return null
                require(outerOutput.graph.canonicalIdentity == graph.canonicalIdentity && outerOutput.numericWordBits == words && outerOutput.tableRecords == tables)
                listOf(innerOutput,outerOutput)
            }
            // Both Lerp functions receive this exact original source owner. Their
            // full conditioned certificates remain retained; the final shared DAG
            // proves the rebased branch outputs and rounded premultiplied merge.
            val lerpOutputs = execution.lerpChildren?.let { (dst,src) ->
                listOf(ColorNumericAuthorityV1.seal(dst,source)?.outputSourceProof ?: return null,
                    ColorNumericAuthorityV1.seal(src,source)?.outputSourceProof ?: return null)
            }
            val proof = composeOutputs?.last()?.conditionedFacts ?: ColorRoundedGraphProofV1.prove(graph, words, tables,
                source.deviceBoundsF32,source.integerWordValuesU32,source.gradientStopSlab) ?: return null
            val certifiedGraph = composeOutputs?.last()?.graph ?: graph
            val identity = "color-composed-proof-v1:${source.canonicalIdentity}:${execution.canonicalIdentity}:${graph.canonicalIdentity}:${proof.map { Triple(it.taken,it.left,it.right) }}"
            return ColorSourceProofV1(identity, filteredIdentity(source.sourceIdentity,execution), source.coordinates,
                certifiedGraph, source.bindingOwners, java.util.Collections.unmodifiableMap(words), java.util.Collections.unmodifiableMap(tables), immutableList(proof),
                source.deviceBoundsF32, source, execution,immutableList(composeOutputs ?: lerpOutputs ?: emptyList()),
                source.integerWordValuesU32,source.gradientStopSlab,source.preparedDefinition)
        }
        fun issuePrepared(definition: PreparedSourceDefinitionV4): ColorSourceProofV1? {
            val graph = ColorSourceProofCompilerV1.graphForPrepared(definition)
            val facts = ColorRoundedGraphProofV1.prove(graph,definition.numericWordsF32Bits,emptyMap(),
                definition.deviceBoundsF32,definition.integerWordsU32,definition.slab) ?: return null
            val identity = "gradient-source-proof-v4:${definition.executionIdentity}:${graph.canonicalIdentity}:" +
                facts.map { Triple(it.taken,it.left,it.right) }
            return ColorSourceProofV1(identity,definition.definitionIdentity,SourceCoordinatesV4.V2(definition.coordinates),
                graph,emptyList(),definition.numericWordsF32Bits,emptyMap(),immutableList(facts),definition.deviceBoundsF32.copy(),
                integerWordValuesU32=definition.integerWordsU32,gradientStopSlab=definition.slab,preparedDefinition=definition)
        }
        fun issue(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: SourceCoordinatesV4,
            boundsF32: RectF32, graph: ColorOperationGraphV1, owners: List<MaterialBindingPlan>,
            words: Map<Long, Int>, tables: Map<Long,ImmutableUBytes>, integers: Map<Long,UInt> = emptyMap(),
            stops: GradientStopSlabPlanV1? = null): ColorSourceProofV1? {
            val proof = ColorRoundedGraphProofV1.prove(graph, words, tables,boundsF32,integers,stops) ?: return null
            val source = table.sourceIdentity(root)
            val identity = "color-source-proof-v1:$source:${coordinates.identityV4()}:" +
                listOf(boundsF32.left, boundsF32.top, boundsF32.right, boundsF32.bottom).joinToString { it.toRawBits().toString() } +
                ":${graph.canonicalIdentity}:${words.entries.joinToString { "${it.key}=${it.value}" }}:${tables.entries.joinToString { "${it.key}=${it.value.canonicalId.value}" }}:${proof.map { Triple(it.taken,it.left,it.right) }}" +
                if (integers.isEmpty() && stops == null) "" else ":u32=$integers:stops=${stops?.canonicalIdentity}"
            return ColorSourceProofV1(identity, source, coordinates, graph, immutableList(owners),
                java.util.Collections.unmodifiableMap(LinkedHashMap(words)),java.util.Collections.unmodifiableMap(LinkedHashMap(tables)), immutableList(proof), boundsF32.copy(),
                integerWordValuesU32=java.util.Collections.unmodifiableMap(LinkedHashMap(integers)),gradientStopSlab=stops)
        }
    }
}

internal fun MaterialBindingPlan.colorUniformWordCountV4(): Long = when (this) {
    is ColorFilterBindingV4 -> execution.dynamicByteCountI64/4L
    is GradientInterpolationBindingV4 -> definition.uniformWordCountI64
    else -> 4L
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
    private val largestDivisorF64 = Math.scalb(1.0,126)
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
    fun prove(graph: ColorOperationGraphV1, words: Map<Long, Int>, tables: Map<Long,ImmutableUBytes>,
        deviceBoundsF32: RectF32? = null, integers: Map<Long,UInt> = emptyMap(),
        stopSlab: GradientStopSlabPlanV1? = null): List<ColorBranchFactV1>? = try {
        // Numeric slots and packed-byte spans are disjoint. A finite-looking U32
        // Table word must never gain permission to be interpreted as a coefficient.
        tables.forEach { (offset, table) ->
            require(table.sizeI32 == 256 && offset >= 0L && offset % 4L == 0L && offset <= UInt.MAX_VALUE.toLong()-63L)
            require((offset..offset+63L).none { it in words || it in integers })
            require(tables.keys.none { it != offset && it < offset+64L && offset < it+64L })
        }
        require(integers.keys.none(words::containsKey))
        val stops = stopSlab?.copyStops()
        val interpolationGraphs = java.util.IdentityHashMap<ColorOperationGraphV1.GradientStopSelection,
            MutableMap<Int,List<ColorOperationGraphV1.Scalar>>>()
        val facts = mutableListOf<ColorBranchFactV1>()
        val cache = java.util.IdentityHashMap<ColorOperationGraphV1.Scalar,
            java.util.IdentityHashMap<Map<ColorOperationGraphV1.Scalar, ColorBoundsV1>, ColorBoundsV1>>()
        fun hull(a: ColorBoundsV1, b: ColorBoundsV1) = ColorBoundsV1(minOf(a.lowerF64,b.lowerF64), maxOf(a.upperF64,b.upperF64))
        fun evaluate(node: ColorOperationGraphV1.Scalar, conditions: Map<ColorOperationGraphV1.Scalar, ColorBoundsV1>): ColorBoundsV1 {
            conditions[node]?.let { return it }
            cache[node]?.get(conditions)?.let { return it }
            fun value(n: ColorOperationGraphV1.Scalar) = evaluate(n, conditions)
            // The emitter materializes comparison operands before composing &&.
            // Validate all operands in the incoming context, not only a context
            // conditioned on an earlier conjunct being true.
            fun predicateOperands(predicate: ColorOperationGraphV1.Predicate) {
                when (predicate) {
                    is ColorOperationGraphV1.Predicate.UniformU32Equal -> require(predicate.wordOffsetU32 in integers)
                    is ColorOperationGraphV1.Predicate.Equal -> { value(predicate.a); value(predicate.b) }
                    is ColorOperationGraphV1.Predicate.LessEqual -> { value(predicate.a); value(predicate.b) }
                    is ColorOperationGraphV1.Predicate.Not -> predicateOperands(predicate.value)
                    is ColorOperationGraphV1.Predicate.And -> { predicateOperands(predicate.a); predicateOperands(predicate.b) }
                    is ColorOperationGraphV1.Predicate.Finite -> value(predicate.value)
                    is ColorOperationGraphV1.Predicate.ProjectiveValid -> value(predicate.division)
                }
            }
            fun contexts(predicate: ColorOperationGraphV1.Predicate, taken: Boolean,
                current: Map<ColorOperationGraphV1.Scalar,ColorBoundsV1>): List<Map<ColorOperationGraphV1.Scalar,ColorBoundsV1>> {
                if (predicate is ColorOperationGraphV1.Predicate.ProjectiveValid) {
                    val division = predicate.division
                    val a = evaluate(division.a,current); val b = evaluate(division.b,current)
                    evaluate(division,current)
                    val zeroDenominator = b.lowerF64 == 0.0 && b.upperF64 == 0.0
                    val nonzeroNormal = b.lowerF64 >= normalF64 || b.upperF64 <= -normalF64
                    val minDenominator = minOf(kotlin.math.abs(b.lowerF64),kotlin.math.abs(b.upperF64))
                    val quotientMagnitude = maxOf(kotlin.math.abs(a.lowerF64),kotlin.math.abs(a.upperF64))/minDenominator
                    // Strictly below the exponent guard even after fraction division rounding.
                    val alwaysValid = nonzeroNormal && quotientMagnitude*(1.0+7.0*Math.scalb(1.0,-23)) < Math.scalb(1.0,127)
                    if (taken && zeroDenominator || !taken && alwaysValid) return emptyList()
                    facts += ColorBranchFactV1(predicate,taken,a,b)
                    return listOf(current)
                }
                if (predicate is ColorOperationGraphV1.Predicate.Finite) {
                    // This branch may claim true only if the actual rounded scalar
                    // has already proved finite; overflow never gets a fake zero.
                    evaluate(predicate.value,current)
                    return if (taken) listOf(current) else emptyList()
                }
                if (predicate is ColorOperationGraphV1.Predicate.UniformU32Equal) {
                    val actual = requireNotNull(integers[predicate.wordOffsetU32])
                    if ((actual == predicate.expectedU32) != taken) return emptyList()
                    facts += ColorBranchFactV1(predicate,taken,ColorBoundsV1(actual.toDouble(),actual.toDouble()),
                        ColorBoundsV1(predicate.expectedU32.toDouble(),predicate.expectedU32.toDouble()))
                    return listOf(current)
                }
                if (predicate is ColorOperationGraphV1.Predicate.Not) return contexts(predicate.value,!taken,current)
                if (predicate is ColorOperationGraphV1.Predicate.And) return if (taken)
                    contexts(predicate.a,true,current).flatMap { contexts(predicate.b,true,it) }
                    else contexts(predicate.a,false,current)+contexts(predicate.a,true,current).flatMap { contexts(predicate.b,false,it) }
                val a = when (predicate) { is ColorOperationGraphV1.Predicate.Equal -> predicate.a; is ColorOperationGraphV1.Predicate.LessEqual -> predicate.a }
                val b = when (predicate) { is ColorOperationGraphV1.Predicate.Equal -> predicate.b; is ColorOperationGraphV1.Predicate.LessEqual -> predicate.b }
                val left = evaluate(a,current); val right = evaluate(b,current)
                val equal = predicate is ColorOperationGraphV1.Predicate.Equal
                val yesPossible = if (equal) left.lowerF64 <= right.upperF64 && right.lowerF64 <= left.upperF64 else left.lowerF64 <= right.upperF64
                val noPossible = if (a === b) false else if (equal) left.lowerF64 != left.upperF64 || right.lowerF64 != right.upperF64 || left != right
                    else left.upperF64 > right.lowerF64
                if (taken && !yesPossible || !taken && !noPossible) return emptyList()
                facts += ColorBranchFactV1(predicate,taken,left,right)
                val bounds = if (taken && equal) ColorBoundsV1(maxOf(left.lowerF64,right.lowerF64),minOf(left.upperF64,right.upperF64))
                    else if (!equal) if (taken) ColorBoundsV1(left.lowerF64,minOf(left.upperF64,right.upperF64))
                        else ColorBoundsV1(maxOf(left.lowerF64,right.lowerF64),left.upperF64) else null
                return listOf(if (bounds == null) current else java.util.IdentityHashMap(current).apply { put(a,bounds) })
            }
            val result = when (node) {
                is ColorOperationGraphV1.Scalar.InputLinearPremul -> error("Unbound source input")
                is ColorOperationGraphV1.Scalar.DevicePositionF32 -> {
                    val bounds = requireNotNull(deviceBoundsF32)
                    require(bounds.isFinite() && bounds.isSorted())
                    if (node.channelI32 == 0) ColorBoundsV1(bounds.left.toDouble(),bounds.right.toDouble())
                    else ColorBoundsV1(bounds.top.toDouble(),bounds.bottom.toDouble())
                }
                is ColorOperationGraphV1.Scalar.DynamicF32 -> exact(Float.fromBits(requireNotNull(words[node.wordOffsetU32])))
                is ColorOperationGraphV1.Scalar.ConstantF32 -> exact(Float.fromBits(node.bitsI32))
                is ColorOperationGraphV1.Scalar.Clamp01 -> value(node.value).let {
                    ColorBoundsV1(it.lowerF64.coerceIn(0.0,1.0), it.upperF64.coerceIn(0.0,1.0)) }
                is ColorOperationGraphV1.Scalar.TableByte -> {
                    val scaled = value(node.scaled)
                    val firstI32 = kotlin.math.ceil(scaled.lowerF64-0.5).toInt()
                    val lastI32 = kotlin.math.floor(scaled.upperF64+0.5).toInt()
                    require(firstI32 in 0..255 && lastI32 in firstI32..255)
                    val table = requireNotNull(tables[node.tableWordOffsetU32])
                    val reachableBytes = (firstI32..lastI32).map { table[it].toInt() }
                    ColorBoundsV1(reachableBytes.min().toDouble(),reachableBytes.max().toDouble())
                }
                is ColorOperationGraphV1.Scalar.Min, is ColorOperationGraphV1.Scalar.Max -> {
                    val a = value(if (node is ColorOperationGraphV1.Scalar.Min) node.a else (node as ColorOperationGraphV1.Scalar.Max).a)
                    val b = value(if (node is ColorOperationGraphV1.Scalar.Min) node.b else (node as ColorOperationGraphV1.Scalar.Max).b)
                    val ordinary = if (node is ColorOperationGraphV1.Scalar.Min)
                        ColorBoundsV1(minOf(a.lowerF64,b.lowerF64),minOf(a.upperF64,b.upperF64))
                    else ColorBoundsV1(maxOf(a.lowerF64,b.lowerF64),maxOf(a.upperF64,b.upperF64))
                    // WGSL's either-input freedom is restricted to the actual
                    // BOTH-subnormal branch. It cannot select a normal extremum
                    // merely because the broad input interval also contains zero.
                    if (a.lowerF64 < normalF64 && a.upperF64 > -normalF64 &&
                        b.lowerF64 < normalF64 && b.upperF64 > -normalF64) {
                        val tinyA = ColorBoundsV1(maxOf(a.lowerF64,-normalF64),minOf(a.upperF64,normalF64))
                        val tinyB = ColorBoundsV1(maxOf(b.lowerF64,-normalF64),minOf(b.upperF64,normalF64))
                        hull(ordinary,hull(tinyA,tinyB))
                    } else ordinary
                }
                is ColorOperationGraphV1.Scalar.Abs -> value(node.value).let {
                    ColorBoundsV1(if (it.lowerF64 <= 0.0 && it.upperF64 >= 0.0) 0.0 else minOf(kotlin.math.abs(it.lowerF64),kotlin.math.abs(it.upperF64)),
                        maxOf(kotlin.math.abs(it.lowerF64),kotlin.math.abs(it.upperF64))) }
                is ColorOperationGraphV1.Scalar.Sqrt -> value(node.value).let {
                    require(it.lowerF64 >= normalF64)
                    // Bare sqrt inherits reciprocal(inverseSqrt), whose finite
                    // accuracy domain excludes zero. A real graph branch must
                    // avoid this operation if an exact zero is to stay admitted.
                    val inverse = rounded(1.0/StrictMath.sqrt(it.upperF64),1.0/StrictMath.sqrt(it.lowerF64),3.0)
                    require(inverse.lowerF64 >= normalF64 && inverse.upperF64 <= largestDivisorF64)
                    rounded(Math.nextDown(1.0/inverse.upperF64),Math.nextUp(1.0/inverse.lowerF64),3.5)
                }
                is ColorOperationGraphV1.Scalar.Atan2 -> {
                    val y = value(node.y); val x = value(node.x)
                    fun hasZero(v: ColorBoundsV1) = v.lowerF64 <= 0.0 && v.upperF64 >= 0.0
                    fun normal(v: ColorBoundsV1) = v.lowerF64 >= normalF64 || v.upperF64 <= -normalF64
                    // WGSL 15.7.4.1 grants 4096 ULP only for normal y and
                    // 2^-126 <= |x| <= 2^126. Sweep's real eager axis guards
                    // substitute one before this call; zero is not an exemption.
                    require(normal(x) && normal(y) &&
                        maxOf(kotlin.math.abs(x.lowerF64),kotlin.math.abs(x.upperF64)) <= largestDivisorF64)
                    val corners = listOf(StrictMath.atan2(y.lowerF64,x.lowerF64),StrictMath.atan2(y.lowerF64,x.upperF64),
                        StrictMath.atan2(y.upperF64,x.lowerF64),StrictMath.atan2(y.upperF64,x.upperF64))
                    val crossesCut = x.lowerF64 < 0.0 && hasZero(y)
                    rounded(Math.nextDown(if (crossesCut) -Math.PI else corners.min()),
                        Math.nextUp(if (crossesCut) Math.PI else corners.max()),4096.0)
                }
                is ColorOperationGraphV1.Scalar.GradientStopComponent -> {
                    val selected = node.selection
                    val numerator = value(selected.numerator)
                    val scale = value(selected.scale)
                    value(selected.parameter)
                    require(scale.lowerF64 >= normalF64)
                    val base = requireNotNull(integers[selected.rangeWordOffsetU32]).toLong()
                    val count = requireNotNull(integers[selected.rangeWordOffsetU32+1L]).toLong()
                    val slab = requireNotNull(stops)
                    require(count in 2L..selected.countBoundU32.toLong() && base+count <= slab.size.toLong())
                    val range = slab.subList(base.toInt(),(base+count).toInt())
                    require(range.all { it.domain == selected.domain } &&
                        range.zipWithNext().all { (a,b) -> a.positionF32 <= b.positionF32 })
                    // Search executes the actual rounded position*scale comparison,
                    // not a nominal t box. Every reachable upper-bound result is kept.
                    val reachable = if (selected.firstOnly) listOf(0) else {
                        val comparisons = range.map { stop -> value(ColorOperationGraphV1.Scalar.Multiply(
                            ColorOperationGraphV1.constant(stop.positionF32),selected.scale)) }
                        (0..range.size).filter { index ->
                            (index == 0 || comparisons[index-1].lowerF64 <= numerator.upperF64) &&
                                (index == range.size || comparisons[index].upperF64 > numerator.lowerF64)
                        }
                    }
                    require(reachable.isNotEmpty())
                    val byIndex = interpolationGraphs.getOrPut(selected) { mutableMapOf() }
                    reachable.map { index ->
                        val outputs = byIndex.getOrPut(index) {
                            val left = range[(index-1).coerceIn(range.indices)]
                            val right = range[index.coerceIn(range.indices)]
                            fun channels(stop: GradientStopPlanV1): List<ColorOperationGraphV1.Scalar> =
                                stop.preparedTupleF32.let { c -> listOf(c.red,c.green,c.blue,c.alpha).map(ColorOperationGraphV1::constant) }
                            val a = channels(left); val b = channels(right)
                            val low = ColorOperationGraphV1.constant(left.positionF32)
                            val high = ColorOperationGraphV1.constant(right.positionF32)
                            val one = ColorOperationGraphV1.constant(1f)
                            val weight = ColorOperationGraphV1.Scalar.Clamp01(ColorOperationGraphV1.Scalar.Divide(
                                ColorOperationGraphV1.Scalar.Subtract(selected.parameter,low),
                                ColorOperationGraphV1.Scalar.Subtract(high,low)))
                            val inverse = ColorOperationGraphV1.Scalar.Subtract(one,weight)
                            List(4) { channel ->
                                if (left.preparedTupleF32 == right.preparedTupleF32) a[channel]
                                else ColorOperationGraphV1.Scalar.LazyBranch(ColorOperationGraphV1.Predicate.LessEqual(high,low),b[channel],
                                    ColorOperationGraphV1.Scalar.LazyBranch(ColorOperationGraphV1.Predicate.Equal(selected.parameter,low),a[channel],
                                        ColorOperationGraphV1.Scalar.Add(ColorOperationGraphV1.Scalar.Multiply(inverse,a[channel]),
                                            ColorOperationGraphV1.Scalar.Multiply(weight,b[channel]))))
                            }
                        }
                        evaluate(outputs[node.channelI32],conditions)
                    }.reduce(::hull)
                }
                is ColorOperationGraphV1.Scalar.BranchComponent -> {
                    predicateOperands(node.branch.predicate)
                    val alternatives = contexts(node.branch.predicate,true,conditions).map {
                        evaluate(node.branch.yes[node.channelI32],it)
                    }+contexts(node.branch.predicate,false,conditions).map {
                        evaluate(node.branch.no[node.channelI32],it)
                    }
                    alternatives.reduce(::hull)
                }
                is ColorOperationGraphV1.Scalar.Floor -> value(node.value).let {
                    // Every reachable integer is exactly representable in this
                    // bounded domain; floor is exact under the pinned profile.
                    require(it.lowerF64 >= -16777216.0 && it.upperF64 <= 16777216.0)
                    // A negative subnormal operand may be flushed to zero.
                    ColorBoundsV1(kotlin.math.floor(it.lowerF64),kotlin.math.floor(
                        if (it.upperF64 > -normalF64 && it.lowerF64 < normalF64) maxOf(0.0,it.upperF64) else it.upperF64))
                }
                is ColorOperationGraphV1.Scalar.Round -> value(node.value).let {
                    require(it.lowerF64 >= -16777215.0 && it.upperF64 <= 16777215.0)
                    // Include either permitted direction at every halfway tie.
                    ColorBoundsV1(kotlin.math.ceil(it.lowerF64-0.5),kotlin.math.floor(it.upperF64+0.5))
                }
                is ColorOperationGraphV1.Scalar.IntegerModulo -> value(node.value).let {
                    // The typed Floor operand is integral. Validate conversion
                    // BEFORE i32; the normalized remainder and its f32 result
                    // are exact, with no floating quotient near sector seams.
                    require(it.lowerF64 >= -16777216.0 && it.upperF64 <= 16777216.0 &&
                        it.lowerF64 == kotlin.math.floor(it.lowerF64) && it.upperF64 == kotlin.math.floor(it.upperF64))
                    val first = it.lowerF64.toInt(); val last = it.upperF64.toInt(); val modulus = node.modulusI32
                    if (Math.floorDiv(first,modulus) == Math.floorDiv(last,modulus))
                        ColorBoundsV1(Math.floorMod(first,modulus).toDouble(),Math.floorMod(last,modulus).toDouble())
                    else ColorBoundsV1(0.0,(modulus-1).toDouble())
                }
                is ColorOperationGraphV1.Scalar.EagerSelect -> {
                    val yes = value(node.yes); val no = value(node.no)
                    predicateOperands(node.predicate)
                    val canYes = contexts(node.predicate,true,conditions).isNotEmpty()
                    val canNo = contexts(node.predicate,false,conditions).isNotEmpty()
                    when { !canYes -> no; !canNo -> yes; else -> hull(yes,no) }
                }
                is ColorOperationGraphV1.Scalar.LazyBranch -> {
                    predicateOperands(node.predicate)
                    val alternatives = contexts(node.predicate,true,conditions).map { evaluate(node.yes,it) }+
                        contexts(node.predicate,false,conditions).map { evaluate(node.no,it) }
                    alternatives.reduce(::hull)
                }
                is ColorOperationGraphV1.Scalar.Pow -> {
                    val base = value(node.a); val exponent = value(node.b)
                    require(base.lowerF64 >= normalF64 && exponent.lowerF64 == exponent.upperF64 &&
                        exponent.lowerF64 > 0.0 && exponent.upperF64 <= 4.0)
                    // Pinned WGSL pow inherits log2 -> rounded multiply -> exp2.
                    // StrictMath's <=1 Double ULP approximation is included before
                    // adding the much larger F32 built-in accuracy envelopes.
                    val logLow = StrictMath.log(base.lowerF64)/StrictMath.log(2.0)
                    val logHigh = StrictMath.log(base.upperF64)/StrictMath.log(2.0)
                    val logError = maxOf(Math.scalb(1.0,-21),4.0*maxOf(
                        Math.ulp(logLow.toFloat()).toDouble(),Math.ulp(logHigh.toFloat()).toDouble()))
                    val log = ColorBoundsV1(Math.nextDown(logLow-logError),Math.nextUp(logHigh+logError))
                    val product = rounded(Math.nextDown(log.lowerF64*exponent.lowerF64),
                        Math.nextUp(log.upperF64*exponent.upperF64))
                    val low = StrictMath.pow(2.0,product.lowerF64)
                    val high = StrictMath.pow(2.0,product.upperF64)
                    val accuracy = 4.0+2.0*maxOf(kotlin.math.abs(product.lowerF64),kotlin.math.abs(product.upperF64))
                    rounded(Math.nextDown(low),Math.nextUp(high),accuracy)
                }
                is ColorOperationGraphV1.Scalar.Divide -> {
                    val a = value(node.a); val b = value(node.b)
                    require(b.lowerF64 >= normalF64 || b.upperF64 <= -normalF64)
                    require(maxOf(kotlin.math.abs(b.lowerF64),kotlin.math.abs(b.upperF64)) <= largestDivisorF64)
                    val corners = listOf(a.lowerF64 / b.lowerF64, a.lowerF64 / b.upperF64,
                        a.upperF64 / b.lowerF64, a.upperF64 / b.upperF64)
                    rounded(corners.min(),corners.max(), 3.5)
                }
                is ColorOperationGraphV1.Scalar.ProjectiveDivide -> {
                    val originalA = value(node.a); val originalB = value(node.b)
                    fun inputFtz(a: ColorBoundsV1): ColorBoundsV1 = if (a.lowerF64 < normalF64 && a.upperF64 > -normalF64)
                        hull(a,exact(0f)) else a
                    val a = inputFtz(originalA); val b = inputFtz(originalB)
                    val maximum = Float.MAX_VALUE.toDouble()
                    if (b.lowerF64 == 0.0 && b.upperF64 == 0.0) exact(0f)
                    else if (b.lowerF64 <= 0.0 && b.upperF64 >= 0.0) {
                        // This is the finite codomain of the ACTUAL checked binary
                        // operation, not a source/color box: invalid input, zero w,
                        // or result exponent>128 returns (0,false). Valid ldexp has
                        // a normalized F32 fraction, so exponent<=128 is finite.
                        ColorBoundsV1(-maximum,maximum)
                    } else {
                        val corners = listOf(a.lowerF64/b.lowerF64,a.lowerF64/b.upperF64,
                            a.upperF64/b.lowerF64,a.upperF64/b.upperF64)
                        val magnitude = maxOf(kotlin.math.abs(corners.min()),kotlin.math.abs(corners.max()))
                        // Exact bit normalization gives |fractions| in [.5,1).
                        // Their actual division lies in [.5,2], inside the WGSL
                        // normal divisor domain. 3.5 ULP is enclosed by relative
                        // 7*2^-23. frexp and normal ldexp preserve the F32 bits;
                        // underflow/FTZ is enclosed by minimum-normal absolute error.
                        val error = Math.nextUp(magnitude*7.0*Math.scalb(1.0,-23)+normalF64)
                        val low = Math.nextDown(corners.min()-error)
                        val high = Math.nextUp(corners.max()+error)
                        if (low > maximum || high < -maximum) exact(0f)
                        else {
                            val bounded = ColorBoundsV1(maxOf(-maximum,low),minOf(maximum,high))
                            if (low < -maximum || high > maximum) hull(bounded,exact(0f)) else bounded
                        }
                    }
                }
                is ColorOperationGraphV1.Scalar.Subtract -> {
                    val a = value(node.a); val b = value(node.b)
                    // Equal point operands subtract to the exactly representable
                    // zero in every rounding mode. Preserve that operation result
                    // before outward Double rounding, e.g. SRC_OUT at dst alpha1.
                    if (a.lowerF64 == a.upperF64 && a == b) exact(0f)
                    else rounded(Math.nextDown(a.lowerF64-b.upperF64),Math.nextUp(a.upperF64-b.lowerF64))
                }
                is ColorOperationGraphV1.Scalar.Add -> {
                    val leftProduct = node.a as? ColorOperationGraphV1.Scalar.Multiply
                    val rightProduct = node.b as? ColorOperationGraphV1.Scalar.Multiply
                    val inverseWeight = leftProduct?.a as? ColorOperationGraphV1.Scalar.Subtract
                    val a = leftProduct?.b as? ColorOperationGraphV1.Scalar.ConstantF32
                    val b = rightProduct?.b as? ColorOperationGraphV1.Scalar.ConstantF32
                    val complementary = inverseWeight?.a as? ColorOperationGraphV1.Scalar.ConstantF32
                    if (a != null && b != null && complementary?.bitsI32 == 1f.toRawBits() &&
                        inverseWeight.b is ColorOperationGraphV1.Scalar.Clamp01 && inverseWeight.b === rightProduct.a) {
                        // Actual stop interpolation shares one rounded/clamped w.
                        // Do not independently choose w=0 and (1-w)=0. These are
                        // the real four operations, not an assumed RGB/alpha box.
                        val weight = value(inverseWeight.b)
                        require(weight.lowerF64 >= 0.0 && weight.upperF64 <= 1.0)
                        val av = Float.fromBits(a.bitsI32).toDouble()
                        val bv = Float.fromBits(b.bitsI32).toDouble()
                        fun endpoint(w: Double): Double = (1.0-w)*av+w*bv
                        val endpoints = listOf(endpoint(weight.lowerF64),endpoint(weight.upperF64))
                        val magnitude = Math.nextUp(kotlin.math.abs(av)+kotlin.math.abs(bv))
                        val growth = gamma(4)
                        // Subtract, two products, add; this also encloses a fused
                        // or reassociated sum and amplification of intermediate FTZ.
                        val error = Math.nextUp(magnitude*growth + 4.0*normalF64*maxOf(1.0,magnitude)*(1.0+growth))
                        require(Math.nextUp(magnitude+error) < Float.MAX_VALUE.toDouble())
                        rounded(Math.nextDown(endpoints.min()-error),Math.nextUp(endpoints.max()+error))
                    } else {
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
                    // Adding exact zeros cannot change a normal representable point,
                    // whichever sum tree/FMA is selected. Upstream products must
                    // already have proved point values; ranges never enter this rule.
                    val nonzero = values.filter { it.lowerF64 != 0.0 || it.upperF64 != 0.0 }
                    if (nonzero.isEmpty()) exact(0f)
                    else if (nonzero.size == 1 && nonzero.single().lowerF64 == nonzero.single().upperF64 &&
                        kotlin.math.abs(nonzero.single().lowerF64) >= normalF64 &&
                        nonzero.single().lowerF64.toFloat().toDouble() == nonzero.single().lowerF64) nonzero.single()
                    else rounded(Math.nextDown(low-error),Math.nextUp(high+error))
                    }
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
                    else if (factors.size == 2 && node.a === node.b) {
                        // The same emitted scalar is squared, not two independent
                        // samples from its interval. Retain all rounded-operation
                        // and FTZ error; only the exact sign correlation is used.
                        val operand = values.first()
                        val low = if (operand.lowerF64 <= 0.0 && operand.upperF64 >= 0.0) 0.0 else
                            minOf(operand.lowerF64*operand.lowerF64,operand.upperF64*operand.upperF64)
                        val high = maxOf(operand.lowerF64*operand.lowerF64,operand.upperF64*operand.upperF64)
                        val error = Math.nextUp(high*growth + 2.0*normalF64*amplification*(1.0+growth))
                        val enclosure = rounded(Math.nextDown(low-error),Math.nextUp(high+error))
                        ColorBoundsV1(maxOf(0.0,enclosure.lowerF64),enclosure.upperF64)
                    }
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
