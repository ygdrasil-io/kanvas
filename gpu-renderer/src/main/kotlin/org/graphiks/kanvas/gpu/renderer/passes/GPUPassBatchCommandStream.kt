package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision

/**
 * Lowers an accepted pass-batch plan into the explicit render-pass command stream used by
 * runtime evidence and follow-on diagnostics.
 */
fun GPUPassCommandStream.Companion.fromBatchPlan(
    streamId: String,
    packetStream: GPUDrawPacketStream,
    batchPlan: GPUPassBatchPlan,
    loadStoreLabel: String,
    materialization: GPUResourceMaterializationDecision.Materialized? = null,
    operandBridge: List<GPUPassCommandOperandBridge> = emptyList(),
): GPUPassCommandStream {
    require(streamId.isNotBlank()) { "GPUPassCommandStream.fromBatchPlan streamId must not be blank" }
    require(loadStoreLabel.isNotBlank()) { "GPUPassCommandStream.fromBatchPlan loadStoreLabel must not be blank" }
    require(packetStream.streamId == batchPlan.streamId) {
        "Batch plan stream ${batchPlan.streamId} must match packet stream ${packetStream.streamId}"
    }
    require(packetStream.passId == batchPlan.passId) {
        "Batch plan pass ${batchPlan.passId} must match packet stream pass ${packetStream.passId}"
    }
    val submissionCompleteLeaseIds = materialization
        ?.dumpResourceLeaseSnapshot
        .orEmpty()
        .filter { lease -> lease.releasePolicy == "submission-complete" }
        .map { lease -> lease.leaseId }
    batchPlan.batches.forEach { batch ->
        submissionCompleteLeaseIds.forEach { leaseId ->
            require(leaseId in batch.queueGuard.retainedRefs) {
                "GPUPassCommandStream.fromBatchPlan requires batch ${batch.batchId} to retain " +
                    "submission-complete lease $leaseId in queueGuard retainedRefs before grouped command emission"
            }
        }
    }
    val expectedPacketIds = packetStream.packets.groupingBy { it.packetId }.eachCount()
    val loweredPacketIds = batchPlan.batches.flatMap { batch -> batch.packets }.groupingBy { it.packetId }.eachCount()
    require(loweredPacketIds == expectedPacketIds) {
        val missingPacketIds = expectedPacketIds.keys - loweredPacketIds.keys
        val unexpectedPacketIds = loweredPacketIds.keys - expectedPacketIds.keys
        val duplicatePacketIds = loweredPacketIds.filterValues { count -> count > 1 }.keys
        "GPUPassCommandStream.fromBatchPlan requires every input packet to appear in exactly one batch; " +
            "missing=${missingPacketIds.map { it.value }.ifEmpty { listOf("none") }.joinToString(",")} " +
            "unexpected=${unexpectedPacketIds.map { it.value }.ifEmpty { listOf("none") }.joinToString(",")} " +
            "duplicate=${duplicatePacketIds.map { it.value }.ifEmpty { listOf("none") }.joinToString(",")}"
    }

    val materializedOperandBridge =
        materialization?.dumpOperandBridgeSnapshot
            ?.map(GPUPassCommandOperandBridge::fromMaterializedBinding)
            .orEmpty()
    require(materializedOperandBridge.isEmpty() || operandBridge.isEmpty()) {
        "GPUPassCommandStream accepts either provider materialization or explicit operandBridge, not both"
    }
    val effectiveOperandBridge = materializedOperandBridge.ifEmpty { operandBridge }

    val commands = buildList {
        for (batch in batchPlan.batches) {
            add(
                GPUPassCommand.BeginRenderPass(
                    targetStateHash = batch.targetStateHash,
                    loadStoreLabel = loadStoreLabel,
                ),
            )
            for (packet in batch.packets) {
                val renderPipelineKey = requireNotNull(packet.renderPipelineKey) {
                    "Packet ${packet.packetId.value} cannot be lowered from batch plan without renderPipelineKey"
                }
                add(
                    GPUPassCommand.SetRenderPipeline(
                        pipelineKey = renderPipelineKey,
                        packetId = packet.packetId,
                    ),
                )
                add(
                    GPUPassCommand.SetBindGroup(
                        bindingLayoutHash = packet.bindingLayoutHash,
                        uniformSlot = packet.uniformSlot,
                        resourceSlot = packet.resourceSlot,
                        packetId = packet.packetId,
                    ),
                )
                if (effectiveOperandBridge.any { bridge ->
                        bridge.packetId == packet.packetId && bridge.commandLabel == "setVertexBuffer"
                    }
                ) {
                    add(GPUPassCommand.SetVertexBuffer(slot = 0, packetId = packet.packetId))
                }
                if (effectiveOperandBridge.any { bridge ->
                        bridge.packetId == packet.packetId && bridge.commandLabel == "setIndexBuffer"
                    }
                ) {
                    add(GPUPassCommand.SetIndexBuffer(indexFormatLabel = "uint32", packetId = packet.packetId))
                }
                packet.scissorBoundsHash?.let { scissorBoundsHash ->
                    add(
                        GPUPassCommand.SetScissor(
                            scissorBoundsHash = scissorBoundsHash,
                            packetId = packet.packetId,
                        ),
                    )
                }
                add(
                    GPUPassCommand.Draw(
                        vertexSourceLabel = packet.vertexSourceLabel,
                        packetId = packet.packetId,
                    ),
                )
            }
            add(GPUPassCommand.EndRenderPass(passId = packetStream.passId))
        }
    }

    val batchDiagnostics = batchPlan.dumpLines().mapIndexed { index, line ->
        val lineCode = index.toString().padStart(3, '0')
        GPUPassDiagnostic(
            code = "batch-plan-line-$lineCode",
            passId = packetStream.passId,
            invocationId = line.replace(' ', '_').take(120),
            terminal = false,
        )
    }

    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = packetStream.streamId,
        passId = packetStream.passId,
        commands = commands,
        diagnostics = (packetStream.diagnostics + batchPlan.diagnostics + batchDiagnostics).distinct(),
        operandBridge = effectiveOperandBridge,
    )
}

