package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.*

/** Sealed geometry ownership used by the shared final-blend envelope. */
internal sealed class W5bGeometryScratchV3 {
    class PathFill(val authority: W4cSessionScratchV1, packets: List<GPUDrawPacket>,
        keys: List<GPUCorePrimitiveRenderPipelineStructuralKey>) : W5bGeometryScratchV3() {
        private val admittedPackets = org.graphiks.kanvas.gpu.renderer.collections.immutableList(packets)
        override val packetIds = org.graphiks.kanvas.gpu.renderer.collections.immutableList(packets.map { it.packetId })
        override val commandIds = org.graphiks.kanvas.gpu.renderer.collections.immutableList(packets.map { it.commandIdValue })
        override val packetStructuralPipelineKeys = org.graphiks.kanvas.gpu.renderer.collections.immutableList(keys)
        override val planId get() = authority.planId
        override val capabilitySealHash get() = authority.capabilitySealHash
        override val deviceGeneration get() = authority.deviceGeneration
        override val target get() = authority.target
        override val staging get() = authority.staging
        override val targetBounds get() = authority.targetBounds
        override val uniformPlan get() = authority.uniformPlan
        override val vertexBytes get() = authority.vertexUsefulBytes
        override val indexBytes get() = authority.indexUsefulBytes
        override val poolCapacities get() = authority.poolCapacities
        override val uniformPayloadBytesI64 = W4cSessionScratchV1.UNIFORM_PAYLOAD_BYTES
        override fun hasExactUniformPayloads(expectedAlignmentBytes: Long, packets: List<GPUDrawPacket>): Boolean =
            expectedAlignmentBytes == uniformPlan.alignmentBytes && packets.size == admittedPackets.size &&
                packets.zip(admittedPackets).all { (a, b) -> a === b } && packets.withIndex().all { (index, packet) ->
                    val prepared = packet.corePrimitivePreparedAuthority ?: return false
                    prepared.structuralPipelineKey == packetStructuralPipelineKeys[index] &&
                        authority.matchesPreparedPacket(planId, capabilitySealHash, packet,
                            prepared.structuralPipelineKey, prepared.renderPipelineKey)
                }
        override fun fitsDeviceLimits(maxBufferSize: Long, maxDynamicUniformBuffersPerPipelineLayout: Long): Boolean =
            maxBufferSize == authority.maxBufferSize && maxDynamicUniformBuffersPerPipelineLayout == authority.maxDynamicUniformBuffersPerPipelineLayout &&
                listOf(poolCapacities.vertexBytes, poolCapacities.indexBytes, poolCapacities.uniformBytes).all { it <= maxBufferSize }
    }
    class AnalyticRRect(val authority: W4bSessionScratchV1, packets: List<GPUDrawPacket>,
        keys: List<GPUCorePrimitiveRenderPipelineStructuralKey>, payloads: List<ByteArray>) : W5bGeometryScratchV3() {
        private val admittedPackets = org.graphiks.kanvas.gpu.renderer.collections.immutableList(packets)
        private val admittedPayloads = payloads.map { it.copyOf() }
        override val packetStructuralPipelineKeys = org.graphiks.kanvas.gpu.renderer.collections.immutableList(keys)
        override val planId get() = authority.planId
        override val capabilitySealHash get() = authority.capabilitySealHash
        override val deviceGeneration get() = authority.deviceGeneration
        override val target get() = authority.target
        override val staging get() = authority.staging
        override val targetBounds get() = authority.targetBounds
        override val packetIds get() = authority.packetIds
        override val commandIds get() = authority.commandIds
        override val uniformPlan get() = authority.uniformPlan
        override val vertexBytes get() = authority.vertexUsefulBytes
        override val indexBytes get() = authority.indexUsefulBytes
        override val poolCapacities get() = authority.poolCapacities
        override val uniformPayloadBytesI64 = W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES
        init {
            require(packets.map { it.packetId } == packetIds && keys.size == packets.size && payloads.size == packets.size)
            require(keys.all { it.shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape &&
                it.topology == GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
                it.sampleCount == 1 && it.uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 })
            require(payloads.all { it.size.toLong() == uniformPayloadBytesI64 })
        }
        fun copyUniformPayloadI32(indexI32: Int): ByteArray = admittedPayloads[indexI32].copyOf()
        override fun hasExactUniformPayloads(expectedAlignmentBytes: Long, packets: List<GPUDrawPacket>): Boolean =
            packets.size == admittedPackets.size && packets.zip(admittedPackets).all { (a, b) -> a === b } &&
                packets.withIndex().all { (index, packet) ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
                    val seal = packet.corePrimitivePreparedAuthority?.analyticShapeUniformSeal ?: return false
                    val draw = authority.draws[index]
                    val geometry = semantic.geometry as? org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.RRect ?: return false
                    val shape = draw.copyDeviceShape()
                    val device = shape.rect
                    geometry.left == device.left && geometry.top == device.top && geometry.right == device.right &&
                        geometry.bottom == device.bottom && geometry.radii == listOf(shape.topLeft.x, shape.topLeft.y,
                            shape.topRight.x, shape.topRight.y, shape.bottomRight.x, shape.bottomRight.y,
                            shape.bottomLeft.x, shape.bottomLeft.y) && semantic.scissorBounds == draw.copyScissorBounds() &&
                        seal.hasExactSemantic(semantic) && seal.hasExactPayload(admittedPayloads[index]) &&
                        packet.corePrimitivePreparedAuthority?.structuralPipelineKey == packetStructuralPipelineKeys[index]
                } && uniformPlan.hasExactPayloads(W4bSessionScratchV1.SOURCE_LABEL, deviceGeneration,
                    expectedAlignmentBytes, admittedPackets.indices.map { index ->
                        GPUUniformSlabPayload("analytic-shape-draw-${commandIds[index]}", admittedPayloads[index])
                    })
        override fun fitsDeviceLimits(maxBufferSize: Long, maxDynamicUniformBuffersPerPipelineLayout: Long): Boolean =
            maxBufferSize == authority.maxBufferSize && maxDynamicUniformBuffersPerPipelineLayout == authority.maxDynamicUniformBuffersPerPipelineLayout &&
                listOf(poolCapacities.vertexBytes, poolCapacities.indexBytes, poolCapacities.uniformBytes).all { it <= maxBufferSize }
    }
    abstract val planId: String
    abstract val capabilitySealHash: String
    abstract val deviceGeneration: Long
    abstract val target: GPUFrameTargetRef
    abstract val staging: GPUFrameBufferRef
    abstract val targetBounds: GPUPixelBounds
    abstract val packetIds: List<GPUDrawPacketID>
    abstract val commandIds: List<Int>
    abstract val packetStructuralPipelineKeys: List<GPUCorePrimitiveRenderPipelineStructuralKey>
    val structuralPipelineKeys: List<GPUCorePrimitiveRenderPipelineStructuralKey> get() = packetStructuralPipelineKeys.distinct()
    abstract val uniformPlan: GPUUniformSlabPlan
    abstract val vertexBytes: Long
    abstract val indexBytes: Long
    abstract val poolCapacities: GPUCorePrimitiveFramePoolCapacities
    abstract val uniformPayloadBytesI64: Long
    abstract fun hasExactUniformPayloads(expectedAlignmentBytes: Long, packets: List<GPUDrawPacket>): Boolean
    abstract fun fitsDeviceLimits(maxBufferSize: Long, maxDynamicUniformBuffersPerPipelineLayout: Long): Boolean
    fun matches(expectedPlanId: String, capabilityHash: String, generation: Long,
        expectedTarget: GPUFrameTargetRef, expectedStaging: GPUFrameBufferRef, bounds: GPUPixelBounds,
        packets: List<GPUDrawPacket>): Boolean = planId == expectedPlanId && capabilitySealHash == capabilityHash &&
        deviceGeneration == generation && target == expectedTarget && staging == expectedStaging && targetBounds == bounds &&
        packetIds == packets.map { it.packetId } && commandIds == packets.map { it.commandIdValue }

