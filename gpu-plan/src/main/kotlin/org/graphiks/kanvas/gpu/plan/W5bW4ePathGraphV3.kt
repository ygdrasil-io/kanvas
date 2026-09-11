package org.graphiks.kanvas.gpu.plan

/** A retained W4e color consumer; its geometry and clip strategy remain source-compiler-owned. */
public class W5bW4ePathDraw internal constructor(
    public val nativeColorPass: PlanPass.PathRenderPass,
    override val blend: BlendPlan,
) : PathDraw {
    private val source: PathRenderDraw get() = nativeColorPass.draw
    override val commandIndex: Int get() = source.commandIndex
    override val color get() = source.color
    override val materialAuthority get() = source.materialAuthority
    override val strategy get() = source.strategy
    override val coverage get() = source.coverage
    override val sample get() = source.sample
    override fun copyPathGeometry(): PathDrawGeometry = source.copyPathGeometry()
    override fun copyScissorI32() =
        ((source as? ClippedGeneralPathDraw)?.clip as? ClipPlanStrategy.InverseDomain)
            ?.geometryF32?.copyDomainI32() ?: source.copyScissorI32()
    internal fun withBlend(value: BlendPlan): W5bW4ePathDraw = W5bW4ePathDraw(nativeColorPass, value)
}

/** W4e publishes its native payload once; the successor adds only the final-color timeline. */
internal fun issueW5bW4ePathGraph(source: RenderGraph, blendsByCommandI32: Map<Int, BlendPlan>): RenderGraph {
    require(source.capabilityId == W4eClipPlanCompiler.W5A_HARD_CAPABILITY_ID && source.verifyW4eCompilerWitness())
    val pathPasses = source.passes().filterIsInstance<PlanPass.PathRenderPass>()
    require(pathPasses.all { it.draw.sample == SamplePlan.SingleSample &&
        it.phase in setOf(PathRenderPhase.SingleSampleDirectColor, PathRenderPhase.SingleSampleStencilProducer,
            PathRenderPhase.SingleSampleStencilColorCover) })
    val colors = pathPasses.filter { it.phase != PathRenderPhase.SingleSampleStencilProducer }
        .filter { blendsByCommandI32.getValue(it.draw.commandIndex) != BlendPlan.NoOpV1 }
        .map { W5bW4ePathDraw(it, blendsByCommandI32.getValue(it.draw.commandIndex)) }
    val extent = source.targetExtent
    if (colors.isEmpty()) return RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.clearOnly(
        PlanId("w5b.w4e.${source.id.value}"), W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID,
        extent, source.capabilities, source.budget, null))
    val data = pathPasses.first().drawDataResources
    val resources = source.resources().filter { it.role !in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging) }
    val readback = source.passes().last() as PlanPass.ReadbackPass
    val graph = W5bDestinationGraphSealer.seal(PlanId("w5b.w4e.${source.id.value}"),
        W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID, extent, source.capabilities, source.budget, colors,
        source.materialPlanTableOrNull(), source.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
        source.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize, readback.bytesPerRow,
        resources, data, depthStencilByCommandI32 = colors.filter { it.strategy == PathFillStrategy.StencilCover }
            .associate { it.commandIndex to requireNotNull(it.nativeColorPass.depthStencil) }, w4eSource = source)
    return RenderGraph.issueW5bGeometry(graph, listOf(W5bGeometryLanePlanV3(source, colors.map { it.commandIndex }, data, null)))
}

/** Exact W4e producer prefix and native bytes are retained under a separately sealed color timeline. */
internal fun validateW5bW4eGeometrySource(source: RenderGraph, passes: List<PlanPass>, resources: List<PlanResource>,
    extent: org.graphiks.math.geometry.SizeI32, capabilities: PlanCapabilitySnapshot, budget: PlanBudget) {
    require(source.verifyW4eCompilerWitness() && source.targetExtent == extent &&
        source.capabilities == capabilities && source.budget == budget)
    val prefix = source.passes().filter { it is PlanPass.ClipMaskInitialize || it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold }
    require(passes.take(prefix.size) == prefix)
    val colors = passes.flatMap { pass -> when (pass) {
        is PlanPass.RenderPass -> pass.draws()
        is PlanPass.StencilCover -> listOf(pass.draw)
        else -> emptyList()
    } }.map { it as W5bW4ePathDraw }
    require(colors.all { color -> source.passes().any { it === color.nativeColorPass } &&
        color.nativeColorPass.phase in setOf(PathRenderPhase.SingleSampleDirectColor, PathRenderPhase.SingleSampleStencilColorCover) })
    require(source.resources().all { original -> resources.singleOrNull { it.id == original.id }?.let { retained ->
        original.role == retained.role && original.kind == retained.kind && original.byteSize == retained.byteSize &&
            original.format == retained.format && original.copyExtent() == retained.copyExtent() &&
            original.sampleCountI32 == retained.sampleCountI32 && original.usages() == retained.usages()
    } == true })
    require(requireNotNull(source.w4eNativePayloadOrNull()).matchesDeclaredResources(resources))
}
