package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

/** Rebind material coordinates only after W4 has issued the native lane geometry. */
internal fun PlanDraw.withW5dCoordinates(coordinates: MaterialCoordinatePlanV2): PlanDraw {
    require(materialAuthority !is PlanDrawMaterialAuthority.MaterialV4) { W5fPlanDiagnostics.Unpromoted }
    return when (this) {
    is SolidRectDraw -> SolidRectDraw.ofMaterial(commandIndex, materialAuthority.materialPlanRef(),
        copyVisibleBounds(), copyScissor(), coverage, sample, blend, coordinatesV2 = coordinates)
    is AnalyticRectDraw -> AnalyticRectDraw.ofMaterial(commandIndex, materialAuthority.materialPlanRef(),
        copyDeviceBounds(), copyRasterBounds(), copyScissor(), blend, coordinatesV2 = coordinates)
    is AnalyticRRectDraw -> AnalyticRRectDraw.ofMaterial(commandIndex, materialAuthority.materialPlanRef(),
        origin, copyDeviceShape(), copyRasterBounds(), copyScissor(), blend, coordinatesV2 = coordinates)
    is PathFillDraw -> PathFillDraw.ofMaterial(commandIndex, materialAuthority.materialPlanRef(),
        copyGeometryF32(), strategy, copyScissorI32(), blend, coordinatesV2 = coordinates)
    is PathStrokeDraw -> PathStrokeDraw.ofMaterial(commandIndex, materialAuthority.materialPlanRef(),
        copyGeometryF32(), copyScissorI32(), mode, styleF64, blend, coordinatesV2 = coordinates)
    is GeneralPathDraw -> GeneralPathDraw.ofMaterial(commandIndex, materialAuthority.materialPlanRef(),
        copyPathGeometry(), strategy, copyScissorI32(), coverage, sample, blend, coordinatesV2 = coordinates)
    else -> error(W5dPlanDiagnostics.CoordinatePlanSchema)
}
}

/** One authentic geometry lane inside an ordered W5b color envelope. */
public class W5bGeometryLanePlanV3 internal constructor(
    public val sourceGraph: RenderGraph,
    commandIndicesI32: List<Int>,
    public val drawDataResources: PlanDrawDataResources?,
    public val depthStencil: PlanResourceId?,
) {
    public val capabilityId: String get() = sourceGraph.capabilityId
    private val commands = immutableList(commandIndicesI32)
    public fun commandIndicesI32(): List<Int> = commands

    public companion object {
        public const val COMPOSITE_CAPABILITY_ID: String = "w5b-native-geometry-composite-v3"
        internal fun clearOnly(id: PlanId, capabilityId: String, extent: SizeI32,
            capabilities: PlanCapabilitySnapshot, budget: PlanBudget, material: MaterialPlanTable?): RenderGraph {
            return constructClearOnly(id,capabilityId,extent,capabilities,budget,material).publish()
        }
        internal fun constructClearOnly(id: PlanId, capabilityId: String, extent: SizeI32,
            capabilities: PlanCapabilitySnapshot, budget: PlanBudget, material: MaterialPlanTable?): RenderGraphConstruction {
            val topology = describeClearOnly(capabilityId,extent,capabilities,budget)
            // The historical empty-draw sealer discards material and charges only
            // the clear target/readback. Keep that exact budget and null table.
            RawMaterialRequirementsV2.requireFrameBudget(emptyList(),topology.peakI64,budget,
                "resource-limit.w5b.destination-budget")
            return RenderGraph.construct(id,capabilityId,extent,topology.format,capabilities,budget,0,
                topology.resources,topology.passes,topology.dependencies,topology.peakI64,null)
        }
        internal fun describeClearOnly(capabilityId: String,extent: SizeI32,
            capabilities: PlanCapabilitySnapshot,budget: PlanBudget): W5bDestinationGraphSealer.DestinationTopologyV4 {
            val targetBytesI64 = Math.multiplyExact(Math.multiplyExact(extent.width.toLong(), extent.height.toLong()), 4L)
            val widthBytesI64 = Math.multiplyExact(extent.width.toLong(), 4L)
            val alignmentI64 = capabilities.copyBytesPerRowAlignment.toLong()
            val rowBytesI64 = Math.addExact(widthBytesI64, (alignmentI64 - widthBytesI64 % alignmentI64) % alignmentI64)
            return W5bDestinationGraphSealer.describeSources(capabilityId, extent, capabilities, budget, emptyList(),
                targetBytesI64, Math.multiplyExact(rowBytesI64, extent.height.toLong()), rowBytesI64)
        }
    }
}

