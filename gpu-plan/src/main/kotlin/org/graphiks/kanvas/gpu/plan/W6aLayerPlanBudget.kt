package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*

/** Typed refusal: never recovered by generic unsupported-child or W5 string-based translation. */
internal class W6aResourceLimitFailure(message: String) : RuntimeException(message)

internal object W6aLayerPlanBudget {
    fun peak(resources: List<PlanResource>, passCountI32: Int, budget: PlanBudget): Long =
        RenderGraph.peak(resources, passCountI32).also { requireWithin(it, budget) }

    fun requireWithin(bytesI64: Long, budget: PlanBudget) {
        if (bytesI64 > budget.maxFrameLocalBytes) throw W6aResourceLimitFailure("Layer frame requires $bytesI64 bytes; budget is ${budget.maxFrameLocalBytes}.")
    }

    fun refusal(message: String): RenderPlanResult.ResourceLimitExceeded = RenderPlanResult.ResourceLimitExceeded(listOf(
        RenderDiagnostic(RenderDiagnosticCode("w6a.layer.resource_limit"), RenderDiagnosticDomain.RESOURCE,
            RenderDiagnosticSeverity.ERROR, message)))

    fun <T : Any> translate(result: RenderPlanResult<T>): RenderPlanResult<T> = when (result) {
        is RenderPlanResult.ResourceLimitExceeded -> refusal(result.diagnostics.joinToString { it.code.value })
        else -> result
    }
}
