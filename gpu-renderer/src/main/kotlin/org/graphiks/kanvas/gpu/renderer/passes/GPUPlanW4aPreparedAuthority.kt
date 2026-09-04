package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.resources.GPUCorePrimitiveFramePoolCapacities
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.math.geometry.RectI32

/** Immutable W4a geometry facts retained until the native analytic lane materializes them. */
internal class W4aSessionScratchDrawV1(
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    deviceBounds: GPURect,
    rasterBounds: RectI32,
    scissorBounds: GPUPixelBounds,
) {
    private val deviceBoundsSnapshot = deviceBounds.copy()
    private val rasterBoundsSnapshot = rasterBounds.copy()
    private val scissorBoundsSnapshot = scissorBounds.copy()

    fun copyDeviceBounds(): GPURect = deviceBoundsSnapshot.copy()
    fun copyRasterBounds(): RectI32 = rasterBoundsSnapshot.copy()
    fun copyScissorBounds(): GPUPixelBounds = scissorBoundsSnapshot.copy()

    init {
        require(commandId >= 0) { "W4a scratch draw command id must be non-negative" }
        require(
            listOf(
                deviceBoundsSnapshot.left,
                deviceBoundsSnapshot.top,
                deviceBoundsSnapshot.right,
                deviceBoundsSnapshot.bottom,
            ).all(Float::isFinite) &&
                deviceBoundsSnapshot.left < deviceBoundsSnapshot.right &&
                deviceBoundsSnapshot.top < deviceBoundsSnapshot.bottom,
        ) { "W4a scratch draw requires finite non-empty device bounds" }
        require(!rasterBoundsSnapshot.isEmpty64() && !scissorBoundsSnapshot.isEmpty) {
            "W4a scratch draw requires non-empty raster and scissor bounds"
        }
    }
}

/**
 * Handle-free W4a authority for one already sealed ScalarAA analytic-rectangle frame.
 *
 * The graph owns the one physical V/I/U reservation.  This authority records those exact
 * reservations and does not create a second logical allocation for them.
 */
