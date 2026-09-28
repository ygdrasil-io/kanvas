package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.planning.W4dGeneralPathGraphLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Source envelope: owns one authenticated occurrence; W6 alone owns allocations and budget. */
internal class GPUW4dAaSourcePreparedAuthority private constructor(
    val binding: PlanW4dAaSourceBindingV1,
    facts: List<W4dGeneralNativePathPassFact>,
    val geometry: W4dGeneralNativeFrameResourceSeal,
    val continuation: GPUW4dPathSampleContinuationAuthority,
    packets: List<W4dGeneralPathGraphLowerer.BuiltPacket>,
) {
    val facts: List<W4dGeneralNativePathPassFact> = java.util.Collections.unmodifiableList(facts.toList())
    val packets: List<W4dGeneralPathGraphLowerer.BuiltPacket> = java.util.Collections.unmodifiableList(packets.toList())
    fun resourceUses(pass: PlanPass.PathRenderPass, refs: Map<PlanResourceId, GPUFrameResourceRef>): List<GPUFrameResourceUse> = buildList {
        add(GPUFrameResourceUse(refs.getValue(pass.target), GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true))
        pass.resolveTarget?.let { add(GPUFrameResourceUse(refs.getValue(it), GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true)) }
        pass.depthStencil?.let { add(GPUFrameResourceUse(refs.getValue(it), GPUFrameResourceRole.PathDepthStencil, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true)) }
        add(GPUFrameResourceUse(refs.getValue(pass.drawDataResources.vertex), GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(pass.drawDataResources.index), GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(pass.drawDataResources.uniform), GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
    }

    fun validates(renders: List<GPUFrameStep.RenderPassStep>, refs: Map<PlanResourceId, GPUFrameResourceRef>): Boolean =
        continuation.revalidates(binding.passes()) && renders.size == packets.size &&
            renders.zip(binding.passes()).withIndex().all { (index, pair) ->
                val (render, pass) = pair
                render.w6aPassV1 === pass && render.drawPackets.singleOrNull() === packets[index].packet &&
                    render.target == refs.getValue(pass.target) && render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                    render.sampleContinuation == null && render.w4eSceneContinuation == null &&
                    (pass.depthStencilLoadStore == null) == (render.depthStencilLoadStore == null) &&
                    render.loadStore.loadOp == (if (pass.load == AttachmentLoadPlan.ClearTransparent) "clear" else "load") &&
                    render.loadStore.storePlan == GPUStorePlan.Store && render.resourceUses == resourceUses(pass, refs)
            }

    companion object {
        fun capture(graph: RenderGraph, binding: PlanW4dAaSourceBindingV1): GPUW4dAaSourcePreparedAuthority {
            require(graph.verifyW6aLayerCompilerWitness())
            val physical = requireNotNull(graph.physicalLayoutOrNull())
            require(physical.w4dAaSourceBindings().any { it === binding } &&
                physical.nativeSiteRecipeCatalogV1().recipe(binding.recipe.owner) === binding.recipe)
            require(binding.passes().all { phase -> graph.passes().any { it === phase } })
            require(binding.resources().all { row -> graph.resources().any { it === row } })
            val extent = binding.copyExtentI32()
            val bounds = GPUPixelBounds(0, 0, extent.width, extent.height)
            val resources = binding.resources().map { row ->
                val size = row.copyExtent()
                W4dGeneralNativeResourceFact(row.id.value, row.role, row.kind, row.format,
                    size?.width, size?.height, row.byteSize, row.usages(), row.lifetime,
                    row.firstPassIndex, row.lastPassIndexExclusive, row.sampleCountI32)
            }
            val facts = W4dGeneralNativeMaterializationSnapshot.capturePathFacts(binding.passes(),
                resources.associateBy { it.resourceId }, bounds, binding.passes().associate { pass ->
                    pass.id.value to requireNotNull(org.graphiks.kanvas.gpu.renderer.planning.W5aMaterialPlanLowerer()
                        .lower(requireNotNull(graph.materialPlanTableOrNull()), pass.draw.materialAuthority.materialPlanRef()))
                })
            val geometry = requireNotNull(W4dGeneralNativeFrameResourceSeal.from(graph.capabilities, null, 0L,
                binding.passes(), resources, facts))
            require(facts.all { it.uniformPayloadBytes.size == 32 })
            return GPUW4dAaSourcePreparedAuthority(binding, facts, geometry,
                GPUW4dPathSampleContinuationAuthority.issueFromValidated(binding.passes()),
                W4dGeneralPathGraphLowerer().preparePhases(binding.passes(), bounds, graph))
        }
    }
}
