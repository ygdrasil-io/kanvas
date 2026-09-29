package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.planning.W4dGeneralPathGraphLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.math.color.ColorF32

/** Coverage-only sibling: no W5 material/template and no colour-AA continuation authority. */
internal class GPUW4dAaCoveragePreparedAuthority private constructor(
    val owner: PlanPass.FilterCoverageSourcePass,
    val binding: PlanW4dAaCoverageSourceBindingV1,
    val fact: W4dGeneralNativePathPassFact,
    val geometry: W4dGeneralNativeFrameResourceSeal,
    val built: W4dGeneralPathGraphLowerer.BuiltPacket,
) {
    val phase: PlanPass.PathRenderPass get() = binding.passes().single()

    fun resourceUses(refs: Map<PlanResourceId, GPUFrameResourceRef>): List<GPUFrameResourceUse> = listOf(
        GPUFrameResourceUse(refs.getValue(phase.target), GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true),
        GPUFrameResourceUse(refs.getValue(owner.output), GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true),
        GPUFrameResourceUse(refs.getValue(phase.drawDataResources.vertex), GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, GPUFrameResourceLifetime.FrameLocal, false),
        GPUFrameResourceUse(refs.getValue(phase.drawDataResources.index), GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, GPUFrameResourceLifetime.FrameLocal, false),
        GPUFrameResourceUse(refs.getValue(phase.drawDataResources.uniform), GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false),
    )

    fun validates(render: GPUFrameStep.RenderPassStep?, refs: Map<PlanResourceId, GPUFrameResourceRef>): Boolean =
        render != null && render.w6aPassV1 === owner && render.drawPackets.singleOrNull() === built.packet &&
            render.target == refs.getValue(phase.target) && render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
            render.sampleContinuation == null && render.w4eSceneContinuation == null && render.depthStencilLoadStore == null &&
            render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store &&
            render.resourceUses == resourceUses(refs)

    companion object {
        fun capture(graph: RenderGraph, binding: PlanW4dAaCoverageSourceBindingV1): GPUW4dAaCoveragePreparedAuthority {
            require(graph.verifyW6aLayerCompilerWitness())
            val physical = requireNotNull(graph.physicalLayoutOrNull())
            require(physical.w4dAaCoverageSourceBindings().any { it === binding } &&
                physical.nativeSiteRecipeCatalogV1().recipe(binding.recipe.owner) === binding.recipe)
            val owner = graph.passes().filterIsInstance<PlanPass.FilterCoverageSourcePass>().single {
                it.id == binding.ownerPassId && it.aaCoverageBinding === binding
            }
            require(binding.resources().all { row -> graph.resources().any { it === row } })
            val extent = binding.copyExtentI32()
            val bounds = GPUPixelBounds(0, 0, extent.width, extent.height)
            val rows = binding.resources().map { row ->
                val size = row.copyExtent()
                W4dGeneralNativeResourceFact(row.id.value, row.role, row.kind, row.format, size?.width, size?.height,
                    row.byteSize, row.usages(), row.lifetime, row.firstPassIndex, row.lastPassIndexExclusive, row.sampleCountI32)
            }
            val phase = binding.passes().single()
            val facts = W4dGeneralNativeMaterializationSnapshot.capturePathFacts(binding.passes(), rows.associateBy { it.resourceId },
                bounds, mapOf(phase.id.value to ColorF32.of(1f, 1f, 1f, 1f)))
            val geometry = requireNotNull(W4dGeneralNativeFrameResourceSeal.from(graph.capabilities, null, 0L,
                binding.passes(), rows, facts))
            val built = W4dGeneralPathGraphLowerer().packet(phase, 0, bounds, GPUColorFormat.RGBA8UnormSrgb, graph,
                aaCoverage = binding)
            require(facts.single().uniformPayloadBytes.size == 32)
            return GPUW4dAaCoveragePreparedAuthority(owner, binding, facts.single(), geometry, built)
        }
    }
}