/**
 * Task 8 lowering for one already validated RenderPassStep.
 *
 * Unlike the legacy overload, packets may retain different source `passId` values because the
 * Task 6 segment is the enclosing pass authority. Every internal batch shares one target state,
 * so load/clear is emitted exactly once for the entire step.
 */
fun GPUPassCommandStream.Companion.fromBatchPlan(
    streamId: String,
    batchPlan: GPUPassBatchPlan,
    loadStoreLabel: String,
    materialization: GPUResourceMaterializationDecision.Materialized? = null,
    operandBridge: List<GPUPassCommandOperandBridge> = emptyList(),
): GPUPassCommandStream {
    require(streamId.isNotBlank()) { "GPUPassCommandStream.fromBatchPlan streamId must not be blank" }
    require(loadStoreLabel.isNotBlank()) { "GPUPassCommandStream.fromBatchPlan loadStoreLabel must not be blank" }
    require(batchPlan.batches.isNotEmpty()) {
        "GPUPassCommandStream.fromBatchPlan requires at least one batch"
    }
    val packets = batchPlan.batches.flatMap { it.packets }
    require(packets.size == batchPlan.packetCount) {
        "GPUPassCommandStream.fromBatchPlan batches must cover the declared packet count"
    }
    require(packets.map { it.packetId }.distinct().size == packets.size) {
        "GPUPassCommandStream.fromBatchPlan packets must appear exactly once"
    }
    val targetStateHashes = batchPlan.batches.map { it.targetStateHash }.distinct()
    require(targetStateHashes.size == 1) {
        "Task 8 RenderPassStep batches must share one target state"
    }
    val submissionCompleteLeaseIds = materialization
        ?.dumpResourceLeaseSnapshot
        .orEmpty()
        .filter { lease -> lease.releasePolicy == "submission-complete" }
        .map { lease -> lease.leaseId }
    batchPlan.batches.forEach { batch ->
        submissionCompleteLeaseIds.forEach { leaseId ->
            require(leaseId in batch.queueGuard.retainedRefs) {
                "GPUPassCommandStream.fromBatchPlan requires batch ${batch.batchId} to retain " +
                    "submission-complete lease $leaseId in queueGuard retainedRefs before grouped command emission"
            }
        }
    }

    val materializedOperandBridge = materialization?.dumpOperandBridgeSnapshot
        ?.map(GPUPassCommandOperandBridge::fromMaterializedBinding)
        .orEmpty()
    require(materializedOperandBridge.isEmpty() || operandBridge.isEmpty()) {
        "GPUPassCommandStream accepts either provider materialization or explicit operandBridge, not both"
    }
    val effectiveOperandBridge = materializedOperandBridge.ifEmpty { operandBridge }

    val commands = buildList {
        add(GPUPassCommand.BeginRenderPass(targetStateHashes.single(), loadStoreLabel))
        for (packet in packets) {
            val renderPipelineKey = requireNotNull(packet.renderPipelineKey) {
                "Packet ${packet.packetId.value} cannot be lowered from batch plan without renderPipelineKey"
            }
            add(GPUPassCommand.SetRenderPipeline(renderPipelineKey, packet.packetId))
            add(
                GPUPassCommand.SetBindGroup(
                    bindingLayoutHash = packet.bindingLayoutHash,
                    uniformSlot = packet.uniformSlot,
                    resourceSlot = packet.resourceSlot,
                    packetId = packet.packetId,
                ),
            )
            if (effectiveOperandBridge.any { bridge ->
                    bridge.packetId == packet.packetId && bridge.commandLabel == "setVertexBuffer"
                }
            ) {
                add(GPUPassCommand.SetVertexBuffer(slot = 0, packetId = packet.packetId))
            }
            if (effectiveOperandBridge.any { bridge ->
                    bridge.packetId == packet.packetId && bridge.commandLabel == "setIndexBuffer"
                }
            ) {
                add(GPUPassCommand.SetIndexBuffer(indexFormatLabel = "uint32", packetId = packet.packetId))
            }
            packet.scissorBoundsHash?.let { add(GPUPassCommand.SetScissor(it, packet.packetId)) }
            add(GPUPassCommand.Draw(packet.vertexSourceLabel, packet.packetId))
        }
        add(GPUPassCommand.EndRenderPass(batchPlan.passId))
    }
    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = batchPlan.streamId,
        passId = batchPlan.passId,
        commands = commands,
        diagnostics = batchPlan.diagnostics,
        operandBridge = effectiveOperandBridge,
        sourcePassIds = packets.map { it.passId }.distinct(),
    )
}
