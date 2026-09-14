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

/** Immutable logical-field and physical-resource mapping for built-in V5 sources. */
public class ComposedBindingLayoutV1 internal constructor(
    mappings: List<UniformMapping>,
    public val uniformBytesI64: Long,
    resources: List<Resource> = emptyList(),
) {
    public data class UniformMapping(public val ownerNodeIndexI32: Int, public val localOffsetBytesI32: Int,
        public val physicalOffsetBytesI32: Int, public val sizeBytesI32: Int, public val alignmentBytesI32: Int)
    public val uniformMappings: List<UniformMapping> = immutableList(mappings)
    public data class Buffer(
        public val bufferTypeTagU32: UInt,
        public val minBindingSizeBytesI64: Long,
        public val hasDynamicOffset: Boolean = false,
    )
    public data class Texture(
        public val textureViewDimensionTagU32: UInt,
        public val textureSampleTypeTagU32: UInt,
        public val multisampled: Boolean = false,
    )
    public data class Resource(
        public val ownerNodeIndexI32: Int,
        public val logicalSlotI32: Int,
        public val groupI32: Int,
        public val bindingI32: Int,
        public val visibilityFlagsU32: UInt,
        public val kindTagU32: UInt,
        public val buffer: Buffer? = null,
        public val texture: Texture? = null,
    )
    public val resources: List<Resource> = immutableList(resources)
    public val composedBindingLayoutHash: String
    init {
        require(uniformBytesI64 in 16L..Int.MAX_VALUE.toLong() && uniformBytesI64 % 16L == 0L) { W5gPlanDiagnostics.Schema }
        var end = 0L
        var owner = -1
        var base = 0L
        uniformMappings.forEach { row ->
            if (row.ownerNodeIndexI32 != owner) {
                require(row.ownerNodeIndexI32 > owner) { W5gPlanDiagnostics.Schema }
                owner = row.ownerNodeIndexI32
                base = end
            }
            require(row.ownerNodeIndexI32 >= 0 && row.localOffsetBytesI32.toLong() == end-base && row.alignmentBytesI32 == 16 &&
                row.sizeBytesI32 > 0 && row.sizeBytesI32 % 16 == 0 && row.physicalOffsetBytesI32.toLong() == end) { W5gPlanDiagnostics.Schema }
            end = Math.addExact(end,row.sizeBytesI32.toLong())
        }
        require(end == uniformBytesI64) { W5gPlanDiagnostics.Schema }
        this.resources.forEachIndexed { index,row ->
            require(row.ownerNodeIndexI32 >= 0 && row.logicalSlotI32 >= 0 && row.groupI32 == 1 &&
                row.bindingI32 == index+1 && row.visibilityFlagsU32 == 2u && row.kindTagU32 == 1u &&
                row.texture == null && row.buffer?.let { it.bufferTypeTagU32 == 2u &&
                    it.minBindingSizeBytesI64 == 32L && !it.hasDynamicOffset } == true) { W5gPlanDiagnostics.Schema }
        }
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        fun i32(value: Int) { repeat(4) { hash.update((value ushr (it*8)).toByte()) } }
        fun i64(value: Long) { repeat(8) { hash.update((value ushr (it*8)).toByte()) } }
        hash.update("kanvas-material-binding-layout-v1".encodeToByteArray()); hash.update(0.toByte())
        i32(1); i32(0); i32(2); i32(1); i64(uniformBytesI64); hash.update(0.toByte()); i64(uniformBytesI64)
        i32(uniformMappings.size)
        uniformMappings.forEach { i32(it.ownerNodeIndexI32); i32(it.localOffsetBytesI32); i32(it.physicalOffsetBytesI32)
            i32(it.sizeBytesI32); i32(it.alignmentBytesI32) }
        i32(this.resources.size)
        this.resources.forEach { row ->
            i32(row.ownerNodeIndexI32); i32(row.logicalSlotI32); i32(row.groupI32); i32(row.bindingI32)
            i32(row.visibilityFlagsU32.toInt()); i32(row.kindTagU32.toInt())
            hash.update(1.toByte()); i32(requireNotNull(row.buffer).bufferTypeTagU32.toInt())
            i64(row.buffer.minBindingSizeBytesI64); hash.update(0.toByte())
            hash.update(0.toByte()); hash.update(0.toByte()) // texture/sampler options absent
        }
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
            entries.map { it.ownerNodeIndexI32 }.distinct().sorted().let { it == it.indices.toList() }) { W5gPlanDiagnostics.Schema }
        entries.forEachIndexed { index,entry -> require(entry.ownerNodeIndexI32 >= 0 &&
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
    integers: Map<Long,UInt>,
    val slab: GradientStopSlabPlanV1?,
    references: List<GradientReference>,
) {
    class GradientReference(
        val evaluationRef: MaterialEvaluationRefV5,
        val ownerNodeIndexI32: Int,
        val resource: ComposedBindingLayoutV1.Resource,
        val definition: PreparedSourceDefinitionV4,
        val rangeWordOffsetI64: Long,
    )
    val gradientReferences: List<GradientReference> = immutableList(references)
    val numericWordsF32Bits: Map<Long,Int> = java.util.Collections.unmodifiableMap(LinkedHashMap(words))
    val integerWordsU32: Map<Long,UInt> = java.util.Collections.unmodifiableMap(LinkedHashMap(integers))
    val tableRecords: Map<Long,ImmutableUBytes> = java.util.Collections.unmodifiableMap(LinkedHashMap(tables))
    val deviceBoundsF32: RectF32 get() = captured.deviceBoundsF32
    val program: ComposedMaterialProgramV5 = ComposedMaterialProgramV5(MaterialProgramPlanId(
        "composed-material-v5:${layout.composedBindingLayoutHash}:" + evaluation.entries.joinToString(";") {
            "${it.ownerNodeIndexI32}:${it.children.map { child -> child.indexI32 }}:${it.program.structuralId.value}"
        } + ":${operationGraph.canonicalIdentity}"),operationGraph)
    init {
        val metadata = requireNotNull(captured.composed) { W5gPlanDiagnostics.Schema }
        require(frameOwner.owns(captured) && metadata.layout === layout &&
            captured.canonicalIdentity == capturedIdentity &&
            (words.keys + integers.keys + tables.keys).all { it >= 0 && it < layout.uniformBytesI64/4 } &&
            evaluation.entries.size == metadata.nodes.size && evaluation.entries.indices.all { index ->
                val entry = evaluation.entries[index]; val original = metadata.nodes[index]
                entry.ownerNodeIndexI32 == original.ownerNodeIndexI32 && entry.children == original.children &&
                    entry.coordinates == (original.gradientSource?.coordinates ?: SourceCoordinatesV4.None)
            }) { W5gPlanDiagnostics.Schema }
        require((slab == null) == layout.resources.isEmpty() &&
            gradientReferences.map { it.evaluationRef.indexI32 } == metadata.nodes.indices.filter {
                metadata.nodes[it].gradientSource?.hasGradientStorage == true
            }) { W5gPlanDiagnostics.Schema }
        if (gradientReferences.isNotEmpty()) require(layout.resources.single().let { resource ->
            resource.ownerNodeIndexI32 == gradientReferences.minOf { it.ownerNodeIndexI32 } && resource.logicalSlotI32 == 0
        }) { W5gPlanDiagnostics.Schema }
        gradientReferences.forEach { reference ->
            val node=metadata.nodes[reference.evaluationRef.indexI32]
            val definition=reference.definition
            require(node.ownerNodeIndexI32 == reference.ownerNodeIndexI32 && definition.captured === node.gradientSource &&
                definition.metadata.leaf === node.original && definition.frameOwner === frameOwner && definition.slab === slab &&
                reference.resource === layout.resources.single() && definition.range == frameOwner.range(definition.captured) &&
                reference.rangeWordOffsetI64 == node.offsetBytesI32.toLong()/4L &&
                integerWordsU32[reference.rangeWordOffsetI64] == definition.range.baseIndexU32 &&
                integerWordsU32[reference.rangeWordOffsetI64+1L] == definition.range.countU32) { W5gPlanDiagnostics.Schema }
        }
    }
    companion object {
        fun prepare(frame: FrameSourceLayoutV4, source: MaterialSourceConstructionV4,
            prepared: FrameSourceLayoutV4.PreparedStops): PreparedComposedSourceV5 {
            require(frame.owns(source)) { W5gPlanDiagnostics.Schema }
            val metadata = requireNotNull(source.composed) { W5gPlanDiagnostics.Schema }
            val definitions=java.util.IdentityHashMap<MaterialSourceConstructionV4,PreparedSourceDefinitionV4>()
            val references=metadata.nodes.mapIndexedNotNull { index,node -> node.gradientSource?.takeIf { it.hasGradientStorage }?.let { child ->
                val definition=definitions.getOrPut(child) { PreparedSourceDefinitionV4.fromPrepared(frame,child,prepared) }
                GradientReference(MaterialEvaluationRefV5(index),node.ownerNodeIndexI32,metadata.layout.resources.single(),
                    definition,node.offsetBytesI32.toLong()/4L)
            } }
            val built = ColorSourceProofCompilerV1.graphForComposed(metadata,definitions)
            return PreparedComposedSourceV5(source,source.canonicalIdentity,frame,built.evaluation,metadata.layout,built.graph,
                built.words,built.tables,built.integers,prepared.slab.takeIf { references.isNotEmpty() },references)
        }
    }
}
