package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

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
            val targetBytesI64 = Math.multiplyExact(Math.multiplyExact(extent.width.toLong(), extent.height.toLong()), 4L)
            val widthBytesI64 = Math.multiplyExact(extent.width.toLong(), 4L)
            val alignmentI64 = capabilities.copyBytesPerRowAlignment.toLong()
            val rowBytesI64 = Math.addExact(widthBytesI64, (alignmentI64 - widthBytesI64 % alignmentI64) % alignmentI64)
            return W5bDestinationGraphSealer.seal(id, capabilityId, extent, capabilities, budget, emptyList(), material,
                targetBytesI64, Math.multiplyExact(rowBytesI64, extent.height.toLong()), rowBytesI64)
        }
    }
}

/** One compiler-owned destination timeline; each lane keeps its original geometry reservations. */
internal fun issueW5bNativeComposite(graphs: List<RenderGraph>): RenderGraph {
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
        return RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.clearOnly(PlanId("w5b.composite.$identity"),
            W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID, first.targetExtent, first.capabilities, first.budget, null))
    }
    val interned = MaterialPlanTable.intern(activeGraphs.map { requireNotNull(it.materialPlanTableOrNull()) })
    val lanes = mutableListOf<W5bGeometryLanePlanV3>()
    val geometryResources = mutableListOf<PlanResource>()
    val colors = mutableListOf<PlanDraw>()
    val dataByCommand = mutableMapOf<Int, PlanDrawDataResources>()
    val depthByCommand = mutableMapOf<Int, PlanResourceId>()
    activeGraphs.forEachIndexed { ordinal, graph ->
        val draws = graph.passes().flatMap { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws()
            is PlanPass.StencilCover -> listOf(pass.draw)
            is PlanPass.PathRenderPass -> if (pass.phase == PathRenderPhase.SingleSampleStencilProducer) emptyList() else listOf(pass.draw as GeneralPathDraw)
            else -> emptyList()
        } }
        if (draws.isEmpty()) return@forEachIndexed
        val resources = graph.resources().filter { it.role in setOf(PlanResourceRole.VertexData,
            PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil) }.map { resource ->
            PlanResource.of(resource.role, ordinal, resource.kind, resource.format, resource.copyExtent(), resource.byteSize,
                resource.usages(), resource.lifetime, 0, 1, resource.sampleCountI32)
        }.toMutableList()
        if (graph.capabilityId == W3SolidRectPlanCompiler.W5A_CAPABILITY_ID) {
            require(resources.isEmpty())
            val alignmentI64 = graph.capabilities.minUniformBufferOffsetAlignment.toLong()
            val strideI64 = Math.addExact(32L, (alignmentI64 - 32L % alignmentI64) % alignmentI64)
            listOf(Triple(PlanResourceRole.VertexData, PlanScratchBufferKind.Vertex, 32L),
                Triple(PlanResourceRole.IndexData, PlanScratchBufferKind.Index, 24L),
                Triple(PlanResourceRole.UniformData, PlanScratchBufferKind.Uniform, strideI64)).forEach { (role, kind, perDraw) ->
                val bytesI64 = requireNotNull(graph.capabilities.bufferAllocationPolicy.reserve(kind,
                    Math.multiplyExact(draws.size.toLong(), perDraw)))
                require(bytesI64 <= graph.capabilities.maxBufferSizeBytes)
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
        val geometrySource = if (graph.capabilityId == W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID)
            graph.w5bGeometryLanes().single().sourceGraph else graph
        lanes += W5bGeometryLanePlanV3(geometrySource, draws.map { it.commandIndex }, data, depth)
        draws.forEach { draw ->
            val ref = interned.remap(ordinal, (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref)
            colors += when (draw) {
                is SolidRectDraw -> draw.withMaterialRef(ref)
                is AnalyticRectDraw -> draw.withMaterialRef(ref)
                is AnalyticRRectDraw -> draw.withMaterialRef(ref)
                is PathFillDraw -> draw.withMaterialRef(ref)
                is PathStrokeDraw -> draw.withMaterialRef(ref)
                is GeneralPathDraw -> GeneralPathDraw.ofMaterial(draw.commandIndex, ref, draw.copyPathGeometry(),
                    draw.strategy, draw.copyScissorI32(), draw.coverage, draw.sample, draw.blend,
                    (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).coordinates)
                else -> error("Unsupported native W5b composite geometry")
            }
            dataByCommand[draw.commandIndex] = data
            if (draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover)
                depthByCommand[draw.commandIndex] = requireNotNull(depth)
        }
    }
    require(colors.zipWithNext().all { (a, b) -> a.commandIndex < b.commandIndex })
    val identity = java.security.MessageDigest.getInstance("SHA-256").digest(
        graphs.joinToString("|") { it.id.value }.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    val graph = W5bDestinationGraphSealer.seal(PlanId("w5b.composite.$identity"),
        W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID, first.targetExtent, first.capabilities, first.budget,
        colors, interned.table, first.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
        first.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize,
        (first.passes().last() as PlanPass.ReadbackPass).bytesPerRow, geometryResources,
        drawDataByCommandI32 = dataByCommand, depthStencilByCommandI32 = depthByCommand)
    return RenderGraph.issueW5bGeometry(graph, lanes)
}

/** Exact color/geometry split for the successor; historical W4 path validation stays closed. */
internal fun validateW5bGeometryPasses(passes: List<PlanPass>, resources: Map<PlanResourceId, PlanResource>,
    visualCommandCountI32: Int, w4eSource: RenderGraph? = null) {
    val colors = passes.flatMap { pass -> when (pass) {
        is PlanPass.RenderPass -> pass.draws()
        is PlanPass.StencilCover -> listOf(pass.draw)
        else -> emptyList()
    } }
    require(colors.size == visualCommandCountI32 && colors.zipWithNext().all { (a, b) -> a.commandIndex < b.commandIndex })
    require(colors.all { it.sample == SamplePlan.SingleSample && it.materialAuthority is PlanDrawMaterialAuthority.MaterialV1 && it.blend != BlendPlan.NoOpV1 })
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
            require(w4eSource?.passes()?.any { it === pass } == true)
        is PlanPass.TextureCopy, is PlanPass.ReadbackPass -> Unit
        else -> error("Invalid W5b geometry-lane pass")
    } }
}
