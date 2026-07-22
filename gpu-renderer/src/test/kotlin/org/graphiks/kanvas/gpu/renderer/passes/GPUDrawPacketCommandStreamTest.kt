package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandClass
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandEncoderPlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.ValidatingCommandOperandResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.ValidatingPayloadResourceProvider

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
    fun `pass command stream maps packets to materialized command operands`() {
        val packetStream = packetStream()
        val materialization = ValidatingCommandOperandResourceProvider().materializeCommandOperands(
            request = commandOperandMaterializationRequest(),
            context = targetPreparationContext(),
        )
        val materialized = materialization as GPUResourceMaterializationDecision.Materialized
        val commandStream = GPUPassCommandStream.fromDrawPacketStream(
            streamId = "pass-command-stream-main",
            packetStream = packetStream,
            targetStateHash = "rgba8-premul-msaa1",
            loadStoreLabel = "clear-store",
            materialization = materialized,
        )

        assertEquals(
            listOf(
                "target-view:root",
                "render-pipeline:solid-fill",
                "bind-group:solid-fill:packet-1",
                "vertex-buffer:solid-quad",
            ),
            commandStream.materializedOperandLabels,
        )
        assertContains(
            commandStream.dumpLines(),
            "passes.command-bridge packet=none command=beginRenderPass " +
                "operand=target-view:root kind=render-target deviceGeneration=4 " +
                "owner=render-pass:main-pass usage=render_attachment invalidation=frame-end " +
                "descriptor=sha256:target-view:root facts=loadStore=clear-store",
        )
        assertContains(
            commandStream.dumpLines(),
            "passes.command-bridge packet=packet-1 command=setRenderPipeline " +
                "operand=render-pipeline:solid-fill kind=render-pipeline deviceGeneration=4 " +
                "owner=render-pass:main-pass usage=render invalidation=device-generation " +
                "descriptor=sha256:render-pipeline:solid-fill facts=cache=warm",
        )
        assertContains(
            commandStream.dumpLines(),
            "passes.command-bridge packet=packet-1 command=setBindGroup " +
                "operand=bind-group:solid-fill:packet-1 kind=bind-group deviceGeneration=4 " +
                "owner=render-pass:main-pass usage=texture_binding,uniform invalidation=pass-end " +
                "descriptor=sha256:bind-group:solid-fill:packet-1 facts=layout=layout-solid-v1",
        )
        assertFalse(commandStream.dumpLines().joinToString("\n").contains("WGPU"))
    }

    @Test
    fun `pass command stream references payload materialized bind groups without raw handles`() {
        val packetStream = packetStream()
        val materialization = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = payloadMaterializationRequest(),
            context = targetPreparationContext(),
        )
        val commandStream = GPUPassCommandStream.fromDrawPacketStream(
            streamId = "pass-command-stream-main",
            packetStream = packetStream,
            targetStateHash = "rgba8-premul-msaa1",
            loadStoreLabel = "clear-store",
            materialization = assertIs<GPUResourceMaterializationDecision.Materialized>(materialization),
        )

        assertContains(
            commandStream.dumpLines(),
            "passes.command-bridge packet=packet-1 command=setBindGroup " +
                "operand=bind-group:pass-a:resource:0 kind=bind-group deviceGeneration=4 " +
                "owner=payload-scope:pass-a usage=uniform invalidation=pass-end " +
                "descriptor=layout-solid-v1 facts=bindingCount=1;dynamicOffsets=0;" +
                "layoutHash=layout-solid-v1;resourceDescriptors=uniform:solid-payload;" +
                "uniformBuffer=payload-upload:pass-a:uniform:0",
        )
        assertContains(commandStream.materializedOperandLabels, "bind-group:pass-a:resource:0")
        assertFalse(commandStream.dumpLines().joinToString("\n").contains("WGPU"))
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
            deviceGeneration = GPUDeviceGenerationID(4L),
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

    private fun commandOperandMaterializationRequest(): GPUCommandOperandMaterializationRequest =
        GPUCommandOperandMaterializationRequest(
            targetId = "root-target",
            taskIds = listOf("task-fill-rect"),
            resourcePlanLabels = listOf("first-route-command-operands"),
            operands = listOf(
                commandOperandPlan(
                    packetId = null,
                    commandLabel = "beginRenderPass",
                    label = "target-view:root",
                    kind = GPUMaterializedCommandOperandKind.RenderTarget,
                    usageLabels = setOf("render_attachment"),
                    invalidationPolicy = "frame-end",
                    evidenceFacts = mapOf("loadStore" to "clear-store"),
                ),
                commandOperandPlan(
                    packetId = "packet-1",
                    commandLabel = "setRenderPipeline",
                    label = "render-pipeline:solid-fill",
                    kind = GPUMaterializedCommandOperandKind.RenderPipeline,
                    usageLabels = setOf("render"),
                    invalidationPolicy = "device-generation",
                    evidenceFacts = mapOf("cache" to "warm"),
                ),
                commandOperandPlan(
                    packetId = "packet-1",
                    commandLabel = "setBindGroup",
                    label = "bind-group:solid-fill:packet-1",
                    kind = GPUMaterializedCommandOperandKind.BindGroup,
                    usageLabels = setOf("uniform", "texture_binding"),
                    evidenceFacts = mapOf("layout" to "layout-solid-v1"),
                ),
                commandOperandPlan(
                    packetId = "packet-1",
                    commandLabel = "draw",
                    label = "vertex-buffer:solid-quad",
                    kind = GPUMaterializedCommandOperandKind.VertexBuffer,
                    usageLabels = setOf("vertex"),
                    evidenceFacts = mapOf("topology" to "triangle-list"),
                ),
            ),
        )

    private fun commandOperandPlan(
        packetId: String?,
        commandLabel: String,
        label: String,
        kind: GPUMaterializedCommandOperandKind,
        usageLabels: Set<String>,
        invalidationPolicy: String = "pass-end",
        evidenceFacts: Map<String, String> = emptyMap(),
    ): GPUCommandOperandMaterializationPlan =
        GPUCommandOperandMaterializationPlan(
            packetId = packetId,
            commandLabel = commandLabel,
            label = label,
            kind = kind,
            descriptorHash = "sha256:$label",
            deviceGeneration = 4,
            ownerScope = "render-pass:main-pass",
            requiredUsageLabels = usageLabels,
            availableUsageLabels = usageLabels,
            invalidationPolicy = invalidationPolicy,
            evidenceFacts = evidenceFacts,
        )

    private fun payloadMaterializationRequest(): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = "root-target",
            packetId = "packet-1",
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:solid-fill"),
            uniformBlock = GPUUniformPayloadBlock(
                fingerprint = GPUPayloadFingerprint("uniform-fingerprint-solid"),
                packingPlanHash = "solid-rect-layout-v1",
                byteSize = 64L,
                zeroedPadding = true,
                scope = "pass-a",
                bytes = listOf(1, 2, 3, 4) + List(60) { 0 },
            ),
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("pass-a:uniform:0"),
                fingerprint = GPUPayloadFingerprint("uniform-fingerprint-solid"),
                byteOffset = 0L,
            ),
            resourceBlock = GPUResourceBindingBlock(
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-solid"),
                bindingPlanHash = "layout-solid-v1",
                bindingCount = 1,
                resourceDescriptorLabels = listOf("uniform:solid-payload"),
                dynamicOffsets = listOf(0L),
            ),
            resourceSlot = GPUResourceBindingSlot(
                slotId = GPUPayloadSlotID("pass-a:resource:0"),
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-solid"),
                bindingIndex = 0,
            ),
            uploadPlan = GPUPayloadUploadPlan(
                planHash = "upload-solid-v1",
                byteRanges = listOf(0L..63L),
                stagingScope = "pass-a-staging",
                budgetClass = "unit-test",
                beforeUseToken = "before-draw-1",
            ),
            reflectedBindingLayoutHash = "layout-solid-v1",
            deviceGeneration = 4,
            payloadGeneration = 7L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 256L,
            uploadCapabilityAvailable = true,
            maxDynamicOffsets = 1,
            requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
            availableUniformUsageLabels = setOf("copy_dst", "uniform"),
        )

    private fun targetPreparationContext(): GPUTargetPreparationContext =
        GPUTargetPreparationContext(
            targetId = "root-target",
            frameId = "frame-1",
            deviceGeneration = 4,
            budgetClass = "unit-test",
        )
}
