package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorInterpolationProgramV1
import org.graphiks.kanvas.render.ir.ColorInterpolation
import org.graphiks.math.color.ColorF32

/** Checked final allocation metadata. It cannot stand in for the real packing permit. */
internal class FrameSourceLayoutV4 private constructor(
    private val deferredLane: SourceDeferredRenderConstructionV4?,
    private val preparedInput: PreparedSourceFrameMetadata?,
    val interner: MaterialTableInterningRecipeV4,
    sources: List<MaterialSourceConstructionV4>,
    entries: List<List<SourceEntry>>,
    ranges: List<RangeAllocation>,
    pendingRanges: Map<MaterialSourceConstructionV4,GradientStopRangeV1>,
    legacyRanges: Map<MaterialBindingPlan,GradientStopRangeV1>,
    legacyAllocations: List<RawMaterialRequirementsV2.RelocatedLegacyLayout>,
    imageInventory: List<ImageAllocation>,
    imageDescriptions: List<EffectiveMaterialPlanner.ImageSampleDescription>,
    noiseRanges: List<NoiseTableRangeV1>,
    val noiseBytesI64: Long,
    val nonUniformBytesI64: Long,
    val stopBytesI64: Long,
    val uniformBytesI64: Long,
    nativeLanes: List<SourceDeferredRenderConstructionV4>,
    nativeOffsetsI32: List<Int>,
    private val nativeGeometry: NativeCompositeGeometryLayoutV4?,
    private val ordinaryLayout: OrdinaryCompositeSourceLayoutV4? = null,
    private val layeredInput: W6aLayerGraphConstruction? = null,
) {
    val lane: SourceDeferredRenderConstructionV4 get() = requireNotNull(deferredLane) { W5fPlanDiagnostics.Schema }
    val capabilities: PlanCapabilitySnapshot get() = layeredInput?.caps ?: deferredLane?.capabilities ?: requireNotNull(preparedInput).capabilities
    val targetExtent: org.graphiks.math.geometry.SizeI32 get() = layeredInput?.extent ?: deferredLane?.targetExtent ?: requireNotNull(preparedInput).targetExtent
    val budget: PlanBudget get() = layeredInput?.budget ?: deferredLane?.budget ?: requireNotNull(preparedInput).budget
    private val nativeLanes = immutableList(nativeLanes)
    private val sources = immutableList(sources)
    private val nativeOffsetsI32 = immutableList(nativeOffsetsI32)
    val entries: List<List<SourceEntry>> = immutableList(entries.map(::immutableList))
    private val ranges = immutableList(ranges)
    private val pendingRanges = java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(pendingRanges))
    private val legacyRanges = java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(legacyRanges))
    private val legacyAllocations = immutableList(legacyAllocations)
    private val imageInventory = immutableList(imageInventory)
    private val imageDescriptions = immutableList(imageDescriptions)
    private val runtimeInventory = immutableList(sources.flatMap { it.composed?.runtimeResources.orEmpty() })
    val noiseRanges: List<NoiseTableRangeV1> = immutableList(noiseRanges)
    fun noiseRange(normalizedSeedI32: Int): NoiseTableRangeV1 =
        noiseRanges.single { it.normalizedSeedI32 == normalizedSeedI32 }
    fun ownsImage(description: EffectiveMaterialPlanner.ImageSampleDescription): Boolean = sources.any { source ->
        source.composed?.nodes?.any { it.imageSource?.description === description } == true
    }
    fun owns(source: MaterialSourceConstructionV4): Boolean = entries.any { row -> row.any {
        it.source === source || it.source.composed?.nodes?.any { node -> node.gradientSource === source } == true
    } }
    fun range(source: MaterialSourceConstructionV4): GradientStopRangeV1 =
        requireNotNull(pendingRanges[source]) { W5fPlanDiagnostics.Schema }

    fun prepareAndPublish(): org.graphiks.kanvas.render.ir.RenderPlanResult<RenderGraph> =
        if (layeredInput != null) prepareAndPublishLayered()
        else if (ordinaryLayout != null) prepareAndPublishOrdinary()
        else when (val constructed = prepareAndConstruct()) {
            is SourceConstructionResultV4.Refused -> constructed.failure
            is SourceConstructionResultV4.Built ->
                org.graphiks.kanvas.render.ir.RenderPlanResult.Ready(constructed.value).publishConstructionResult()
        }

    private fun prepareAndPublishLayered(): org.graphiks.kanvas.render.ir.RenderPlanResult<RenderGraph> {
        val frame = requireNotNull(layeredInput)
        if (sources.isEmpty()) return org.graphiks.kanvas.render.ir.RenderPlanResult.Ready(frame.publish(null, emptyList()))
        return when (val bound = prepareAndFinish { table, roots, inventory ->
            val lanes = nativeLanes.mapIndexed { index, lane ->
                val w4eColors = lane.geometrySource?.takeIf { it.w4ePayload != null }?.let { geometry ->
                    remapSourcePassesV4(geometry.passes(), composed = { table.entry(it).bindings is ComposedMaterialBindingV5 }) {
                        roots[nativeOffsetsI32[index] + it.indexI32]
                    }.filterIsInstance<PlanPass.PathRenderPass>().filter { it.phase != PathRenderPhase.SingleSampleStencilProducer }
                        .associateBy { it.draw.commandIndex }
                }
                remapSourcePassesV4(lane.passes(), composed = { table.entry(it).bindings is ComposedMaterialBindingV5 },
                    w4eColorPasses = w4eColors) {
                    roots[nativeOffsetsI32[index] + it.indexI32]
                }
            }
            frame.publish(table, lanes, inventory)
        }) {
            is SourceConstructionResultV4.Built -> org.graphiks.kanvas.render.ir.RenderPlanResult.Ready(bound.value)
            is SourceConstructionResultV4.Refused -> bound.failure
        }
    }

    /** Image and ordinary consumers cross the one real permit before either graph is published. */
    fun <T> prepareImageFrame(finish: (RenderGraph,MaterialPlanTable,Long)->T): SourceConstructionResultV4<T> =
        prepareAndFinish { table,roots,_ ->
            val graphs = if (ordinaryLayout == null) listOf(constructBound(table,roots))
                else nativeLanes.mapIndexed { index,source -> constructLaneBound(source,nativeOffsetsI32[index],table,roots) }
            val nonUniformAndStops = Math.addExact(nonUniformBytesI64,stopBytesI64)
            val packed = packConstructedFrame(graphs,table,nonUniformAndStops)
            val geometry = if (ordinaryLayout == null) RenderGraph.publishConstruction(graphs.single(),packed)
                else W5aCompositeConstruction(graphs,table,nonUniformAndStops,ordinaryLayout.rectScratch).publish(packed)
            finish(geometry,table,Math.addExact(nonUniformAndStops,uniformBytesI64))
        }

    /** All pending conversion follows this checked owner; publication still uses the real permit. */
    fun prepareAndConstruct(): SourceConstructionResultV4<RenderGraphConstruction> = prepareAndFinish { table,roots,_ ->
        require(ordinaryLayout == null) { W5fPlanDiagnostics.Schema }
        constructBound(table,roots)
    }

    /** Prepared geometry projects the same bound sources through the same permit and packer. */
    fun preparePreparedFrame(metadata: PreparedSourceFrameMetadata): SourceConstructionResultV4<PreparedSourceFrameV6> =
        prepareAndFinish { table, roots,_ ->
            require(metadata === preparedInput && deferredLane == null) { W5fPlanDiagnostics.Schema }
            val footprints = sources.mapIndexed { index, source ->
                RawMaterialRequirementsV2.measureV4(table, roots[index]).also {
                    require(it.proof.authenticates(table, roots[index], source.coordinates)) { W5fPlanDiagnostics.Schema }
                }
            }
            val packed = PackedFrameSourcesV4.issuePrepared(table, capabilities, budget, footprints,
                Math.addExact(nonUniformBytesI64, stopBytesI64))
            PreparedSourceFrameV6(metadata, table, roots, packed)
        }

    fun prepareAndPublishOrdinary(): org.graphiks.kanvas.render.ir.RenderPlanResult<RenderGraph> =
        when (val result = prepareAndFinish { table,roots,_ ->
            val layout = requireNotNull(ordinaryLayout) { W5fPlanDiagnostics.Schema }
            val graphs = nativeLanes.mapIndexed { index,source ->
                constructLaneBound(source,nativeOffsetsI32[index],table,roots)
            }
            W5aCompositeConstruction(graphs,table,Math.addExact(nonUniformBytesI64,stopBytesI64),layout.rectScratch).publish()
        }) {
            is SourceConstructionResultV4.Built -> org.graphiks.kanvas.render.ir.RenderPlanResult.Ready(result.value)
            is SourceConstructionResultV4.Refused -> result.failure
        }

    private fun <T> prepareAndFinish(finish: (MaterialPlanTable,List<MaterialPlanRef>,SourcePhysicalConstructionV1)->T): SourceConstructionResultV4<T> = try {
        val prepared = PreparedStops.prepare(this)
        val boundSources = java.util.IdentityHashMap<MaterialSourceConstructionV4,EffectiveMaterialPlanner.Result.Ready>()
        val boundImages = java.util.IdentityHashMap<MaterialSourceConstructionV4,ImageSampleExecutionPlanV1>()
        fun bind(source: MaterialSourceConstructionV4): EffectiveMaterialPlanner.Result.Ready =
            boundSources.getOrPut(source) {
                source.resolvedSource ?: run {
                var table = if (source.composed != null) {
                    val definition = PreparedComposedSourceV5.prepare(this,source,prepared)
                    val proof = ColorSourceProofV1.issueComposed(definition)
                        ?: throw IllegalArgumentException(if (definition.noiseReferences.isNotEmpty())
                            W5gPlanDiagnostics.NoiseNumericDomainUnbounded else W5gPlanDiagnostics.NumericDomainUnbounded)
                    MaterialPlanTable.of(listOf(MaterialPlanEntry(definition.program,ComposedMaterialBindingV6(definition,proof),definition.slab)))
                } else if (source.image != null) {
                    val resolved = source.image.bind(source.image.child?.let(::bind),
                        Math.addExact(Math.addExact(nonUniformBytesI64,stopBytesI64),uniformBytesI64),
                        prepared.imageUpload(source.image))
                    boundImages[source] = (resolved.table.entry(resolved.root).bindings as ImageSampleV3).execution
                    resolved.table.sealColorSourceV4(resolved.root,source.coordinates,source.deviceBoundsF32)
                } else {
                    val definition = PreparedSourceDefinitionV4.fromPrepared(this,source,prepared)
                    val leaf = GradientInterpolationBindingV4.seal(definition)
                    MaterialPlanTable.of(listOf(MaterialPlanEntry(
                        GradientInterpolationProgramV4(definition.addressing,definition.domain),leaf,definition.slab)))
                }
                source.wrappers.forEach { wrapper ->
                    val child = MaterialPlanRef(table.sizeI32-1)
                    val program = table.entry(child).program
                    val parent = when (wrapper) {
                        is SourceUnaryMetadataV4.Opacity -> MaterialPlanEntry(MaterialProgramPlan.OpacityV1(program),
                            MaterialBindingPlan.OpacityF32V1.of(wrapper.alphaF32))
                        is SourceUnaryMetadataV4.Filter -> {
                            val proof = table.colorSourceProofV4(child)
                            val numeric = ColorNumericAuthorityV1.seal(wrapper.execution,proof)
                                ?: throw RawMaterialRequirementsV2.Refusal(W5fPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(ColorFilteredProgramV4(program,wrapper.execution.structuralIdentity),
                                ColorFilterBindingV4.seal(wrapper.execution,proof,numeric))
                        }
                    }
                    table = MaterialPlanTable.of(table.entries()+parent).sealColorSourceV4(
                        MaterialPlanRef(table.sizeI32),source.coordinates,source.deviceBoundsF32)
                }
                EffectiveMaterialPlanner.Result.Ready(table,MaterialPlanRef(table.sizeI32-1),source.blend)
                }
            }
        val boundRows = sources.map { bind(it).table.entries() }
        data class BoundEntry(val entry: MaterialPlanEntry,val actualDescriptor: MaterialInternerDescriptorV4)
        val bound = boundRows.mapIndexed { row,values ->
            require(values.size == entries[row].size) { W5fPlanDiagnostics.Schema }
            values.mapIndexed { index,value ->
                val planned = entries[row][index]
                val source = planned.source
                val descriptor = if (!source.pending) value.internerDescriptorV4() else if (source.composed != null) {
                    val binding = value.bindings as? ComposedMaterialBindingV5
                    require(binding != null && binding.definition.captured === source && binding.definition.frameOwner === this &&
                        planned.wrapperOrdinalI32 == -1) { W5gPlanDiagnostics.Schema }
                    planned.descriptor
                } else {
                    val unary = when (val wrapper = source.wrappers.getOrNull(planned.wrapperOrdinalI32)) {
                        null -> {
                            require(planned.wrapperOrdinalI32 == -1) { W5fPlanDiagnostics.Schema }
                            if (source.image != null) {
                                require((value.bindings as? ImageSampleV3)?.execution === boundImages[source]) { W5fPlanDiagnostics.Schema }
                                source.image.child != null
                            } else {
                                val leaf = value.bindings as? GradientInterpolationBindingV4
                                require(leaf != null && leaf.definition.captured === source &&
                                    leaf.definition.frameOwner === this && leaf.sourceProof.preparedDefinition === leaf.definition &&
                                    leaf.authenticates(value.program as GradientInterpolationProgramV4,value.stopSlab)) {
                                    W5fPlanDiagnostics.Schema
                                }
                                false
                            }
                        }
                        is SourceUnaryMetadataV4.Opacity -> {
                            require(value.program is MaterialProgramPlan.OpacityV1 &&
                                (value.bindings as? MaterialBindingPlan.OpacityF32V1)?.alphaF32?.toRawBits() == wrapper.alphaF32.toRawBits()) {
                                W5fPlanDiagnostics.Schema
                            }; true
                        }
                        is SourceUnaryMetadataV4.Filter -> {
                            require(value.program is ColorFilteredProgramV4 &&
                                (value.bindings as? ColorFilterBindingV4)?.execution === wrapper.execution) { W5fPlanDiagnostics.Schema }; true
                        }
                    }
                    MaterialInternerDescriptorV4("pending-source-entry-v4:${source.canonicalIdentity}:${planned.wrapperOrdinalI32+1}",unary)
                }
                BoundEntry(value,descriptor)
            }
        }
        // Bind the already-recorded placements/root maps, including copied unary
        // chains. A second structural interning pass cannot drop/add an entry.
        val selected = interner.bind(bound) { it.actualDescriptor }
        var table = MaterialPlanTable.of(selected.map { it.entry })
        require(table.sizeI32 == interner.sizeI32 && table.gradientStopSlab?.canonicalIdentity == prepared.slab?.canonicalIdentity &&
            (table.gradientStopSlab?.byteSizeI64 ?: 0L) == stopBytesI64) { W5fPlanDiagnostics.Schema }
        val roots = interner.laneRemaps().mapIndexed { index,refs ->
            refs[entries[index].lastIndex]
        }
        sources.forEachIndexed { index,source -> if (source.image != null)
            table = table.sealColorSourceV4(roots[index],source.coordinates,source.deviceBoundsF32)
        }
        val actualLegacy = mutableListOf<RawMaterialRequirementsV2.LegacyLayout>()
        val actualV4 = linkedMapOf<String,MaterialSourceFootprintV4>()
        val pendingPhysical = linkedMapOf<String,String>()
        sources.forEachIndexed { index,source ->
            val root = roots[index]
            if (source.pending || source.resolvedSource?.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) {
                val footprint = RawMaterialRequirementsV2.measureV4(table,root)
                require(footprint.proof.authenticates(table,root,source.coordinates)) { W5fPlanDiagnostics.Schema }
                actualV4[footprint.canonicalIdentity] = footprint
                if (source.pending) {
                    if (source.composed != null) require(footprint.proof.composedDefinition?.captured === source &&
                        footprint.proof.composedDefinition.frameOwner === this) { W5gPlanDiagnostics.Schema }
                    else if (source.image != null) require(footprint.proof.imageExecution === boundImages[source]) {
                        W5fPlanDiagnostics.Schema
                    } else {
                        var leafIndex = root.indexI32
                        while (table.entry(MaterialPlanRef(leafIndex)).bindings.let {
                            it is MaterialBindingPlan.OpacityF32V1 || it is ColorFilterBindingV4 }) leafIndex--
                        val actualLeaf = table.entry(MaterialPlanRef(leafIndex)).bindings as? GradientInterpolationBindingV4
                        require(actualLeaf != null && actualLeaf.definition.frameOwner === this &&
                            actualLeaf.definition.allocationIdentity == source.canonicalIdentity &&
                            actualLeaf.sourceProof.preparedDefinition === actualLeaf.definition) { W5fPlanDiagnostics.Schema }
                    }
                    val previous = pendingPhysical.putIfAbsent(source.canonicalIdentity,footprint.canonicalIdentity)
                    require(previous == null || previous == footprint.canonicalIdentity) { W5fPlanDiagnostics.Schema }
                }
            } else {
                val actual = RawMaterialRequirementsV2.measureLegacy(table,root)
                require(legacyAllocations.any { it.authenticatesFinal(actual,table.gradientStopSlab) }) { W5fPlanDiagnostics.Schema }
                if (actualLegacy.none { it.canonicalIdentity == actual.canonicalIdentity }) actualLegacy += actual
            }
        }
        require(pendingPhysical.values.distinct().size == pendingPhysical.size &&
            actualLegacy.size == legacyAllocations.size && legacyAllocations.all { planned ->
                actualLegacy.any { planned.authenticatesFinal(it,table.gradientStopSlab) }
            }) { W5fPlanDiagnostics.Schema }
        val composedImages=actualV4.values.flatMap { it.proof.composedImageResources }
        val issuedImages=java.util.IdentityHashMap<ImageUploadPlanV1,Unit>()
        require(imageDescriptions.all { description ->
            val issued=prepared.imageUpload(description)
            issuedImages.put(issued,Unit) == null && composedImages.any {
                it.metadata.description.pixels === description.pixels && it.upload === issued &&
                    it.upload.cacheRequest === issued.cacheRequest
            }
        } && composedImages.all { binding -> imageDescriptions.any { description ->
            binding.metadata.description.pixels === description.pixels && binding.prepared === prepared &&
                binding.upload === prepared.imageUpload(binding.metadata.description) &&
                binding.upload === prepared.imageUpload(description)
        } } && issuedImages.size == imageDescriptions.size) { W5gPlanDiagnostics.Schema }
        val actualUploads=java.util.IdentityHashMap<ImageUploadPlanV1,Unit>()
        (actualV4.values.mapNotNull { it.proof.imageExecution?.upload } + composedImages.map { it.upload } +
            actualV4.values.flatMap { it.proof.runtimeResources }.mapNotNull { it.imageUpload })
            .forEach { actualUploads[it]=Unit }
        require(actualUploads.size == imageInventory.size && imageInventory.all { allocation ->
            val issued=prepared.uploads.getValue(allocation.pixels)
            allocation.authenticates(issued) && actualUploads.containsKey(issued) &&
                actualUploads.keys.single { it.pixelsOwner === allocation.pixels }.cacheRequest === issued.cacheRequest
        }) { W5gPlanDiagnostics.Schema }
        val plannedImageBytes=imageInventory.fold(0L) { bytes,allocation ->
            Math.addExact(bytes,allocation.physicalBytesI64(capabilities)) }
        val issuedImageBytes=actualUploads.keys.fold(0L) { bytes,upload ->
            Math.addExact(bytes,imagePhysicalBytesI64(upload,capabilities)) }
        require(plannedImageBytes == issuedImageBytes) { W5gPlanDiagnostics.Schema }
        val actualUniformBytes = actualLegacy.fold(0L) { bytes,value -> Math.addExact(bytes,value.uniformByteCountI64) }
        require(actualV4.values.fold(actualUniformBytes) { bytes,value ->
            Math.addExact(bytes,value.uniformByteCountI64) } == uniformBytesI64) { W5fPlanDiagnostics.Schema }
        val inventory = if (layeredInput == null) SourcePhysicalConstructionV1() else {
            val resources = mutableListOf<PlanResource>()
            val uniforms = linkedMapOf<String, PlanResourceId>()
            val caches = mutableListOf<PlanCacheBindingV1>()
            fun buffer(role: PlanResourceRole, ordinal: Int, bytes: Long, usage: PlanResourceUsage,
                lifetime: PlanResourceLifetime = PlanResourceLifetime.FrameLocal): PlanResourceId =
                PlanResource.of(role, ordinal, PlanResourceKind.Buffer, null, null, bytes,
                    setOf(usage, PlanResourceUsage.CopyDestination), lifetime, 0, layeredInput.passCountI32)
                    .also { resources += it }.id
            (actualLegacy.map { it.canonicalIdentity to it.uniformByteCountI64 } +
                actualV4.values.map { it.canonicalIdentity to it.uniformByteCountI64 }).forEach { (identity, bytes) ->
                uniforms[identity] = buffer(PlanResourceRole.SourceUniformData, uniforms.size, bytes, PlanResourceUsage.Uniform)
            }
            imageInventory.forEachIndexed { index, allocation ->
                val request = prepared.uploads.getValue(allocation.pixels).cacheRequest
                val row = PlanResource.of(PlanResourceRole.DecodedImageV1, index, PlanResourceKind.Texture2D,
                    PlanTextureFormat.ImageV1(request.format), org.graphiks.math.geometry.SizeI32(request.widthI32, request.heightI32),
                    request.byteSizeI64, request.usages(), request.lifetime, 0, layeredInput.passCountI32)
                resources += row
                val uploadBytes = Math.subtractExact(allocation.physicalBytesI64(capabilities), allocation.byteCountI64)
                val upload = buffer(PlanResourceRole.ImageUploadStaging, index, uploadBytes, PlanResourceUsage.CopySource)
                caches += PlanCacheBindingV1(row.id, request, upload, uploadBytes / request.heightI32)
            }
            actualV4.values.flatMap { it.proof.runtimeResources }.map { it.cacheRequest }
                .filterIsInstance<PlanCacheResourceRequest.Storage>().distinct().forEachIndexed { index, request ->
                    caches += PlanCacheBindingV1(buffer(PlanResourceRole.RuntimeStorageData, index, request.byteSizeI64,
                        PlanResourceUsage.StorageRead, request.lifetime), request)
                }
            actualV4.values.flatMap { it.proof.runtimeResources }.map { it.cacheRequest }
                .filterIsInstance<PlanCacheResourceRequest.Sampler>().distinct().forEachIndexed { index, request ->
                    caches += PlanCacheBindingV1(planResourceId(PlanResourceRole.RuntimeSampler, index), request)
                }
            require(resources.filter { it.role == PlanResourceRole.SourceUniformData }.sumOf { it.byteSize } == uniformBytesI64)
            val extra = resources.filterNot { it.role == PlanResourceRole.SourceUniformData }.fold(0L) { bytes, row -> Math.addExact(bytes, row.byteSize) }
            require(Math.addExact(layeredInput.nonUniformBytesI64, Math.addExact(extra, noiseBytesI64)) == nonUniformBytesI64)
            SourcePhysicalConstructionV1(resources, uniforms, caches)
        }
        SourceConstructionResultV4.Built(finish(table,roots,inventory))
    } catch (failure: RawMaterialRequirementsV2.Refusal) {
        sourceConstructionRefusalV4(failure.code)
    } catch (failure: IllegalArgumentException) {
        sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
    } catch (_: ArithmeticException) {
        sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget)
    }

    private fun constructBound(table: MaterialPlanTable,roots: List<MaterialPlanRef>): RenderGraphConstruction {
        val geometrySource = lane.geometrySource?.let { constructLaneBound(it,0,table,roots) }
        val passes = remapSourcePassesV4(lane.passes(),composed={ table.entry(it).bindings is ComposedMaterialBindingV5 },
            w4eColorPasses=geometrySource?.takeIf { it.w4ePayload != null }?.passes()?.filterIsInstance<PlanPass.PathRenderPass>()
                ?.filter { it.phase != PathRenderPhase.SingleSampleStencilProducer }?.associateBy { it.draw.commandIndex }) { symbolic -> roots[symbolic.indexI32] }
        var constructed = RenderGraph.construct(lane.id,lane.capabilityId,lane.targetExtent,lane.colorFormat,
            lane.capabilities,lane.budget,lane.visualCommandCount,lane.resources(),passes,lane.dependencies(),
            lane.peakFrameLocalBytesI64,table,
            w5bW4eFacts=geometrySource?.takeIf { it.w4ePayload != null }?.let(W4eGeometryFactsV6::from))
        val nativeWitnessLanes = nativeGeometry?.lanes?.map { metadata ->
            val deferred = nativeLanes[metadata.ordinalI32]
            GeometryLaneConstruction(constructLaneBound(deferred.geometrySource ?: deferred,nativeOffsetsI32[metadata.ordinalI32],table,roots),
                metadata.commandsI32,metadata.data,metadata.depth)
        }.orEmpty()
        if (lane.topology == DeferredLaneTopologyV4.GeneralGeometryAndColor) {
            val source = requireNotNull(geometrySource)
            val commands = lane.geometryCommandsI32()
            val data = lane.drawDataByCommandI32().values.distinct().single()
            val depth = lane.depthStencilByCommandI32().values.distinct().singleOrNull()
            constructed = RenderGraph.issueW5bGeometry(constructed,listOf(GeometryLaneConstruction(source,commands,data,depth)))
        } else if (lane.topology == DeferredLaneTopologyV4.GeometryBridge)
            constructed = RenderGraph.issueW5bGeometry(constructed,nativeWitnessLanes)
        else if (lane.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID)
            constructed = RenderGraph.issueW4dGeneralCompilerWitness(constructed)
        return constructed
    }

    private fun constructLaneBound(source: SourceDeferredRenderConstructionV4,offset: Int,
        table: MaterialPlanTable,roots: List<MaterialPlanRef>): RenderGraphConstruction {
        var geometry = RenderGraph.construct(source.id,source.capabilityId,source.targetExtent,
            source.colorFormat,source.capabilities,source.budget,source.visualCommandCount,
            source.resources(),remapSourcePassesV4(source.passes(),composed={ table.entry(it).bindings is ComposedMaterialBindingV5 }) { roots[offset+it.indexI32] },
            source.dependencies(),source.peakFrameLocalBytesI64,table)
        if (source.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID)
            geometry = RenderGraph.issueW4dGeneralCompilerWitness(geometry)
        if (source.capabilityId == W4dPathStrokePlanCompiler.CAPABILITY_ID)
            geometry = RenderGraph.issueW4dCompilerWitness(geometry)
        if (source.topology == DeferredLaneTopologyV4.GeometryBridge) geometry = RenderGraph.issueW5bGeometry(geometry)
        source.w4ePayload?.let { geometry = geometry.withW4ePayload(it) }
        return geometry
    }

    internal class SourceEntry(val source: MaterialSourceConstructionV4, val oldEntry: MaterialPlanEntry?,
        val wrapperOrdinalI32: Int, val descriptor: MaterialInternerDescriptorV4)

    private sealed interface RangeValues {
        val countI32: Int
        fun same(other: RangeValues): Boolean
        class Legacy(stops: List<GradientStopPlanV1>) : RangeValues {
            val stops = immutableList(stops)
            override val countI32: Int get() = stops.size
            override fun same(other: RangeValues): Boolean = when (other) {
                is Legacy -> stops == other.stops
                is Pending -> other.sameLegacy(this)
            }
        }
        class Pending(val metadata: MaterialSourceConstructionV4.GradientMetadata,
            val composedOwner: org.graphiks.kanvas.render.ir.MaterialNode? = null) : RangeValues {
            override val countI32: Int get() = metadata.stops.countI32
            override fun same(other: RangeValues): Boolean = when (other) {
                is Pending -> composedOwner === other.composedOwner && metadata.interpolation == other.metadata.interpolation &&
                    metadata.recipeIdentity == other.metadata.recipeIdentity && metadata.stops.sameSequence(other.metadata.stops)
                is Legacy -> sameLegacy(other)
            }

            // A selected SRGB wrapper uses the historical no-conversion tuple.
            // Match that SAME eventual range before budgeting, in either first-use
            // order; no prepared tuple is allocated during this metadata scan.
            fun sameLegacy(other: Legacy): Boolean {
                if(composedOwner != null) return false
                if (metadata.interpolation != ColorInterpolation.SRGB || metadata.recipeIdentity != null ||
                    countI32 != other.countI32) return false
                val pending = metadata.stops.values().iterator()
                return other.stops.all { legacy ->
                    val stop = pending.next()
                    val color = stop.color
                    fun sameColor(value: ColorF32): Boolean =
                        value.red.toRawBits() == color.redNormalized.toRawBits() &&
                        value.green.toRawBits() == color.greenNormalized.toRawBits() &&
                        value.blue.toRawBits() == color.blueNormalized.toRawBits() &&
                        value.alpha.toRawBits() == color.alphaNormalized.toRawBits()
                    legacy.domain == ColorInterpolation.SRGB && legacy.preparationRecipeIdentity == null &&
                        legacy.positionF32.toRawBits() == stop.positionF32.toRawBits() &&
                        sameColor(legacy.straightSrgbF32) && sameColor(legacy.preparedTupleF32)
                } && !pending.hasNext()
            }
        }
    }
    private class RangeAllocation(val range: GradientStopRangeV1,val values: RangeValues)

    /** One physical reservation keyed by captured Pixels identity, never its content hash. */
    private class ImageAllocation(val pixels: org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels,
        val logicalRowBytesI64: Long,val byteCountI64: Long,val physicalFormat: ImagePhysicalFormatV1,
        val upload: ImageUploadPlanV1?) {
        fun authenticates(candidate: ImageUploadPlanV1): Boolean = candidate.pixelsOwner === pixels &&
            candidate.widthI32 == pixels.width && candidate.heightI32 == pixels.height &&
            candidate.sourceRowBytesI64 == pixels.rowBytes.toLong() && candidate.logicalFormat == pixels.pixelFormat &&
            candidate.logicalRowBytesI64 == logicalRowBytesI64 && candidate.byteCountI64 == byteCountI64 &&
            candidate.physicalFormat == physicalFormat && (upload?.sharesOwnerAndPhysicalFacts(candidate) ?: true)
        fun authenticates(description: EffectiveMaterialPlanner.ImageSampleDescription): Boolean =
            description.pixels === pixels && description.logicalRowBytesI64 == logicalRowBytesI64 &&
                description.byteCountI64 == byteCountI64 && description.physicalFormat == physicalFormat
        fun physicalBytesI64(caps: PlanCapabilitySnapshot): Long = imagePhysicalBytesI64(pixels.width,pixels.height,
            logicalRowBytesI64,byteCountI64,physicalFormat,caps)
    }

    /** Only this factory can create prepared frame data; it accepts no caller tuple. */
    internal class PreparedStops private constructor(val owner: FrameSourceLayoutV4,val slab: GradientStopSlabPlanV1?,
        val noiseSlab: NoiseTableSlabV1?,
        uploads: Map<org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels,ImageUploadPlanV1>) {
        internal val uploads: Map<org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels,ImageUploadPlanV1> =
            java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(uploads))
        private val runtime = java.util.IdentityHashMap<CapturedRuntimeResourceV1,RuntimeEffectResourceReferenceV1>()
        private val runtimeRequests = RuntimeResourceOwnerIndexV1<PlanCacheResourceRequest>()
        fun runtimeResource(captured: CapturedRuntimeResourceV1): RuntimeEffectResourceReferenceV1 {
            require(owner.runtimeInventory.any { it === captured }) { W5hPlanDiagnostics.Descriptor }
            return runtime.getOrPut(captured) {
                val image = (captured.binding as? org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.SampledTexture)
                    ?.let { requireNotNull(uploads[it.image]) }
                val request = runtimeRequests.getOrPut(captured) { when(val binding = captured.binding) {
                    is org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.StorageRead ->
                        PlanCacheResourceRequest.Storage(captured.descriptor.abiHash,captured.slot.logicalSlotI32,binding.bytes)
                    is org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.SampledTexture -> requireNotNull(image).cacheRequest
                    is org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.Sampler -> PlanCacheResourceRequest.Sampler(binding.type)
                } }
                RuntimeEffectResourceReferenceV1(captured,request,image)
            }
        }
        fun imageUpload(metadata: MaterialSourceConstructionV4.ImageMetadata): ImageUploadPlanV1 {
            require(owner.sources.any { it.image === metadata }) { W5fPlanDiagnostics.Schema }
            return requireNotNull(uploads[metadata.upload.pixelsOwner]) { W5fPlanDiagnostics.Schema }.also {
                require(metadata.upload.sharesOwnerAndPhysicalFacts(it)) { W5fPlanDiagnostics.Schema }
            }
        }
        fun imageUpload(description: EffectiveMaterialPlanner.ImageSampleDescription): ImageUploadPlanV1 {
            require(owner.ownsImage(description)) { W5gPlanDiagnostics.Schema }
            val upload=requireNotNull(uploads[description.pixels]) { W5gPlanDiagnostics.Schema }
            require(upload.pixelsOwner === description.pixels &&
                upload.widthI32 == description.pixels.width && upload.heightI32 == description.pixels.height &&
                upload.sourceRowBytesI64 == description.pixels.rowBytes.toLong() &&
                upload.logicalRowBytesI64 == description.logicalRowBytesI64 && upload.byteCountI64 == description.byteCountI64 &&
                upload.logicalFormat == description.pixels.pixelFormat && upload.physicalFormat == description.physicalFormat) { W5gPlanDiagnostics.Schema }
            return upload
        }
        companion object {
            fun prepare(owner: FrameSourceLayoutV4): PreparedStops {
                val values = ArrayList<GradientStopPlanV1>(Math.toIntExact(owner.stopBytesI64/32L))
                owner.ranges.forEach { allocation ->
                    require(values.size.toUInt() == allocation.range.baseIndexU32) { W5fPlanDiagnostics.Schema }
                    when (val source = allocation.values) {
                        is RangeValues.Legacy -> values.addAll(source.stops)
                        is RangeValues.Pending -> for (stop in source.metadata.stops.values()) {
                            val c = stop.color
                            val original = ColorF32.of(c.redNormalized,c.greenNormalized,c.blueNormalized,c.alphaNormalized)
                            val rgb = listOf(original.red,original.green,original.blue)
                            val domain = source.metadata.interpolation
                            if (domain == ColorInterpolation.SRGB) {
                                values += GradientStopPlanV1(stop.positionF32,original)
                                continue
                            }
                            val prepared = if (domain == ColorInterpolation.HSL || domain == ColorInterpolation.OKLCH)
                                ColorInterpolationProgramV1.evaluateHostF32(if (domain == ColorInterpolation.HSL)
                                    ColorInterpolationProgramV1.RecipeKind.SRGB_TO_HSL_STOP else ColorInterpolationProgramV1.RecipeKind.SRGB_TO_OKLCH_STOP,
                                    rgb,stop.color)
                            else {
                                val linear = rgb.map {
                                    ColorInterpolationProgramV1.evaluateHostF32(ColorInterpolationProgramV1.RecipeKind.EOTF,listOf(it)).single()
                                }
                                if (domain == ColorInterpolation.OKLAB)
                                    ColorInterpolationProgramV1.evaluateHostF32(ColorInterpolationProgramV1.RecipeKind.LINEAR_RGB_TO_OKLAB,linear)
                                else linear
                            }
                            values += GradientStopPlanV1.prepared(stop.positionF32,original,source.metadata.interpolation,
                                ColorF32.of(prepared[0],prepared[1],prepared[2],original.alpha),requireNotNull(source.metadata.recipeIdentity))
                        }
                    }
                    require(values.size.toLong() == allocation.range.baseIndexU32.toLong()+allocation.range.countU32.toLong()) {
                        W5fPlanDiagnostics.Schema
                    }
                }
                require(values.size.toLong()*32L == owner.stopBytesI64) { W5fPlanDiagnostics.Schema }
                val uploads=java.util.IdentityHashMap<org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels,ImageUploadPlanV1>()
                owner.imageInventory.forEach { allocation ->
                    val upload=allocation.upload ?: ImageUploadPlanV1.seal(allocation.pixels)
                    require(allocation.authenticates(upload) && uploads.put(allocation.pixels,upload) == null) { W5gPlanDiagnostics.Schema }
                }
                return PreparedStops(owner,values.takeIf { it.isNotEmpty() }?.let(GradientStopSlabPlanV1::of),
                    NoiseTableSlabV1.prepare(owner),uploads)
            }
        }
    }

    companion object {
        /** Explicit occurrence-to-target bindings; no synthetic common target or child publication. */
        fun layeredFrame(frame: W6aLayerGraphConstruction): SourceConstructionResultV4<FrameSourceLayoutV4> {
            var count = 0
            val offsets = frame.lanes.map { lane -> count.also { count = Math.addExact(count, lane.sourceTable().sources().size) } }
            return checked(frame.lanes.firstOrNull(), frame.lanes, offsets, null, layeredInput = frame)
        }
        fun prepared(metadata: PreparedSourceFrameMetadata): SourceConstructionResultV4<FrameSourceLayoutV4> =
            checked(null, emptyList(), emptyList(), null, preparedInput = metadata)
        fun standalone(lane: SourceDeferredRenderConstructionV4): SourceConstructionResultV4<FrameSourceLayoutV4> =
            checked(lane,emptyList(),emptyList(),null)

        fun ordinaryComposite(lanes: List<SourceDeferredRenderConstructionV4>): SourceConstructionResultV4<FrameSourceLayoutV4> = try {
            require(lanes.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32) { W5fPlanDiagnostics.Schema }
            val first = lanes.first()
            require(lanes.all { it.topology == DeferredLaneTopologyV4.Ordinary &&
                it.capabilityId in setOf(W3SolidRectPlanCompiler.W5A_CAPABILITY_ID,
                    W4bAnalyticRRectPlanCompiler.CAPABILITY_ID,W4cPathFillPlanCompiler.CAPABILITY_ID,
                    W4dPathStrokePlanCompiler.CAPABILITY_ID) && it.targetExtent == first.targetExtent &&
                it.capabilities == first.capabilities && it.budget == first.budget && it.colorFormat == first.colorFormat }) {
                W5fPlanDiagnostics.Schema
            }
            val commands = lanes.flatMap { RenderGraph.visualDraws(it.passes()).map { draw -> draw.commandIndex } }
            require(commands.zipWithNext().all { (a,b) -> a < b }) { W5fPlanDiagnostics.Schema }
            var count = 0
            val offsets = lanes.map { source -> count.also { count = Math.addExact(count,source.sourceTable().sources().size) } }
            val layout = ordinaryCompositeSourceLayoutV4(lanes.map {
                NativeGeometryInputV4(it.capabilityId,it.resources(),it.passes()) },first.capabilities)
            // The first lane remains a real lane, not a fabricated aggregate
            // RenderGraph. The checked owner carries every immutable lane and
            // its exact symbolic offset, and the shared historical inventory.
            checked(first,lanes,offsets,null,layout)
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (_: ArithmeticException) { sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget) }

        fun nativeComposite(lanes: List<SourceDeferredRenderConstructionV4>): SourceConstructionResultV4<FrameSourceLayoutV4> {
            try {
                require(lanes.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32) { W5fPlanDiagnostics.Schema }
                val first = lanes.first()
                require(lanes.all { it.targetExtent == first.targetExtent && it.colorFormat == first.colorFormat &&
                    it.capabilities == first.capabilities && it.budget == first.budget }) { W5fPlanDiagnostics.Schema }
                val active = lanes.filter { it.visualCommandCount > 0 }
                val captures = mutableListOf<MaterialSourceConstructionV4>()
                val offsets = active.map { lane -> captures.size.also { captures += lane.sourceTable().sources() } }
                val sources = when (val result = MaterialSourceConstructionTableV4.of(captures)) {
                    is SourceConstructionResultV4.Built -> result.value
                    is SourceConstructionResultV4.Refused -> return result
                }
                val geometry = nativeCompositeGeometryLayoutV4(active.mapIndexed { ordinal,lane ->
                    NativeGeometryInputV4(lane.capabilityId,lane.resources(),remapSourcePassesV4(lane.passes()) {
                        MaterialPlanRef(Math.addExact(offsets[ordinal],it.indexI32)) })
                },first.capabilities)
                val topology = W5bDestinationGraphSealer.describeSources(W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID,
                    first.targetExtent,first.capabilities,first.budget,geometry.colors,
                    first.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
                    first.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize,
                    (first.passes().last() as PlanPass.ReadbackPass).bytesPerRow,geometry.geometryResources,
                    drawDataByCommandI32=geometry.dataByCommand,depthStencilByCommandI32=geometry.depthByCommand)
                val identity = java.security.MessageDigest.getInstance("SHA-256").digest(
                    lanes.joinToString("|") { it.id.value }.encodeToByteArray()).joinToString("") { "%02x".format(it) }
                val envelope = when (val result = SourceDeferredRenderConstructionV4.of(PlanId("w5b.composite.$identity"),
                    W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID,first.targetExtent,topology.format,
                    first.capabilities,first.budget,geometry.colors.size,topology.resources,topology.passes,topology.dependencies,
                    sources,DeferredLaneTopologyV4.GeometryBridge,null,emptyList(),emptyMap(),emptyMap())) {
                    is SourceConstructionResultV4.Built -> result.value
                    is SourceConstructionResultV4.Refused -> return result
                }
                return checked(envelope,active,offsets,geometry)
            } catch (failure: IllegalArgumentException) {
                return sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
            } catch (_: ArithmeticException) { return sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget) }
        }

        private fun checked(lane: SourceDeferredRenderConstructionV4?,nativeLanes: List<SourceDeferredRenderConstructionV4>,
            nativeOffsetsI32: List<Int>,nativeGeometry: NativeCompositeGeometryLayoutV4?,
            ordinaryLayout: OrdinaryCompositeSourceLayoutV4? = null,
            preparedInput: PreparedSourceFrameMetadata? = null,
            layeredInput: W6aLayerGraphConstruction? = null): SourceConstructionResultV4<FrameSourceLayoutV4> = try {
            require(if (layeredInput != null) preparedInput == null else (lane == null) != (preparedInput == null)) { W5fPlanDiagnostics.Schema }
            require(lane?.resources().orEmpty().none { it.role == PlanResourceRole.GradientStopData }) { W5fPlanDiagnostics.Schema }
            val sources = preparedInput?.sources ?: if (ordinaryLayout == null && layeredInput == null) requireNotNull(lane).sourceTable().sources()
                else nativeLanes.flatMap { it.sourceTable().sources() }
            // Image geometry may remove every pending origin while retaining ordinary sources.
            // Those exact surviving rows still use this one interner/budget/publication owner.
            require(sources.isNotEmpty() || layeredInput != null) { W5fPlanDiagnostics.Schema }
            fun row(source: MaterialSourceConstructionV4): List<SourceEntry> =
                source.resolvedSource?.table?.entries()?.map { SourceEntry(source,it,-1,it.internerDescriptorV4()) }
                    ?: source.image?.child?.let(::row).orEmpty() + List(source.wrappers.size+1) { index ->
                        SourceEntry(source,null,index-1,MaterialInternerDescriptorV4(
                            "pending-source-entry-v4:${source.canonicalIdentity}:$index",index != 0 || source.image?.child != null))
                    }
            val rows = sources.map(::row)
            // This is the existing structural interner, including contiguous unary
            // copies and the exact2048 entry limit, before any pending conversion.
            val interner = if (sources.isEmpty()) MaterialTableInterningRecipeV4.emptyFrame()
                else MaterialTableInterningRecipeV4.of(rows.map { row -> row.map { it.descriptor } })
            val finalEntries = interner.bind(rows) { it.descriptor }
            val allocations = mutableListOf<RangeAllocation>()
            val composedOwners=java.util.IdentityHashMap<MaterialSourceConstructionV4,org.graphiks.kanvas.render.ir.MaterialNode>()
            val composedEntries=sources.flatMap { source -> source.composed?.nodes.orEmpty().mapNotNull { node ->
                node.gradientSource?.let { child ->
                    composedOwners[child]=node.original
                    SourceEntry(child,null,-1,MaterialInternerDescriptorV4("composed-stop-context:${child.canonicalIdentity}",false))
                }
            } }
            fun values(entry: SourceEntry): RangeValues? {
                if (entry.oldEntry == null) return if (entry.wrapperOrdinalI32 == -1 &&
                    entry.source.gradient?.stops?.countI32?.let { it > 0 } == true)
                    RangeValues.Pending(requireNotNull(entry.source.gradient),composedOwners[entry.source]) else null
                val range = when (val binding = entry.oldEntry.bindings) {
                    is MaterialBindingPlan.GradientV1 -> binding.stopRange
                    is MaterialBindingPlan.GradientV2 -> binding.stopRange
                    else -> return null
                }
                val slab = requireNotNull(entry.oldEntry.stopSlab)
                require(slab.rangeHasDomain(range,ColorInterpolation.SRGB)) { W5fPlanDiagnostics.Schema }
                val stops = slab.copyStops()
                return RangeValues.Legacy(stops.subList(range.baseIndexU32.toInt(),
                    (range.baseIndexU32.toLong()+range.countU32.toLong()).toInt()))
            }
            var stopCountI64 = 0L
            for (entry in finalEntries+composedEntries) values(entry)?.let { current ->
                if (allocations.none { it.values.same(current) }) {
                    val next = Math.addExact(stopCountI64,current.countI32.toLong())
                    require(next <= UInt.MAX_VALUE.toLong() && Math.multiplyExact(next,32L) <= Int.MAX_VALUE.toLong()) {
                        W5cPlanDiagnostics.StopBudget
                    }
                    allocations += RangeAllocation(GradientStopRangeV1(stopCountI64.toUInt(),current.countI32.toUInt()),current)
                    stopCountI64 = next
                }
            }
            val pendingRanges = java.util.IdentityHashMap<MaterialSourceConstructionV4,GradientStopRangeV1>()
            val legacyRanges = java.util.IdentityHashMap<MaterialBindingPlan,GradientStopRangeV1>()
            (rows.flatten()+composedEntries).forEach { entry -> values(entry)?.let { current ->
                val range = allocations.single { it.values.same(current) }.range
                if (entry.oldEntry == null) pendingRanges[entry.source] = range
                else legacyRanges[entry.oldEntry.bindings] = range
            } }
            val slabOccurrence = Any() // Identity-scoped to this one final frame, never a proof or permit.
            val legacy = sources.mapNotNull { source -> source.resolvedSource?.takeUnless {
                it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4
            }?.let { RawMaterialRequirementsV2.measureLegacy(it.table,it.root).relocated(legacyRanges,slabOccurrence) } }
                .fold(mutableListOf<RawMaterialRequirementsV2.RelocatedLegacyLayout>()) { unique,current ->
                    if (unique.none { it.sameAllocation(current) }) unique += current
                    unique
                }
            val retainedV4 = sources.mapNotNull { source -> source.resolvedSource?.takeIf {
                it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4
            }?.let { RawMaterialRequirementsV2.measureV4(it.table,it.root) } }.distinctBy { it.canonicalIdentity }
            val pending = sources.filter { it.pending }.distinctBy { it.canonicalIdentity }
            val caps = layeredInput?.caps ?: lane?.capabilities ?: requireNotNull(preparedInput).capabilities
            val budget = layeredInput?.budget ?: lane?.budget ?: requireNotNull(preparedInput).budget
            val extent = layeredInput?.extent ?: lane?.targetExtent ?: requireNotNull(preparedInput).targetExtent
            val imageInventory=mutableListOf<ImageAllocation>()
            val imageOwners=java.util.IdentityHashMap<org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels,ImageAllocation>()
            sources.mapNotNull { it.image?.upload }.forEach { upload ->
                val allocation=imageOwners.getOrPut(upload.pixelsOwner) {
                    ImageAllocation(upload.pixelsOwner,upload.logicalRowBytesI64,upload.byteCountI64,upload.physicalFormat,upload)
                        .also { imageInventory += it }
                }
                require(allocation.authenticates(upload)) { W5fPlanDiagnostics.Schema }
            }
            val seenImages=java.util.IdentityHashMap<org.graphiks.kanvas.render.ir.ImageResourceSnapshot.Pixels,Unit>()
            val imageDescriptions=sources.flatMap { it.composed?.nodes.orEmpty() }.mapNotNull { it.imageSource?.description }
                .onEach { description ->
                    val allocation=imageOwners.getOrPut(description.pixels) {
                        ImageAllocation(description.pixels,description.logicalRowBytesI64,description.byteCountI64,
                            description.physicalFormat,null).also { imageInventory += it }
                    }
                    require(allocation.authenticates(description)) { W5gPlanDiagnostics.Schema }
                }
                .filter { seenImages.put(it.pixels,Unit) == null }
            val runtimeInventory = sources.flatMap { it.composed?.runtimeResources.orEmpty() }
            val runtimeEntries = sources.flatMap { it.composed?.nodes.orEmpty() }.mapNotNull { it.runtime }
            val noiseWorkLimitI64 = minOf(budget.materialFrameLimits.maxNoiseOctaveEvaluationsI64,
                runtimeEntries.minOfOrNull { it.frameLimits.maxNoiseOctaveEvaluationsI64 } ?: Long.MAX_VALUE)
            val runtimeOwners=RuntimeResourceOwnerIndexV1<CapturedRuntimeResourceV1>()
            val runtimeAllocations=runtimeInventory.map { runtimeOwners.getOrPut(it) { it } }.distinct()
            runtimeInventory.mapNotNull { (it.binding as? org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.SampledTexture)?.image }
                .forEach { pixels -> imageOwners.getOrPut(pixels) {
                    val row = Math.multiplyExact(pixels.width.toLong(),pixels.pixelFormat.bytesPerPixel.toLong())
                    val size = Math.multiplyExact(row,pixels.height.toLong())
                    require(pixels.width > 0 && pixels.height > 0 && pixels.rowBytes >= row && size <= Int.MAX_VALUE &&
                        pixels.pixelFormat in setOf(org.graphiks.kanvas.render.ir.ImagePixelFormat.RGBA_8888,
                            org.graphiks.kanvas.render.ir.ImagePixelFormat.BGRA_8888,org.graphiks.kanvas.render.ir.ImagePixelFormat.SRGBA_8888,
                            org.graphiks.kanvas.render.ir.ImagePixelFormat.ALPHA_8)) { W5hPlanDiagnostics.Descriptor }
                    ImageAllocation(pixels,row,size,if(pixels.pixelFormat == org.graphiks.kanvas.render.ir.ImagePixelFormat.ALPHA_8)
                        ImagePhysicalFormatV1.R8_UNORM else ImagePhysicalFormatV1.RGBA8_UNORM,null).also { imageInventory += it }
                } }
            val runtimeStorage = runtimeAllocations.mapNotNull { it.binding as? org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.StorageRead }
            val runtimeBytes = runtimeStorage.fold(0L) { bytes,binding ->
                val size = alignRuntimeStorageBytesI64(binding.bytes.sizeBytesI32.toLong())
                require(size <= caps.maxBufferSizeBytes && caps.maxStorageBufferBindingSizeBytesI64?.let { size <= it } == true) {
                    W5hPlanDiagnostics.Budget
                }
                Math.addExact(bytes,size)
            }
            require(runtimeAllocations.count { it.binding !is org.graphiks.kanvas.render.ir.RuntimeEffectResourceBindingV1.SampledTexture } <=
                PlanCacheResourceRequest.MAX_RUNTIME_ENTRIES_I32 && runtimeBytes <= PlanCacheResourceRequest.MAX_RUNTIME_BYTES_I64 &&
                imageInventory.size <= PlanCacheResourceRequest.MAX_RUNTIME_ENTRIES_I32 &&
                imageInventory.fold(0L) { bytes,image -> Math.addExact(bytes,image.byteCountI64) } <= PlanCacheResourceRequest.MAX_RUNTIME_BYTES_I64) {
                W5hPlanDiagnostics.Budget
            }
            // Scalar seed decisions are deduplicated only within this physical frame slab.
            val noiseSeeds=sources.flatMap { it.composed?.nodes.orEmpty() }
                .mapNotNull { it.noiseSource?.parameters?.normalizedSeedI32 }.distinct()
            val noiseBytes=try { Math.multiplyExact(noiseSeeds.size.toLong(),NoiseTableV1.BYTE_COUNT_I32.toLong()) }
                catch (_: ArithmeticException) { throw IllegalArgumentException(W5gPlanDiagnostics.NoiseStorage) }
            require(noiseBytes <= Int.MAX_VALUE && noiseBytes <= caps.maxBufferSizeBytes &&
                (noiseBytes == 0L || caps.maxStorageBufferBindingSizeBytesI64?.let { noiseBytes <= it } == true)) {
                W5gPlanDiagnostics.NoiseStorage
            }
            val noiseRanges=noiseSeeds.mapIndexed { index,seed ->
                NoiseTableRangeV1(seed,Math.multiplyExact(index.toLong(),NoiseTableV1.WORD_COUNT_I32.toLong()).toUInt())
            }
            var nonUniform = layeredInput?.nonUniformBytesI64 ?: preparedInput?.nonUniformBytesI64 ?: ordinaryLayout?.nonUniformWithoutStopsI64 ?: requireNotNull(lane).peakFrameLocalBytesI64
            if (ordinaryLayout == null && layeredInput == null && lane?.capabilityId == W3SolidRectPlanCompiler.W5A_CAPABILITY_ID) {
                val alignment = caps.minUniformBufferOffsetAlignment.toLong()
                require(alignment > 0L) { W5fPlanDiagnostics.FilterBinding }
                val stride = Math.addExact(32L,(alignment-32L%alignment)%alignment)
                listOf(PlanScratchBufferKind.Vertex to 32L,PlanScratchBufferKind.Index to 24L,
                    PlanScratchBufferKind.Uniform to stride).forEach { (kind,perDraw) ->
                    val bytes = requireNotNull(caps.bufferAllocationPolicy.reserve(kind,
                        Math.multiplyExact(lane.visualCommandCount.toLong(),perDraw))) { W5fPlanDiagnostics.FilterBinding }
                    require(bytes <= caps.maxBufferSizeBytes) { W5fPlanDiagnostics.FilterBinding }
                    nonUniform = Math.addExact(nonUniform,bytes)
                }
            }
            imageInventory.filter { it.upload != null }.forEach {
                nonUniform = Math.addExact(nonUniform,it.physicalBytesI64(caps)) }
            require(nonUniform <= budget.maxFrameLocalBytes) { W5eImagePlanDiagnostics.FrameBudget }
            try {
                imageInventory.filter { it.upload == null }.forEach { allocation ->
                    nonUniform=Math.addExact(nonUniform,allocation.physicalBytesI64(caps))
                    require(nonUniform <= budget.maxFrameLocalBytes) { W5gPlanDiagnostics.Binding }
                }
            } catch (_: IllegalArgumentException) { throw IllegalArgumentException(W5gPlanDiagnostics.Binding) }
                catch (_: ArithmeticException) { throw IllegalArgumentException(W5gPlanDiagnostics.Binding) }
            // Inspect every actual final draw, not the canonically deduplicated
            // source list: a shared source may have different final blend ABIs.
            val actualSourceDraws=preparedInput?.sources?.map { Triple(it,it.blend,extent) } ?:
                (if(ordinaryLayout == null && layeredInput == null) listOf(requireNotNull(lane)) else nativeLanes).flatMap { actualLane ->
                RenderGraph.visualDraws(actualLane.passes()).map { draw ->
                    Triple(actualLane.sourceTable().source(draw.materialAuthority.materialPlanRef()),draw.blend,actualLane.targetExtent)
                }
            }
            var noiseWork=0L
            var runtimeLeasesI64=0L
            actualSourceDraws.forEach { (source,blend,sourceExtent) -> source.composed?.layout?.let { layout ->
                require(sources.any { it === source }) { W5gPlanDiagnostics.Schema }
                val destination=blend as? BlendPlan.DestinationReadV1
                val finalTextures=when(destination?.compositionAbiI32) {
                    null -> 0
                    3 -> 1
                    4 -> 2
                    else -> throw IllegalArgumentException(W5gPlanDiagnostics.Binding)
                }
                val textures=Math.addExact(layout.resources.count { it.texture != null },finalTextures)
                runtimeLeasesI64=Math.addExact(runtimeLeasesI64,requireNotNull(source.composed).runtimeResources.size.toLong())
                require(runtimeLeasesI64 <= PlanCacheResourceRequest.MAX_RUNTIME_LEASES_I32) { W5hPlanDiagnostics.Budget }
                val samplers=layout.resources.count { it.sampler != null }
                if(samplers > 0) require(caps.maxSamplersPerShaderStageI32?.let { it >= samplers } == true) { W5hPlanDiagnostics.Budget }
                if(layout.resources.any { it.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.RUNTIME_READ })
                    require(caps.supportedOperations().containsAll(setOf(PlanOperationCapability.StorageBuffer,PlanOperationCapability.CopyUpload)) &&
                        caps.maxStorageBuffersPerShaderStageI32?.let { it >= layout.resources.count { row -> row.buffer != null } } == true) {
                        W5hPlanDiagnostics.Budget
                    }
                if(textures > 0) require(caps.maxSampledTexturesPerShaderStageI32?.let { it >= textures } == true) {
                    W5gPlanDiagnostics.Binding
                }
                val noiseNodes=requireNotNull(source.composed).nodes.mapNotNull { it.noiseSource }
                if (noiseNodes.isNotEmpty()) {
                    val storageCount=layout.resources.count { it.buffer != null }
                    require(caps.supportedOperations().containsAll(setOf(PlanOperationCapability.StorageBuffer,
                        PlanOperationCapability.CopyUpload)) && caps.maxStorageBuffersPerShaderStageI32?.let { it >= storageCount } == true) {
                        W5gPlanDiagnostics.NoiseStorage
                    }
                    try {
                        val bounds=source.deviceBoundsF32
                        val width=kotlin.math.ceil((minOf(bounds.right.toDouble(),sourceExtent.width.toDouble())-
                            maxOf(bounds.left.toDouble(),0.0)).coerceAtLeast(0.0))
                        val height=kotlin.math.ceil((minOf(bounds.bottom.toDouble(),sourceExtent.height.toDouble())-
                            maxOf(bounds.top.toDouble(),0.0)).coerceAtLeast(0.0))
                        require(width.isFinite() && height.isFinite() && width >= 0.0 && height >= 0.0 &&
                            width < Long.MAX_VALUE.toDouble() && height < Long.MAX_VALUE.toDouble()) { W5gPlanDiagnostics.NoiseWork }
                        val area=Math.multiplyExact(width.toLong(),height.toLong())
                        noiseNodes.forEach { noise -> noiseWork=Math.addExact(noiseWork,
                            Math.multiplyExact(Math.multiplyExact(area,noise.parameters.octavesI32.toLong()),4L)) }
                    } catch (_: ArithmeticException) { throw IllegalArgumentException(W5gPlanDiagnostics.NoiseWork) }
                    require(noiseWork <= noiseWorkLimitI64) { if(runtimeEntries.isEmpty()) W5gPlanDiagnostics.NoiseWork else W5hPlanDiagnostics.Budget }
                }
            } }
            require(sources.filter { it.composed != null }.all { source -> actualSourceDraws.any { it.first === source } }) {
                W5gPlanDiagnostics.Schema
            }
            nonUniform=Math.addExact(nonUniform,noiseBytes)
            nonUniform=Math.addExact(nonUniform,runtimeBytes)
            require(nonUniform <= budget.maxFrameLocalBytes) { W5gPlanDiagnostics.NoiseStorage }
            var total = nonUniform
            fun add(bytes: Long,code: String) {
                total = Math.addExact(total,bytes)
                if (layeredInput != null) W6aLayerPlanBudget.requireWithin(total,budget)
                else require(total <= budget.maxFrameLocalBytes) { code }
            }
            legacy.sortedBy { it.hasCoordinatesV2 }.forEach {
                require(it.original.fitsUniformBinding(caps)) { if (it.hasCoordinatesV2)
                    W5dPlanDiagnostics.CoordinateUniformBudget else W5cPlanDiagnostics.StorageUnavailable }
                add(it.uniformByteCountI64,if (it.hasCoordinatesV2) W5dPlanDiagnostics.CoordinateUniformBudget else W5cPlanDiagnostics.StopBudget)
            }
            retainedV4.forEach { requireColorUniformBindingV4(it.uniformByteCountI64,caps)
                add(it.sourceUniformByteCountI64,"resource-limit.w5b.source-budget") }
            pending.forEach { source ->
                val bytes = source.uniformBytesI64()
                val bindings=source.composed?.layout?.resources?.size?.let { Math.addExact(1,it) }
                    ?: ((if (source.hasGradientStorage) 2 else 1) + (if (source.image != null) 1 else 0))
                requireColorUniformBindingV4(bytes,caps,bindings,
                    if (source.composed != null) W5gPlanDiagnostics.Binding else W5dPlanDiagnostics.CoordinateUniformBudget)
                add(source.uniformBytesI64(true),if (source.composed != null) W5gPlanDiagnostics.Uniform else W5dPlanDiagnostics.CoordinateUniformBudget)
            }
            val stopBytes = Math.multiplyExact(stopCountI64,32L)
            val ordinaryStopBytes=allocations.filter { (it.values as? RangeValues.Pending)?.composedOwner == null }
                .fold(0L) { bytes,allocation -> Math.addExact(bytes,Math.multiplyExact(allocation.values.countI32.toLong(),32L)) }
            if(ordinaryStopBytes > 0L) requireGradientStorageCapabilitiesV4(ordinaryStopBytes,caps)
            if (stopBytes > ordinaryStopBytes) try {
                requireGradientStorageCapabilitiesV4(stopBytes,caps)
            } catch(failure: IllegalArgumentException) {
                throw IllegalArgumentException(when(failure.message) {
                    W5cPlanDiagnostics.StorageUnavailable -> W5gPlanDiagnostics.Binding
                    W5cPlanDiagnostics.StopBudget -> W5gPlanDiagnostics.Storage
                    else -> failure.message
                })
            }
            add(ordinaryStopBytes,W5cPlanDiagnostics.StopBudget)
            add(stopBytes-ordinaryStopBytes,W5gPlanDiagnostics.Storage)
            retainedV4.forEach { add(it.uniformByteCountI64-it.sourceUniformByteCountI64,W5fPlanDiagnostics.FilterUniform) }
            pending.forEach { add(it.uniformBytesI64()-it.uniformBytesI64(true),W5fPlanDiagnostics.FilterUniform) }
            SourceConstructionResultV4.Built(FrameSourceLayoutV4(lane,preparedInput,interner,sources,rows,allocations,pendingRanges,legacyRanges,
                legacy,imageInventory,imageDescriptions,noiseRanges,noiseBytes,nonUniform,stopBytes,Math.subtractExact(Math.subtractExact(total,nonUniform),stopBytes),
                nativeLanes,nativeOffsetsI32,nativeGeometry,ordinaryLayout,layeredInput))
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (_: ArithmeticException) {
            sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget)
        }

        private fun imagePhysicalBytesI64(upload: ImageUploadPlanV1,caps: PlanCapabilitySnapshot): Long =
            imagePhysicalBytesI64(upload.widthI32,upload.heightI32,upload.logicalRowBytesI64,upload.byteCountI64,upload.physicalFormat,caps)
        private fun imagePhysicalBytesI64(widthI32: Int,heightI32: Int,logicalRowBytesI64: Long,byteCountI64: Long,
            physicalFormat: ImagePhysicalFormatV1,caps: PlanCapabilitySnapshot): Long {
            require(widthI32 <= caps.maxTextureDimension2D && heightI32 <= caps.maxTextureDimension2D &&
                caps.supportsTexture(PlanTextureFormat.ImageV1(physicalFormat),1,
                    setOf(PlanResourceUsage.Sampled,PlanResourceUsage.CopyDestination))) { W5eImagePlanDiagnostics.TextureLimit }
            val alignment = lcmI64(256L,caps.copyBytesPerRowAlignment.toLong())
            val row = Math.addExact(logicalRowBytesI64,
                (alignment-logicalRowBytesI64%alignment)%alignment)
            val staging = Math.multiplyExact(row,heightI32.toLong())
            require(staging <= minOf(caps.maxBufferSizeBytes,Int.MAX_VALUE.toLong())) { W5eImagePlanDiagnostics.Capability }
            return Math.addExact(byteCountI64,staging)
        }
    }
}
