package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*

/** Typed refusal: never recovered by generic unsupported-child or W5 string-based translation. */
internal class W6aResourceLimitFailure(message: String) : RuntimeException(message)

internal object W6aLayerPlanBudget {
    fun peak(resources: List<PlanResource>, passCountI32: Int, budget: PlanBudget): Long =
        try {
            val semanticPeakI64 = peakFrameLocalBytesI64(resources.map { resource ->
                FrameResourceSpan(resource.byteSize, resource.firstPassIndex, resource.lastPassIndexExclusive)
            }, passCountI32)
            // Every declared resource maps to one frozen physical slot. A cache hit can avoid
            // an upload, but cannot make its slot or lease cheaper before completion/quarantine.
            val physicalPeakI64 = checkedPhysicalAllocationPeakI64(resources)
            maxOf(semanticPeakI64, physicalPeakI64).also { requireWithin(it, budget) }
        } catch (_: ArithmeticException) {
            throw W6aResourceLimitFailure("Layer frame resource peak overflows I64.")
        }

    private fun checkedPhysicalAllocationPeakI64(resources: List<PlanResource>): Long =
        resources.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) }

    fun requireWithin(bytesI64: Long, budget: PlanBudget) {
        if (bytesI64 > budget.maxFrameLocalBytes) throw W6aResourceLimitFailure("Layer frame requires $bytesI64 bytes; budget is ${budget.maxFrameLocalBytes}.")
    }

    fun refusal(message: String): RenderPlanResult.ResourceLimitExceeded = RenderPlanResult.ResourceLimitExceeded(listOf(
        RenderDiagnostic(RenderDiagnosticCode(W6aPlanDiagnostics.FrameBudgetExceeded), RenderDiagnosticDomain.RESOURCE,
            RenderDiagnosticSeverity.ERROR, message)))
}
