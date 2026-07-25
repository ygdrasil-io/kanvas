package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawImageRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUPreparedSurfaceFrameTaskListBuilderTest {
    @Test
    fun `core image core stays in paint order and splits only contiguous route runs`() {
        val base = recording(coreCommand(0, 0), imageCommand(1, 1), coreCommand(2, 2)).taskList
        val semantics = semantics(base)

        val result = GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics))
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            result,
            (result as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
        ).taskList
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), renders.map { run ->
            run.drawPackets.map(GPUDrawPacket::commandIdValue)
        })
        assertEquals(listOf("clear", "load", "load"), renders.map { it.loadStore.loadOp })
        assertEquals(listOf(0, 1, 2), renders.flatMap { it.drawPackets }.map { it.originalPaintOrder })
        val solidRun = renders.first()
        assertFalse(solidRun.resourceUses.any {
            it.role == GPUFrameResourceRole.UploadStaging ||
                it.usage == GPUFrameResourceUsage.TextureBinding
        })
    }

    @Test
    fun `one artifact emits one upload and every separated image run depends on it`() {
        val base = recording(imageCommand(0, 0), coreCommand(1, 1), imageCommand(2, 2)).taskList
        val sharedImage = imageSemantic(base, commandId = 0)
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to sharedImage,
            1 to coreSemantic(base, 1),
            2 to imageSemantic(base, commandId = 2, artifactOverride = sharedImage),
        )

        val result = GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics))
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            result,
            (result as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
        ).taskList
        val upload = taskList.tasks.filterIsInstance<GPUTask.Upload>().single()
        val imageRuns = taskList.tasks.filterIsInstance<GPUTask.Render>().filter { render ->
            render.drawPackets.all { it.semanticPayload is GPUDrawSemanticPayload.SampledImage }
        }

        assertEquals(2, imageRuns.size)
        imageRuns.forEach { consumer ->
            assertTrue(taskList.dependencies.any {
                it.fromTaskId == upload.taskId && it.toTaskId == consumer.taskId &&
                    it.reasonCode == "prepared.image.upload-before-consumer"
            })
        }
        assertEquals("RGBA8Unorm", taskList.diagnostics.single {
            it.code.value == "info.recording.prepared_image_color_contract"
        }.facts.getValue("image.upload.format"))
        assertEquals("false", taskList.diagnostics.single {
            it.code.value == "info.recording.prepared_image_color_contract"
        }.facts.getValue("image.attachment.srgbConversion"))
    }

    @Test
    fun `missing extra and duplicate semantic identities refuse atomically before task emission`() {
        val base = recording(coreCommand(0, 0), imageCommand(1, 1)).taskList
        val valid = semantics(base)
        val cases = listOf(
            valid - 1,
            valid + (9 to valid.getValue(0)),
            linkedMapOf(0 to valid.getValue(0), 1 to valid.getValue(0)),
        )

        cases.forEach { forged ->
            val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
                GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, forged)),
            )
            assertEquals("invalid.recording.prepared_surface_semantics", refused.diagnostic.code.value)
        }
    }

    private fun request(
        base: GPUTaskList,
        semantics: Map<Int, GPUDrawSemanticPayload>,
    ) = GPUPreparedSurfaceFrameRequest(
        baseTaskList = base,
        capabilities = capabilities(),
        target = GPUFrameTargetRef("target.prepared-surface"),
        targetBounds = bounds,
        semanticsByCommandId = semantics,
        readbackRequestId = GPUReadbackRequestID("readback.prepared-surface"),
    )

    private fun recording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
        GPURecorder(
            GPURecordingID("recording.prepared-surface"),
            GPUFrameID(17),
            capabilities(),
        ).apply { commands.forEach(::record) }.close()

    private fun semantics(base: GPUTaskList): Map<Int, GPUDrawSemanticPayload> =
        base.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .associate { packet ->
                packet.commandIdValue to if (packet.renderStepId.value == "image.draw.texture_upload") {
                    imageSemantic(base, packet.commandIdValue)
                } else {
                    coreSemantic(base, packet.commandIdValue)
                }
            }
            .toSortedMap()

    private fun coreSemantic(base: GPUTaskList, commandId: Int): GPUDrawSemanticPayload.CorePrimitive {
        val packet = packet(base, commandId)
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = commandId,
                sourceFamily = GPUCorePrimitiveSourceFamily.Color,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = bounds,
                scissorBounds = bounds,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
                coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
            ),
        )
    }

    private fun imageSemantic(
        base: GPUTaskList,
        commandId: Int,
        artifactOverride: GPUDrawSemanticPayload.SampledImage? = null,
    ): GPUDrawSemanticPayload.SampledImage {
        val packet = packet(base, commandId)
        return GPUPreparedImagePayloadGatherer().gatherSemantic(
            GPUPreparedImagePayloadInput(
                payloadRef = GPUDrawPayloadRef(commandId, "image.draw.texture_upload"),
                artifact = artifactOverride?.artifact ?: artifact(),
                geometry = GPUPreparedImageGeometry(
                    GPUPreparedImageGeometryClass.Rect,
                    listOf(
                        GPUPreparedImageVertex(1f, 1f, 0f, 0f),
                        GPUPreparedImageVertex(8f, 1f, 1f, 0f),
                        GPUPreparedImageVertex(8f, 8f, 1f, 1f),
                        GPUPreparedImageVertex(1f, 8f, 0f, 1f),
                    ),
                    listOf(0, 1, 2, 0, 2, 3),
                ),
                sampling = GPUPreparedImageSampling.Nearest,
                tintPremultipliedRgba = listOf(1f, 1f, 1f, 1f),
                atlasColorPremultipliedRgba = null,
                atlasSourceBlend = null,
                targetBounds = bounds,
                scissorBounds = bounds,
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
            ),
        )
    }

    private fun packet(base: GPUTaskList, commandId: Int): GPUDrawPacket =
        base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
            .single { it.commandIdValue == commandId }

    private fun coreCommand(commandId: Int, paintOrder: Int) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 8f, 8f),
        target = target,
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        paintOrder = paintOrder,
        source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
    )

    private fun imageCommand(commandId: Int, paintOrder: Int) = GPUDrawImageRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        imageSourceId = "shared-image",
        src = GPURect(0f, 0f, 3f, 2f),
        dst = GPURect(1f, 1f, 8f, 8f),
        target = target,
        material = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "shared-image",
            imageWidth = 3,
            imageHeight = 2,
            rgbaPixels = artifact().tightRgba8BytesForUpload(),
            samplingFilterMode = "nearest",
        ),
        samplingFilterMode = "nearest",
        pixelsWidth = 3,
        pixelsHeight = 2,
        pixelsRowBytes = 12,
        pixelsContentHash = artifact().contentHash,
        pixelsProvenance = "test",
        paintOrder = paintOrder,
        source = GPUCommandSource("test", "drawImageRect", GPUFrameProvenance.GmContent),
    )

    private fun artifact() = (GPUPreparedImageArtifactFactory.prepare(
        GPUPreparedImageSourceInput(
            GPUPreparedImageSourceClass.DecodedCpu,
            "shared-image",
            3,
            2,
            GPUPreparedImageSourceFormat.Rgba8,
            AlphaType.PREMUL,
            12,
            GPUPreparedImageProfile.Srgb,
            GPUPreparedImageOrientation.AppliedIdentity,
            GPUPreparedImageProvenance.CallerPixels,
            0,
            ByteArray(24) { (it + 1).toByte() },
        ),
    ) as GPUPreparedImageArtifactResult.Ready).artifact

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "test", "supported", true, "test"),
            GPUCapabilityFact("first_slice.draw_image_rect.prepared", "test", "supported", true, "test"),
        ),
        snapshotId = "prepared-surface",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private companion object {
        val bounds = GPUPixelBounds(0, 0, 16, 16)
        val target = GPUTargetFacts(16, 16, "rgba8unorm")
    }
}
