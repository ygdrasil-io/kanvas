package org.graphiks.kanvas.gpu.renderer.resources

import java.math.BigInteger
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity

/** Complete memory categories that participate in frame preflight budgeting. */
enum class GPUFrameMemoryCategory(val targetResident: Boolean) {
    CanonicalTarget(targetResident = true),
    RetainedMsaaColor(targetResident = true),
    RetainedMsaaDepthStencil(targetResident = true),
    FrameLocalMsaaColor(targetResident = false),
    FrameLocalMsaaDepthStencil(targetResident = false),
    LayerTarget(targetResident = false),
    FilterTarget(targetResident = false),
    DestinationSnapshot(targetResident = false),
    ReadbackStaging(targetResident = false),
    ReusableScratch(targetResident = false),
}

/** Resource class that determines whether texture-dimension limits apply. */
enum class GPUFrameMemoryResourceKind {
    Texture2D,
    Buffer,
}

/** One handle-free allocation fact consumed by aggregate frame budgeting. */
data class GPUFrameMemoryAllocation(
    val label: String,
    val category: GPUFrameMemoryCategory,
    val bytes: Long,
    val resourceKind: GPUFrameMemoryResourceKind,
    val extent: GPUPixelBounds?,
) {
    init {
        require(label.isNotBlank()) { "GPUFrameMemoryAllocation.label must not be blank" }
        require(bytes >= 0L) { "GPUFrameMemoryAllocation.bytes must be non-negative" }
        when (resourceKind) {
            GPUFrameMemoryResourceKind.Texture2D -> requireNotNull(extent) {
                "GPUFrameMemoryAllocation.extent is required for Texture2D allocations"
            }
            GPUFrameMemoryResourceKind.Buffer -> require(extent == null) {
                "GPUFrameMemoryAllocation.extent must be absent for Buffer allocations"
            }
        }
    }
}

/** Inputs for checked aggregate frame memory accounting. */
data class GPUFrameMemoryBudgetRequest(
    val allocations: List<GPUFrameMemoryAllocation>,
    val configuredAggregateBudgetBytes: Long,
    val deviceLimits: GPULimits,
) {
    init {
        require(configuredAggregateBudgetBytes > 0L) {
            "GPUFrameMemoryBudgetRequest.configuredAggregateBudgetBytes must be positive"
        }
    }
}

/** Checked aggregate accounting consumed by resource preflight and telemetry. */
data class GPUFrameMemoryBudgetPlan(
    val peakFrameTransientBytes: Long,
    val targetResidentBytes: Long,
    val categoryTotals: Map<GPUFrameMemoryCategory, Long>,
    val deviceLimitFacts: List<GPUCapabilityFact>,
    val configuredAggregateBudgetBytes: Long,
    val diagnostic: GPUDiagnostic?,
    val allocations: List<GPUFrameMemoryAllocation> = emptyList(),
)

/** Pure checked planner for complete per-frame memory accounting. */
object GPUFrameMemoryBudgetPlanner {
    fun plan(request: GPUFrameMemoryBudgetRequest): GPUFrameMemoryBudgetPlan {
        val exact = aggregateFacts(request.allocations)

        val diagnostic = when {
            request.allocations.any { allocation -> allocation.exceeds(request.deviceLimits) } ->
                frameMemoryDiagnostic(
                    code = "unsupported.frame_memory.device_limit_exceeded",
                    message = "Frame memory allocation exceeds maxTextureDimension2D.",
                    aggregatePeak = exact.aggregatePeak,
                    configuredAggregateBudgetBytes =
                        request.configuredAggregateBudgetBytes,
                    maxTextureDimension2D = request.deviceLimits.maxTextureDimension2D,
                )
            else -> limitIndependentDiagnostic(
                exact = exact,
                configuredAggregateBudgetBytes = request.configuredAggregateBudgetBytes,
            )
        }

        return GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = exact.peakTransient.clampedLong(),
            targetResidentBytes = exact.targetResident.clampedLong(),
            categoryTotals = exact.categoryTotals.mapValues { (_, total) -> total.clampedLong() },
            deviceLimitFacts = request.deviceLimits.capabilityFacts("frame-memory-budget"),
            configuredAggregateBudgetBytes = request.configuredAggregateBudgetBytes,
            diagnostic = diagnostic,
            allocations = request.allocations.toList(),
        )
    }

    /**
     * Authenticates allocation-derived facts that do not require an observed device limit.
     *
     * A device-limit diagnostic is accepted here only as conditional evidence. The complete
     * planner comparison authenticates it when observed limits are available.
     */
    fun hasExactLimitIndependentFacts(plan: GPUFrameMemoryBudgetPlan): Boolean {
        if (plan.configuredAggregateBudgetBytes <= 0L) {
            return false
        }
        val exact = aggregateFacts(plan.allocations)
        val exactDiagnostic = limitIndependentDiagnostic(
            exact = exact,
            configuredAggregateBudgetBytes = plan.configuredAggregateBudgetBytes,
        )
        val diagnosticMatches = when (plan.diagnostic?.code?.value) {
            "unsupported.frame_memory.device_limit_exceeded" -> true
            else -> plan.diagnostic == exactDiagnostic
        }
        return plan.peakFrameTransientBytes == exact.peakTransient.clampedLong() &&
            plan.targetResidentBytes == exact.targetResident.clampedLong() &&
            plan.categoryTotals ==
            exact.categoryTotals.mapValues { (_, total) -> total.clampedLong() } &&
            diagnosticMatches
    }
}

