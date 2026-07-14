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

/** One handle-free allocation fact consumed by aggregate frame budgeting. */
data class GPUFrameMemoryAllocation(
    val label: String,
    val category: GPUFrameMemoryCategory,
    val bytes: Long,
    val bounds: GPUPixelBounds? = null,
) {
    init {
        require(label.isNotBlank()) { "GPUFrameMemoryAllocation.label must not be blank" }
        require(bytes >= 0L) { "GPUFrameMemoryAllocation.bytes must be non-negative" }
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
)

/** Pure checked planner for complete per-frame memory accounting. */
object GPUFrameMemoryBudgetPlanner {
    fun plan(request: GPUFrameMemoryBudgetRequest): GPUFrameMemoryBudgetPlan {
        val exactCategoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
            request.allocations
                .asSequence()
                .filter { allocation -> allocation.category == category }
                .fold(BigInteger.ZERO) { total, allocation -> total + allocation.bytes.toBigInteger() }
        }
        val exactTargetResident = exactCategoryTotals
            .filterKeys(GPUFrameMemoryCategory::targetResident)
            .values
            .fold(BigInteger.ZERO, BigInteger::add)
        val exactPeakTransient = exactCategoryTotals
            .filterKeys { category -> !category.targetResident }
            .values
            .fold(BigInteger.ZERO, BigInteger::add)
        val exactAggregatePeak = exactTargetResident + exactPeakTransient

        val diagnostic = when {
            request.allocations.any { allocation -> allocation.exceeds(request.deviceLimits) } -> diagnostic(
                code = "unsupported.frame_memory.device_limit_exceeded",
                message = "Frame memory allocation exceeds maxTextureDimension2D.",
                request = request,
                aggregatePeak = exactAggregatePeak,
            )
            exactAggregatePeak > Long.MAX_VALUE.toBigInteger() -> diagnostic(
                code = "unsupported.frame_memory.accounting_overflow",
                message = "Frame memory accounting exceeds the signed 64-bit byte range.",
                request = request,
                aggregatePeak = exactAggregatePeak,
            )
            exactAggregatePeak > request.configuredAggregateBudgetBytes.toBigInteger() -> diagnostic(
                code = "unsupported.frame_memory.aggregate_budget_exceeded",
                message = "Frame aggregate memory exceeds the configured budget.",
                request = request,
                aggregatePeak = exactAggregatePeak,
            )
            else -> null
        }

        return GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = exactPeakTransient.clampedLong(),
            targetResidentBytes = exactTargetResident.clampedLong(),
            categoryTotals = exactCategoryTotals.mapValues { (_, total) -> total.clampedLong() },
            deviceLimitFacts = request.deviceLimits.capabilityFacts("frame-memory-budget"),
            configuredAggregateBudgetBytes = request.configuredAggregateBudgetBytes,
            diagnostic = diagnostic,
        )
    }
}

private fun GPUFrameMemoryAllocation.exceeds(limits: GPULimits): Boolean =
    bounds?.let { pixelBounds ->
        pixelBounds.right.toLong() > limits.maxTextureDimension2D ||
            pixelBounds.bottom.toLong() > limits.maxTextureDimension2D
    } ?: false

private fun BigInteger.clampedLong(): Long = min(Long.MAX_VALUE.toBigInteger()).toLong()

private fun diagnostic(
    code: String,
    message: String,
    request: GPUFrameMemoryBudgetRequest,
    aggregatePeak: BigInteger,
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Resources,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = mapOf(
        "aggregatePeakBytes" to aggregatePeak.toString(),
        "configuredAggregateBudgetBytes" to request.configuredAggregateBudgetBytes.toString(),
        "maxTextureDimension2D" to request.deviceLimits.maxTextureDimension2D.toString(),
    ),
)
