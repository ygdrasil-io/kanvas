package org.graphiks.kanvas.gpu.renderer.destination

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionDiagnostic
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.execution.dumpLines
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketStream
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.dumpLines
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

/** Verifies KGPU-M11-005 destination-read copy/intermediate live materialization contracts. */
class DestinationReadLiveMaterializationTest {
    @Test
    fun `target copy materialization creates copy texture operands and copy before sample commands`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(destinationRequest())

        val result = ValidatingDestinationReadMaterializer().materialize(
            request = destinationMaterializationRequest(gate),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        assertEquals(listOf(GPUTextureResourceRef("texture-ref:dst-copy:blend-screen")), materialized.resources)
        assertEquals(
            listOf(
                "dst-copy:blend-screen" to GPUMaterializedCommandOperandKind.DestinationCopyTexture,
                "texture-view:dst-read:blend-screen" to GPUMaterializedCommandOperandKind.TextureView,
                "sampler:dst-read:blend-screen" to GPUMaterializedCommandOperandKind.Sampler,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label to binding.operand.kind },
        )
        assertContains(
            materialized.dumpLines(),
            "resource.materialization:operand operand=dst-copy:blend-screen kind=destination-copy-texture " +
                "deviceGeneration=17 owner=destination-read:pass-local usage=copy_dst,texture_binding " +
                "invalidation=pass-end descriptor=${gate.copyDescriptorHash} " +
                "facts=action=SplitPassAndCopyTarget;bounds=4,8,64,32;copyBeforeSample=true;" +
                "copyBytes=8192;source=target:main;strategy=TargetCopySnapshot;targetGeneration=42",
        )

        val commandStream = result.commandStream
        assertEquals(
            listOf(
                "beginRenderPass",
                "endRenderPass",
                "copyTexture",
                "beginRenderPass",
                "setRenderPipeline",
                "setBindGroup",
                "setScissor",
                "draw",
                "endRenderPass",
            ),
            commandStream.commandLabels,
        )
        assertContains(
            commandStream.dumpLines(),
            "passes.command copyTexture source=target:main destination=dst-copy:blend-screen " +
                "bounds=4,8,64,32 token=dst-token:blend:screen:42",
        )
        assertContains(
            result.dumpLines(),
            "destination-read:materialization row=gpu-renderer.destination-read.live-materialization " +
                "strategy=TargetCopySnapshot action=SplitPassAndCopyTarget resource=dst-copy:blend-screen " +
                "binding=dst-read:blend-screen copyBeforeSample=true passSplit=true " +
                "adapterBacked=false productActivation=true",
        )
        assertFalse(result.dumpLines().joinToString("\n").contains("WGPU"))
        assertFalse(result.dumpLines().joinToString("\n").contains("@0x"))

        val skippedReadback = skippedDestinationReadback()
        val readbackDump = skippedReadback.dumpLines().joinToString("\n")
        assertContains(readbackDump, "execution.readback:skipped")
        assertContains(readbackDump, "failureReason=kgpu-m11-005.adapter-readback-not-promoted")
        assertFalse(readbackDump.contains("execution.readback:completed"))
    }

    @Test
    fun `existing intermediate materialization validates and binds separate sampled texture`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(
            destinationRequest(
                requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
                eligibleIntermediate = liveEligibleIntermediate(),
            ),
        )

        val result = ValidatingDestinationReadMaterializer().materialize(
            request = destinationMaterializationRequest(gate),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        assertEquals(listOf(GPUTextureResourceRef("texture-ref:intermediate:layer-card")), materialized.resources)
        assertEquals(
            listOf(
                "texture-view:dst-read:blend-screen" to GPUMaterializedCommandOperandKind.TextureView,
                "sampler:dst-read:blend-screen" to GPUMaterializedCommandOperandKind.Sampler,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label to binding.operand.kind },
        )
        assertEquals(
            listOf(
                "beginRenderPass",
                "setRenderPipeline",
                "setBindGroup",
                "setScissor",
                "draw",
                "endRenderPass",
            ),
            result.commandStream.commandLabels,
        )
        assertContains(
            result.dumpLines(),
            "destination-read:materialization row=gpu-renderer.destination-read.live-materialization " +
                "strategy=SampleExistingIntermediate action=UseExistingIntermediate resource=intermediate:layer-card " +
                "binding=dst-read:blend-screen copyBeforeSample=false passSplit=false " +
                "adapterBacked=false productActivation=true",
        )
    }

