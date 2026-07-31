package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

internal class GPUCorePrimitiveCoverageMaskPreparedSlabAuthority(
    val vertexResource: GPUFrameBufferRef,
    val vertexGeneration: Long,
    val vertexByteSize: Long,
    val indexResource: GPUFrameBufferRef,
    val indexGeneration: Long,
    val indexByteSize: Long,
    val uniformResource: GPUFrameBufferRef,
    val uniformGeneration: Long,
    val uniformByteSize: Long,
    val uniformAlignmentBytes: Long,
    val uniformSlabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal,
) {
    init {
        require(setOf(vertexResource, indexResource, uniformResource).size == 3 &&
            vertexGeneration >= 0L && indexGeneration >= 0L && uniformGeneration >= 0L &&
            vertexByteSize > 0L && vertexByteSize % 4L == 0L &&
            indexByteSize > 0L && indexByteSize % 4L == 0L &&
            uniformByteSize == uniformSlabSeal.plan.totalBytes &&
            uniformAlignmentBytes == uniformSlabSeal.plan.alignmentBytes &&
            uniformSlabSeal.hasCanonicalRenderPipelineKeys
        ) { "Prepared coverage-mask slabs require exact distinct generated resources" }
    }
}

internal class GPUCorePrimitiveCoverageMaskPreparedAttachmentAuthority(
    val resource: GPUFrameTargetRef,
    val resourceGeneration: Long,
) {
    init {
        require(resourceGeneration >= 0L) {
            "Prepared coverage-mask attachment requires a current generated target"
        }
    }
}

internal data class GPUCorePrimitiveCoverageMaskPreparedUniformSlice(
    val resource: GPUFrameBufferRef,
    val resourceGeneration: Long,
    val slotIndex: Int,
    val alignedOffset: Long,
    val payloadBytes: Long,
    val allocatedBytes: Long,
) {
    init {
        require(resourceGeneration >= 0L && slotIndex >= 0 && alignedOffset >= 0L &&
            alignedOffset <= UInt.MAX_VALUE.toLong() && payloadBytes == 64L &&
            allocatedBytes >= payloadBytes
        ) { "Prepared coverage-mask uniform slice requires one exact dynamic-uniform64 slot" }
    }
}

internal data class GPUCorePrimitiveCoverageMaskPreparedGeometrySlice(
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int,
    val vertexCount: Int,
) {
    init {
        require(firstIndex >= 0 && indexCount > 0 && baseVertex >= 0 && vertexCount > 0) {
            "Prepared coverage-mask consumer geometry requires one exact indexed slice"
        }
    }
}

internal sealed interface GPUCorePrimitiveCoverageMaskPreparedDraw {
    data class Draw(val vertexCount: Int = 3) : GPUCorePrimitiveCoverageMaskPreparedDraw {
        init { require(vertexCount == 3) }
    }

    data class DrawIndexed(
        val indexCount: Int,
        val firstIndex: Int,
        val baseVertex: Int,
    ) : GPUCorePrimitiveCoverageMaskPreparedDraw {
        init { require(indexCount > 0 && firstIndex >= 0 && baseVertex >= 0) }
    }
}

/** Exact handle-free command facts captured only after coverage-mask stream validation. */
internal class GPUCorePrimitiveCoverageMaskPreparedCommandAuthority(
    val passId: String,
    sourcePassIds: List<String>,
    val packetId: GPUDrawPacketID,
    val targetStateHash: String,
    val loadStoreLabel: String,
    val pipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
    val uniformSlot: GPUUniformPayloadSlot?,
    val resourceSlot: GPUResourceBindingSlot?,
    val vertexBufferSlot: Int?,
    val indexFormatLabel: String?,
    val vertexSourceLabel: String,
    val scissorBoundsHash: String?,
    val uniformSlice: GPUCorePrimitiveCoverageMaskPreparedUniformSlice,
    val geometrySlice: GPUCorePrimitiveCoverageMaskPreparedGeometrySlice?,
    val draw: GPUCorePrimitiveCoverageMaskPreparedDraw,
) {
    val sourcePassIds: List<String> = immutableList(sourcePassIds)

    init {
        require(passId.isNotBlank() && this.sourcePassIds.isNotEmpty() &&
            this.sourcePassIds.none(String::isBlank) && targetStateHash.isNotBlank() &&
            loadStoreLabel.isNotBlank() && bindingLayoutHash.isNotBlank() &&
            vertexSourceLabel.isNotBlank() && (vertexBufferSlot == null || vertexBufferSlot >= 0) &&
            (indexFormatLabel == null || indexFormatLabel.isNotBlank()) &&
            (geometrySlice == null) == (draw is GPUCorePrimitiveCoverageMaskPreparedDraw.Draw)
        ) { "Prepared coverage-mask command authority requires complete typed command facts" }
    }
}