/** One compiler-owned destination timeline; each lane keeps its original geometry reservations. */
internal fun issueW5bNativeComposite(graphs: List<RenderGraphConstruction>): RenderGraphConstruction {
    require(graphs.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32)
    val first = graphs.first()
    val admitted = setOf(W3SolidRectPlanCompiler.CAPABILITY_ID, W3SolidRectPlanCompiler.W5A_CAPABILITY_ID,
        W4aAnalyticRectPlanCompiler.W5A_CAPABILITY_ID, W4aAnalyticRectPlanCompiler.W5B_CAPABILITY_ID,
        W4bAnalyticRRectPlanCompiler.CAPABILITY_ID,
        W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID, W4cPathFillPlanCompiler.CAPABILITY_ID,
        W4cPathFillPlanCompiler.W5B_CAPABILITY_ID, W4dPathStrokePlanCompiler.CAPABILITY_ID,
        W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID, W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID,
        W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID)
    require(graphs.all { it.capabilityId in admitted && it.targetExtent == first.targetExtent &&
        it.capabilities == first.capabilities && it.budget == first.budget && it.colorFormat == first.colorFormat })
    require(graphs.filter { it.capabilityId == W3SolidRectPlanCompiler.CAPABILITY_ID }.all {
        it.visualCommandCount == 0 && it.materialPlanTableOrNull() == null
    })
    val activeGraphs = graphs.filter { it.visualCommandCount > 0 }
    if (activeGraphs.isEmpty()) {
        val identity = java.security.MessageDigest.getInstance("SHA-256").digest(
            graphs.joinToString("|") { it.id.value }.encodeToByteArray()).joinToString("") { "%02x".format(it) }
        return RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.constructClearOnly(PlanId("w5b.composite.$identity"),
            W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID, first.targetExtent, first.capabilities, first.budget, null))
    }
    val interned = MaterialPlanTable.intern(activeGraphs.map { requireNotNull(it.materialPlanTableOrNull()) })
    val layout = nativeCompositeGeometryLayoutV4(activeGraphs.mapIndexed { ordinal,graph ->
        NativeGeometryInputV4(graph.capabilityId,graph.resources(),
            remapSourcePassesV4(graph.passes()) { interned.remap(ordinal,it) })
    },first.capabilities)
    val lanes = layout.lanes.map { lane ->
        val graph = activeGraphs[lane.ordinalI32]
        val source = if (graph.capabilityId == W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID)
            graph.w5bGeometryLanes().single().sourceGraph else graph
        GeometryLaneConstruction(source.rebindMaterials(interned.table) { interned.remap(lane.ordinalI32,it) },
            lane.commandsI32,lane.data,lane.depth)
    }
    val identity = java.security.MessageDigest.getInstance("SHA-256").digest(
        graphs.joinToString("|") { it.id.value }.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    val graph = W5bDestinationGraphSealer.construct(PlanId("w5b.composite.$identity"),
        W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID, first.targetExtent, first.capabilities, first.budget,
        layout.colors, interned.table, first.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
        first.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize,
        (first.passes().last() as PlanPass.ReadbackPass).bytesPerRow, layout.geometryResources,
        drawDataByCommandI32 = layout.dataByCommand, depthStencilByCommandI32 = layout.depthByCommand)
    return RenderGraph.issueW5bGeometry(graph, lanes)
}