    @Test
    fun `destination read materialization refuses invalid live resource facts`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(destinationRequest())
        val staleGate = GPUDestinationReadStrategyPlanner().plan(destinationRequest(targetGeneration = 41))
        val cases = listOf(
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.copy_unavailable",
                request = destinationMaterializationRequest(gate, copyCapabilityAvailable = false),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.copy_usage_missing",
                request = destinationMaterializationRequest(gate, availableSourceUsageLabels = setOf("render_attachment")),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.texture_binding_missing",
                request = destinationMaterializationRequest(gate, availableReadUsageLabels = setOf("copy_dst")),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.copy_budget_exceeded",
                request = destinationMaterializationRequest(gate, copyBudgetBytes = 1024),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.target_generation_stale",
                request = destinationMaterializationRequest(gate, actualTargetGeneration = 41),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.target_generation_stale",
                request = destinationMaterializationRequest(staleGate),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.packet_stream_empty",
                request = destinationMaterializationRequest(gate, packetStream = emptyPacketStream()),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.active_attachment_sampled",
                request = destinationMaterializationRequest(gate, activeAttachmentSampled = true),
            ),
        )

        for (case in cases) {
            val result = ValidatingDestinationReadMaterializer().materialize(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode)
            assertContains(refused.dumpLines().joinToString("\n"), "resource.materialization:refused")
        }
    }

    @Test
    fun `existing intermediate materialization refuses stale mismatched or unsampleable intermediate`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(
            destinationRequest(
                requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
                eligibleIntermediate = liveEligibleIntermediate(),
            ),
        )
        val cases = listOf(
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.generation_stale",
                request = destinationMaterializationRequest(gate, actualIntermediateGeneration = 41),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.intermediate_unvalidated",
                request = destinationMaterializationRequest(gate, intermediateBoundsLabel = "0,0,32,32"),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.intermediate_unvalidated",
                request = destinationMaterializationRequest(gate, intermediateFormatClass = "bgra8unorm"),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.intermediate_unvalidated",
                request = destinationMaterializationRequest(gate, intermediateSampleCount = 4),
            ),
            DestinationMaterializationRefusalCase(
                expectedCode = "unsupported.destination_read.texture_binding_missing",
                request = destinationMaterializationRequest(gate, availableReadUsageLabels = setOf("copy_dst")),
            ),
        )

        for (case in cases) {
            val result = ValidatingDestinationReadMaterializer().materialize(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode)
        }
    }
}

private data class DestinationMaterializationRefusalCase(
    val expectedCode: String,
    val request: GPUDestinationReadMaterializationRequest,
)

private fun destinationMaterializationRequest(
    gate: GPUDestinationReadStrategyGatePlan,
    deviceGeneration: Long = 17,
    actualTargetGeneration: Long = 42,
    actualIntermediateGeneration: Long = 42,
    availableSourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
    availableReadUsageLabels: Set<String> = setOf("copy_dst", "texture_binding"),
    copyCapabilityAvailable: Boolean = true,
    copyBudgetBytes: Long = 16 * 1024 * 1024,
    activeAttachmentSampled: Boolean = false,
    intermediateBoundsLabel: String = gate.plan.bounds.copyBoundsLabel,
    intermediateFormatClass: String = "rgba8unorm",
    intermediateSampleCount: Int = 1,
    packetStream: GPUDrawPacketStream = packetStream(),
): GPUDestinationReadMaterializationRequest =
    GPUDestinationReadMaterializationRequest(
        targetId = "root-target",
        taskIds = listOf("task-destination-read"),
        resourcePlanLabels = listOf("destination-read:blend-screen"),
        gatePlan = gate,
        packetStream = packetStream,
        targetStateHash = "rgba8-premul-msaa1",
        loadStoreLabel = "load-store",
        deviceGeneration = deviceGeneration,
        expectedTargetGeneration = 42,
        actualTargetGeneration = actualTargetGeneration,
        actualIntermediateGeneration = actualIntermediateGeneration,
        availableSourceUsageLabels = availableSourceUsageLabels,
        availableReadUsageLabels = availableReadUsageLabels,
        copyCapabilityAvailable = copyCapabilityAvailable,
        copyBudgetBytes = copyBudgetBytes,
        activeAttachmentSampled = activeAttachmentSampled,
        intermediateBoundsLabel = intermediateBoundsLabel,
        intermediateFormatClass = intermediateFormatClass,
        intermediateSampleCount = intermediateSampleCount,
    )