private fun hasExactCoverageMaskUniformSlice(
    packetId: GPUDrawPacketID,
    commandId: Int,
    sourceOrder: Int,
    uniformSlice: GPUCorePrimitiveCoverageMaskPreparedUniformSlice,
    slabAuthority: GPUCorePrimitiveCoverageMaskPreparedSlabAuthority,
    producer: Boolean,
): Boolean {
    val slabSeal = slabAuthority.uniformSlabSeal
    val slot = if (producer) {
        slabSeal.producerSlots.getOrNull(uniformSlice.slotIndex)
    } else {
        slabSeal.consumerSlots.getOrNull(
            uniformSlice.slotIndex - slabSeal.producerSlots.size,
        )
    } ?: return false
    val exactIdentity = when (slot) {
        is GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal ->
            slot.packetId == packetId && slot.commandId == commandId &&
                slot.sourceOrder == sourceOrder
        is GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal ->
            slot.packetId == packetId && slot.commandId == commandId &&
                slot.sourceOrder == sourceOrder
        else -> false
    }
    val slabSlot = slabSeal.plan.slots.getOrNull(uniformSlice.slotIndex) ?: return false
    val exactSlice = GPUCorePrimitiveCoverageMaskPreparedUniformSlice(
        slabAuthority.uniformResource,
        slabAuthority.uniformGeneration,
        uniformSlice.slotIndex,
        slabSlot.alignedOffset,
        slabSlot.payloadBytes,
        slabSlot.allocatedBytes,
    )
    val intervalEnd = uniformSlice.alignedOffset + uniformSlice.allocatedBytes
    return exactIdentity && uniformSlice == exactSlice &&
        intervalEnd >= uniformSlice.alignedOffset &&
        intervalEnd <= slabAuthority.uniformByteSize
}

