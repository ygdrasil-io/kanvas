package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.resources.GPUCorePrimitiveFramePoolCapacities
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillLimitsI32

/** Immutable planned path-fill facts for one W4c visual draw. */
internal class W4cSessionScratchDrawV1(
    val commandId: Int,
    val strategy: PathFillStrategy,
    geometryF32: PathFillGeometryF32,
    scissorBounds: GPUPixelBounds,
    val vertexOffsetBytes: Long,
    val vertexRangeBytes: Long,
    val indexOffsetBytes: Long,
    val indexRangeBytes: Long,
    val uniformSlotIndex: Int,
    val atomicGroupId: String?,
) {
    private val geometrySnapshotF32: PathFillGeometryF32 = geometryF32
    private val scissorBoundsSnapshot: GPUPixelBounds = scissorBounds.copy()
    val fillRule: FillRule = geometrySnapshotF32.fillRule

    fun copyGeometryF32(): PathFillGeometryF32 = geometrySnapshotF32

    fun copyScissorBounds(): GPUPixelBounds = scissorBoundsSnapshot.copy()

    init {
        val isDirect = geometrySnapshotF32.copyDirectTriangleF32OrNull() != null
        val isStencil = geometrySnapshotF32.copyStencilEdgeFanF32OrNull() != null
        val expectedVertexBytes = Math.multiplyExact(geometrySnapshotF32.vertexCostI64, VERTEX_BYTES)
        val expectedIndexBytes = Math.multiplyExact(geometrySnapshotF32.indexCostI64, INDEX_BYTES)
        require(commandId >= 0 && !scissorBoundsSnapshot.isEmpty) {
            "W4c scratch draw requires a non-negative command and non-empty scissor"
        }
        require(vertexOffsetBytes >= 0L && indexOffsetBytes >= 0L && uniformSlotIndex >= 0) {
            "W4c scratch draw offsets and uniform slot must be non-negative"
        }
        require(vertexRangeBytes == expectedVertexBytes && indexRangeBytes == expectedIndexBytes) {
            "W4c scratch draw ranges must retain exact immutable geometry costs"
        }
        require(
            when (strategy) {
                PathFillStrategy.DirectTriangle ->
                    isDirect && !isStencil && fillRule == FillRule.WINDING && atomicGroupId == null
                PathFillStrategy.StencilCover ->
                    !isDirect && isStencil && fillRule in setOf(FillRule.WINDING, FillRule.EVEN_ODD) &&
                        !atomicGroupId.isNullOrBlank()
            },
        ) { "W4c scratch draw strategy must retain its exact immutable geometry authority" }
        require(
            strategy != PathFillStrategy.StencilCover ||
                fillRule != FillRule.WINDING ||
                geometrySnapshotF32.emittedNonZeroClosedEdgeCountI32 <= UByte.MAX_VALUE.toInt(),
        ) { "W4c winding stencil authority must fit the sealed stencil8 range" }
        require(geometrySnapshotF32.emittedNonZeroClosedEdgeCountI32 <=
            PathFillLimitsI32().maxAttemptedEdgesPerPathI32
        ) { "W4c scratch draw exceeds the immutable math path edge limit" }
    }

    private companion object {
        const val VERTEX_BYTES: Long = 8L
        const val INDEX_BYTES: Long = 4L
    }
}

/**
 * Handle-free authority for one closed W4c path-fill frame.
 *
 * Geometry remains the immutable `:math` authority selected by the graph; this class only seals
 * its fixed native offsets, dynamic-uniform slots, resource identities, and capability evidence.
 */