private fun destinationRequest(
    requirement: GPUBlendDestinationReadRequirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
    eligibleIntermediate: GPUDestinationReadEligibleIntermediate? = null,
    targetGeneration: Long = 42,
): GPUDestinationReadStrategyRequest =
    GPUDestinationReadStrategyRequest(
        commandId = "blend:screen",
        requirement = requirement,
        bounds = GPUDestinationReadBounds(
            boundsLabel = "shape-local",
            conservative = true,
            pixelAligned = true,
            requestedBoundsLabel = "shape-local",
            unclippedBoundsLabel = "0,0,80,40",
            clippedBoundsLabel = "4,8,64,32",
            copyBoundsLabel = "4,8,64,32",
            originX = 4,
            originY = 8,
            width = 64,
            height = 32,
            targetWidth = 128,
            targetHeight = 96,
        ),
        sourceTargetLabel = "target:main",
        sourceUsageLabels = setOf("render_attachment", "copy_src"),
        copyUsageLabels = setOf("copy_dst", "texture_binding"),
        targetFormatClass = "rgba8unorm",
        targetGeneration = targetGeneration,
        eligibleIntermediate = eligibleIntermediate,
    )

private fun liveEligibleIntermediate(): GPUDestinationReadEligibleIntermediate =
    GPUDestinationReadEligibleIntermediate(
        descriptor = GPUIntermediateTextureDescriptor(
            label = "intermediate:layer-card",
            purpose = GPUIntermediatePurpose.ExistingIntermediate,
            descriptorHash = "descriptor:layer-card",
            sourceTargetLabel = "target:main",
            boundsLabel = "4,8,64,32",
            width = 64,
            height = 32,
            formatClass = "rgba8unorm",
            usageLabels = listOf("texture_binding"),
            sampleCount = 1,
            generation = 42,
            lifetimeClass = "layer-local",
            ownerScope = "layer:card",
            byteEstimate = 8192,
        ),
    )

private fun packetStream(): GPUDrawPacketStream =
    GPUDrawPacketStream(
        streamId = "packet-stream-destination-read",
        passId = "main-pass",
        packets = listOf(
            GPUDrawPacket(
                packetId = GPUDrawPacketID("packet-dst-read"),
                commandIdValue = 7,
                analysisRecordId = "analysis-dst-read",
                passId = "main-pass",
                layerId = "root-layer",
                bindingListId = "bindings-dst-read",
                insertionReasonCode = "destination-read",
                sortKey = 700L,
                sortKeyPreimage = "dst-read|blend-screen|7",
                renderStepId = GPURenderStepID("fill-rect"),
                renderStepVersion = 1,
                role = GPUDrawPacketRole.Shading,
                renderPipelineKey = GPURenderPipelineKey("render:shader-blend-dst-read"),
                bindingLayoutHash = "layout-dst-read-v1",
                uniformSlot = GPUUniformPayloadSlot(
                    slotId = GPUPayloadSlotID("dst-read:uniform:0"),
                    fingerprint = GPUPayloadFingerprint("uniform-dst-read"),
                    byteOffset = 0L,
                ),
                resourceSlot = GPUResourceBindingSlot(
                    slotId = GPUPayloadSlotID("dst-read:resource:0"),
                    fingerprint = GPUPayloadFingerprint("resource-dst-read"),
                    bindingIndex = 0,
                ),
                vertexSourceLabel = "solid-quad",
                scissorBoundsHash = "scissor-4-8-64-32",
                targetStateHash = "rgba8-premul-msaa1",
                originalPaintOrder = 7,
                resourceGeneration = 42L,
            ),
        ),
    )

private fun emptyPacketStream(): GPUDrawPacketStream =
    GPUDrawPacketStream(
        streamId = "packet-stream-destination-read-empty",
        passId = "main-pass",
        packets = emptyList(),
    )

private fun targetPreparationContext(): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 17,
        budgetClass = "unit-test",
    )

private fun skippedDestinationReadback(): GPUReadbackResult.Skipped {
    val request = GPUReadbackRequest(
        requestId = "readback-destination-read-skipped",
        sourceLabel = "kgpu-m11-005-destination-read-materialization",
        boundsLabel = "4,8,64,32",
        format = "rgba8unorm",
        synchronizationLabel = "after-destination-read-copy",
        expectedArtifactLabel = "destination-read-copy.png",
        failureReason = "kgpu-m11-005.adapter-readback-not-promoted",
    )
    return GPUReadbackResult.Skipped(
        request = request,
        reasonCode = "unsupported.execution.readback_unavailable",
        diagnostics = listOf(GPUExecutionDiagnostic.readbackUnavailable(request, stage = "readback")),
    )
}
