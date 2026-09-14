package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ImmutableUBytes
import org.graphiks.math.geometry.RectF32

public data class MaterialEvaluationRefV5(public val indexI32: Int) {
    init { require(indexI32 >= 0) { W5gPlanDiagnostics.Schema } }
}

public class ComposedMaterialProgramV5 internal constructor(
    override val structuralId: MaterialProgramPlanId,
    internal val operationGraph: ColorOperationGraphV1,
) : MaterialProgramPlan {
    override val versionI32: Int = 5
    override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.colorSourceV4()
}

public class ComposedMaterialBindingV5 internal constructor(
    internal val definition: PreparedComposedSourceV5,
    public val sourceProof: ColorSourceProofV1,
) : MaterialBindingPlan {
    override val versionI32: Int = 5
    init { require(sourceProof.composedDefinition === definition) { W5gPlanDiagnostics.Schema } }
    internal fun authenticates(program: ComposedMaterialProgramV5): Boolean =
        program.structuralId == definition.program.structuralId &&
            program.operationGraph.canonicalIdentity == definition.operationGraph.canonicalIdentity &&
            sourceProof.composedDefinition === definition && definition.frameOwner.owns(definition.captured)
}

/** Physical scalar layout. Resource families deliberately have no Task1 constructor. */
public class ComposedBindingLayoutV1 internal constructor(mappings: List<UniformMapping>, public val uniformBytesI64: Long) {
    public data class UniformMapping(public val ownerNodeIndexI32: Int, public val localOffsetBytesI32: Int,
        public val physicalOffsetBytesI32: Int, public val sizeBytesI32: Int, public val alignmentBytesI32: Int)
    public val uniformMappings: List<UniformMapping> = immutableList(mappings)
    public val composedBindingLayoutHash: String
    init {
        require(uniformBytesI64 in 16L..Int.MAX_VALUE.toLong() && uniformBytesI64 % 16L == 0L) { W5gPlanDiagnostics.Schema }
        var end = 0L
        uniformMappings.forEach { row ->
            require(row.ownerNodeIndexI32 >= 0 && row.localOffsetBytesI32 == 0 && row.alignmentBytesI32 == 16 &&
                row.sizeBytesI32 > 0 && row.sizeBytesI32 % 16 == 0 && row.physicalOffsetBytesI32.toLong() == end) { W5gPlanDiagnostics.Schema }
            end = Math.addExact(end,row.sizeBytesI32.toLong())
        }
        require(end == uniformBytesI64 && uniformMappings.map { it.ownerNodeIndexI32 }.distinct().size == uniformMappings.size) { W5gPlanDiagnostics.Schema }
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        fun i32(value: Int) { repeat(4) { hash.update((value ushr (it*8)).toByte()) } }
        fun i64(value: Long) { repeat(8) { hash.update((value ushr (it*8)).toByte()) } }
        hash.update("kanvas-material-binding-layout-v1".encodeToByteArray()); hash.update(0.toByte())
        i32(1); i32(0); i32(2); i32(1); i64(uniformBytesI64); hash.update(0.toByte()); i64(uniformBytesI64)
        i32(uniformMappings.size)
        uniformMappings.forEach { i32(it.ownerNodeIndexI32); i32(it.localOffsetBytesI32); i32(it.physicalOffsetBytesI32)
            i32(it.sizeBytesI32); i32(it.alignmentBytesI32) }
        i32(0) // ordered resources: absent in the scalar slice
        composedBindingLayoutHash = hash.digest().joinToString("") { "%02x".format(it) }
    }
}

internal class MaterialEvaluationDagV5 private constructor(entries: List<Entry>, val root: MaterialEvaluationRefV5) {
    val entries: List<Entry> = immutableList(entries)
    class Entry internal constructor(val ownerNodeIndexI32: Int, children: List<MaterialEvaluationRefV5>,
        val coordinates: SourceCoordinatesV4, val program: MaterialProgramPlan) {
        val children: List<MaterialEvaluationRefV5> = immutableList(children)
    }
    init {
        require(root.indexI32 == entries.lastIndex && entries.isNotEmpty()) { W5gPlanDiagnostics.Schema }
        require(entries[root.indexI32].ownerNodeIndexI32 == 0 &&
            entries.map { it.ownerNodeIndexI32 }.sorted() == entries.indices.toList()) { W5gPlanDiagnostics.Schema }
        entries.forEachIndexed { index,entry -> require(entry.ownerNodeIndexI32 >= 0 && entry.coordinates == SourceCoordinatesV4.None &&
            entry.program is ComposedMaterialProgramV5 && entry.children.all { it.indexI32 < index }) { W5gPlanDiagnostics.Schema } }
    }
    companion object { fun of(entries: List<Entry>): MaterialEvaluationDagV5 = MaterialEvaluationDagV5(entries,MaterialEvaluationRefV5(entries.lastIndex)) }
}

/** Issued only after the existing complete frame inventory; retains all value ownership. */
internal class PreparedComposedSourceV5 private constructor(
    val captured: MaterialSourceConstructionV4,
    val capturedIdentity: String,
    val frameOwner: FrameSourceLayoutV4,
    val evaluation: MaterialEvaluationDagV5,
    val layout: ComposedBindingLayoutV1,
    val operationGraph: ColorOperationGraphV1,
    words: Map<Long,Int>, tables: Map<Long,ImmutableUBytes>,
) {
    val numericWordsF32Bits: Map<Long,Int> = java.util.Collections.unmodifiableMap(LinkedHashMap(words))
    val tableRecords: Map<Long,ImmutableUBytes> = java.util.Collections.unmodifiableMap(LinkedHashMap(tables))
    val deviceBoundsF32: RectF32 get() = captured.deviceBoundsF32
    val program: ComposedMaterialProgramV5 = ComposedMaterialProgramV5(MaterialProgramPlanId(
        "composed-material-v5:${layout.composedBindingLayoutHash}:" + evaluation.entries.joinToString(";") {
            "${it.ownerNodeIndexI32}:${it.children.map { child -> child.indexI32 }}:${it.program.structuralId.value}"
        } + ":${operationGraph.canonicalIdentity}"),operationGraph)
    init {
        val metadata = requireNotNull(captured.composed) { W5gPlanDiagnostics.Schema }
        require(frameOwner.owns(captured) && metadata.layout === layout &&
            captured.canonicalIdentity == capturedIdentity && words.keys.all { it >= 0 && it < layout.uniformBytesI64/4 } &&
            evaluation.entries.size == metadata.nodes.size && evaluation.entries.indices.all { index ->
                val entry = evaluation.entries[index]; val original = metadata.nodes[index]
                entry.ownerNodeIndexI32 == original.ownerNodeIndexI32 && entry.children == original.children
            }) { W5gPlanDiagnostics.Schema }
    }
    companion object {
        fun prepare(frame: FrameSourceLayoutV4, source: MaterialSourceConstructionV4): PreparedComposedSourceV5 {
            require(frame.owns(source)) { W5gPlanDiagnostics.Schema }
            val metadata = requireNotNull(source.composed) { W5gPlanDiagnostics.Schema }
            val built = ColorSourceProofCompilerV1.graphForComposed(metadata)
            return PreparedComposedSourceV5(source,source.canonicalIdentity,frame,built.evaluation,metadata.layout,built.graph,built.words,built.tables)
        }
    }
}
