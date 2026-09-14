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
    val layout = generalColorInputsV4(source.passes(), source.resources(), blendsByCommandI32)
    val colors = layout.colors
    val graph = W5bDestinationGraphSealer.construct(generalFinalBlendPlanId(source.id, colors),
        W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, source.targetExtent, source.capabilities, source.budget,
        colors, source.materialPlanTableOrNull(), layout.targetBytesI64, layout.stagingBytesI64, layout.rowBytesI64,
        layout.geometryResources, layout.data, depthStencilByCommandI32 = layout.depthByCommand)
    return RenderGraph.issueW5bGeometry(graph, listOf(GeometryLaneConstruction(source,
        colors.map { it.commandIndex }, layout.data, layout.depth)))
}

/** Same General colour-envelope recipe, retaining its distinct geometry source until binding. */
internal fun describeW5bGeneralPathSourcesV4(source: SourceDeferredRenderConstructionV4,
    blendsByCommandI32: Map<Int, BlendPlan>): SourceConstructionResultV4<SourceDeferredRenderConstructionV4> {
    require(source.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID &&
        source.topology == DeferredLaneTopologyV4.Ordinary)
    val layout = generalColorInputsV4(source.passes(), source.resources(), blendsByCommandI32)
    val envelope = W5bDestinationGraphSealer.describeSources(W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID,
        source.targetExtent, source.capabilities, source.budget, layout.colors, layout.targetBytesI64,
        layout.stagingBytesI64, layout.rowBytesI64, layout.geometryResources, layout.data,
        depthStencilByCommandI32 = layout.depthByCommand)
    return SourceDeferredRenderConstructionV4.of(generalFinalBlendPlanId(source.id, layout.colors),
        W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, source.targetExtent, envelope.format,
        source.capabilities, source.budget, layout.colors.size, envelope.resources, envelope.passes,
        envelope.dependencies, source.sourceTable(), DeferredLaneTopologyV4.GeneralGeometryAndColor,
        source, layout.colors.map { it.commandIndex }, layout.colors.associate { it.commandIndex to layout.data },
        layout.depthByCommand)
}

private class GeneralColorInputsV4(colors: List<GeneralPathDraw>, geometryResources: List<PlanResource>,
    val data: PlanDrawDataResources, val depth: PlanResourceId?, val targetBytesI64: Long,
    val stagingBytesI64: Long, val rowBytesI64: Long) {
    val colors = immutableList(colors)
    val geometryResources = immutableList(geometryResources)
    val depthByCommand = java.util.Collections.unmodifiableMap(colors.filter { it.strategy == PathFillStrategy.StencilCover }
        .associate { it.commandIndex to requireNotNull(depth) })
}

private fun generalColorInputsV4(passes: List<PlanPass>, resources: List<PlanResource>,
    blendsByCommandI32: Map<Int, BlendPlan>): GeneralColorInputsV4 {
    val pathPasses = passes.filterIsInstance<PlanPass.PathRenderPass>()
    require(pathPasses.all { it.draw is GeneralPathDraw && it.draw.sample == SamplePlan.SingleSample &&
        it.phase in setOf(PathRenderPhase.SingleSampleDirectColor, PathRenderPhase.SingleSampleStencilProducer,
            PathRenderPhase.SingleSampleStencilColorCover) })
    val colors = pathPasses.filter { it.phase != PathRenderPhase.SingleSampleStencilProducer }
        .map { (it.draw as GeneralPathDraw).withBlend(requireNotNull(blendsByCommandI32[it.draw.commandIndex])) }
    require(colors.map { it.commandIndex }.toSet() == blendsByCommandI32.keys)
    val geometryResources = resources.filter { it.role in setOf(
        PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil) }
    val data = pathPasses.first().drawDataResources
    val depth = geometryResources.singleOrNull { it.role == PlanResourceRole.DepthStencil }?.id
    val readback = passes.last() as PlanPass.ReadbackPass
    return GeneralColorInputsV4(colors, geometryResources, data, depth,
        resources.single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
        resources.single { it.role == PlanResourceRole.ReadbackStaging }.byteSize, readback.bytesPerRow)
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