internal sealed interface GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
    data object Missing : GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal
    data object Empty : GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal

    class Producer internal constructor(
        val sourceStepIndex: Int,
        val packetId: GPUDrawPacketID,
        val commandId: Int,
        val sourceOrder: Int,
        val route: GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted,
        val slabAuthority: GPUCorePrimitiveCoverageMaskPreparedSlabAuthority,
        val attachmentAuthority: GPUCorePrimitiveCoverageMaskPreparedAttachmentAuthority,
        val uniformSlice: GPUCorePrimitiveCoverageMaskPreparedUniformSlice,
        val draw: GPUCorePrimitiveCoverageMaskPreparedDraw.Draw =
            GPUCorePrimitiveCoverageMaskPreparedDraw.Draw(),
        val commandAuthority: GPUCorePrimitiveCoverageMaskPreparedCommandAuthority? = null,
    ) : GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal

    /** One render scope containing the exact ordered producer packet partition. */
    class ProducerPartition internal constructor(
        val sourceStepIndex: Int,
        units: List<Producer>,
    ) : GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
        val units: List<Producer> = immutableList(units)

        init {
            val firstUnit = this.units.firstOrNull()
            val routeProducerSourceOrders = firstUnit?.route?.producers
                ?.map { producer -> producer.sourceOrder }
                .orEmpty()
            val routeProducerSourceOrderIndex = routeProducerSourceOrders.toSet()
            require(sourceStepIndex >= 0 && this.units.size > 1 &&
                routeProducerSourceOrderIndex.size == routeProducerSourceOrders.size &&
                this.units.all { it.sourceStepIndex == sourceStepIndex } &&
                this.units.all { unit ->
                    hasExactCoverageMaskUniformSlice(
                        unit.packetId,
                        unit.commandId,
                        unit.sourceOrder,
                        unit.uniformSlice,
                        unit.slabAuthority,
                        producer = true,
                    ) && unit.sourceOrder in routeProducerSourceOrderIndex &&
                        unit.commandAuthority?.let { authority ->
                        authority.packetId == unit.packetId &&
                            authority.uniformSlice == unit.uniformSlice &&
                            authority.geometrySlice == null && authority.draw == unit.draw
                    } != false
                } &&
                this.units.map(Producer::packetId).distinct().size == this.units.size &&
                this.units.map(Producer::sourceOrder).zipWithNext().all { (left, right) -> left < right } &&
                this.units.map { it.uniformSlice.slotIndex }.zipWithNext().all { (left, right) -> left < right } &&
                this.units.map(Producer::commandAuthority).let { authorities ->
                    authorities.all { it == null } || authorities.all { it != null }
                } &&
                this.units.drop(1).all { unit ->
                    unit.route === firstUnit?.route &&
                        unit.slabAuthority === firstUnit?.slabAuthority &&
                        unit.attachmentAuthority === firstUnit?.attachmentAuthority
                }
            ) {
                "Coverage-mask producer partition requires one exact multi-packet source scope"
            }
        }
    }

    class Consumer internal constructor(
        val sourceStepIndex: Int,
        val packetId: GPUDrawPacketID,
        val commandId: Int,
        val sourceOrder: Int,
        val dependencyFromPreviousConsumerToken: String?,
        val isLastConsumer: Boolean,
        val route: GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted,
        val slabAuthority: GPUCorePrimitiveCoverageMaskPreparedSlabAuthority,
        val attachmentAuthority: GPUCorePrimitiveCoverageMaskPreparedAttachmentAuthority,
        val uniformSlice: GPUCorePrimitiveCoverageMaskPreparedUniformSlice,
        val geometrySlice: GPUCorePrimitiveCoverageMaskPreparedGeometrySlice,
        val sceneTarget: GPUFrameTargetRef,
        val sceneTargetGeneration: Long,
        val draw: GPUCorePrimitiveCoverageMaskPreparedDraw.DrawIndexed =
            GPUCorePrimitiveCoverageMaskPreparedDraw.DrawIndexed(
                geometrySlice.indexCount,
                geometrySlice.firstIndex,
                geometrySlice.baseVertex,
            ),
        val commandAuthority: GPUCorePrimitiveCoverageMaskPreparedCommandAuthority? = null,
    ) : GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
        init { require(sceneTargetGeneration >= 0L) }
    }

    /** One render scope containing the exact ordered consumer packet partition. */
    class ConsumerPartition internal constructor(
        val sourceStepIndex: Int,
        units: List<Consumer>,
    ) : GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
        val units: List<Consumer> = immutableList(units)

        init {
            val firstUnit = this.units.firstOrNull()
            val routeConsumerIdentities = firstUnit?.route?.consumers
                ?.map { consumer ->
                    Triple(consumer.packetId, consumer.commandId, consumer.sourceOrder)
                }
                .orEmpty()
            val routeConsumerIdentityIndex = routeConsumerIdentities.toSet()
            require(sourceStepIndex >= 0 && this.units.size > 1 &&
                routeConsumerIdentityIndex.size == routeConsumerIdentities.size &&
                this.units.all { it.sourceStepIndex == sourceStepIndex } &&
                this.units.all { unit ->
                    hasExactCoverageMaskUniformSlice(
                        unit.packetId,
                        unit.commandId,
                        unit.sourceOrder,
                        unit.uniformSlice,
                        unit.slabAuthority,
                        producer = false,
                    ) && Triple(unit.packetId, unit.commandId, unit.sourceOrder) in
                        routeConsumerIdentityIndex && unit.commandAuthority?.let { authority ->
                        authority.packetId == unit.packetId &&
                            authority.uniformSlice == unit.uniformSlice &&
                            authority.geometrySlice == unit.geometrySlice &&
                            authority.draw == unit.draw
                    } != false
                } &&
                this.units.map(Consumer::packetId).distinct().size == this.units.size &&
                this.units.map(Consumer::commandId).distinct().size == this.units.size &&
                this.units.map(Consumer::sourceOrder).zipWithNext().all { (left, right) -> left < right } &&
                this.units.map { it.uniformSlice.slotIndex }.zipWithNext().all { (left, right) -> left < right } &&
                this.units.map(Consumer::commandAuthority).let { authorities ->
                    authorities.all { it == null } || authorities.all { it != null }
                } &&
                this.units.dropLast(1).none(Consumer::isLastConsumer) &&
                this.units.drop(1).all { unit ->
                    unit.route === firstUnit?.route &&
                        unit.slabAuthority === firstUnit?.slabAuthority &&
                        unit.attachmentAuthority === firstUnit?.attachmentAuthority &&
                        unit.sceneTarget == firstUnit?.sceneTarget &&
                        unit.sceneTargetGeneration == firstUnit?.sceneTargetGeneration
                }
            ) {
                "Coverage-mask consumer partition requires one exact multi-packet source scope"
            }
        }
    }
}

