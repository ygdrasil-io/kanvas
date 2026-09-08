package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler

/** Immutable W4e trust boundary: lowering consumes only a compiler-sealed graph snapshot. */
internal class GPUPlanW4ePreparedAuthority private constructor(
    private val planId: String,
    private val capabilityId: String,
    private val resourceFacts: List<String>,
    private val passFacts: List<String>,
) {
    fun revalidates(graph: RenderGraph): Boolean =
        graph.verifyW4eCompilerWitness() &&
            graph.id.value == planId &&
            graph.capabilityId == capabilityId &&
            graph.resources().map(PlanResource::id).map { it.value } == resourceFacts &&
            graph.passes().map(PlanPass::id).map { it.value } == passFacts

    companion object {
        fun issueAfterFullGraphValidation(graph: RenderGraph): GPUPlanW4ePreparedAuthority {
            require(graph.capabilityId in setOf(
                W4eClipPlanCompiler.HARD_CAPABILITY_ID,
                W4eClipPlanCompiler.AA_CAPABILITY_ID,
            ) && graph.verifyW4eCompilerWitness()) {
                "W4e prepared authority requires the compiler-authenticated graph"
            }
            return GPUPlanW4ePreparedAuthority(
                graph.id.value,
                graph.capabilityId,
                graph.resources().map(PlanResource::id).map { it.value },
                graph.passes().map(PlanPass::id).map { it.value },
            )
        }
    }
}
