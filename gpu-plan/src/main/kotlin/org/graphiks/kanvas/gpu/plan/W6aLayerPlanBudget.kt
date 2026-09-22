package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*

/** Typed refusal: never recovered by generic unsupported-child or W5 string-based translation. */
internal class W6aResourceLimitFailure(message: String) : RuntimeException(message)

internal object W6aLayerPlanBudget {
    fun peak(resources: List<PlanResource>, passCountI32: Int, budget: PlanBudget): Long =
        try {
            val logicalPeakI64 = RenderGraph.peak(resources, passCountI32)
            // A cache hit may avoid a native upload but cannot make admission cheaper: every
            // declared physical slot remains reserved until completion or quarantine.
            val physicalPeakI64 = resources.fold(0L) { total, resource ->
                Math.addExact(total, resource.byteSize)
            }
            maxOf(logicalPeakI64, physicalPeakI64).also { requireWithin(it, budget) }
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