internal fun GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.units(): List<
    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal
    > = when (this) {
    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> listOf(this)
    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition -> units
    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> listOf(this)
    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition -> units
    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
    -> emptyList()
}

/**
 * Validates one freshly lowered coverage-mask stream and returns a scope seal retaining its exact
 * typed command authority. The frame-level route remains reusable and handle-free.
 */
internal fun sealGPUCorePrimitiveCoverageMaskPreparedCommandAuthority(
    seal: GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal,
    packet: GPUDrawPacket,
    stream: GPUPassCommandStream,
    expectedPassId: String,
    expectedLoadStoreLabel: String,
): GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
    require(seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ||
        seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
    ) { "Only one coverage-mask producer or consumer scope can seal command authority" }
    val packetId = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> seal.packetId
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> seal.packetId
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    val commandId = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> seal.commandId
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> seal.commandId
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    val uniformSlice = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> seal.uniformSlice
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> seal.uniformSlice
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    val slabs = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> seal.slabAuthority
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> seal.slabAuthority
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    val slot = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> {
            require(uniformSlice.slotIndex in slabs.uniformSlabSeal.producerSlots.indices) {
                "Coverage-mask producer uniform slot index is outside its sealed partition"
            }
            slabs.uniformSlabSeal.producerSlots[uniformSlice.slotIndex]
        }
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> {
            val consumerIndex = uniformSlice.slotIndex -
                slabs.uniformSlabSeal.producerSlots.size
            require(consumerIndex in slabs.uniformSlabSeal.consumerSlots.indices) {
                "Coverage-mask consumer uniform slot index is outside its sealed partition"
            }
            slabs.uniformSlabSeal.consumerSlots[consumerIndex]
        }
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    val structuralPipelineKey = when (slot) {
        is GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal -> slot.structuralPipelineKey
        is GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal -> slot.structuralPipelineKey
        else -> error("Unreachable coverage-mask uniform slot")
    }
    val renderPipelineKey = when (slot) {
        is GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal -> slot.renderPipelineKey
        is GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal -> slot.renderPipelineKey
        else -> error("Unreachable coverage-mask uniform slot")
    }
    val bindingLayoutHash = when (slot) {
        is GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal -> slot.bindingLayoutHash
        is GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal -> slot.bindingLayoutHash
        else -> error("Unreachable coverage-mask uniform slot")
    }
    require(when (slot) {
        is GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal ->
            slot.slotIndex == uniformSlice.slotIndex && slot.packetId == packetId &&
                slot.commandId == commandId
        is GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal ->
            slot.slotIndex == uniformSlice.slotIndex && slot.packetId == packetId &&
                slot.commandId == commandId
        else -> false
    }) { "Coverage-mask indexed uniform slot identity differs from its scope seal" }
    val slabSlot = slabs.uniformSlabSeal.plan.slots[uniformSlice.slotIndex]
    require(uniformSlice == GPUCorePrimitiveCoverageMaskPreparedUniformSlice(
        slabs.uniformResource,
        slabs.uniformGeneration,
        uniformSlice.slotIndex,
        slabSlot.alignedOffset,
        slabSlot.payloadBytes,
        slabSlot.allocatedBytes,
    )) { "Coverage-mask command authority requires the exact uniform slab slot and byte slice" }
    require(packet.packetId == packetId && packet.commandIdValue == when (slot) {
        is GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal -> slot.commandId
        is GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal -> slot.commandId
        else -> error("Unreachable coverage-mask uniform slot")
    } && packet.renderPipelineKey == renderPipelineKey &&
        packet.corePrimitivePreparedAuthority?.structuralPipelineKey == structuralPipelineKey &&
        packet.bindingLayoutHash == bindingLayoutHash && packet.scissorBoundsHash == null
    ) { "Coverage-mask command authority differs from its exact packet and structural slot" }
    val consumer = seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
    if (seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer) {
        require(seal.draw == GPUCorePrimitiveCoverageMaskPreparedDraw.Draw(3)) {
            "Coverage-mask producer command authority requires Draw(3)"
        }
    } else {
        seal as GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
        require(seal.draw == GPUCorePrimitiveCoverageMaskPreparedDraw.DrawIndexed(
            seal.geometrySlice.indexCount,
            seal.geometrySlice.firstIndex,
            seal.geometrySlice.baseVertex,
        )) { "Coverage-mask consumer command authority requires its exact indexed geometry slice" }
    }
    val authority = GPUCorePrimitiveCoverageMaskPreparedCommandAuthority(
        passId = expectedPassId,
        sourcePassIds = listOf(packet.passId),
        packetId = packetId,
        targetStateHash = packet.targetStateHash,
        loadStoreLabel = expectedLoadStoreLabel,
        pipelineKey = renderPipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = packet.uniformSlot,
        resourceSlot = packet.resourceSlot,
        vertexBufferSlot = if (consumer) 0 else null,
        indexFormatLabel = if (consumer) "uint32" else null,
        vertexSourceLabel = packet.vertexSourceLabel,
        scissorBoundsHash = packet.scissorBoundsHash,
        uniformSlice = uniformSlice,
        geometrySlice = (seal as?
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer)?.geometrySlice,
        draw = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> seal.draw
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> seal.draw
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
            -> error("Unreachable coverage-mask scope seal")
        },
    )
    seal.requireExactCoverageMaskPassCommandAuthority(stream, authority)
    return when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ->
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer(
                seal.sourceStepIndex,
                seal.packetId,
                seal.commandId,
                seal.sourceOrder,
                seal.route,
                seal.slabAuthority,
                seal.attachmentAuthority,
                seal.uniformSlice,
                seal.draw,
                authority,
            )
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer ->
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer(
                seal.sourceStepIndex,
                seal.packetId,
                seal.commandId,
                seal.sourceOrder,
                seal.dependencyFromPreviousConsumerToken,
                seal.isLastConsumer,
                seal.route,
                seal.slabAuthority,
                seal.attachmentAuthority,
                seal.uniformSlice,
                seal.geometrySlice,
                seal.sceneTarget,
                seal.sceneTargetGeneration,
                seal.draw,
                authority,
            )
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
}

