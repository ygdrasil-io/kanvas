package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

class GPUPassBatcherTest {
    @Test
    fun `solid fill packets with retained resources form one batch`() {
        val packets = packetStream(
            packet("packet-1", commandId = 1, target = "rgba8-premul-msaa1"),
            packet("packet-2", commandId = 2, target = "rgba8-premul-msaa1"),
            packet("packet-3", commandId = 3, target = "rgba8-premul-msaa1"),
        )
        val eligibility = packets.packets.associate { packet ->
            packet.packetId to eligibility(kind = GPUPassBatchKind.SolidFill)
        }

        val plan = GPUPassBatcher().plan(
            GPUPassBatcherRequest(
                packetStream = packets,
                eligibilityByPacketId = eligibility,
            ),
        )

        assertEquals(1, plan.batches.size)
        assertEquals(3, plan.batches.single().packetCount)
        assertEquals(listOf(GPUDrawPacketID("packet-1"), GPUDrawPacketID("packet-2"), GPUDrawPacketID("packet-3")), plan.batches.single().packetIds)
        assertEquals(0, plan.cuts.size)
        assertEquals(1, plan.acceptedBatchCount)
        assertEquals(3, plan.packetCount)
        assertContains(
            plan.dumpLines(),
            "passes.batch-plan stream=packet-stream-main pass=main-pass batches=1 accepted=1 cuts=0 packets=3 diagnostics=none",
        )
        assertContains(
            plan.dumpLines(),
            "passes.batch id=batch-1 kind=solid-fill target=rgba8-premul-msaa1 packets=packet-1,packet-2,packet-3 pipelines=render:solid-fill queueRetained=true",
        )
        assertFalse(plan.dumpLines().joinToString("\n").contains("WGPU"))
        assertFalse(plan.dumpLines().joinToString("\n").contains("0x"))
        assertFalse(plan.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `batcher cuts on target change and preserves packet order`() {
        val packets = packetStream(
            packet("packet-1", commandId = 1, target = "target-a"),
            packet("packet-2", commandId = 2, target = "target-b"),
            packet("packet-3", commandId = 3, target = "target-b"),
        )
        val eligibility = packets.packets.associate { packet ->
            packet.packetId to eligibility(kind = GPUPassBatchKind.SolidFill)
        }

        val plan = GPUPassBatcher().plan(
            GPUPassBatcherRequest(
                packetStream = packets,
                eligibilityByPacketId = eligibility,
            ),
        )

        assertEquals(listOf("packet-1"), plan.batches[0].packetIds.map { it.value })
        assertEquals(listOf("packet-2", "packet-3"), plan.batches[1].packetIds.map { it.value })
        assertEquals(1, plan.cuts.size)
        assertEquals(GPUPassBatchReason.TARGET_CHANGED, plan.cuts.single().reasonCode)
        assertContains(
            plan.dumpLines(),
            "passes.batch-cut before=packet-1 after=packet-2 code=unsupported.batch.target_changed message=target target-a cannot batch with target-b",
        )
    }

    @Test
    fun `batcher cuts on fixed state change`() {
        val packets = packetStream(
            packet("packet-1", commandId = 1, target = "target-a"),
            packet("packet-2", commandId = 2, target = "target-a"),
        )
        val plan = GPUPassBatcher().plan(
            GPUPassBatcherRequest(
                packetStream = packets,
                eligibilityByPacketId = mapOf(
                    GPUDrawPacketID("packet-1") to eligibility(GPUPassBatchKind.SolidFill, fixedStateHash = "fixed:src-over"),
                    GPUDrawPacketID("packet-2") to eligibility(GPUPassBatchKind.SolidFill, fixedStateHash = "fixed:dst-over"),
                ),
            ),
        )

        assertEquals(GPUPassBatchReason.BLEND_OR_FIXED_STATE_CHANGED, plan.cuts.single().reasonCode)
    }

    @Test
    fun `diagnostic text is not a batching or routing input`() {
        val diagnostic = GPUPassDiagnostic(
            code = "requires.destination-read",
            passId = "main-pass",
            invocationId = "draw-2",
            terminal = false,
        )
        val packets = packetStream(
            packet("packet-1", commandId = 1, target = "target-a"),
            packet("packet-2", commandId = 2, target = "target-a", diagnostics = listOf(diagnostic)),
        )
        val plan = GPUPassBatcher().plan(requestForAll(packets, GPUPassBatchKind.SolidFill))

        assertEquals(1, plan.batches.size)
        assertEquals(2, plan.batches.single().packetCount)
        assertTrue(plan.cuts.isEmpty())
    }

    @Test
    fun `save layer filter and text diagnostic labels do not reconstruct routing`() {
        val cases = listOf(
            "requires.save-layer" to GPUPassBatchReason.SAVE_LAYER,
            "requires.filter-intermediate" to GPUPassBatchReason.FILTER_INTERMEDIATE,
            "requires.text-complex" to GPUPassBatchReason.TEXT_COMPLEX,
        )

        cases.forEachIndexed { index, (code, _) ->
            val packets = packetStream(
                packet("packet-${index}a", commandId = 1, target = "target-a"),
                packet(
                    "packet-${index}b",
                    commandId = 2,
                    target = "target-a",
                    diagnostics = listOf(GPUPassDiagnostic(code = code, passId = "main-pass", invocationId = code, terminal = false)),
                ),
            )
            val plan = GPUPassBatcher().plan(requestForAll(packets, GPUPassBatchKind.SolidFill))
            assertEquals(1, plan.batches.size, code)
            assertTrue(plan.cuts.isEmpty(), code)
        }
    }

    @Test
    fun `resource retention does not affect packet adjacency`() {
        val packets = packetStream(
            packet("packet-1", commandId = 1, target = "target-a"),
            packet("packet-2", commandId = 2, target = "target-a"),
        )
        val unretained = GPUPassBatchQueueGuard(
            requiredRetainedRefs = listOf("lease:uniform-slab:frame-1"),
            retainedRefs = emptyList(),
        )

        val plan = GPUPassBatcher().plan(
            GPUPassBatcherRequest(
                packetStream = packets,
                eligibilityByPacketId = packets.packets.associate { packet ->
                    packet.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.SolidFill,
                        fixedStateHash = "fixed:src-over",
                        queueGuard = unretained,
                    )
                },
            ),
        )

        assertEquals(1, plan.batches.size)
        assertEquals(2, plan.batches.single().packetCount)
        assertTrue(plan.cuts.isEmpty())
    }

    @Test
    fun `packet count includes refused and cut packets in plan totals`() {
        val acceptedThenCut = packetStream(
            packet("packet-1", commandId = 1, target = "target-a"),
            packet("packet-2", commandId = 2, target = "target-a"),
            packet("packet-3", commandId = 3, target = "target-a", role = GPUDrawPacketRole.Upload),
        )
        val acceptedThenCutPlan = GPUPassBatcher().plan(requestForAll(acceptedThenCut, GPUPassBatchKind.SolidFill))

        assertEquals(3, acceptedThenCutPlan.packetCount)
        assertEquals(2, acceptedThenCutPlan.batches.size)
        assertEquals(1, acceptedThenCutPlan.cuts.size)

        val fullyRefused = packetStream(
            packet("packet-4", commandId = 4, target = "target-a", role = GPUDrawPacketRole.Upload),
            packet("packet-5", commandId = 5, target = "target-a", role = GPUDrawPacketRole.Copy),
        )
        val fullyRefusedPlan = GPUPassBatcher().plan(requestForAll(fullyRefused, GPUPassBatchKind.SolidFill))

        assertEquals(2, fullyRefusedPlan.packetCount)
        assertEquals(2, fullyRefusedPlan.batches.size)
        assertEquals(1, fullyRefusedPlan.cuts.size)
    }

    @Test
    fun `dump lines sanitize unsafe identifiers and diagnostics`() {
        val unsafeStreamId = "packet-stream@0x1-WGPUBuffer"
        val unsafePassId = "main-pass@0x2-WGPUQueue"
        val unsafePipeline = GPURenderPipelineKey("render:solid-fill@0x3-WGPURenderPipeline")
        val packets = packetStream(
            packet(
                packetId = "packet@0x4-WGPUTexture",
                commandId = 1,
                target = "target@0x5-WGPUTextureView",
                pipeline = unsafePipeline,
                passId = unsafePassId,
            ),
            packet(
                packetId = "packet@0x6-WGPUTexture",
                commandId = 2,
                target = "target@0x7-WGPUTextureView",
                pipeline = unsafePipeline,
                passId = unsafePassId,
            ),
            streamId = unsafeStreamId,
            passId = unsafePassId,
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = "diag@0x8-WGPUTexture",
                    passId = unsafePassId,
                    invocationId = "invoke@0x9-WGPUQueue",
                    terminal = false,
                ),
            ),
        )