/** Same native topology recipe for already-bound and captured sources; no source issuance. */
internal class NativeGeometryInputV4(val capabilityId: String,resources: List<PlanResource>,passes: List<PlanPass>) {
    val resources = immutableList(resources)
    val passes = immutableList(passes)
}
internal class NativeGeometryLaneMetadataV4(val ordinalI32: Int,commandsI32: List<Int>,
    val data: PlanDrawDataResources,val depth: PlanResourceId?) {
    val commandsI32 = immutableList(commandsI32)
}
internal class NativeCompositeGeometryLayoutV4(lanes: List<NativeGeometryLaneMetadataV4>,resources: List<PlanResource>,
    colors: List<PlanDraw>,data: Map<Int,PlanDrawDataResources>,depth: Map<Int,PlanResourceId>) {
    val lanes = immutableList(lanes)
    val geometryResources = immutableList(resources)
    val colors = immutableList(colors)
    val dataByCommand = java.util.Collections.unmodifiableMap(LinkedHashMap(data))
    val depthByCommand = java.util.Collections.unmodifiableMap(LinkedHashMap(depth))
}
internal fun nativeCompositeGeometryLayoutV4(inputs: List<NativeGeometryInputV4>,
    capabilities: PlanCapabilitySnapshot): NativeCompositeGeometryLayoutV4 {
    val lanes = mutableListOf<NativeGeometryLaneMetadataV4>()
    val geometryResources = mutableListOf<PlanResource>()
    val colors = mutableListOf<PlanDraw>()
    val dataByCommand = mutableMapOf<Int, PlanDrawDataResources>()
    val depthByCommand = mutableMapOf<Int, PlanResourceId>()
    inputs.forEachIndexed { ordinal, graph ->
        val draws = graph.passes.flatMap { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws()
            is PlanPass.StencilCover -> listOf(pass.draw)
            is PlanPass.PathRenderPass -> if (pass.phase == PathRenderPhase.SingleSampleStencilProducer) emptyList() else listOf(pass.draw as GeneralPathDraw)
            else -> emptyList()
        } }
        if (draws.isEmpty()) return@forEachIndexed
        val resources = graph.resources.filter { it.role in setOf(PlanResourceRole.VertexData,
            PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil) }.map { resource ->
            PlanResource.of(resource.role, ordinal, resource.kind, resource.format, resource.copyExtent(), resource.byteSize,
                resource.usages(), resource.lifetime, 0, 1, resource.sampleCountI32)
        }.toMutableList()
        if (graph.capabilityId == W3SolidRectPlanCompiler.W5A_CAPABILITY_ID) {
            require(resources.isEmpty())
            val alignmentI64 = capabilities.minUniformBufferOffsetAlignment.toLong()
            val strideI64 = Math.addExact(32L, (alignmentI64 - 32L % alignmentI64) % alignmentI64)
            listOf(Triple(PlanResourceRole.VertexData, PlanScratchBufferKind.Vertex, 32L),
                Triple(PlanResourceRole.IndexData, PlanScratchBufferKind.Index, 24L),
                Triple(PlanResourceRole.UniformData, PlanScratchBufferKind.Uniform, strideI64)).forEach { (role, kind, perDraw) ->
                val bytesI64 = requireNotNull(capabilities.bufferAllocationPolicy.reserve(kind,
                    Math.multiplyExact(draws.size.toLong(), perDraw)))
                require(bytesI64 <= capabilities.maxBufferSizeBytes)
                val usage = when (role) {
                    PlanResourceRole.VertexData -> PlanResourceUsage.Vertex
                    PlanResourceRole.IndexData -> PlanResourceUsage.Index
                    else -> PlanResourceUsage.Uniform
                }
                resources += PlanResource.of(role, ordinal, PlanResourceKind.Buffer, null, null, bytesI64,
                    setOf(usage, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, 1)
            }
        }
        val data = PlanDrawDataResources(resources.single { it.role == PlanResourceRole.VertexData }.id,
            resources.single { it.role == PlanResourceRole.IndexData }.id, resources.single { it.role == PlanResourceRole.UniformData }.id)
        val depth = resources.singleOrNull { it.role == PlanResourceRole.DepthStencil }?.id
        geometryResources += resources
        lanes += NativeGeometryLaneMetadataV4(ordinal,draws.map { it.commandIndex },data,depth)
        draws.forEach { draw ->
            require(draw is SolidRectDraw || draw is AnalyticRectDraw || draw is AnalyticRRectDraw ||
                draw is PathFillDraw || draw is PathStrokeDraw || draw is GeneralPathDraw)
            colors += draw
            dataByCommand[draw.commandIndex] = data
            if (draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover)
                depthByCommand[draw.commandIndex] = requireNotNull(depth)
        }
    }
    require(colors.zipWithNext().all { (a, b) -> a.commandIndex < b.commandIndex })
    return NativeCompositeGeometryLayoutV4(lanes,geometryResources,colors,dataByCommand,depthByCommand)
}

