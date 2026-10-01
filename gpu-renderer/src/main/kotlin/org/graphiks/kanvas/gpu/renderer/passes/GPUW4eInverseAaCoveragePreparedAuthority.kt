package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/**
 * W7's source-owned W4e handoff.  This intentionally does not borrow the root W4e frame
 * authority: the two logical path phases are one deferred coverage render scope.
 */
internal class GPUW4eInverseAaCoveragePreparedAuthority private constructor(
    val owner: PlanPass.AaCoverageSourcePass,
    val binding: PlanW4eInverseAaCoverageSourceBindingV1,
    val prepared: GPUPlanW4ePreparedAuthority,
) {
    val phases: List<PlanPass.PathRenderPass> = binding.passes()
    val payload: W4eNativePayloadPlan = binding.payload()
    val recipe: W4eInverseAaCoverageSourceNativeSiteRecipeV1 = binding.recipe

    fun resourceUses(refs: Map<PlanResourceId, GPUFrameResourceRef>): List<GPUFrameResourceUse> = buildList {
        val terminal = phases.last()
        add(GPUFrameResourceUse(refs.getValue(terminal.target), GPUFrameResourceRole.LayerTarget,
            GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true))
        add(GPUFrameResourceUse(refs.getValue(requireNotNull(terminal.resolveTarget)), GPUFrameResourceRole.FilterTarget,
            GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true))
        phases.first().depthStencil?.let { depth ->
            add(GPUFrameResourceUse(refs.getValue(depth), GPUFrameResourceRole.PathDepthStencil,
                GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true))
        }
        add(GPUFrameResourceUse(refs.getValue(terminal.drawDataResources.vertex), GPUFrameResourceRole.VertexData,
            GPUFrameResourceUsage.Vertex, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(terminal.drawDataResources.index), GPUFrameResourceRole.IndexData,
            GPUFrameResourceUsage.Index, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(terminal.drawDataResources.uniform), GPUFrameResourceRole.UniformData,
            GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
    }

    fun validates(render: GPUFrameStep.RenderPassStep?, refs: Map<PlanResourceId, GPUFrameResourceRef>): Boolean {
        val first = phases.first()
        return binding.recipe.binding === binding && binding.validatesNativeOperationFacts() &&
            render != null && render.w6aPassV1 === owner &&
            phases.size == 2 && phases[0].phase == PathRenderPhase.MultisampleStencilProducer &&
            phases[1].phase == PathRenderPhase.MultisampleStencilColorCover &&
            phases[0].atomicGroup != null && phases[0].atomicGroup == phases[1].atomicGroup &&
            phases[0].depthStencil != null && phases[0].depthStencil == phases[1].depthStencil &&
            phases[0].resolveTarget == null && phases[1].resolveTarget != null &&
            render.target == refs.getValue(first.target) && render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
            render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store &&
            render.resourceUses == resourceUses(refs) &&
            render.drawPackets.size == phases.size && render.drawPackets.zip(phases).all { (packet, phase) ->
                packet.role == GPUDrawPacketRole.W4ePrepared && packet.passId == phase.id.value &&
                    packet.commandIdValue == binding.commandIndexI32 &&
                    packet.w4ePreparedPath === prepared.pathFor(phase.id.value)
            } && first.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore &&
            phases[1].depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset &&
            render.depthStencilLoadStore == GPUDepthStencilLoadStorePlan.WritableStencil(GPUStencilLoadOperation.Clear, GPUStorePlan.Store, 0u)
    }

    companion object {
        fun capture(graph: RenderGraph, binding: PlanW4eInverseAaCoverageSourceBindingV1):
            GPUW4eInverseAaCoveragePreparedAuthority {
            require(graph.verifyW6aLayerCompilerWitness())
            val physical = requireNotNull(graph.physicalLayoutOrNull())
            require(physical.w4eInverseAaCoverageSourceBindings().any { it === binding } &&
                physical.nativeSiteRecipeCatalogV1().recipe(binding.recipe.owner) === binding.recipe &&
                binding.recipe.binding === binding && binding.validatesNativeOperationFacts())
            val owner = graph.passes().singleOrNull { pass ->
                pass is PlanPass.AaCoverageSourcePass && pass.binding === binding && pass.id == binding.ownerPassId
            } as? PlanPass.AaCoverageSourcePass ?: error("W7 inverse-AA binding has no final AaCoverageSource owner")
            require(binding.resources().size == 6 && binding.resources().all { row -> graph.resources().any { it === row } })
            val payload = binding.payload()
            require(payload.matchesDeclaredResources(binding.resources()) &&
                payload.vertexResourceId == binding.passes().last().drawDataResources.vertex &&
                payload.indexResourceId == binding.passes().last().drawDataResources.index &&
                payload.uniformResourceId == binding.passes().last().drawDataResources.uniform)
            return GPUW4eInverseAaCoveragePreparedAuthority(owner, binding,
                GPUPlanW4ePreparedAuthority.issueLayered(graph, binding))
        }
    }
}