        val plan = GPUPassBatcher().plan(requestForAll(packets, GPUPassBatchKind.SolidFill))
        val dump = plan.dumpLines()
        val dumpText = dump.joinToString("\n")

        assertEquals(dump, plan.dumpLines())
        listOf(
            unsafeStreamId,
            unsafePassId,
            "packet@0x4-WGPUTexture",
            "packet@0x6-WGPUTexture",
            "target@0x5-WGPUTextureView",
            "target@0x7-WGPUTextureView",
            unsafePipeline.value,
            "diag@0x8-WGPUTexture",
            "invoke@0x9-WGPUQueue",
        ).forEach { unsafeToken ->
            assertFalse(dumpText.contains(unsafeToken), unsafeToken)
        }
        assertFalse(dumpText.contains("WGPU"))
        assertFalse(dumpText.contains("0x"))
        assertFalse(dumpText.contains("@"))
    }

    @Test
    fun `single packets remain represented without semantic refusal cuts`() {
        val computePlan = GPUPassBatcher().plan(
            requestForAll(
                packetStream(packetForRole("compute-packet", 20, "target-a", GPUDrawPacketRole.Compute)),
                GPUPassBatchKind.SolidFill,
            ),
        )
        assertEquals(1, computePlan.batches.size)
        assertTrue(computePlan.cuts.isEmpty())

        val compositePlan = GPUPassBatcher().plan(
            requestForAll(
                packetStream(packetForRole("composite-packet", 21, "target-a", GPUDrawPacketRole.Composite)),
                GPUPassBatchKind.SolidFill,
            ),
        )
        assertEquals(1, compositePlan.batches.size)
        assertTrue(compositePlan.cuts.isEmpty())

        val discardPlan = GPUPassBatcher().plan(
            requestForAll(
                packetStream(packetForRole("discard-packet", 22, "target-a", GPUDrawPacketRole.Discard)),
                GPUPassBatchKind.SolidFill,
            ),
        )
        assertEquals(1, discardPlan.batches.size)
        assertTrue(discardPlan.cuts.isEmpty())
    }

    @Test
    fun `simple gradients can batch when explicitly eligible`() {
        val packets = packetStream(
            packet("packet-1", commandId = 1, target = "target-a", pipeline = GPURenderPipelineKey("render:linear-gradient")),
            packet("packet-2", commandId = 2, target = "target-a", pipeline = GPURenderPipelineKey("render:linear-gradient")),
        )
        val plan = GPUPassBatcher().plan(requestForAll(packets, GPUPassBatchKind.SimpleGradient))

        assertEquals(1, plan.acceptedBatchCount)
        assertEquals(GPUPassBatchKind.SimpleGradient, plan.batches.single().kind)
    }

    private fun eligibility(
        kind: GPUPassBatchKind,
        fixedStateHash: String = "fixed:src-over",
        retainedRefs: List<String> = listOf("lease:uniform-slab:frame-1", "lease:bind-group:frame-1"),
    ): GPUPassBatchEligibility =
        GPUPassBatchEligibility(
            kind = kind,
            fixedStateHash = fixedStateHash,
            queueGuard = GPUPassBatchQueueGuard(
                requiredRetainedRefs = retainedRefs,
                retainedRefs = retainedRefs,
            ),
        )

    private fun requestForAll(
        packets: GPUDrawPacketStream,
        kind: GPUPassBatchKind,
    ): GPUPassBatcherRequest =
        GPUPassBatcherRequest(
            packetStream = packets,
            eligibilityByPacketId = packets.packets.associate { packet ->
                packet.packetId to eligibility(kind = kind)
            },
        )

    private fun packetStream(
        vararg packets: GPUDrawPacket,
        streamId: String = "packet-stream-main",
        passId: String = "main-pass",
        diagnostics: List<GPUPassDiagnostic> = emptyList(),
    ): GPUDrawPacketStream =
        GPUDrawPacketStream(
            streamId = streamId,
            passId = passId,
            packets = packets.toList(),
            diagnostics = diagnostics,
        )

    private fun packet(
        packetId: String,
        commandId: Int,
        target: String,
        pipeline: GPURenderPipelineKey = GPURenderPipelineKey("render:solid-fill"),
        role: GPUDrawPacketRole = GPUDrawPacketRole.Shading,
        diagnostics: List<GPUPassDiagnostic> = emptyList(),
        passId: String = "main-pass",
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = GPUDrawPacketID(packetId),
            commandIdValue = commandId,
            analysisRecordId = "analysis-$commandId",
            passId = passId,
            layerId = "root-layer",
            bindingListId = "bindings-$commandId",
            insertionReasonCode = "native-fill-rect",
            sortKey = commandId.toLong() * 100L,
            sortKeyPreimage = "paint|clip|transform|$commandId",
            renderStepId = GPURenderStepID("fill-rect"),
            renderStepVersion = 1,
            role = role,
            renderPipelineKey = if (role == GPUDrawPacketRole.Shading) pipeline else null,
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
            diagnostics = diagnostics,
        )

    private fun packetForRole(
        packetId: String,
        commandId: Int,
        target: String,
        role: GPUDrawPacketRole,
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
            renderPipelineKey = if (role == GPUDrawPacketRole.Composite) GPURenderPipelineKey("render:composite") else null,
            computePipelineKey = if (role == GPUDrawPacketRole.Compute) GPUComputePipelineKey("compute:batch-cut") else null,
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

    private fun GPUDrawPacket.copyForRole(
        id: String,
        newRole: GPUDrawPacketRole,
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = GPUDrawPacketID(id),
            commandIdValue = commandIdValue + 1,
            analysisRecordId = "$analysisRecordId-$id",
            passId = passId,
            layerId = layerId,
            bindingListId = "$bindingListId-$id",
            insertionReasonCode = insertionReasonCode,
            sortKey = sortKey + 1,
            sortKeyPreimage = "$sortKeyPreimage-$id",
            renderStepId = renderStepId,
            renderStepVersion = renderStepVersion,
            role = newRole,
            bindingLayoutHash = bindingLayoutHash,
            vertexSourceLabel = vertexSourceLabel,
            targetStateHash = targetStateHash,
            originalPaintOrder = originalPaintOrder + 1,
            resourceGeneration = resourceGeneration,
        )
}
