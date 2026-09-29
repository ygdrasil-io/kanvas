package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.planning.W4dGeneralPathGraphLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.math.color.ColorF32

/** Coverage-only sibling: no W5 material/template and no colour-AA continuation authority. */
internal class GPUW4dAaCoveragePreparedAuthority private constructor(
    val owner: PlanPass.FilterCoverageSourcePass,
    val binding: PlanW4dAaCoverageSourceBindingV1,
    facts: List<W4dGeneralNativePathPassFact>,
    val geometry: W4dGeneralNativeFrameResourceSeal,
    built: List<W4dGeneralPathGraphLowerer.BuiltPacket>,
) {
    val facts: List<W4dGeneralNativePathPassFact> = java.util.Collections.unmodifiableList(facts.toList())
    val built: List<W4dGeneralPathGraphLowerer.BuiltPacket> = java.util.Collections.unmodifiableList(built.toList())
    val phases: List<PlanPass.PathRenderPass> get() = binding.passes()

    fun resourceUses(refs: Map<PlanResourceId, GPUFrameResourceRef>): List<GPUFrameResourceUse> = buildList {
        val terminal = phases.last()
        add(GPUFrameResourceUse(refs.getValue(phases.first().target), GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true))
        add(GPUFrameResourceUse(refs.getValue(owner.output), GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true))
        phases.first().depthStencil?.let { add(GPUFrameResourceUse(refs.getValue(it), GPUFrameResourceRole.PathDepthStencil, GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true)) }
        add(GPUFrameResourceUse(refs.getValue(terminal.drawDataResources.vertex), GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(terminal.drawDataResources.index), GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(terminal.drawDataResources.uniform), GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
    }

    fun validates(render: GPUFrameStep.RenderPassStep?, refs: Map<PlanResourceId, GPUFrameResourceRef>): Boolean =
        render != null && render.w6aPassV1 === owner && render.drawPackets == built.map { it.packet } &&
            render.target == refs.getValue(phases.first().target) && render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
            render.sampleContinuation == null && render.w4eSceneContinuation == null &&
            render.depthStencilLoadStore == when (phases.first().depthStencilLoadStore) {
                PlanDepthStencilLoadStore.ClearZeroStore -> GPUDepthStencilLoadStorePlan.WritableStencil(
                    GPUStencilLoadOperation.Clear, GPUStorePlan.Store, 0u)
                null -> null
                else -> error("AA coverage opens a native stencil scope only with its producer")
            } &&
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
            val facts = W4dGeneralNativeMaterializationSnapshot.capturePathFacts(binding.passes(), rows.associateBy { it.resourceId },
                bounds, binding.passes().associate { it.id.value to ColorF32.of(1f, 1f, 1f, 1f) })
            val geometry = requireNotNull(W4dGeneralNativeFrameResourceSeal.from(graph.capabilities, null, 0L,
                binding.passes(), rows, facts))
            val built = binding.passes().mapIndexed { index, phase -> W4dGeneralPathGraphLowerer().packet(phase, index, bounds,
                GPUColorFormat.RGBA8UnormSrgb, graph, aaCoverage = binding) }
            require(facts.all { it.uniformPayloadBytes.size == 32 })
            return GPUW4dAaCoveragePreparedAuthority(owner, binding, facts, geometry, built)
        }
    }
}
