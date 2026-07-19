package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference

class GPUPassBatchCommandStreamTest {
    @Test
    fun `packet stream overload omits bind group for explicit no-bindings producer`() {
        val producer = packet("producer", 1, "target-a")
        val consumer = packet("consumer", 2, "target-a")
        val packetStream = packetStream(producer, consumer)
        val plan = GPUPassBatcher().plan(requestForAll(packetStream))

        val commandStream = GPUPassCommandStream.fromBatchPlan(
            streamId = "batch-command-stream-main",
            packetStream = packetStream,
            batchPlan = plan,
            loadStoreLabel = "clear-store",
            operandBridge = clipStencilOperandBridge(producer, consumer),
        )

        assertEquals(
            listOf(
                "beginRenderPass",
                "setRenderPipeline", "setVertexBuffer", "setIndexBuffer", "setScissor", "draw",
                "setRenderPipeline", "setBindGroup", "setVertexBuffer", "setIndexBuffer", "setScissor", "draw",
                "endRenderPass",
            ),
            commandStream.commandLabels,
        )
        assertEquals(
            listOf(consumer.packetId),
            commandStream.commands.filterIsInstance<GPUPassCommand.SetBindGroup>().map { it.packetId },
        )
    }

    @Test
    fun `render step overload omits bind group for explicit no-bindings producer`() {
        val producer = packet("producer", 1, "target-a")
        val consumer = packet("consumer", 2, "target-a")
        val packetStream = packetStream(producer, consumer)
        val plan = GPUPassBatcher().plan(requestForAll(packetStream))

        val commandStream = GPUPassCommandStream.fromBatchPlan(
            streamId = "batch-command-stream-main",
            batchPlan = plan,
            loadStoreLabel = "clear-store",
            operandBridge = clipStencilOperandBridge(producer, consumer),
        )

        assertEquals(
            listOf(
                "beginRenderPass",
                "setRenderPipeline", "setVertexBuffer", "setIndexBuffer", "setScissor", "draw",
                "setRenderPipeline", "setBindGroup", "setVertexBuffer", "setIndexBuffer", "setScissor", "draw",
                "endRenderPass",
            ),
            commandStream.commandLabels,
        )
        assertEquals(
            listOf(consumer.packetId),
            commandStream.commands.filterIsInstance<GPUPassCommand.SetBindGroup>().map { it.packetId },
        )
    }

    @Test
    fun `single accepted batch lowers to one render pass`() {
        val packetStream = packetStream(
            packet("packet-1", 1, "target-a"),
            packet("packet-2", 2, "target-a"),
        )
        val plan = GPUPassBatcher().plan(requestForAll(packetStream))

        val commandStream = GPUPassCommandStream.fromBatchPlan(
            streamId = "batch-command-stream-main",
            packetStream = packetStream,
            batchPlan = plan,
            loadStoreLabel = "clear-store",
        )

        assertEquals(
            listOf(
                "beginRenderPass",
                "setRenderPipeline",
                "setBindGroup",
                "setScissor",
                "draw",
                "setRenderPipeline",
                "setBindGroup",
                "setScissor",
                "draw",
                "endRenderPass",
            ),
            commandStream.commandLabels,
        )
        assertEquals(10, commandStream.commandCount)
        assertContains(commandStream.dumpLines(), "passes.command draw packet=packet-1 vertex=solid-quad")
        assertContains(commandStream.dumpLines(), "passes.command draw packet=packet-2 vertex=solid-quad")
        assertContains(commandStream.dumpLines().joinToString("\n"), "batch-plan-line-0")
    }

    @Test
    fun `cut batches lower to separate render pass scopes in order`() {
        val packetStream = packetStream(
            packet("packet-1", 1, "target-a"),
            packet("packet-2", 2, "target-b"),
        )
        val plan = GPUPassBatcher().plan(requestForAll(packetStream))

        val commandStream = GPUPassCommandStream.fromBatchPlan(
            streamId = "batch-command-stream-main",
            packetStream = packetStream,
            batchPlan = plan,
            loadStoreLabel = "clear-store",
        )

        assertEquals(
            listOf(
                "beginRenderPass", "setRenderPipeline", "setBindGroup", "setScissor", "draw", "endRenderPass",
                "beginRenderPass", "setRenderPipeline", "setBindGroup", "setScissor", "draw", "endRenderPass",
            ),
            commandStream.commandLabels,
        )
        assertEquals(listOf("packet-1", "packet-2"), commandStream.sourcePacketIds.distinct().map { it.value })
    }