internal class W4aSessionScratchV1(
    val planId: String,
    val capabilitySealHash: String,
    val deviceGeneration: Long,
    val target: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val targetBounds: GPUPixelBounds,
    val vertexResourceId: PlanResourceId,
    val indexResourceId: PlanResourceId,
    val uniformResourceId: PlanResourceId,
    packetIds: List<GPUDrawPacketID>,
    commandIds: List<Int>,
    draws: List<W4aSessionScratchDrawV1>,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val uniformPlan: GPUUniformSlabPlan,
    val uniformStrideBytes: Long,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformUsefulBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val poolCapacities: GPUCorePrimitiveFramePoolCapacities,
    val maxBufferSize: Long,
    val maxDynamicUniformBuffersPerPipelineLayout: Long,
) {
    val packetIds: List<GPUDrawPacketID> = immutableList(packetIds)
    val commandIds: List<Int> = immutableList(commandIds)
    val draws: List<W4aSessionScratchDrawV1> = immutableList(draws)

    init {
        require(planId.isNotBlank() && capabilitySealHash.isNotBlank()) {
            "W4a scratch requires exact plan and capability seals"
        }
        require(deviceGeneration >= 0L && !targetBounds.isEmpty) {
            "W4a scratch requires a current non-empty target"
        }
        require(maxBufferSize > 0L && maxDynamicUniformBuffersPerPipelineLayout >= 1L) {
            "W4a scratch requires observed positive buffer and dynamic-uniform limits"
        }
        require(
            packetIds.size in 1..512 &&
                packetIds.distinct().size == packetIds.size &&
                commandIds.size == packetIds.size &&
                commandIds.distinct().size == commandIds.size &&
                commandIds.all { it >= 0 } &&
                draws.size == packetIds.size &&
                draws.map(W4aSessionScratchDrawV1::packetId) == packetIds &&
                draws.map(W4aSessionScratchDrawV1::commandId) == commandIds,
        ) { "W4a scratch requires one ordered packet, command, and geometry snapshot per draw" }
        require(
            structuralPipelineKey.shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape &&
                structuralPipelineKey.topology == GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
                structuralPipelineKey.sampleCount == 1 &&
                structuralPipelineKey.uniformLayout ==
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
        ) { "W4a scratch accepts only the analytic single-sample Uniform80 pipeline" }
        require(
            vertexUsefulBytes == packetIds.size.toLong() * VERTEX_BYTES_PER_DRAW &&
                indexUsefulBytes == packetIds.size.toLong() * INDEX_BYTES_PER_DRAW &&
                uniformStrideBytes == uniformPlan.alignmentBytes &&
                uniformUsefulBytes == packetIds.size.toLong() * uniformStrideBytes &&
                uniformPlan.sourceLabel == SOURCE_LABEL &&
                uniformPlan.deviceGeneration == deviceGeneration &&
                uniformPlan.uploadBudgetBytes == uniformCapacityBytes &&
                uniformPlan.totalBytes == uniformUsefulBytes &&
                uniformPlan.slots.size == packetIds.size &&
                uniformPlan.slots.withIndex().all { (index, slot) ->
                    slot.slotLabel == "analytic-shape-draw-${commandIds[index]}" &&
                        slot.payloadBytes == UNIFORM_PAYLOAD_BYTES &&
                        slot.allocatedBytes == uniformStrideBytes &&
                        slot.alignedOffset == index.toLong() * uniformStrideBytes
                },
        ) { "W4a scratch Uniform80 slab must exactly match ordered draw facts" }
        require(
            vertexCapacityBytes >= vertexUsefulBytes &&
                indexCapacityBytes >= indexUsefulBytes &&
                uniformCapacityBytes >= uniformUsefulBytes &&
                vertexCapacityBytes <= maxBufferSize &&
                indexCapacityBytes <= maxBufferSize &&
                uniformCapacityBytes <= maxBufferSize &&
                poolCapacities.vertexBytes == vertexCapacityBytes &&
                poolCapacities.indexBytes == indexCapacityBytes &&
                poolCapacities.uniformBytes == uniformCapacityBytes &&
                poolCapacities == corePrimitiveFramePoolCapacitiesOrNull(
                vertexUsefulBytes,
                indexUsefulBytes,
                uniformUsefulBytes,
            ),
        ) { "W4a scratch reserved capacities must be the graph's exact native pool capacities" }
    }

    internal fun matches(
        expectedPlanId: String,
        capabilityHash: String,
        generation: Long,
        expectedTarget: GPUFrameTargetRef,
        expectedStaging: GPUFrameBufferRef,
        bounds: GPUPixelBounds,
        packets: List<GPUDrawPacket>,
    ): Boolean =
        planId == expectedPlanId &&
            capabilitySealHash == capabilityHash &&
            deviceGeneration == generation &&
            target == expectedTarget &&
            staging == expectedStaging &&
            targetBounds == bounds &&
            packetIds == packets.map(GPUDrawPacket::packetId) &&
            commandIds == packets.map(GPUDrawPacket::commandIdValue)

    internal companion object {
        const val SOURCE_LABEL: String = "core-primitive-analytic-shape-uniform-pass"
        const val UNIFORM_PAYLOAD_BYTES: Long = 80L

        /** Checked canonical dynamic-uniform stride for the fixed Uniform80 ABI. */
        fun canonicalUniformStrideOrNull(minimumAlignmentBytes: Long): Long? {
            if (minimumAlignmentBytes <= 0L) return null
            return try {
                val remainder = UNIFORM_PAYLOAD_BYTES % minimumAlignmentBytes
                if (remainder == 0L) UNIFORM_PAYLOAD_BYTES else Math.addExact(
                    UNIFORM_PAYLOAD_BYTES,
                    minimumAlignmentBytes - remainder,
                )
            } catch (_: ArithmeticException) {
                null
            }
        }

        private const val VERTEX_BYTES_PER_DRAW: Long = 32L
        private const val INDEX_BYTES_PER_DRAW: Long = 24L
    }
}
