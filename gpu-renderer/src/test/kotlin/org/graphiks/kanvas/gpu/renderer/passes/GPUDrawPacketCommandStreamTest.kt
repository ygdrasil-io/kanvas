package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandClass
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandEncoderPlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope
import org.graphiks.kanvas.gpu.renderer.execution.GPUDeviceGeneration
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

/** Verifies the Dawn-shaped packet and command-stream scaffold from spec 37. */
class GPUDrawPacketCommandStreamTest {
    @Test
    fun `draw packet captures immutable pass local evidence without backend handles`() {
        val diagnostics = mutableListOf(
            GPUPassDiagnostic(
                code = "packet.built",
                passId = "main-pass",
                invocationId = "draw-1",
                terminal = false,
            ),
        )
        val packet = packet(
            packetId = GPUDrawPacketID("packet-1"),
            commandId = 1,
            sortKey = 100L,
            diagnostics = diagnostics,
        )

        diagnostics += GPUPassDiagnostic(
            code = "caller.mutated",
            passId = "main-pass",
            invocationId = "draw-1",
            terminal = true,
        )

        assertEquals(GPUDrawPacketRole.Shading, packet.role)
        assertEquals(GPURenderStepID("fill-rect"), packet.renderStepId)
        assertEquals(GPURenderPipelineKey("render:solid-fill"), packet.renderPipelineKey)
        assertEquals(uniformSlot(1), packet.uniformSlot)
        assertEquals(resourceSlot(1), packet.resourceSlot)
        assertEquals(listOf("packet.built"), packet.diagnosticCodes)
        assertEquals("draw-command:1:fill-rect", packet.provenanceLabel)
        assertTrue(enumValues<GPUDrawPacketRole>().contains(GPUDrawPacketRole.Discard))
    }

    @Test
    fun `packet stream snapshots packets and exposes stable ordering keys`() {
        val mutablePackets = mutableListOf(
            packet(packetId = GPUDrawPacketID("packet-1"), commandId = 1, sortKey = 200L),
            packet(packetId = GPUDrawPacketID("packet-2"), commandId = 2, sortKey = 100L),
        )
        val stream = GPUDrawPacketStream(
            streamId = "packet-stream-main",
            passId = "main-pass",
            packets = mutablePackets,
        )

        mutablePackets += packet(packetId = GPUDrawPacketID("packet-3"), commandId = 3, sortKey = 300L)

        assertEquals(listOf(GPUDrawPacketID("packet-1"), GPUDrawPacketID("packet-2")), stream.packetIds)
        assertEquals(listOf(1, 2), stream.commandIds)
        assertEquals(listOf(200L, 100L), stream.sortKeys)
        assertEquals(listOf(GPURenderPipelineKey("render:solid-fill")), stream.renderPipelineKeys)
        assertEquals(2, stream.packetCount)
    }

    @Test
    fun `pass command stream lowers packets to Dawn ordered facade operations`() {
        val packetStream = packetStream()

        val commandStream = GPUPassCommandStream.fromDrawPacketStream(
            streamId = "pass-command-stream-main",
            packetStream = packetStream,
            targetStateHash = "rgba8-premul-msaa1",
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
        assertEquals(packetStream.packetIds, commandStream.sourcePacketIds.distinct())
        assertTrue(commandStream.commands.first() is GPUPassCommand.BeginRenderPass)
        assertTrue(commandStream.commands.last() is GPUPassCommand.EndRenderPass)
    }

    @Test
    fun `encoder plan preserves packet command and resource generation evidence`() {
        val resources = mutableListOf("payload-generation:7", "target-generation:11")
        val packetStream = packetStream()
        val commandStream = GPUPassCommandStream.fromDrawPacketStream(
            streamId = "pass-command-stream-main",
            packetStream = packetStream,
            targetStateHash = "rgba8-premul-msaa1",
            loadStoreLabel = "clear-store",
        )
        val plan = GPUCommandEncoderPlan.fromPassCommandStream(
            planId = "encoder-plan-frame-1",
            contextIdentity = "wgpu-context-main",
            deviceGeneration = GPUDeviceGeneration(4L),
            targetGeneration = 11L,
            scope = GPUCommandScope.Render(
                label = "main-pass",
                useTokenLabels = resources,
            ),
            packetStream = packetStream,
            passCommandStream = commandStream,
            resourceGenerationLabels = resources,
        )

        resources += "caller-mutated"

        assertEquals(GPUCommandClass.Render, plan.commandClass)
        assertEquals(packetStream.streamId, plan.packetStreamId)
        assertEquals(commandStream.streamId, plan.passCommandStreamId)
        assertEquals(2, plan.packetCount)
        assertEquals(commandStream.commandCount, plan.passCommandCount)
        assertEquals(commandStream.commandLabels, plan.facadeOperationClasses)
        assertEquals(listOf("payload-generation:7", "target-generation:11"), plan.resourceGenerationLabels)
    }

    private fun packetStream(): GPUDrawPacketStream =
        GPUDrawPacketStream(
            streamId = "packet-stream-main",
            passId = "main-pass",
            packets = listOf(
                packet(packetId = GPUDrawPacketID("packet-1"), commandId = 1, sortKey = 100L),
                packet(packetId = GPUDrawPacketID("packet-2"), commandId = 2, sortKey = 200L),
            ),
        )

    private fun packet(
        packetId: GPUDrawPacketID,
        commandId: Int,
        sortKey: Long,
        diagnostics: List<GPUPassDiagnostic> = emptyList(),
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = packetId,
            commandIdValue = commandId,
            analysisRecordId = "analysis-$commandId",
            passId = "main-pass",
            layerId = "root-layer",
            bindingListId = "bindings-$commandId",
            insertionReasonCode = "native-fill-rect",
            sortKey = sortKey,
            sortKeyPreimage = "paint|clip|transform|$commandId",
            renderStepId = GPURenderStepID("fill-rect"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            renderPipelineKey = GPURenderPipelineKey("render:solid-fill"),
            bindingLayoutHash = "layout-solid-v1",
            uniformSlot = uniformSlot(commandId),
            resourceSlot = resourceSlot(commandId),
            vertexSourceLabel = "solid-quad",
            scissorBoundsHash = "scissor-0-0-64-64",
            targetStateHash = "rgba8-premul-msaa1",
            originalPaintOrder = commandId,
            resourceGeneration = 7L,
            diagnostics = diagnostics,
        )

    private fun uniformSlot(commandId: Int): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("uniform-$commandId"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-$commandId"),
            byteOffset = ((commandId - 1) * 64).toLong(),
        )

    private fun resourceSlot(commandId: Int): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("resource-$commandId"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-$commandId"),
            bindingIndex = 0,
        )
}
