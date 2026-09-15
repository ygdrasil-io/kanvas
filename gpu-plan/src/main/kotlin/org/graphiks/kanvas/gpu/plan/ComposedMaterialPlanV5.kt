package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ImmutableUBytes
import org.graphiks.math.geometry.RectF32

public data class MaterialEvaluationRefV5(public val indexI32: Int) {
    init { require(indexI32 >= 0) { W5gPlanDiagnostics.Schema } }
}

public open class ComposedMaterialProgramV5 internal constructor(
    override val structuralId: MaterialProgramPlanId,
    internal val operationGraph: ColorOperationGraphV1,
) : MaterialProgramPlan {
    override val versionI32: Int = 5
    override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.colorSourceV4()
}

public open class ComposedMaterialBindingV5 internal constructor(
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
    public enum class StorageKind { GRADIENT_STOPS, NOISE_U32, RUNTIME_READ }
    public data class Buffer(
        public val bufferTypeTagU32: UInt,
        public val minBindingSizeBytesI64: Long,
        public val hasDynamicOffset: Boolean = false,
        public val storageKind: StorageKind = StorageKind.GRADIENT_STOPS,
    )
    public data class Texture(
        public val textureViewDimensionTagU32: UInt,
        public val textureSampleTypeTagU32: UInt,
        public val multisampled: Boolean = false,
    )
    public data class Sampler(public val samplerTypeTagU32: UInt)
    public data class Resource(
        public val ownerNodeIndexI32: Int,
        public val logicalSlotI32: Int,
        public val groupI32: Int,
        public val bindingI32: Int,
        public val visibilityFlagsU32: UInt,
        public val kindTagU32: UInt,
        public val buffer: Buffer? = null,
        public val texture: Texture? = null,
        public val sampler: Sampler? = null,
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
                base = Math.addExact(end,(16L-end%16L)%16L)
            }
            require(row.ownerNodeIndexI32 >= 0 && row.localOffsetBytesI32 >= 0 && row.alignmentBytesI32 in setOf(4,8,16) &&
                row.sizeBytesI32 > 0 && row.physicalOffsetBytesI32.toLong() >= end &&
                row.physicalOffsetBytesI32.toLong() == Math.addExact(base,row.localOffsetBytesI32.toLong()) &&
                row.physicalOffsetBytesI32 % row.alignmentBytesI32 == 0) { W5gPlanDiagnostics.Schema }
            end = Math.addExact(row.physicalOffsetBytesI32.toLong(),row.sizeBytesI32.toLong())
        }
        require(Math.addExact(end,(16L-end%16L)%16L) == uniformBytesI64) { W5gPlanDiagnostics.Schema }
        this.resources.forEachIndexed { index,row ->
            require(row.ownerNodeIndexI32 >= 0 && row.logicalSlotI32 >= 0 && row.groupI32 == 1 &&
                row.bindingI32 == index+1 && row.visibilityFlagsU32 == 2u && when(row.kindTagU32) {
                    1u -> row.texture == null && row.sampler == null && row.buffer?.let { it.bufferTypeTagU32 == 2u &&
                        it.minBindingSizeBytesI64 == when (it.storageKind) {
                            StorageKind.GRADIENT_STOPS -> 32L
                            StorageKind.NOISE_U32 -> 16L
                            StorageKind.RUNTIME_READ -> it.minBindingSizeBytesI64.also { bytes -> require(bytes > 0L) }
                        } && !it.hasDynamicOffset } == true
                    2u -> row.buffer == null && row.sampler == null && row.texture?.let { it.textureViewDimensionTagU32 == 1u &&
                        it.textureSampleTypeTagU32 == 1u && !it.multisampled } == true
                    3u -> row.buffer == null && row.texture == null && row.sampler?.samplerTypeTagU32 in setOf(1u,2u)
                    else -> false
                }) { W5gPlanDiagnostics.Schema }
        }
        composedBindingLayoutHash = recomputeBindingLayoutHash()
    }
    public fun recomputeBindingLayoutHash(): String = org.graphiks.kanvas.render.ir.CanonicalHashBytesV1("kanvas-material-binding-layout-v1").apply {
            i32(1).i32(0).u32(2).u32(1).i64(uniformBytesI64).u8(0).i64(uniformBytesI64)
            list(uniformMappings) { i32(it.ownerNodeIndexI32).i32(it.localOffsetBytesI32).i32(it.physicalOffsetBytesI32)
                .i32(it.sizeBytesI32).i32(it.alignmentBytesI32) }
            list(this@ComposedBindingLayoutV1.resources) { row ->
                i32(row.ownerNodeIndexI32).i32(row.logicalSlotI32).i32(row.groupI32).i32(row.bindingI32)
                    .u32(row.visibilityFlagsU32.toLong()).u32(row.kindTagU32.toLong())
                option(row.buffer) { u32(it.bufferTypeTagU32.toLong()).i64(it.minBindingSizeBytesI64).u8(if(it.hasDynamicOffset) 1 else 0) }
                option(row.texture) { u32(it.textureViewDimensionTagU32.toLong()).u32(it.textureSampleTypeTagU32.toLong()).u8(if(it.multisampled) 1 else 0) }
                option(row.sampler) { u32(it.samplerTypeTagU32.toLong()) }
            }
        }.sha256Hex()
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

