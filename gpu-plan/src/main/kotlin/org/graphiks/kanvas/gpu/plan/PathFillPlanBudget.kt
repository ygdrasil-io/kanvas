package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.SizeI32

/** Byte-exact physical footprint for a bounded W4c path-fill frame. */
public data class PathFillMemoryFootprint(
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
    public val depthStencilBytes: Long,
    public val peakBytes: Long,
)

/** Result of computing a W4c plan footprint before any native allocation. */
public sealed interface PathFillPlanBudgetResult {
    public data class WithinBudget(public val footprint: PathFillMemoryFootprint) : PathFillPlanBudgetResult
    public data class Exceeded(public val requiredBytes: Long, public val limitBytes: Long) : PathFillPlanBudgetResult
    public data class Invalid(public val code: String) : PathFillPlanBudgetResult
}

/** Checked arithmetic and pool reservations for W4c path-fill resources. */
public object PathFillPlanBudget {
    public fun calculate(
        targetExtent: SizeI32,
        geometriesF32: Collection<PathFillGeometryF32>,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): PathFillPlanBudgetResult {
        if (targetExtent.isEmpty() || geometriesF32.isEmpty()) {
            return PathFillPlanBudgetResult.Invalid(INVALID_INPUT)
        }
        return try {
            // A stencil fill has two executable phases.  Its producer carries a geometry-only
            // uniform and its cover carries the material-bearing color uniform.
            val uniformPayloadCount = geometriesF32.sumOf { geometry ->
                if (geometry.copyStencilEdgeFanF32OrNull() != null) 2L else 1L
            }
            val targetPixelCount = Math.multiplyExact(targetExtent.width.toLong(), targetExtent.height.toLong())
            val targetBytes = Math.multiplyExact(targetPixelCount, PIXEL_BYTES)
            val widthBytes = Math.multiplyExact(targetExtent.width.toLong(), PIXEL_BYTES)
            val readbackBytesPerRow = alignUp(widthBytes, capabilities.copyBytesPerRowAlignment.toLong())
            val readbackBytes = Math.multiplyExact(readbackBytesPerRow, targetExtent.height.toLong())
            if (readbackBytes > Int.MAX_VALUE.toLong()) {
                return PathFillPlanBudgetResult.Invalid(READBACK_HOST_SIZE_OVERFLOW)
            }

            val vertexCount = geometriesF32.fold(0L) { total, geometry ->
                Math.addExact(total, geometry.vertexCostI64)
            }
            val indexCount = geometriesF32.fold(0L) { total, geometry ->
                Math.addExact(total, geometry.indexCostI64)
            }
            val vertexUsefulBytes = Math.multiplyExact(vertexCount, VERTEX_BYTES)
            val indexUsefulBytes = Math.multiplyExact(indexCount, INDEX_BYTES)
            val uniformStrideBytes = alignUp(UNIFORM_BYTES, capabilities.minUniformBufferOffsetAlignment.toLong())
            val uniformUsefulBytes = Math.multiplyExact(uniformPayloadCount, UNIFORM_BYTES)
            val uniformReservedBytes = Math.multiplyExact(uniformPayloadCount, uniformStrideBytes)
            val lastDynamicUniformOffsetBytes = Math.multiplyExact(uniformPayloadCount - 1L, uniformStrideBytes)
            if (lastDynamicUniformOffsetBytes > UInt.MAX_VALUE.toLong()) {
                return PathFillPlanBudgetResult.Invalid(UNIFORM_DYNAMIC_OFFSET_OVERFLOW)
            }
            if (uniformReservedBytes > Int.MAX_VALUE.toLong()) {
                return PathFillPlanBudgetResult.Invalid(UNIFORM_HOST_SIZE_OVERFLOW)
            }

            val policy = capabilities.bufferAllocationPolicy
            val vertexCapacityBytes = policy.reserve(PlanScratchBufferKind.Vertex, vertexUsefulBytes)
                ?: return PathFillPlanBudgetResult.Invalid(POOL_CAPACITY_OVERFLOW)
            val indexCapacityBytes = policy.reserve(PlanScratchBufferKind.Index, indexUsefulBytes)
                ?: return PathFillPlanBudgetResult.Invalid(POOL_CAPACITY_OVERFLOW)
            val uniformCapacityBytes = policy.reserve(PlanScratchBufferKind.Uniform, uniformReservedBytes)
                ?: return PathFillPlanBudgetResult.Invalid(POOL_CAPACITY_OVERFLOW)
            if (vertexCapacityBytes > Int.MAX_VALUE.toLong() ||
                indexCapacityBytes > Int.MAX_VALUE.toLong() ||
                uniformCapacityBytes > Int.MAX_VALUE.toLong()
            ) {
                return PathFillPlanBudgetResult.Invalid(POOL_HOST_SIZE_OVERFLOW)
            }

            val needsDepthStencil = geometriesF32.any { it.copyStencilEdgeFanF32OrNull() != null }
            val depthStencilBytes = if (needsDepthStencil) {
                Math.multiplyExact(targetPixelCount, DEPTH_STENCIL_BYTES_PER_PIXEL)
            } else {
                0L
            }
            val peakBytes = listOf(
                targetBytes,
                readbackBytes,
                vertexCapacityBytes,
                indexCapacityBytes,
                uniformCapacityBytes,
                depthStencilBytes,
            ).fold(0L, Math::addExact)
            val footprint = PathFillMemoryFootprint(
                targetBytes = targetBytes,
                readbackBytesPerRow = readbackBytesPerRow,
                readbackBytes = readbackBytes,
                vertexUsefulBytes = vertexUsefulBytes,
                indexUsefulBytes = indexUsefulBytes,
                uniformStrideBytes = uniformStrideBytes,
                uniformUsefulBytes = uniformUsefulBytes,
                vertexCapacityBytes = vertexCapacityBytes,
                indexCapacityBytes = indexCapacityBytes,
                uniformCapacityBytes = uniformCapacityBytes,
                depthStencilBytes = depthStencilBytes,
                peakBytes = peakBytes,
            )
            if (peakBytes <= budget.maxFrameLocalBytes) {
                PathFillPlanBudgetResult.WithinBudget(footprint)
            } else {
                PathFillPlanBudgetResult.Exceeded(peakBytes, budget.maxFrameLocalBytes)
            }
        } catch (_: ArithmeticException) {
            PathFillPlanBudgetResult.Invalid(SIZE_OVERFLOW)
        }
    }

    private fun alignUp(value: Long, alignment: Long): Long {
        require(alignment > 0L)
        val remainder = value % alignment
        return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
    }

    private const val PIXEL_BYTES: Long = 4L
    private const val DEPTH_STENCIL_BYTES_PER_PIXEL: Long = 4L
    private const val VERTEX_BYTES: Long = 8L
    private const val INDEX_BYTES: Long = 4L
    private const val UNIFORM_BYTES: Long = 32L
    private const val INVALID_INPUT: String = "invalid-input"
    private const val SIZE_OVERFLOW: String = "size-overflow"
    private const val READBACK_HOST_SIZE_OVERFLOW: String = "readback-host-size-overflow"
    private const val POOL_CAPACITY_OVERFLOW: String = "pool-capacity-overflow"
    private const val POOL_HOST_SIZE_OVERFLOW: String = "pool-host-size-overflow"
    private const val UNIFORM_HOST_SIZE_OVERFLOW: String = "uniform-host-size-overflow"
    private const val UNIFORM_DYNAMIC_OFFSET_OVERFLOW: String = "uniform-dynamic-offset-overflow"
}
