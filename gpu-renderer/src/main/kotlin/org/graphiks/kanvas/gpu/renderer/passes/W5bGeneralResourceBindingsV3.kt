package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.resources.*

/** Rebinds only resource identities; original General native sizes, geometry and ABI remain sealed. */
internal class W5bGeneralResourceBindingsV3 private constructor(bindings: Map<String, GPUFrameResourceRef>) {
    val values = immutableMap(bindings)
    companion object {
        fun issue(graph: RenderGraph, lane: W5bGeometryLanePlanV3,
            target: GPUFrameTargetRef, staging: GPUFrameBufferRef): W5bGeneralResourceBindingsV3 {
            require(graph.verifyW5bGeometryCompilerWitness() && graph.w5bGeometryLanes().any { it === lane })
            val source = lane.sourceGraph
            require(source.verifyW4dGeneralCompilerWitness() &&
                source.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID)
            require(graph.targetExtent == source.targetExtent && graph.capabilities == source.capabilities && graph.budget == source.budget)
            require(target.value.endsWith(".target") && staging.value == target.value.removeSuffix(".target") + ".staging")
            val identity = target.value.removeSuffix(".target")
            // The merged stop slab belongs to the shared W5 source stage, not native W4 geometry.
            return W5bGeneralResourceBindingsV3(source.resources().filter { it.role != PlanResourceRole.GradientStopData }.associate { item ->
                val successorId = when (item.role) {
                    PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging -> graph.resources().single { it.role == item.role }.id
                    PlanResourceRole.VertexData -> requireNotNull(lane.drawDataResources).vertex
                    PlanResourceRole.IndexData -> requireNotNull(lane.drawDataResources).index
                    PlanResourceRole.UniformData -> requireNotNull(lane.drawDataResources).uniform
                    PlanResourceRole.DepthStencil -> requireNotNull(lane.depthStencil)
                    else -> error("General hard successor cannot rebind another native resource family")
                }
                val successor = graph.resources().single { it.id == successorId }
                require(successor.role == item.role && successor.kind == item.kind && successor.format == item.format &&
                    successor.copyExtent() == item.copyExtent() && successor.byteSize == item.byteSize &&
                    successor.sampleCountI32 == item.sampleCountI32 && successor.usages() == item.usages())
                item.id.value to when (item.role) {
                    PlanResourceRole.LogicalTarget -> target
                    PlanResourceRole.ReadbackStaging -> staging
                    PlanResourceRole.DepthStencil -> GPUFrameTextureRef("$identity.${successor.id.value}.depth-stencil")
                    PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData ->
                        GPUFrameBufferRef("$identity.${successor.id.value}")
                    else -> error("General hard successor cannot rebind another native resource family")
                }
            })
        }
    }
}