internal class W4cSessionScratchV1(
    val planId: String,
    val capabilitySealHash: String,
    val deviceGeneration: Long,
    val target: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val targetBounds: GPUPixelBounds,
    val vertexResourceId: PlanResourceId,
    val indexResourceId: PlanResourceId,
    val uniformResourceId: PlanResourceId,
    val depthStencilResourceId: PlanResourceId?,
    draws: List<W4cSessionScratchDrawV1>,
    val uniformPlan: GPUUniformSlabPlan,
    val uniformStrideBytes: Long,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformUsefulBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val depthStencilBytes: Long,
    val poolCapacities: GPUCorePrimitiveFramePoolCapacities,
    val maxBufferSize: Long,
    val maxDynamicUniformBuffersPerPipelineLayout: Long,
) {
    val graphHash: String = planId
    val capabilityHash: String = capabilitySealHash
    val draws: List<W4cSessionScratchDrawV1> = immutableList(draws)

    init {
        val usesStencil = this.draws.any { draw -> draw.strategy == PathFillStrategy.StencilCover }
        val expectedVertexUseful = this.draws.sumOf(W4cSessionScratchDrawV1::vertexRangeBytes)
        val expectedIndexUseful = this.draws.sumOf(W4cSessionScratchDrawV1::indexRangeBytes)
        val expectedUniformPayload = Math.multiplyExact(this.draws.size.toLong(), UNIFORM_PAYLOAD_BYTES)
        val expectedUniformReserved = Math.multiplyExact(this.draws.size.toLong(), uniformStrideBytes)
        require(planId.isNotBlank() && capabilitySealHash.isNotBlank() && deviceGeneration >= 0L) {
            "W4c scratch requires exact graph and capability hashes"
        }
        require(!targetBounds.isEmpty && maxBufferSize > 0L && maxDynamicUniformBuffersPerPipelineLayout >= 1L) {
            "W4c scratch requires current target and observed buffer facts"
        }
        require(
            this.draws.size in 1..512 &&
                this.draws.map(W4cSessionScratchDrawV1::commandId).distinct().size == this.draws.size &&
                this.draws.zipWithNext().all { (first, second) -> first.commandId < second.commandId } &&
                this.draws.withIndex().all { (index, draw) -> draw.uniformSlotIndex == index } &&
                this.draws.zipWithNext().all { (first, second) ->
                    first.vertexOffsetBytes + first.vertexRangeBytes == second.vertexOffsetBytes &&
                        first.indexOffsetBytes + first.indexRangeBytes == second.indexOffsetBytes
                } &&
                this.draws.first().vertexOffsetBytes == 0L && this.draws.first().indexOffsetBytes == 0L,
        ) { "W4c scratch requires ordered exact draw offsets" }
        require(
            vertexUsefulBytes == expectedVertexUseful &&
                indexUsefulBytes == expectedIndexUseful &&
                uniformUsefulBytes == expectedUniformPayload &&
                uniformStrideBytes == uniformPlan.alignmentBytes &&
                uniformPlan.sourceLabel == SOURCE_LABEL &&
                uniformPlan.deviceGeneration == deviceGeneration &&
                uniformPlan.uploadBudgetBytes == uniformCapacityBytes &&
                uniformPlan.totalBytes == expectedUniformReserved &&
                uniformPlan.slots.size == this.draws.size &&
                uniformPlan.slots.withIndex().all { (index, slot) ->
                    slot.slotLabel == "path-fill-draw-${this.draws[index].commandId}" &&
                        slot.payloadBytes == UNIFORM_PAYLOAD_BYTES &&
                        slot.allocatedBytes == uniformStrideBytes &&
                        slot.alignedOffset == index.toLong() * uniformStrideBytes
                },
        ) { "W4c scratch Uniform32 slab must exactly match the graph draw order" }
        require(
            vertexCapacityBytes >= vertexUsefulBytes &&
                indexCapacityBytes >= indexUsefulBytes &&
                uniformCapacityBytes >= uniformPlan.totalBytes &&
                vertexCapacityBytes <= maxBufferSize &&
                indexCapacityBytes <= maxBufferSize &&
                uniformCapacityBytes <= maxBufferSize &&
                poolCapacities.vertexBytes == vertexCapacityBytes &&
                poolCapacities.indexBytes == indexCapacityBytes &&
                poolCapacities.uniformBytes == uniformCapacityBytes &&
                poolCapacities == corePrimitiveFramePoolCapacitiesOrNull(
                vertexUsefulBytes,
                indexUsefulBytes,
                uniformPlan.totalBytes,
            ),
        ) { "W4c scratch capacities must be the graph's exact pooled reservations" }
        require(
            if (usesStencil) {
                depthStencilResourceId != null && depthStencilBytes > 0L
            } else {
                depthStencilResourceId == null && depthStencilBytes == 0L
            },
        ) { "W4c scratch depth-stencil authority must match the sealed strategies" }
    }

    internal fun matches(
        expectedPlanId: String,
        expectedCapabilityHash: String,
        expectedGeneration: Long,
        expectedTarget: GPUFrameTargetRef,
        expectedStaging: GPUFrameBufferRef,
        expectedBounds: GPUPixelBounds,
    ): Boolean =
        planId == expectedPlanId &&
            capabilitySealHash == expectedCapabilityHash &&
            deviceGeneration == expectedGeneration &&
            target == expectedTarget &&
            staging == expectedStaging &&
            targetBounds == expectedBounds

    internal companion object {
        const val SOURCE_LABEL: String = "w4c-path-fill-uniform32-pass"
        const val UNIFORM_PAYLOAD_BYTES: Long = 32L
    }
}
