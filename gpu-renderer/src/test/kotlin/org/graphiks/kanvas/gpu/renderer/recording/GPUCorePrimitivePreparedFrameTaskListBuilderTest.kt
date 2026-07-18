package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitivePreparedFrameTaskListBuilderTest {
    @Test
    fun `refused base task remains refused with its original diagnostic`() {
        val base = recording(
            command(
                commandId = 8,
                paintOrder = 0,
                clip = GPUClipFacts.complexStack(GPUBounds(0f, 0f, 16f, 16f)),
            ),
        ).taskList
        val original = assertIs<GPUTask.Refused>(base.tasks.single()).diagnostic

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, emptyMap()).copy(
                targetBounds = GPUPixelBounds(1, 1, 1, 1),
                configuredAggregateBudgetBytes = 0,
            ),
        )

        val refused = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result)
        assertSame(original, refused.diagnostic)
        assertEquals("unsupported.clip.complex_stack", refused.diagnostic.code.value)
    }

    @Test
    fun `prepared packets preserve exact base ordering blend provenance and clip authorities`() {
        val base = recording(
            command(9, 7, GPUFrameProvenance.HarnessBackground, GPUClipCoveragePlan.NoClip),
            command(4, 2, GPUFrameProvenance.GmContent, scissor()),
        ).taskList
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = basePackets.associate { packet -> packet.commandIdValue to semantic(packet) }

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        assertEquals(basePackets.map { it.commandIdValue }, prepared.map { it.commandIdValue })
        assertEquals(basePackets.map { it.sortKey }, prepared.map { it.sortKey })
        assertEquals(basePackets.map { it.originalPaintOrder }, prepared.map { it.originalPaintOrder })
        assertEquals(basePackets.map { it.blendPlan }, prepared.map { it.blendPlan })
        assertEquals(basePackets.map { it.frameProvenance }, prepared.map { it.frameProvenance })
        assertEquals(basePackets.map { it.clipCoveragePlan }, prepared.map { it.clipCoveragePlan })
    }

    @Test
    fun `mask clip remains refused until B2 records real clip topology`() {
        val mask = GPUClipCoveragePlan.Mask(
            contentKey = "clip.mask.pending-b2",
            width = 16,
            height = 16,
            sampleCount = 1,
            resolvedBytes = 256,
            requiredBytes = 256,
            elements = listOf(
                GPUClipCoverageElement(
                    operation = GPUClipCoverageOperation.Intersect,
                    kind = GPUClipCoverageElementKind.Rect,
                    values = listOf(1f, 1f, 12f, 12f),
                    vertexCount = 0,
                    antiAlias = true,
                    fillRule = GPUClipFillRule.Winding,
                    inverseFill = false,
                ),
            ),
        )
        val base = recording(command(12, 0, GPUFrameProvenance.GmContent, mask)).taskList
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(packet.commandIdValue to semantic(packet))),
        )

        assertEquals(
            "unsupported.recording.core_primitive_clip_topology_unavailable",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    private fun request(
        base: GPUTaskList,
        semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    ) = GPUCorePrimitivePreparedFrameRequest(
        baseTaskList = base,
        capabilities = capabilities(),
        target = GPUFrameTargetRef("target.core.authority"),
        targetBounds = targetBounds,
        semanticsByCommandId = semantics,
    )

    private fun semantic(packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket) =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = targetBounds,
                scissorBounds = targetBounds,
                clipCoveragePlan = requireNotNull(packet.clipCoveragePlan),
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
            ),
        )

    private fun recording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
        GPURecorder(GPURecordingID("recording.core.authority"), GPUFrameID(91), capabilities()).apply {
            commands.forEach(::record)
        }.close()

    private fun command(
        commandId: Int,
        paintOrder: Int,
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        clipPlan: GPUClipCoveragePlan = GPUClipCoveragePlan.NoClip,
    ) = command(
        commandId,
        paintOrder,
        GPUClipFacts(
            kind = when (clipPlan) {
                GPUClipCoveragePlan.NoClip -> GPUClipKind.WideOpen
                is GPUClipCoveragePlan.Scissor -> GPUClipKind.DeviceRect
                is GPUClipCoveragePlan.Mask,
                is GPUClipCoveragePlan.Refused,
                -> GPUClipKind.ComplexStack
            },
            bounds = GPUBounds(0f, 0f, 16f, 16f),
            coveragePlan = clipPlan,
        ),
        provenance,
    )

    private fun command(
        commandId: Int,
        paintOrder: Int,
        clip: GPUClipFacts,
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
    ) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 8f, 8f),
        target = targetFacts,
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        clip = clip,
        paintOrder = paintOrder,
        source = GPUCommandSource("unit-test", "fillRect", provenance),
    )

    private fun scissor() = GPUClipCoveragePlan.Scissor(GPUClipBounds(0f, 0f, 16f, 16f))

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit-test", "supported", true, "core-authority"),
            GPUCapabilityFact("first_slice.scissor.native", "unit-test", "supported", true, "core-authority"),
        ),
        snapshotId = "core-authority",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )

    private companion object {
        val targetBounds = GPUPixelBounds(0, 0, 16, 16)
        val targetFacts = GPUTargetFacts(16, 16, "rgba8unorm")
    }
}
