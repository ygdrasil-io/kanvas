package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest

/** Retains the verified General native byte/geometry source; only final color passes are reissued. */
internal fun issueW5bGeneralPathGraph(source: RenderGraph, blendsByCommandI32: Map<Int, BlendPlan>): RenderGraph {
    require(source.verifyW4dGeneralCompilerWitness())
    require(source.materialPlanTableOrNull()?.entries()?.none { it.bindings is ColorFilterBindingV4 } != false) {
        W5fPlanDiagnostics.Unpromoted
    }
    return issueW5bGeneralPathGraph(source.canonicalConstruction().withWitness(general = true),blendsByCommandI32).publish()
}

internal fun issueW5bGeneralPathGraph(source: RenderGraphConstruction, blendsByCommandI32: Map<Int, BlendPlan>): RenderGraphConstruction {
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
    val graph = W5bDestinationGraphSealer.construct(generalFinalBlendPlanId(source.id, colors),
        W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, source.targetExtent, source.capabilities, source.budget,
        colors, source.materialPlanTableOrNull(), source.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
        source.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize, readback.bytesPerRow,
        geometryResources, data, depthStencilByCommandI32 = colors.filter { it.strategy == PathFillStrategy.StencilCover }
            .associate { it.commandIndex to requireNotNull(depth) })
    return RenderGraph.issueW5bGeometry(graph, listOf(GeometryLaneConstruction(source, colors.map { it.commandIndex }, data, depth)))
}

/** Source geometry identity plus every final-blend fact not assigned by the destination sealer. */
private fun generalFinalBlendPlanId(sourceId: PlanId, colors: List<GeneralPathDraw>): PlanId {
    val digest = MessageDigest.getInstance("SHA-256")
    fun field(value: String) {
        val bytes = value.encodeToByteArray()
        digest.update(bytes.size.toString().encodeToByteArray())
        digest.update(0)
        digest.update(bytes)
        digest.update(0)
    }
    field("w5b-general-final-blends-v1")
    field(sourceId.value)
    field(colors.size.toString())
    colors.sortedBy { it.commandIndex }.forEach { draw ->
        field(draw.commandIndex.toString())
        when (val blend = draw.blend) {
            BlendPlan.LegacySrcOverV1 -> field("LegacySrcOverV1")
            is BlendPlan.FixedFunctionV1 -> {
                field("FixedFunctionV1")
                field(blend.mode.name)
                field(blend.colorSource.name)
                field(blend.colorDestination.name)
                field(blend.alphaSource.name)
                field(blend.alphaDestination.name)
                field(blend.operation.name)
                field(blend.coverage.name)
            }
            is BlendPlan.DestinationReadV1 -> {
                field("DestinationReadV1")
                field(blend.mode.name)
                field(blend.formulaIdentity)
                field(blend.coverage.name)
                field(blend.compositionAbiI32.toString())
                // The sealer overwrites destination version and snapshot from this ordered timeline.
            }
            BlendPlan.NoOpV1 -> error("General final-blend identity requires surviving color consumers")
        }
    }
    return PlanId("w5b.general.${digest.digest().joinToString("") { "%02x".format(it) }}")
}
