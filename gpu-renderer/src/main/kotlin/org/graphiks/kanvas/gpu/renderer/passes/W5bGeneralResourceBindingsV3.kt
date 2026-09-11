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
            return W5bGeneralResourceBindingsV3(source.resources().associate { item ->
                val successor = graph.resources().single { it.id == item.id }
                require(successor.role == item.role && successor.kind == item.kind && successor.format == item.format &&
                    successor.copyExtent() == item.copyExtent() && successor.byteSize == item.byteSize &&
                    successor.sampleCountI32 == item.sampleCountI32 && successor.usages() == item.usages())
                item.id.value to when (item.role) {
                    PlanResourceRole.LogicalTarget -> target
                    PlanResourceRole.ReadbackStaging -> staging
                    PlanResourceRole.DepthStencil -> GPUFrameTextureRef("$identity.${item.id.value}.depth-stencil")
                    PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData ->
                        GPUFrameBufferRef("$identity.${item.id.value}")
                    else -> error("General hard successor cannot rebind another native resource family")
                }
            })
        }
    }
}

