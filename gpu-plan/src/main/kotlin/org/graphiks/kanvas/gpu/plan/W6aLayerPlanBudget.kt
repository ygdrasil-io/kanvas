package org.graphiks.kanvas.gpu.plan

internal object W6aLayerPlanBudget {
    fun peak(resources: List<PlanResource>, passCountI32: Int, budget: PlanBudget): Long =
        RenderGraph.peak(resources, passCountI32).also { require(it <= budget.maxFrameLocalBytes) { "w6a.layer.resource_limit" } }
}
