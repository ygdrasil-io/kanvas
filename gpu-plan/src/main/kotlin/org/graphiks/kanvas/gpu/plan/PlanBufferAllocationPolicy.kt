package org.graphiks.kanvas.gpu.plan

public enum class PlanScratchBufferKind { Vertex, Index, Uniform }
public enum class PlanBufferGrowth { PowerOfTwo }

@ConsistentCopyVisibility
public data class PlanBufferAllocationPolicy private constructor(
    public val vertexFloorBytes: Long,
    public val indexFloorBytes: Long,
    public val uniformFloorBytes: Long,
    public val growth: PlanBufferGrowth,
) {
    public fun reserve(kind: PlanScratchBufferKind, usefulBytes: Long): Long? {
        if (usefulBytes <= 0L) return null
        val floor = when (kind) {
            PlanScratchBufferKind.Vertex -> vertexFloorBytes
            PlanScratchBufferKind.Index -> indexFloorBytes
            PlanScratchBufferKind.Uniform -> uniformFloorBytes
        }
        var capacity = floor
        while (capacity < usefulBytes) {
            if (capacity > Long.MAX_VALUE / 2L) return null
            capacity *= 2L
        }
        return capacity
    }

    public companion object {
        public fun of(
            vertexFloorBytes: Long,
            indexFloorBytes: Long,
            uniformFloorBytes: Long,
            growth: PlanBufferGrowth = PlanBufferGrowth.PowerOfTwo,
        ): PlanBufferAllocationPolicy {
            require(vertexFloorBytes > 0L && indexFloorBytes > 0L && uniformFloorBytes > 0L) {
                "Buffer pool floors must be positive"
            }
            return PlanBufferAllocationPolicy(vertexFloorBytes, indexFloorBytes, uniformFloorBytes, growth)
        }
    }
}
