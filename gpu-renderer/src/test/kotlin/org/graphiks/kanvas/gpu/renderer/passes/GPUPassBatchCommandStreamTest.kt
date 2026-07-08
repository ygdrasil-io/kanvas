package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

class GPUPassBatchCommandStreamTest {
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

    private fun requestForAll(packetStream: GPUDrawPacketStream): GPUPassBatcherRequest =
        GPUPassBatcherRequest(
            packetStream = packetStream,
            eligibilityByPacketId = packetStream.packets.associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    fixedStateHash = "fixed:src-over",
                    queueGuard = GPUPassBatchQueueGuard(
                        requiredRetainedRefs = listOf("lease:uniform-slab:frame-1"),
                        retainedRefs = listOf("lease:uniform-slab:frame-1"),
                    ),
                )
            },
        )

    private fun packetStream(vararg packets: GPUDrawPacket): GPUDrawPacketStream =
        GPUDrawPacketStream(
            streamId = "packet-stream-main",
            passId = "main-pass",
            packets = packets.toList(),
        )

    private fun packet(packetId: String, commandId: Int, target: String): GPUDrawPacket =
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
            role = GPUDrawPacketRole.Shading,
            renderPipelineKey = GPURenderPipelineKey("render:solid-fill"),
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
