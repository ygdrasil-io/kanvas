package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

public data class PlanBudget(public val maxFrameLocalBytes: Long) {
    init { require(maxFrameLocalBytes > 0) { "Frame-local budget must be positive" } }
}

public sealed interface PlanMemoryBudgetResult {
    public data class WithinBudget(public val peakBytes: Long, public val readbackBytesPerRow: Long) : PlanMemoryBudgetResult
    public data class Exceeded(public val requiredBytes: Long, public val limitBytes: Long) : PlanMemoryBudgetResult
    public data class Invalid(public val code: String) : PlanMemoryBudgetResult
}

public object PlanMemoryBudget {
    public fun calculate(
        targetExtent: SizeI32,
        bytesPerPixel: Long,
        copyBytesPerRowAlignment: Int,
        budget: PlanBudget,
    ): PlanMemoryBudgetResult {
        if (targetExtent.isEmpty() || bytesPerPixel <= 0L || copyBytesPerRowAlignment <= 0) {
            return PlanMemoryBudgetResult.Invalid("invalid-input")
        }
        return try {
            val widthBytes = Math.multiplyExact(targetExtent.width.toLong(), bytesPerPixel)
            val alignedRow = aligned(widthBytes, copyBytesPerRowAlignment.toLong())
            val targetBytes = Math.multiplyExact(widthBytes, targetExtent.height.toLong())
            val stagingBytes = Math.multiplyExact(alignedRow, targetExtent.height.toLong())
            val peak = Math.addExact(targetBytes, stagingBytes)
            if (peak <= budget.maxFrameLocalBytes) {
                PlanMemoryBudgetResult.WithinBudget(peak, alignedRow)
            } else {
                PlanMemoryBudgetResult.Exceeded(peak, budget.maxFrameLocalBytes)
            }
        } catch (_: ArithmeticException) {
            PlanMemoryBudgetResult.Invalid("size-overflow")
        }
    }

    private fun aligned(value: Long, alignment: Long): Long {
        val remainder = value % alignment
        return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
    }
}