    class Direct(val authority: W3SessionScratchV1) : W5bGeometryScratchV3() {
        override val planId get() = authority.planId
        override val capabilitySealHash get() = authority.capabilitySealHash
        override val deviceGeneration get() = authority.deviceGeneration
        override val target get() = authority.target
        override val staging get() = authority.staging
        override val targetBounds get() = authority.targetBounds
        override val packetIds get() = authority.packetIds
        override val commandIds get() = authority.commandIds
        override val packetStructuralPipelineKeys get() = authority.packetStructuralPipelineKeys
        override val uniformPlan get() = authority.uniformPlan
        override val vertexBytes get() = authority.vertexBytes
        override val indexBytes get() = authority.indexBytes
        override val poolCapacities get() = authority.poolCapacities
        override val uniformPayloadBytesI64 = 32L
        override fun hasExactUniformPayloads(expectedAlignmentBytes: Long, packets: List<GPUDrawPacket>) =
            authority.hasExactUniformPayloads(expectedAlignmentBytes, packets)
        override fun fitsDeviceLimits(maxBufferSize: Long, maxDynamicUniformBuffersPerPipelineLayout: Long) =
            authority.fitsDeviceLimits(maxBufferSize, maxDynamicUniformBuffersPerPipelineLayout)
    }

    /** W4a remains the geometry and capacity producer; only pipeline selection is per consumer. */
    class AnalyticRect(val authority: W4aSessionScratchV1, packets: List<GPUDrawPacket>,
        keys: List<GPUCorePrimitiveRenderPipelineStructuralKey>, payloads: List<ByteArray>) : W5bGeometryScratchV3() {
        private val admittedPackets = org.graphiks.kanvas.gpu.renderer.collections.immutableList(packets)
        private val admittedPayloads = payloads.map { it.copyOf() }
        override val packetStructuralPipelineKeys = org.graphiks.kanvas.gpu.renderer.collections.immutableList(keys)
        override val planId get() = authority.planId
        override val capabilitySealHash get() = authority.capabilitySealHash
        override val deviceGeneration get() = authority.deviceGeneration
        override val target get() = authority.target
        override val staging get() = authority.staging
        override val targetBounds get() = authority.targetBounds
        override val packetIds get() = authority.packetIds
        override val commandIds get() = authority.commandIds
        override val uniformPlan get() = authority.uniformPlan
        override val vertexBytes get() = authority.vertexUsefulBytes
        override val indexBytes get() = authority.indexUsefulBytes
        override val poolCapacities get() = authority.poolCapacities
        override val uniformPayloadBytesI64 = W4aSessionScratchV1.UNIFORM_PAYLOAD_BYTES
        init {
            require(packets.map { it.packetId } == packetIds && keys.size == packets.size && payloads.size == packets.size)
            require(keys.all { it.shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape &&
                it.topology == GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
                it.sampleCount == 1 && it.uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 })
            require(payloads.all { it.size.toLong() == uniformPayloadBytesI64 })
        }
        fun copyUniformPayloadI32(indexI32: Int): ByteArray = admittedPayloads[indexI32].copyOf()
        override fun hasExactUniformPayloads(expectedAlignmentBytes: Long, packets: List<GPUDrawPacket>): Boolean =
            packets.size == admittedPackets.size && packets.zip(admittedPackets).all { (a, b) -> a === b } &&
                packets.withIndex().all { (index, packet) ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
                    val seal = packet.corePrimitivePreparedAuthority?.analyticShapeUniformSeal ?: return false
                    val draw = authority.draws[index]
                    val geometry = semantic.geometry as? org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.Rect ?: return false
                    val device = draw.copyDeviceBounds()
                    geometry.left == device.left && geometry.top == device.top && geometry.right == device.right &&
                        geometry.bottom == device.bottom && semantic.scissorBounds == draw.copyScissorBounds() &&
                        seal.hasExactSemantic(semantic) && seal.hasExactPayload(admittedPayloads[index]) &&
                        packet.corePrimitivePreparedAuthority?.structuralPipelineKey == packetStructuralPipelineKeys[index]
                } && uniformPlan.hasExactPayloads(W4aSessionScratchV1.SOURCE_LABEL, deviceGeneration,
                    expectedAlignmentBytes, admittedPackets.indices.map { index ->
                        GPUUniformSlabPayload("analytic-shape-draw-${commandIds[index]}", admittedPayloads[index])
                    })
        override fun fitsDeviceLimits(maxBufferSize: Long, maxDynamicUniformBuffersPerPipelineLayout: Long): Boolean =
            maxBufferSize == authority.maxBufferSize && maxDynamicUniformBuffersPerPipelineLayout == authority.maxDynamicUniformBuffersPerPipelineLayout &&
                listOf(poolCapacities.vertexBytes, poolCapacities.indexBytes, poolCapacities.uniformBytes).all { it <= maxBufferSize }
    }
}