/** Seals every unit of one already-authenticated multi-packet render scope. */
internal fun sealGPUCorePrimitiveCoverageMaskPreparedPartitionCommandAuthority(
    seal: GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal,
    packets: List<GPUDrawPacket>,
    stream: GPUPassCommandStream,
    expectedPassId: String,
    expectedLoadStoreLabel: String,
): GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
    val units = seal.units()
    require(units.size > 1 && packets.map(GPUDrawPacket::packetId) == units.map { unit ->
        when (unit) {
            is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> unit.packetId
            is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> unit.packetId
            else -> error("Coverage-mask partition contains a non-unit seal")
        }
    }) { "Coverage-mask command partition must retain every packet in exact order" }
    val begin = stream.commands.firstOrNull() as? GPUPassCommand.BeginRenderPass
        ?: throw IllegalArgumentException("Coverage-mask partition stream must begin with BeginRenderPass")
    val end = stream.commands.lastOrNull() as? GPUPassCommand.EndRenderPass
        ?: throw IllegalArgumentException("Coverage-mask partition stream must end with EndRenderPass")
    val packetIds = packets.map(GPUDrawPacket::packetId).toSet()
    require(packetIds.size == packets.size) {
        "Coverage-mask command partition requires unique packet identities"
    }
    val commandsByPacketId = linkedMapOf<GPUDrawPacketID, MutableList<GPUPassCommand>>()
    stream.commands.drop(1).dropLast(1).forEach { command ->
        require(command !is GPUPassCommand.BeginRenderPass &&
            command !is GPUPassCommand.EndRenderPass
        ) { "Coverage-mask partition stream contains a nested render-pass boundary" }
        val sourcePacketId = requireNotNull(command.sourcePacketId) {
            "Coverage-mask partition interior command is missing packet identity"
        }
        require(sourcePacketId in packetIds) {
            "Coverage-mask partition stream contains a foreign packet command"
        }
        commandsByPacketId.getOrPut(sourcePacketId, ::mutableListOf).add(command)
    }
    require(commandsByPacketId.keys == packetIds) {
        "Coverage-mask partition stream must retain commands for every exact packet"
    }
    val commonOperandBridges = mutableListOf<IndexedValue<
        org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
        >>()
    val operandBridgesByPacketId = linkedMapOf<GPUDrawPacketID, MutableList<IndexedValue<
        org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
        >>>()
    stream.operandBridge.forEachIndexed { index, bridge ->
        val indexed = IndexedValue(index, bridge)
        val packetId = bridge.packetId
        if (packetId == null) {
            commonOperandBridges += indexed
        } else {
            require(packetId in packetIds) {
                "Coverage-mask partition stream contains a foreign packet operand bridge"
            }
            operandBridgesByPacketId.getOrPut(packetId, ::mutableListOf).add(indexed)
        }
    }
    fun operandBridgesFor(packetId: GPUDrawPacketID) = buildList {
        val packetBridges = operandBridgesByPacketId[packetId].orEmpty()
        var commonIndex = 0
        var packetIndex = 0
        while (commonIndex < commonOperandBridges.size || packetIndex < packetBridges.size) {
            val common = commonOperandBridges.getOrNull(commonIndex)
            val packet = packetBridges.getOrNull(packetIndex)
            if (packet == null || common != null && common.index < packet.index) {
                add(requireNotNull(common).value)
                commonIndex++
            } else {
                add(packet.value)
                packetIndex++
            }
        }
    }
    val sealedUnits = units.zip(packets).map { (unit, packet) ->
        val packetCommands = listOf(begin) + requireNotNull(commandsByPacketId[packet.packetId]) +
            listOf(end)
        val packetStream = GPUPassCommandStream(
            streamId = "${stream.streamId}.packet.${packet.packetId.value}",
            packetStreamId = stream.packetStreamId,
            passId = expectedPassId,
            commands = packetCommands,
            operandBridge = operandBridgesFor(packet.packetId),
            sourcePassIds = listOf(packet.passId),
        )
        sealGPUCorePrimitiveCoverageMaskPreparedCommandAuthority(
            unit,
            packet,
            packetStream,
            expectedPassId,
            expectedLoadStoreLabel,
        )
    }
    val sealed = when (seal) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition ->
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition(
                seal.sourceStepIndex,
                sealedUnits.map {
                    it as? GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer
                        ?: error("Coverage-mask producer partition changed unit kind")
                },
            )
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition ->
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition(
                seal.sourceStepIndex,
                sealedUnits.map {
                    it as? GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
                        ?: error("Coverage-mask consumer partition changed unit kind")
                },
            )
        else -> error("Coverage-mask partition seal requires a multi-packet partition")
    }
    sealed.requireExactCoverageMaskPassCommandAuthority(stream)
    return sealed
}