    @Test
    fun `from batch plan refuses submission complete lease missing from retained refs`() {
        val packetStream = packetStream(
            packet("packet-1", 1, "target-a"),
            packet("packet-2", 2, "target-a"),
        )
        val plan = GPUPassBatcher().plan(
            requestForAll(
                packetStream = packetStream,
                retainedRefs = listOf("lease:uniform-slab:frame-1"),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            GPUPassCommandStream.fromBatchPlan(
                streamId = "batch-command-stream-main",
                packetStream = packetStream,
                batchPlan = plan,
                loadStoreLabel = "clear-store",
                materialization = materializedDecision(resourceLease("lease:bind-group:frame-1")),
            )
        }

        assertContains(error.message.orEmpty(), "submission-complete")
        assertContains(error.message.orEmpty(), "lease:bind-group:frame-1")
        assertContains(error.message.orEmpty(), "retainedRefs")
    }

    @Test
    fun `from batch plan accepts retained submission complete lease from materialization`() {
        val packetStream = packetStream(
            packet("packet-1", 1, "target-a"),
            packet("packet-2", 2, "target-a"),
        )
        val plan = GPUPassBatcher().plan(
            requestForAll(
                packetStream = packetStream,
                retainedRefs = listOf("lease:uniform-slab:frame-1", "lease:bind-group:frame-1"),
            ),
        )

        val commandStream = GPUPassCommandStream.fromBatchPlan(
            streamId = "batch-command-stream-main",
            packetStream = packetStream,
            batchPlan = plan,
            loadStoreLabel = "clear-store",
            materialization = materializedDecision(resourceLease("lease:bind-group:frame-1")),
        )

        assertEquals(10, commandStream.commandCount)
        assertContains(commandStream.dumpLines().joinToString("\n"), "batch-plan-line-0")
    }

    @Test
    fun `from batch plan refuses submission complete lease missing from one batch retained refs`() {
        val packetStream = packetStream(
            packet("packet-1", 1, "target-a"),
            packet("packet-2", 2, "target-b"),
        )
        val plan = GPUPassBatchPlan(
            streamId = packetStream.streamId,
            passId = packetStream.passId,
            batches = listOf(
                batch(
                    batchId = "batch-1",
                    packet = packetStream.packets[0],
                    targetStateHash = "target-a",
                    retainedRefs = listOf("lease:uniform-slab:frame-1", "lease:bind-group:frame-1"),
                ),
                batch(
                    batchId = "batch-2",
                    packet = packetStream.packets[1],
                    targetStateHash = "target-b",
                    retainedRefs = listOf("lease:uniform-slab:frame-1"),
                ),
            ),
            cuts = listOf(
                GPUPassBatchCut(
                    beforePacketId = packetStream.packets[0].packetId,
                    afterPacketId = packetStream.packets[1].packetId,
                    reasonCode = GPUPassBatchReason.TARGET_CHANGED,
                    message = "target target-a cannot batch with target-b",
                ),
            ),
            diagnostics = packetStream.diagnostics,
            inputPacketCount = packetStream.packetCount,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            GPUPassCommandStream.fromBatchPlan(
                streamId = "batch-command-stream-main",
                packetStream = packetStream,
                batchPlan = plan,
                loadStoreLabel = "clear-store",
                materialization = materializedDecision(resourceLease("lease:bind-group:frame-1")),
            )
        }

        assertContains(error.message.orEmpty(), "submission-complete")
        assertContains(error.message.orEmpty(), "lease:bind-group:frame-1")
        assertContains(error.message.orEmpty(), "batch-2")
    }

    @Test
    fun `from batch plan refuses plans that omit cut packets from lowered batches`() {
        val packetStream = packetStream(
            packet("packet-1", 1, "target-a"),
            packet("packet-2", 2, "target-a"),
        )
        val plan = GPUPassBatchPlan(
            streamId = packetStream.streamId,
            passId = packetStream.passId,
            batches = listOf(
                batch(
                    batchId = "batch-1",
                    packet = packetStream.packets.first(),
                    targetStateHash = "target-a",
                    retainedRefs = emptyList(),
                ),
            ),
            cuts = listOf(
                GPUPassBatchCut(
                    beforePacketId = packetStream.packets.first().packetId,
                    afterPacketId = packetStream.packets.last().packetId,
                    reasonCode = GPUPassBatchReason.BLEND_OR_FIXED_STATE_CHANGED,
                    message = "fixture intentionally omits the cut packet",
                ),
            ),
            inputPacketCount = packetStream.packetCount,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            GPUPassCommandStream.fromBatchPlan(
                streamId = "batch-command-stream-main",
                packetStream = packetStream,
                batchPlan = plan,
                loadStoreLabel = "clear-store",
            )
        }

        assertContains(error.message.orEmpty(), "every input packet")
        assertContains(error.message.orEmpty(), "packet-2")
    }

    private fun requestForAll(
        packetStream: GPUDrawPacketStream,
        retainedRefs: List<String> = listOf("lease:uniform-slab:frame-1"),
    ): GPUPassBatcherRequest =
        GPUPassBatcherRequest(
            packetStream = packetStream,
            eligibilityByPacketId = packetStream.packets.associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(
                        requiredRetainedRefs = retainedRefs,
                        retainedRefs = retainedRefs,
                    ),
                )
            },
        )

    private fun materializedDecision(vararg leases: GPUResourceLease): GPUResourceMaterializationDecision.Materialized =
        GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            resourceLeases = leases.toList(),
        )

    private fun clipStencilOperandBridge(
        producer: GPUDrawPacket,
        consumer: GPUDrawPacket,
    ): List<GPUPassCommandOperandBridge> =
        listOf(
            operandBridge(producer, "setRenderPipeline", GPUMaterializedCommandOperandKind.RenderPipeline),
            operandBridge(producer, "setVertexBuffer", GPUMaterializedCommandOperandKind.VertexBuffer),
            operandBridge(producer, "setIndexBuffer", GPUMaterializedCommandOperandKind.IndexBuffer),
            operandBridge(consumer, "setRenderPipeline", GPUMaterializedCommandOperandKind.RenderPipeline),
            operandBridge(consumer, "setBindGroup", GPUMaterializedCommandOperandKind.BindGroup),
            operandBridge(consumer, "setVertexBuffer", GPUMaterializedCommandOperandKind.VertexBuffer),
            operandBridge(consumer, "setIndexBuffer", GPUMaterializedCommandOperandKind.IndexBuffer),
        )

    private fun operandBridge(
        packet: GPUDrawPacket,
        commandLabel: String,
        kind: GPUMaterializedCommandOperandKind,
    ): GPUPassCommandOperandBridge =
        GPUPassCommandOperandBridge(
            packetId = packet.packetId,
            commandLabel = commandLabel,
            operand = GPUMaterializedCommandOperandReference(
                label = "$commandLabel.${packet.packetId.value}",
                kind = kind,
                descriptorHash = "descriptor.$commandLabel.${packet.packetId.value}",
                deviceGeneration = 7L,
                ownerScope = "frame.clip-stencil",
                usageLabels = listOf("render"),
                invalidationPolicy = "submission-complete",
            ),
        )

    private fun batch(
        batchId: String,
        packet: GPUDrawPacket,
        targetStateHash: String,
        retainedRefs: List<String>,
    ): GPUPassBatch =
        GPUPassBatch(
            batchId = batchId,
            packets = listOf(packet),
            kind = GPUPassBatchKind.SolidFill,
            targetStateHash = targetStateHash,
            queueGuard = GPUPassBatchQueueGuard(
                requiredRetainedRefs = retainedRefs,
                retainedRefs = retainedRefs,
            ),
        )

    private fun resourceLease(leaseId: String): GPUResourceLease =
        GPUResourceLease(
            leaseId = leaseId,
            resourceKind = GPUResourceLeaseKind.BindGroup,
            deviceGeneration = 11L,
            descriptorHash = "bind-group-layout-v1",
            ownerScope = "frame-1",
            usageLabels = listOf("uniform"),
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
        )

    private fun packetStream(vararg packets: GPUDrawPacket): GPUDrawPacketStream =
        GPUDrawPacketStream(
            streamId = "packet-stream-main",
            passId = "main-pass",
            packets = packets.toList(),
        )

    private fun packet(
        packetId: String,
        commandId: Int,
        target: String,
        role: GPUDrawPacketRole = GPUDrawPacketRole.Shading,
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = GPUDrawPacketID(packetId),
            commandIdValue = commandId,
            analysisRecordId = "analysis-$commandId",
            passId = "main-pass",
            layerId = "root-layer",
            bindingListId = "bindings-$commandId",
            insertionReasonCode = "native-fill-rect",
            sortKey = commandId.toLong() * 100L,
            sortKeyPreimage = "paint|clip|transform|$commandId",
            renderStepId = GPURenderStepID("fill-rect"),
            renderStepVersion = 1,
            role = role,
            renderPipelineKey = if (role == GPUDrawPacketRole.Shading) GPURenderPipelineKey("render:solid-fill") else null,
            bindingLayoutHash = "layout-solid-v1",
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("uniform-$commandId"),
                fingerprint = GPUPayloadFingerprint("sha256:uniform-$commandId"),
                byteOffset = 0L,
            ),
            resourceSlot = GPUResourceBindingSlot(
                slotId = GPUPayloadSlotID("resource-$commandId"),
                fingerprint = GPUPayloadFingerprint("sha256:resource-$commandId"),
                bindingIndex = 0,
            ),
            vertexSourceLabel = "solid-quad",
            scissorBoundsHash = "scissor-0-0-64-64",
            targetStateHash = target,
            originalPaintOrder = commandId,
            resourceGeneration = 7L,
        )
}
