package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
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
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
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

    @Test
    fun `mixed frame budget includes core image target and readback allocations`() {
        val base = recording(coreCommand(0, 0), imageCommand(1, 1)).taskList
        val successful = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics(base))),
        )
        val aggregate = successful.taskList.memoryBudget.let { budget ->
            budget.targetResidentBytes + budget.peakFrameTransientBytes
        }

        assertTrue(aggregate > 0L)
        val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(base, semantics(base)),
                configuredAggregateBudgetBytes = aggregate - 1L,
            ),
        )
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", refused.diagnostic.code.value)
    }

    @Test
    fun `image tasks retain the exact resource plan upload payload and per packet samplers`() {
        val base = recording(imageCommand(0, 0), coreCommand(1, 1), imageCommand(2, 2)).taskList
        val nearest = imageSemantic(
            base,
            commandId = 0,
            sampling = GPUPreparedImageSampling.Nearest,
        )
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to nearest,
            1 to coreSemantic(base, 1),
            2 to imageSemantic(
                base,
                commandId = 2,
                artifactOverride = nearest,
                sampling = GPUPreparedImageSampling.Linear,
            ),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val upload = taskList.tasks.filterIsInstance<GPUTask.Upload>().single()
        val planField = upload.javaClass.declaredFields.singleOrNull {
            it.name == "preparedImagePlan"
        }
        assertNotNull(planField, "Upload must retain its exact prepared-image resource plan")
        planField.isAccessible = true
        val plan = assertNotNull(planField.get(upload))
        val uploadLayout = plan.javaClass.getMethod("getUploadLayout").invoke(plan)
        val bytesForUpload = uploadLayout.javaClass.getMethod("bytesForUpload").invoke(uploadLayout) as ByteArray
        assertEquals(bytesForUpload.size.toLong(), upload.layout.byteSize)

        val bindings = plan.javaClass.getMethod("getBindingRequests").invoke(plan) as List<*>
        val samplerModes = bindings.associate { binding ->
            val value = assertNotNull(binding)
            val packetId = value.javaClass.getMethod("getPacketId").invoke(value) as String
            val sampler = value.javaClass.getMethod("getSampler").invoke(value)
            packetId to sampler.javaClass.getMethod("getMagFilter").invoke(sampler) as String
        }.values.toSet()
        assertEquals(setOf("nearest", "linear"), samplerModes)
        val retainedBindings = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap { render ->
                val field = render.javaClass.declaredFields.singleOrNull {
                    it.name == "preparedImageBindingsByPacketId"
                }
                assertNotNull(field, "Render must retain exact prepared-image binding requests")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (field.get(render) as Map<*, *>).values
            }
        assertEquals(2, retainedBindings.size)
    }

    @Test
    fun `mixed core packet without clip authorities refuses instead of synthesizing no clip`() {
        val original = rawRecording(coreCommand(0, 0), imageCommand(1, 1)).taskList
        val base = original.transformPackets { packet ->
            if (packet.commandIdValue == 0) packet.rebuilt(
                clipCoveragePlan = null,
                clipExecutionPlan = null,
            ) else packet
        }

        val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics(base))),
        )

        assertEquals("invalid.recording.prepared_surface_core_authority", refused.diagnostic.code.value)
    }

    @Test
    fun `image packet and semantic render step mismatch refuses before task emission`() {
        val original = recording(coreCommand(0, 0), imageCommand(1, 1)).taskList
        val semanticMap = semantics(original)
        val base = original.transformPackets { packet ->
            if (packet.commandIdValue == 1) packet.rebuilt(
                renderStepId = GPURenderStepID("image.draw.bitmap_shader"),
            ) else packet
        }

        val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semanticMap)),
        )

        assertEquals("invalid.recording.prepared_surface_route_identity", refused.diagnostic.code.value)
    }

    @Test
    fun `adjacent image packets with distinct pipeline identities form distinct route runs`() {
        val original = recording(imageCommand(0, 0), imageCommand(1, 1)).taskList
        val base = original.transformPackets { packet ->
            if (packet.commandIdValue == 1) packet.rebuilt(
                renderPipelineKey = GPURenderPipelineKey("pipeline.prepared-image.distinct"),
            ) else packet
        }
        val shared = imageSemantic(base, 0)
        val semanticMap = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to shared,
            1 to imageSemantic(base, 1, artifactOverride = shared),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semanticMap)),
        ).taskList

        assertEquals(2, taskList.tasks.filterIsInstance<GPUTask.Render>().size)
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
        rawRecording(*commands).let { recording ->
            recording.copy(
                taskList = recording.taskList.transformPackets { packet ->
                    if (packet.renderStepId.value.startsWith("image.draw.")) {
                        packet
                    } else {
                        packet.rebuilt(
                            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                            clipExecutionPlan = GPUClipExecutionPlan.NoClip,
                        )
                    }
                },
            )
        }

    private fun rawRecording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
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
                clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
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
        sampling: GPUPreparedImageSampling = GPUPreparedImageSampling.Nearest,
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
                sampling = sampling,
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

    private fun GPUTaskList.transformPackets(
        transform: (GPUDrawPacket) -> GPUDrawPacket,
    ): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) {
                task
            } else {
                val transformed = task.drawPackets.map(transform)
                GPUTask.Render(
                    taskId = task.taskId,
                    recordingId = task.recordingId,
                    phase = task.phase,
                    target = task.target,
                    loadStore = task.loadStore,
                    samplePlan = task.samplePlan,
                    resourceUses = task.resourceUses,
                    provisionalSegmentKey = task.provisionalSegmentKey,
                    drawPackets = transformed,
                    batchEligibilityByPacketId = transformed.associate { packet ->
                        packet.packetId to task.batchEligibilityByPacketId.getValue(packet.packetId)
                    },
                    sampleContinuationKey = task.sampleContinuationKey,
                    compositeMembership = task.compositeMembership,
                    depthStencilLoadStore = task.depthStencilLoadStore,
                )
            }
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUDrawPacket.rebuilt(
        renderStepId: GPURenderStepID = this.renderStepId,
        renderPipelineKey: GPURenderPipelineKey? = this.renderPipelineKey,
        clipCoveragePlan: GPUClipCoveragePlan? = this.clipCoveragePlan,
        clipExecutionPlan: GPUClipExecutionPlan? = this.clipExecutionPlan,
    ) = GPUDrawPacket(
        packetId = packetId,
        commandIdValue = commandIdValue,
        analysisRecordId = analysisRecordId,
        passId = passId,
        layerId = layerId,
        bindingListId = bindingListId,
        insertionReasonCode = insertionReasonCode,
        sortKey = sortKey,
        sortKeyPreimage = sortKeyPreimage,
        renderStepId = renderStepId,
        renderStepVersion = renderStepVersion,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = renderPipelineKey,
        computePipelineKey = computePipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = uniformSlot,
        resourceSlot = resourceSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        frameProvenance = frameProvenance,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
        diagnostics = diagnostics,
        clipProducerAuthority = clipProducerAuthority,
    )

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