/** Refuses any post-preflight mutation of the exact coverage-mask typed command stream. */
internal fun GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal
    .requireExactCoverageMaskPassCommandAuthority(stream: GPUPassCommandStream) {
    if (this is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition ||
        this is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition
    ) {
        val authorities = units().map { unit -> when (unit) {
            is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> unit.commandAuthority
            is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> unit.commandAuthority
            else -> error("Coverage-mask partition contains a non-unit seal")
        } ?: throw IllegalArgumentException("Coverage-mask partition command authority is not sealed") }
        val expectedCommands = coverageMaskCommands(authorities.first()).dropLast(1) +
            authorities.drop(1).flatMap { authority ->
                coverageMaskCommands(authority).drop(1).dropLast(1)
            } + coverageMaskCommands(authorities.last()).takeLast(1)
        require(stream.passId == authorities.first().passId &&
            stream.sourcePassIds == authorities
                .flatMap(GPUCorePrimitiveCoverageMaskPreparedCommandAuthority::sourcePassIds)
                .distinct() &&
            stream.commands == expectedCommands
        ) { "Coverage-mask partition command stream differs from its sealed ordered units" }
        return
    }
    val authority = when (this) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> commandAuthority
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> commandAuthority
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> throw IllegalArgumentException("Coverage-mask partition command authority is not sealed")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty -> return
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing ->
            throw IllegalArgumentException("Coverage-mask command authority is missing")
    } ?: throw IllegalArgumentException("Coverage-mask typed command authority is not sealed")
    requireExactCoverageMaskPassCommandAuthority(stream, authority)
}

