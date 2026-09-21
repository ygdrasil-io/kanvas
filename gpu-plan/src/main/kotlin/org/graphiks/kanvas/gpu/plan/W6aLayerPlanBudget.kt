package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*

/** Typed refusal: never recovered by generic unsupported-child or W5 string-based translation. */
internal class W6aResourceLimitFailure(message: String) : RuntimeException(message)

internal object W6aLayerPlanBudget {
    fun peak(resources: List<PlanResource>, passCountI32: Int, budget: PlanBudget): Long =
        try {
            RenderGraph.peak(resources, passCountI32).also { requireWithin(it, budget) }
        } catch (_: ArithmeticException) {
            throw W6aResourceLimitFailure("Layer frame resource peak overflows I64.")
        }

    fun requireWithin(bytesI64: Long, budget: PlanBudget) {
        if (bytesI64 > budget.maxFrameLocalBytes) throw W6aResourceLimitFailure("Layer frame requires $bytesI64 bytes; budget is ${budget.maxFrameLocalBytes}.")
    }

    fun refusal(message: String): RenderPlanResult.ResourceLimitExceeded = RenderPlanResult.ResourceLimitExceeded(listOf(
        RenderDiagnostic(RenderDiagnosticCode(W6aPlanDiagnostics.FrameBudgetExceeded), RenderDiagnosticDomain.RESOURCE,
            RenderDiagnosticSeverity.ERROR, message)))
}