/** Binding-owned original image execution and its exact declared resource row. */
public class ComposedImageResourceV5 internal constructor(
    public val resource: ComposedBindingLayoutV1.Resource,
    internal val metadata: MaterialSourceConstructionV4.ImageChildMetadata,
    internal val prepared: FrameSourceLayoutV4.PreparedStops,
) {
    public val upload: ImageUploadPlanV1 = prepared.imageUpload(metadata.description)
    public val graph: ImageNumericOperationGraphV1 = ImageNumericOperationGraphV1.of(metadata.description.color,
        metadata.description.sampling,metadata.description.tileModes)
    internal val projection: ImageCoordinatePlanV1 = ImageCoordinatePlanV1.sealShader(org.graphiks.math.matrix.Matrix3x3F32(),emptyList())
    init {
        require(resource.kindTagU32 == 2u && resource.texture != null && resource.buffer == null &&
            upload.widthI32 > 0 && upload.heightI32 > 0 &&
            upload.widthI32.toFloat().toDouble() == upload.widthI32.toDouble() &&
            upload.heightI32.toFloat().toDouble() == upload.heightI32.toDouble()) { W5gPlanDiagnostics.Schema }
        require(ImageNumericAuthorityV1.provesFiniteTexelDomain(graph.colorAlpha,upload)) { W5eImagePlanDiagnostics.NumericDomainUnbounded }
    }
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
    images: List<ImageReference> = emptyList(),
    val noiseSlab: NoiseTableSlabV1? = null,
    noises: List<NoiseReference> = emptyList(),
    runtimeResources: List<RuntimeEffectResourceReferenceV1> = emptyList(),
) {
    val runtimeResources: List<RuntimeEffectResourceReferenceV1> = immutableList(runtimeResources)
    class NoiseReference(val evaluationRef: MaterialEvaluationRefV5,val ownerNodeIndexI32: Int,
        val metadata: MaterialSourceConstructionV4.NoiseChildMetadata,
        val resource: ComposedBindingLayoutV1.Resource,val range: NoiseTableRangeV1,val wordOffsetI64: Long)
    val noiseReferences: List<NoiseReference> = immutableList(noises)
    fun resolveNoise(region: NoiseOperationGraphV1): NoiseReference = noiseReferences.single {
        it.ownerNodeIndexI32 == region.ownerNodeIndexI32 && it.wordOffsetI64 == region.wordOffsetU32
    }.also { require(noiseSlab?.authenticates(it.range) == true && noiseSlab.owner === frameOwner &&
        integerWordsU32[it.wordOffsetI64+4L] == it.range.baseWordU32) { W5gPlanDiagnostics.Schema } }
    class GradientReference(
        val evaluationRef: MaterialEvaluationRefV5,
        val ownerNodeIndexI32: Int,
        val resource: ComposedBindingLayoutV1.Resource,
        val definition: PreparedSourceDefinitionV4,
        val rangeWordOffsetI64: Long,
    )
    val gradientReferences: List<GradientReference> = immutableList(references)
    class ImageReference(val evaluationRef: MaterialEvaluationRefV5,val ownerNodeIndexI32: Int,
        val binding: ComposedImageResourceV5,val wordOffsetI64: Long)
    val imageReferences: List<ImageReference> = immutableList(images)
    fun resolveImage(read: ImageNumericOperationGraphV1.TexelRead): ComposedImageResourceV5 {
        val logical=read.resource as? ImageNumericOperationGraphV1.TexelResource.Logical
            ?: error(W5gPlanDiagnostics.Schema)
        val width=(read.width as? ColorOperationGraphV1.Scalar.DynamicF32)?.wordOffsetU32
        val height=(read.height as? ColorOperationGraphV1.Scalar.DynamicF32)?.wordOffsetU32
        val reference=imageReferences.single { it.ownerNodeIndexI32 == logical.ownerNodeIndexI32 &&
            it.binding.resource.logicalSlotI32 == logical.logicalSlotI32 &&
            width == it.wordOffsetI64+20L && height == it.wordOffsetI64+21L }
        val image=reference.binding
        require(layout.resources.any { it === reference.binding.resource } && frameOwner.owns(captured) &&
            image.prepared.owner === frameOwner && image.prepared.imageUpload(image.metadata.description) === image.upload &&
            read.topologyIdentity == image.graph.topologyIdentity &&
            read.colorAlpha == image.graph.colorAlpha && read.tileModes == image.graph.tileModes &&
            numericWordsF32Bits[width] == image.upload.widthI32.toFloat().toRawBits() &&
            numericWordsF32Bits[height] == image.upload.heightI32.toFloat().toRawBits()) { W5gPlanDiagnostics.Schema }
        return reference.binding
    }
    val numericWordsF32Bits: Map<Long,Int> = java.util.Collections.unmodifiableMap(LinkedHashMap(words))
    val integerWordsU32: Map<Long,UInt> = java.util.Collections.unmodifiableMap(LinkedHashMap(integers))
    val tableRecords: Map<Long,ImmutableUBytes> = java.util.Collections.unmodifiableMap(LinkedHashMap(tables))
    val deviceBoundsF32: RectF32 get() = captured.deviceBoundsF32
    val program: ComposedMaterialProgramV6 = ComposedMaterialProgramV6(evaluation,operationGraph,layout,
        requireNotNull(captured.composed).nodes.mapIndexedNotNull { index,node -> node.runtime?.let {
            ComposedMaterialProgramV6.RuntimeNodeExpectation(index,RuntimeEffectExpectationV1.from(it))
        } })
    init {
        val metadata = requireNotNull(captured.composed) { W5gPlanDiagnostics.Schema }
        require(frameOwner.owns(captured) && metadata.layout === layout &&
            captured.canonicalIdentity == capturedIdentity &&
            (words.keys + integers.keys + tables.keys).all { it >= 0 && it < layout.uniformBytesI64/4 } &&
            evaluation.entries.size == metadata.nodes.size && evaluation.entries.indices.all { index ->
                val entry = evaluation.entries[index]; val original = metadata.nodes[index]
                entry.ownerNodeIndexI32 == original.ownerNodeIndexI32 && entry.children == original.children &&
                    entry.coordinates == (original.gradientSource?.coordinates ?:
                        original.imageSource?.coordinates?.let(SourceCoordinatesV4::V2) ?:
                        original.noiseSource?.coordinates?.let(SourceCoordinatesV4::V2) ?: SourceCoordinatesV4.None)
            }) { W5gPlanDiagnostics.Schema }
        val storage=layout.resources.singleOrNull { it.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS }
        val noiseStorage=layout.resources.singleOrNull { it.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.NOISE_U32 }
        require((noiseSlab == null) == (noiseStorage == null) &&
            noiseReferences.map { it.evaluationRef.indexI32 } == metadata.nodes.indices.filter { metadata.nodes[it].noiseSource != null }) {
            W5gPlanDiagnostics.Schema
        }
        noiseReferences.forEach { reference ->
            val node=metadata.nodes[reference.evaluationRef.indexI32]
            require(node.noiseSource === reference.metadata && node.ownerNodeIndexI32 == reference.ownerNodeIndexI32 &&
                reference.resource === noiseStorage && reference.wordOffsetI64 == node.offsetBytesI32.toLong()/4L &&
                noiseSlab?.owner === frameOwner && noiseSlab.authenticates(reference.range) &&
                reference.range.normalizedSeedI32 == reference.metadata.parameters.normalizedSeedI32 &&
                integerWordsU32[reference.wordOffsetI64+2L] == reference.metadata.parameters.octavesI32.toUInt() &&
                integerWordsU32[reference.wordOffsetI64+4L] == reference.range.baseWordU32) { W5gPlanDiagnostics.Schema }
        }
        require((slab == null) == (storage == null) &&
            gradientReferences.map { it.evaluationRef.indexI32 } == metadata.nodes.indices.filter {
                metadata.nodes[it].gradientSource?.hasGradientStorage == true
            }) { W5gPlanDiagnostics.Schema }
        if (gradientReferences.isNotEmpty()) require(requireNotNull(storage).let { resource ->
            resource.ownerNodeIndexI32 == gradientReferences.minOf { it.ownerNodeIndexI32 } && resource.logicalSlotI32 == 0
        }) { W5gPlanDiagnostics.Schema }
        gradientReferences.forEach { reference ->
            val node=metadata.nodes[reference.evaluationRef.indexI32]
            val definition=reference.definition
            require(node.ownerNodeIndexI32 == reference.ownerNodeIndexI32 && definition.captured === node.gradientSource &&
                definition.metadata.leaf === node.original && definition.frameOwner === frameOwner && definition.slab === slab &&
                reference.resource === storage && definition.range == frameOwner.range(definition.captured) &&
                reference.rangeWordOffsetI64 == node.offsetBytesI32.toLong()/4L &&
                integerWordsU32[reference.rangeWordOffsetI64] == definition.range.baseIndexU32 &&
                integerWordsU32[reference.rangeWordOffsetI64+1L] == definition.range.countU32) { W5gPlanDiagnostics.Schema }
        }
        require(imageReferences.map { it.evaluationRef.indexI32 } == metadata.nodes.indices.filter {
            metadata.nodes[it].imageSource != null
        } && layout.resources.filter { it.texture != null }.all { resource ->
            imageReferences.any { it.binding.resource === resource } || runtimeResources.any { it.resource === resource } }) { W5gPlanDiagnostics.Schema }
        require(runtimeResources.map { it.captured } == metadata.runtimeResources && runtimeResources.all {
            layout.resources.any { row -> row === it.resource }
        }) { W5hPlanDiagnostics.Descriptor }
        imageReferences.forEach { reference ->
            val node=metadata.nodes[reference.evaluationRef.indexI32]
            val image=reference.binding
            require(node.imageSource === image.metadata && image.metadata.original === node.original &&
                reference.ownerNodeIndexI32 == node.ownerNodeIndexI32 && image.resource.ownerNodeIndexI32 == node.ownerNodeIndexI32 &&
                reference.wordOffsetI64 == node.offsetBytesI32.toLong()/4L && image.prepared.owner === frameOwner &&
                image.upload === image.prepared.imageUpload(image.metadata.description)) { W5gPlanDiagnostics.Schema }
            var offset=reference.wordOffsetI64
            RawMaterialRequirementsV2.forEachImageHeaderWord(image.projection,image.upload,1f,image.graph.sampling,null,null) {
                require(numericWordsF32Bits[offset++] == it) { W5gPlanDiagnostics.Schema }
            }
            require(offset == reference.wordOffsetI64+image.metadata.headerBytesI64/4L) { W5gPlanDiagnostics.Schema }
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
                GradientReference(MaterialEvaluationRefV5(index),node.ownerNodeIndexI32,metadata.layout.resources.single {
                    it.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS },
                    definition,node.offsetBytesI32.toLong()/4L)
            } }
            val images=metadata.nodes.mapIndexedNotNull { index,node -> node.imageSource?.let { image ->
                ImageReference(MaterialEvaluationRefV5(index),node.ownerNodeIndexI32,
                    ComposedImageResourceV5(metadata.layout.resources.single { it.texture != null && it.ownerNodeIndexI32 == node.ownerNodeIndexI32 },
                        image,prepared),node.offsetBytesI32.toLong()/4L)
            } }
            val noises=metadata.nodes.mapIndexedNotNull { index,node -> node.noiseSource?.let { noise ->
                NoiseReference(MaterialEvaluationRefV5(index),node.ownerNodeIndexI32,noise,metadata.layout.resources.single {
                    it.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.NOISE_U32 },
                    frame.noiseRange(noise.parameters.normalizedSeedI32),node.offsetBytesI32.toLong()/4L)
            } }
            val built = ColorSourceProofCompilerV1.graphForComposed(metadata,definitions,images,noises)
            return PreparedComposedSourceV5(source,source.canonicalIdentity,frame,built.evaluation,metadata.layout,built.graph,
                built.words,built.tables,built.integers,prepared.slab.takeIf { references.isNotEmpty() },references,images,
                prepared.noiseSlab.takeIf { noises.isNotEmpty() },noises,metadata.runtimeResources.map(prepared::runtimeResource))
        }
    }
}