/** Exact color/geometry split for the successor; historical W4 path validation stays closed. */
internal fun validateW5bGeometryPasses(passes: List<PlanPass>, resources: Map<PlanResourceId, PlanResource>,
    visualCommandCountI32: Int, w4eSource: W4eGeometryFactsV6? = null) {
    val colors = passes.flatMap { pass -> when (pass) {
        is PlanPass.RenderPass -> pass.draws()
        is PlanPass.StencilCover -> listOf(pass.draw)
        else -> emptyList()
    } }
    require(colors.size == visualCommandCountI32 && colors.zipWithNext().all { (a, b) -> a.commandIndex < b.commandIndex })
    require(colors.all { it.sample == SamplePlan.SingleSample && (it.materialAuthority is PlanDrawMaterialAuthority.MaterialV1 || it.materialAuthority is PlanDrawMaterialAuthority.MaterialV2 ||
        (it is SolidRectDraw || it is AnalyticRectDraw || it is AnalyticRRectDraw || it is PathFillDraw || it is PathStrokeDraw ||
            it is GeneralPathDraw || it is W5bW4ePathDraw) &&
            it.materialAuthority is PlanDrawMaterialAuthority.MaterialV5 ||
        (it is SolidRectDraw || it is AnalyticRectDraw || it is AnalyticRRectDraw || it is PathFillDraw ||
            it is PathStrokeDraw || it is GeneralPathDraw) &&
            it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) && it.blend != BlendPlan.NoOpV1 })
    fun data(value: PlanDrawDataResources) {
        for ((id, role, usage) in listOf(Triple(value.vertex, PlanResourceRole.VertexData, PlanResourceUsage.Vertex),
            Triple(value.index, PlanResourceRole.IndexData, PlanResourceUsage.Index),
            Triple(value.uniform, PlanResourceRole.UniformData, PlanResourceUsage.Uniform))) {
            val resource = requireNotNull(resources[id])
            require(resource.role == role && resource.kind == PlanResourceKind.Buffer &&
                resource.usages() == setOf(usage, PlanResourceUsage.CopyDestination))
        }
    }
    passes.forEachIndexed { indexI32, pass -> when (pass) {
        is PlanPass.RenderPass -> {
            require(pass.draws().size <= 1)
            if (pass.draws().any { it is PathDraw }) {
                require((pass.draws().single() as PathDraw).strategy == PathFillStrategy.DirectTriangle)
                data(requireNotNull(pass.drawDataResources))
            } else pass.drawDataResources?.let(::data)
        }
        is PlanPass.StencilGeometryProducerV3 -> {
            val cover = passes.getOrNull(indexI32 + 1) as? PlanPass.StencilCover
            require(cover != null && cover.draw.commandIndex == pass.commandIndexI32 &&
                cover.draw.copyPathGeometry() == pass.copyGeometry() && cover.draw.copyScissorI32() == pass.copyScissorI32() &&
                cover.draw.strategy == PathFillStrategy.StencilCover && cover.target == pass.target &&
                cover.depthStencil == pass.depthStencil && cover.atomicGroup == pass.atomicGroup &&
                pass.atomicGroup == canonicalPathAtomicGroup(cover.draw) && cover.drawDataResources == pass.drawDataResources &&
                cover.load == AttachmentLoadPlan.Load && cover.store == AttachmentStorePlan.Store &&
                pass.store == AttachmentStorePlan.Store && cover.depthStencilAccess == PlanDepthStencilAccess.ReadWrite &&
                cover.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset)
            val depth = requireNotNull(resources[pass.depthStencil])
            require(depth.role == PlanResourceRole.DepthStencil && depth.sampleCountI32 == 1 &&
                depth.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                depth.usages() == setOf(PlanResourceUsage.DepthStencilAttachment))
            data(pass.drawDataResources)
        }
        is PlanPass.StencilCover -> require(passes.getOrNull(indexI32 - 1) is PlanPass.StencilGeometryProducerV3)
        is PlanPass.ClipMaskInitialize, is PlanPass.ClipMaskProducer, is PlanPass.ClipMaskFold ->
            require(w4eSource?.passes?.any { it === pass } == true)
        is PlanPass.TextureCopy, is PlanPass.ReadbackPass -> Unit
        else -> error("Invalid W5b geometry-lane pass")
    } }
}
