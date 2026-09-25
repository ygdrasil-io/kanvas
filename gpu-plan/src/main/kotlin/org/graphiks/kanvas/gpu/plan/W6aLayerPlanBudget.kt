package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*

/** Typed refusal: never recovered by generic unsupported-child or W5 string-based translation. */
internal class W6aResourceLimitFailure(message: String) : RuntimeException(message)

internal object W6aLayerPlanBudget {
    fun peak(
        resources: List<PlanResource>,
        passes: List<PlanPass>,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): Long =
        try {
            val programLeases = W6dProgramLeasePlannerV1.freeze(resources, passes, capabilities)
            val semanticPeakI64 = peakFrameLocalBytesI64(resources.map { resource ->
                FrameResourceSpan(resource.byteSize, resource.firstPassIndex, resource.lastPassIndexExclusive)
            } + programLeases.map { lease ->
                FrameResourceSpan(lease.reservedBytesI64, lease.firstPassIndexI32, lease.lastPassIndexExclusiveI32)
            }, passes.size)
            // Every declared resource maps to one frozen physical slot. A cache hit can avoid
            // an upload, but cannot make its slot or lease cheaper before completion/quarantine.
            val physicalResourcePeakI64 = checkedPhysicalAllocationPeakI64(resources)
            // Programs have opaque driver allocations, so their separately frozen logical leases
            // are charged conservatively before native preparation rather than guessed afterward.
            val logicalProgramLeasePeakI64 = checkedProgramLeasePeakI64(programLeases)
            val resourceAndLeasePeakI64 = Math.addExact(physicalResourcePeakI64, logicalProgramLeasePeakI64)
            maxOf(semanticPeakI64, resourceAndLeasePeakI64).also { requireWithin(it, budget) }
        } catch (_: ArithmeticException) {
            throw W6aResourceLimitFailure("Layer frame resource peak overflows I64.")
        }

    private fun checkedPhysicalAllocationPeakI64(resources: List<PlanResource>): Long =
        resources.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) }

    private fun checkedProgramLeasePeakI64(leases: List<W6dProgramLeaseV1>): Long =
        leases.fold(0L) { total, lease -> Math.addExact(total, lease.reservedBytesI64) }

    fun requireWithin(bytesI64: Long, budget: PlanBudget) {
        if (bytesI64 > budget.maxFrameLocalBytes) throw W6aResourceLimitFailure("Layer frame requires $bytesI64 bytes; budget is ${budget.maxFrameLocalBytes}.")
    }

    fun refusal(message: String): RenderPlanResult.ResourceLimitExceeded = RenderPlanResult.ResourceLimitExceeded(listOf(
        RenderDiagnostic(RenderDiagnosticCode(W6aPlanDiagnostics.FrameBudgetExceeded), RenderDiagnosticDomain.RESOURCE,
            RenderDiagnosticSeverity.ERROR, message)))
}