private fun GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal
    .requireExactCoverageMaskPassCommandAuthority(
        stream: GPUPassCommandStream,
        authority: GPUCorePrimitiveCoverageMaskPreparedCommandAuthority,
    ) {
    val expectedCommands = coverageMaskCommands(authority)
    val retainedSlice = when (this) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> uniformSlice
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> uniformSlice
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    val retainedGeometry = (this as?
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer)?.geometrySlice
    val retainedDraw = when (this) {
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> draw
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> draw
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
        -> error("A coverage-mask command authority requires one packet unit")
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
        -> error("Unreachable coverage-mask scope seal")
    }
    require(stream.passId == authority.passId &&
        stream.sourcePassIds == authority.sourcePassIds &&
        stream.commands == expectedCommands && retainedSlice == authority.uniformSlice &&
        retainedGeometry == authority.geometrySlice && retainedDraw == authority.draw
    ) { "Coverage-mask typed command stream differs from its sealed authority" }
}

private fun coverageMaskCommands(
    authority: GPUCorePrimitiveCoverageMaskPreparedCommandAuthority,
): List<GPUPassCommand> = buildList {
        add(GPUPassCommand.BeginRenderPass(
            authority.targetStateHash,
            authority.loadStoreLabel,
        ))
        add(GPUPassCommand.SetRenderPipeline(authority.pipelineKey, authority.packetId))
        add(GPUPassCommand.SetBindGroup(
            authority.bindingLayoutHash,
            authority.uniformSlot,
            authority.resourceSlot,
            authority.packetId,
        ))
        authority.vertexBufferSlot?.let { slot ->
            add(GPUPassCommand.SetVertexBuffer(slot, authority.packetId))
        }
        authority.indexFormatLabel?.let { format ->
            add(GPUPassCommand.SetIndexBuffer(format, authority.packetId))
        }
        authority.scissorBoundsHash?.let { scissor ->
            add(GPUPassCommand.SetScissor(scissor, authority.packetId))
        }
        add(GPUPassCommand.Draw(authority.vertexSourceLabel, authority.packetId))
        add(GPUPassCommand.EndRenderPass(authority.passId))
    }

internal data class GPUCorePrimitiveCoverageMaskPreparedProducerLocation(
    val sourceStepIndex: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val sourceOrder: Int,
)

internal data class GPUCorePrimitiveCoverageMaskPreparedConsumerLocation(
    val sourceStepIndex: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val sourceOrder: Int,
    val dependencyFromPreviousConsumerToken: String?,
    val geometrySlice: GPUCorePrimitiveCoverageMaskPreparedGeometrySlice,
)

internal sealed interface GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal {
    data object Empty : GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal

    class Route internal constructor(
        val route: GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted,
        val slabAuthority: GPUCorePrimitiveCoverageMaskPreparedSlabAuthority,
        val attachmentAuthority: GPUCorePrimitiveCoverageMaskPreparedAttachmentAuthority,
        val sceneTarget: GPUFrameTargetRef,
        val sceneTargetGeneration: Long,
        viewsBySourceStepIndex: Map<Int, GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal>,
    ) : GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal {
        private val viewsBySourceStepIndex = immutableMap(viewsBySourceStepIndex)

        fun retainedFor(
            sourceStepIndex: Int,
            packetIds: List<GPUDrawPacketID>,
        ): GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal {
            val view = viewsBySourceStepIndex[sourceStepIndex]
                ?: return GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty
            val expected = view.units().map { unit -> when (unit) {
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> unit.packetId
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> unit.packetId
                else -> error("Coverage-mask scope partition contains a non-unit seal")
            } }
            return if (packetIds == expected) view
            else GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing
        }

    }
}

