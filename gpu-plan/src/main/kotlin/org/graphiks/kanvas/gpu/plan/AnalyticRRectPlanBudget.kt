package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

public data class AnalyticRRectMemoryFootprint(
    public val targetBytes: Long,
    public val readbackBytesPerRow: Long,
    public val readbackBytes: Long,
    public val vertexUsefulBytes: Long,
    public val indexUsefulBytes: Long,
    public val uniformStrideBytes: Long,
    public val uniformUsefulBytes: Long,
    public val vertexCapacityBytes: Long,
    public val indexCapacityBytes: Long,
    public val uniformCapacityBytes: Long,
    public val peakBytes: Long,
)

public sealed interface AnalyticRRectPlanBudgetResult {
    public data class WithinBudget(public val footprint: AnalyticRRectMemoryFootprint) : AnalyticRRectPlanBudgetResult
    public data class Exceeded(public val requiredBytes: Long, public val limitBytes: Long) : AnalyticRRectPlanBudgetResult
    public data class Invalid(public val code: String) : AnalyticRRectPlanBudgetResult
}

public object AnalyticRRectPlanBudget {
    public fun calculate(
        targetExtent: SizeI32,
        drawCount: Int,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): AnalyticRRectPlanBudgetResult {
        if (targetExtent.isEmpty() || drawCount <= 0) return AnalyticRRectPlanBudgetResult.Invalid(INVALID_INPUT)
        return try {
            val targetBytes = Math.multiplyExact(
                Math.multiplyExact(targetExtent.width.toLong(), targetExtent.height.toLong()),
                PIXEL_BYTES,
            )
            val widthBytes = Math.multiplyExact(targetExtent.width.toLong(), PIXEL_BYTES)
            val readbackBytesPerRow = alignUp(widthBytes, capabilities.copyBytesPerRowAlignment.toLong())
            val readbackBytes = Math.multiplyExact(readbackBytesPerRow, targetExtent.height.toLong())
            val vertexUsefulBytes = Math.multiplyExact(drawCount.toLong(), VERTEX_BYTES_PER_DRAW)
            val indexUsefulBytes = Math.multiplyExact(drawCount.toLong(), INDEX_BYTES_PER_DRAW)
            val uniformStrideBytes = alignUp(UNIFORM_BYTES_PER_DRAW, capabilities.minUniformBufferOffsetAlignment.toLong())
            val uniformUsefulBytes = Math.multiplyExact(drawCount.toLong(), uniformStrideBytes)
            val policy = capabilities.bufferAllocationPolicy
            val vertexCapacityBytes = policy.reserve(PlanScratchBufferKind.Vertex, vertexUsefulBytes)
                ?: return AnalyticRRectPlanBudgetResult.Invalid(POOL_CAPACITY_OVERFLOW)
            val indexCapacityBytes = policy.reserve(PlanScratchBufferKind.Index, indexUsefulBytes)
                ?: return AnalyticRRectPlanBudgetResult.Invalid(POOL_CAPACITY_OVERFLOW)
            val uniformCapacityBytes = policy.reserve(PlanScratchBufferKind.Uniform, uniformUsefulBytes)
                ?: return AnalyticRRectPlanBudgetResult.Invalid(POOL_CAPACITY_OVERFLOW)
            val peakBytes = listOf(targetBytes, readbackBytes, vertexCapacityBytes, indexCapacityBytes, uniformCapacityBytes)
                .fold(0L, Math::addExact)
            val footprint = AnalyticRRectMemoryFootprint(
                targetBytes,
                readbackBytesPerRow,
                readbackBytes,
                vertexUsefulBytes,
                indexUsefulBytes,
                uniformStrideBytes,
                uniformUsefulBytes,
                vertexCapacityBytes,
                indexCapacityBytes,
                uniformCapacityBytes,
                peakBytes,
            )
            if (peakBytes <= budget.maxFrameLocalBytes) {
                AnalyticRRectPlanBudgetResult.WithinBudget(footprint)
            } else {
                AnalyticRRectPlanBudgetResult.Exceeded(peakBytes, budget.maxFrameLocalBytes)
            }
        } catch (_: ArithmeticException) {
            AnalyticRRectPlanBudgetResult.Invalid(SIZE_OVERFLOW)
        }
    }

    private fun alignUp(value: Long, alignment: Long): Long {
        val remainder = value % alignment
        return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
    }

    private const val PIXEL_BYTES: Long = 4L
    private const val VERTEX_BYTES_PER_DRAW: Long = 32L
    private const val INDEX_BYTES_PER_DRAW: Long = 24L
    private const val UNIFORM_BYTES_PER_DRAW: Long = 80L
    private const val INVALID_INPUT: String = "invalid-input"
    private const val SIZE_OVERFLOW: String = "size-overflow"
    private const val POOL_CAPACITY_OVERFLOW: String = "pool-capacity-overflow"
}