private fun limitIndependentDiagnostic(
    exact: GPUFrameMemoryAggregateFacts,
    configuredAggregateBudgetBytes: Long,
): GPUDiagnostic? = when {
    exact.aggregatePeak > Long.MAX_VALUE.toBigInteger() ->
        frameMemoryDiagnostic(
            code = "unsupported.frame_memory.accounting_overflow",
            message = "Frame memory accounting exceeds the signed 64-bit byte range.",
            aggregatePeak = exact.aggregatePeak,
            configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
        )
    exact.aggregatePeak > configuredAggregateBudgetBytes.toBigInteger() ->
        frameMemoryDiagnostic(
            code = "unsupported.frame_memory.aggregate_budget_exceeded",
            message = "Frame aggregate memory exceeds the configured budget.",
            aggregatePeak = exact.aggregatePeak,
            configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
        )
    else -> null
}

private data class GPUFrameMemoryAggregateFacts(
    val categoryTotals: Map<GPUFrameMemoryCategory, BigInteger>,
    val targetResident: BigInteger,
    val peakTransient: BigInteger,
) {
    val aggregatePeak: BigInteger = targetResident + peakTransient
}

private fun aggregateFacts(
    allocations: List<GPUFrameMemoryAllocation>,
): GPUFrameMemoryAggregateFacts {
    val categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
        allocations
            .asSequence()
            .filter { allocation -> allocation.category == category }
            .fold(BigInteger.ZERO) { total, allocation ->
                total + allocation.bytes.toBigInteger()
            }
    }
    return GPUFrameMemoryAggregateFacts(
        categoryTotals = categoryTotals,
        targetResident = categoryTotals
            .filterKeys(GPUFrameMemoryCategory::targetResident)
            .values
            .fold(BigInteger.ZERO, BigInteger::add),
        peakTransient = categoryTotals
            .filterKeys { category -> !category.targetResident }
            .values
            .fold(BigInteger.ZERO, BigInteger::add),
    )
}

private fun GPUFrameMemoryAllocation.exceeds(limits: GPULimits): Boolean = when (resourceKind) {
    GPUFrameMemoryResourceKind.Texture2D -> {
        val textureExtent = checkNotNull(extent)
        textureExtent.width.toLong() > limits.maxTextureDimension2D ||
            textureExtent.height.toLong() > limits.maxTextureDimension2D
    }
    GPUFrameMemoryResourceKind.Buffer -> false
}

private fun BigInteger.clampedLong(): Long = min(Long.MAX_VALUE.toBigInteger()).toLong()

private fun frameMemoryDiagnostic(
    code: String,
    message: String,
    aggregatePeak: BigInteger,
    configuredAggregateBudgetBytes: Long,
    maxTextureDimension2D: Long? = null,
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Resources,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = buildMap {
        put("aggregatePeakBytes", aggregatePeak.toString())
        put("configuredAggregateBudgetBytes", configuredAggregateBudgetBytes.toString())
        maxTextureDimension2D?.let { limit ->
            put("maxTextureDimension2D", limit.toString())
        }
    },
)