internal fun sealGPUCorePrimitiveCoverageMaskPreparedFrameRoute(
    route: GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted,
    slabAuthority: GPUCorePrimitiveCoverageMaskPreparedSlabAuthority,
    attachmentAuthority: GPUCorePrimitiveCoverageMaskPreparedAttachmentAuthority,
    sceneTarget: GPUFrameTargetRef,
    sceneTargetGeneration: Long,
    producers: List<GPUCorePrimitiveCoverageMaskPreparedProducerLocation>,
    consumers: List<GPUCorePrimitiveCoverageMaskPreparedConsumerLocation>,
): GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route {
    val seal = slabAuthority.uniformSlabSeal
    require(producers.size == route.producers.size && consumers.size == route.consumers.size &&
        producers.zip(route.producers).all { (location, producer) ->
            location.sourceOrder == producer.sourceOrder
        } && consumers.zip(route.consumers).all { (location, consumer) ->
            location.packetId == consumer.packetId && location.commandId == consumer.commandId &&
                location.sourceOrder == consumer.sourceOrder
        } && producers.zip(seal.producerSlots).all { (location, slot) ->
            location.packetId == slot.packetId && location.commandId == slot.commandId &&
                location.sourceOrder == slot.sourceOrder
        } && consumers.zip(seal.consumerSlots).all { (location, slot) ->
            location.packetId == slot.packetId && location.commandId == slot.commandId &&
                location.sourceOrder == slot.sourceOrder &&
                location.dependencyFromPreviousConsumerToken ==
                slot.dependencyFromPreviousConsumerToken
        } && producers.map { it.sourceStepIndex }.zipWithNext().all { (left, right) -> left <= right } &&
        consumers.map { it.sourceStepIndex }.zipWithNext().all { (left, right) -> left <= right } &&
        producers.lastOrNull()?.sourceStepIndex?.let { producerLast ->
            consumers.firstOrNull()?.sourceStepIndex?.let { consumerFirst -> producerLast < consumerFirst }
                ?: true
        } != false &&
        sceneTargetGeneration >= 0L &&
        attachmentAuthority.resource.value == route.attachment.logicalReference &&
        route.attachment.resourceGeneration == 0L
    ) { "Prepared coverage-mask frame locations must exactly match the sealed pure route" }
    require(seal.producerPacketIds == producers.map { it.packetId } &&
        seal.consumerPacketIds == consumers.map { it.packetId }
    ) { "Prepared coverage-mask uniform slots must match frame packet order" }
    fun uniformSlice(slotIndex: Int): GPUCorePrimitiveCoverageMaskPreparedUniformSlice =
        seal.plan.slots[slotIndex].let { slot ->
            GPUCorePrimitiveCoverageMaskPreparedUniformSlice(
                slabAuthority.uniformResource,
                slabAuthority.uniformGeneration,
                slotIndex,
                slot.alignedOffset,
                slot.payloadBytes,
                slot.allocatedBytes,
            )
        }
    val views = linkedMapOf<Int, GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal>()
    val producerUnits = producers.mapIndexed { index, location ->
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer(
            location.sourceStepIndex,
            location.packetId,
            location.commandId,
            location.sourceOrder,
            route,
            slabAuthority,
            attachmentAuthority,
            uniformSlice(index),
        )
    }
    producerUnits.groupBy { unit -> unit.sourceStepIndex }.forEach { (sourceStepIndex, units) ->
        views[sourceStepIndex] = units.singleOrNull() ?:
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition(sourceStepIndex, units)
    }
    val consumerUnits = consumers.mapIndexed { index, location ->
        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer(
            location.sourceStepIndex,
            location.packetId,
            location.commandId,
            location.sourceOrder,
            location.dependencyFromPreviousConsumerToken,
            index == consumers.lastIndex,
            route,
            slabAuthority,
            attachmentAuthority,
            uniformSlice(producers.size + index),
            location.geometrySlice,
            sceneTarget,
            sceneTargetGeneration,
        )
    }
    consumerUnits.groupBy { unit -> unit.sourceStepIndex }.forEach { (sourceStepIndex, units) ->
        views[sourceStepIndex] = units.singleOrNull() ?:
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition(sourceStepIndex, units)
    }
    return GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route(
        route,
        slabAuthority,
        attachmentAuthority,
        sceneTarget,
        sceneTargetGeneration,
        views,
    )
}
