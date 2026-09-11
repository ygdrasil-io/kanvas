package org.graphiks.kanvas.gpu.plan

/** Retains the verified General native byte/geometry source; only final color passes are reissued. */
internal fun issueW5bGeneralPathGraph(source: RenderGraph, blendsByCommandI32: Map<Int, BlendPlan>): RenderGraph {
    require(source.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID &&
        source.verifyW4dGeneralCompilerWitness())
    val pathPasses = source.passes().filterIsInstance<PlanPass.PathRenderPass>()
    require(pathPasses.all { it.draw is GeneralPathDraw && it.draw.sample == SamplePlan.SingleSample &&
        it.phase in setOf(PathRenderPhase.SingleSampleDirectColor, PathRenderPhase.SingleSampleStencilProducer,
            PathRenderPhase.SingleSampleStencilColorCover) })
    val colors = pathPasses.filter { it.phase != PathRenderPhase.SingleSampleStencilProducer }
        .map { (it.draw as GeneralPathDraw).withBlend(requireNotNull(blendsByCommandI32[it.draw.commandIndex])) }
    require(colors.map { it.commandIndex }.toSet() == blendsByCommandI32.keys)
    val geometryResources = source.resources().filter { it.role in setOf(
        PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil) }
    val data = pathPasses.first().drawDataResources
    val depth = geometryResources.singleOrNull { it.role == PlanResourceRole.DepthStencil }?.id
    val readback = source.passes().last() as PlanPass.ReadbackPass
    val graph = W5bDestinationGraphSealer.seal(PlanId("w5b.general.${source.id.value}"),
        W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, source.targetExtent, source.capabilities, source.budget,
        colors, source.materialPlanTableOrNull(), source.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
        source.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize, readback.bytesPerRow,
        geometryResources, data, depthStencilByCommandI32 = colors.filter { it.strategy == PathFillStrategy.StencilCover }
            .associate { it.commandIndex to requireNotNull(depth) })
    return RenderGraph.issueW5bGeometry(graph, listOf(W5bGeometryLanePlanV3(source, colors.map { it.commandIndex }, data, depth)))
}
