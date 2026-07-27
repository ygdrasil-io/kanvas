package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.execution.GPUEncoderOperationKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandEncoderScopePlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflightContext
import org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflightResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighter
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageRenderRunPlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeOperandKey
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeOperandKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeOperandRole
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeScopeKey
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeScopeOperand
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedRenderRunMaterialization
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionProvider
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicket
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicketAbandonResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicketID
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicketRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicketReservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceAcquisitionRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceAcquisitionResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceOutputProvider
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceReleaseResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUAcquiredSurfaceOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializer
import org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageSessionCache
import org.graphiks.kanvas.gpu.renderer.execution.RecordingPreparedImageDevice
import org.graphiks.kanvas.gpu.renderer.execution.RecordingPreparedImageHandleFactory
import org.graphiks.kanvas.gpu.renderer.execution.gpuPreparedNativeBindingKey
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUPreparedSurfaceFrameTaskListBuilderTest {
    @Test
    fun `sRGB all core and mixed requests retain the exact scene target format`() {
        val cases = listOf(
            recording(coreCommand(0, 0)).taskList,
            recording(coreCommand(0, 0), imageCommand(1, 1)).taskList,
        )

        cases.forEach { base ->
            val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
                GPUPreparedSurfaceFrameTaskListBuilder().build(
                    request(
                        base = base,
                        semantics = semantics(base),
                        targetFormat = GPUColorFormat.RGBA8UnormSrgb,
                    ),
                ),
            ).taskList

            val targetPreparation = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
                .flatMap(GPUTask.PrepareResources::requests)
                .single { it.role == GPUFrameResourceRole.SceneTarget }
            assertEquals(
                GPUColorFormat.RGBA8UnormSrgb,
                assertIs<GPUFrameTextureDescriptor>(targetPreparation.descriptor).format,
            )
            assertEquals(
                setOf("target.rgba8unorm-srgb.single-sample"),
                taskList.tasks.filterIsInstance<GPUTask.Render>()
                    .flatMap(GPUTask.Render::drawPackets)
                    .filter { packet ->
                        packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                    }
                    .map(GPUDrawPacket::targetStateHash)
                    .toSet(),
            )
        }
    }

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

        val result = GPUPreparedSurfaceFrameTaskListBuilder().build(
            request(base, semantics, GPUColorFormat.RGBA8UnormSrgb),
        )
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
        val colorContract = taskList.diagnostics.single {
            it.code.value == "info.recording.prepared_image_color_contract"
        }
        assertEquals("RGBA8UnormSrgb", colorContract.facts.getValue("image.upload.format"))
        assertEquals("StraightEncodedSrgb", colorContract.facts.getValue("image.upload.encoding"))
        assertEquals("rgba8unorm-srgb", colorContract.facts.getValue("image.target.format"))
        assertEquals("LinearPremul", colorContract.facts.getValue("image.shader.interpretation"))
        assertEquals("true", colorContract.facts.getValue("image.attachment.srgbConversion"))
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
            it.name == "imageResourcePlan"
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

    @Test
    fun `adjacent image packets with distinct pass identities form distinct route runs`() {
        val base = recording(imageCommand(0, 0), imageCommand(1, 1)).taskList
        val shared = imageSemantic(base, 0)
        val semanticMap = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to shared,
            1 to imageSemantic(base, 1, artifactOverride = shared),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semanticMap)),
        ).taskList

        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(2, renders.size)
        assertTrue(renders.all { render ->
            render.drawPackets.map(GPUDrawPacket::passId).distinct().size == 1
        })
        assertFalse(GPUFramePlanner.plan(taskList).atomicallyRefused)
    }

    @Test
    fun `adjacent image packets with distinct provisional segment keys form distinct route runs`() {
        val original = recording(imageCommand(0, 0), imageCommand(1, 1)).taskList
        val base = original.splitRenders(
            provisionalKeyFor = { packet ->
                GPUProvisionalRenderSegmentKey("segment.${packet.commandIdValue}")
            },
        )
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

    @Test
    fun `adjacent image packets with distinct depth stencil authorities form distinct route runs`() {
        val original = recording(imageCommand(0, 0), imageCommand(1, 1)).taskList
        val base = original.splitRenders(
            depthStencilFor = { packet ->
                if (packet.commandIdValue == 0) null else GPUDepthStencilLoadStorePlan.ReadOnlyKeep
            },
        )
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

    @Test
    fun `frame plan linearization retains exact prepared image upload and binding authority`() {
        val base = recording(imageCommand(0, 0)).taskList
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics(base))),
        ).taskList
        val uploadTask = taskList.tasks.filterIsInstance<GPUTask.Upload>().single()
        val renderTask = taskList.tasks.filterIsInstance<GPUTask.Render>().single()

        val framePlan = GPUFramePlanner.plan(taskList)
        val uploadStep = framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>().single()
        val uploadPlanField = uploadStep.javaClass.declaredFields.singleOrNull {
            it.name == "imageResourcePlan"
        }
        assertNotNull(uploadPlanField, "Frame upload step must retain its prepared-image plan")
        uploadPlanField.isAccessible = true
        assertSame(uploadTask.imageResourcePlan, uploadPlanField.get(uploadStep))
        val renderStep = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val bindingsField = renderStep.javaClass.declaredFields.singleOrNull {
            it.name == "preparedImageBindingsByPacketId"
        }
        assertNotNull(bindingsField, "Frame render step must retain prepared-image bindings")
        bindingsField.isAccessible = true
        assertEquals(renderTask.preparedImageBindingsByPacketId, bindingsField.get(renderStep))
    }

    @Test
    fun `two packet image run materializes one explicitly sealed render scope in packet order`() {
        val recorded = recording(imageCommand(0, 0), imageCommand(1, 1)).taskList
        val sharedPassId = packet(recorded, 0).passId
        val base = recorded.transformPackets { packet -> packet.rebuilt(passId = sharedPassId) }
        val nearest = imageSemantic(
            base,
            commandId = 0,
            sampling = GPUPreparedImageSampling.Nearest,
        )
        val imageSemantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to nearest,
            1 to imageSemantic(
                base,
                commandId = 1,
                artifactOverride = nearest,
                sampling = GPUPreparedImageSampling.Linear,
            ),
        )
        val target = GPUFrameTargetRef("target.prepared-image-key-authority")
        val capabilities = capabilities()
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = target,
                    targetBounds = bounds,
                    semanticsByCommandId = imageSemantics,
                    readbackRequestId = null,
                ),
            ),
        ).taskList
        val framePlan = GPUFramePlanner.plan(taskList)
        assertFalse(framePlan.atomicallyRefused, framePlan.diagnostics.toString())
        val renderPackets = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .single()
            .drawPackets
        assertEquals(listOf(0, 1), renderPackets.map(GPUDrawPacket::commandIdValue))
        val targetGeneration = renderPackets.first().resourceGeneration
        val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap { it.requests }
            .associate { request ->
                request.resource to if (request.resource == target) targetGeneration else 5L
            }
        val completion = object : GPUQueueCompletionProvider {
            override fun reserveTicket(
                request: GPUQueueCompletionTicketRequest,
            ): GPUQueueCompletionTicketReservation =
                GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(
                        GPUQueueCompletionTicketID("ticket.prepared-image-key-authority"),
                        request.frameId,
                        request.deviceGeneration,
                    ),
                )

            override fun abandonReservedTicket(
                ticket: GPUQueueCompletionTicket,
            ): GPUQueueCompletionTicketAbandonResult =
                GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
        }
        val surface = object : GPUSurfaceOutputProvider {
            override fun acquire(
                request: GPUSurfaceAcquisitionRequest,
            ): GPUSurfaceAcquisitionResult =
                error("Prepared image authority preflight must not acquire a surface")

            override fun release(
                output: GPUAcquiredSurfaceOutput,
            ): GPUSurfaceReleaseResult =
                error("Prepared image authority preflight must not release a surface")
        }
        var nominalScopes = emptyList<GPUCommandEncoderScopePlan>()
        val preflight = GPUFramePreflighter(
            context = GPUFramePreflightContext(
                targetId = target.value,
                deviceGeneration = taskList.capabilitySeal.deviceGeneration,
                targetGeneration = targetGeneration,
                resourceGenerations = resourceGenerations,
            ),
            capabilities = capabilities,
            resourceProvider = GPUConcreteResourceProvider(),
            completionProvider = completion,
            surfaceProvider = surface,
            nominalEncoderScopeObserver = { scopes -> nominalScopes = scopes },
        ).preflight(framePlan)
        assertEquals(
            "unsupported.preflight.sampled_image_unmaterialized",
            assertIs<GPUFramePreflightResult.Refused>(preflight).diagnostic.code.value,
            "Task 5 nominal-key inspection must not open the Task 6 product route",
        )
        val scopes = nominalScopes
        assertTrue(scopes.isEmpty(), "Early sampled-image refusal must not publish nominal scopes")
        val resource = requireNotNull(
            taskList.tasks.filterIsInstance<GPUTask.Upload>().single().imageResourcePlan,
        )
        val semantics = renderPackets.map { packet ->
            val nominal = assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload)
            GPUPreparedImagePayloadGatherer().gatherSemantic(
                GPUPreparedImagePayloadInput(
                    payloadRef = nominal.payloadRef,
                    artifact = nominal.artifact,
                    geometry = nominal.geometry,
                    sampling = nominal.sampling,
                    tintPremultipliedRgba = nominal.tintPremultipliedRgba,
                    atlasColorPremultipliedRgba = nominal.atlasColorPremultipliedRgba,
                    atlasSourceBlend = nominal.atlasSourceBlend,
                    targetBounds = nominal.targetBounds,
                    scissorBounds = nominal.scissorBounds,
                    blendPlanIdentity = "SrcOver",
                    frameProvenance = nominal.frameProvenance,
                ),
            )
        }
        val sourceScopeIndices = listOf(
            framePlan.steps.indexOf(
                framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>().single(),
            ),
            framePlan.steps.indexOf(
                framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single(),
            ),
        )
        fun testOperand(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            suffix: String,
        ) = GPUPreparedNativeOperandKey(
            role = role,
            kind = kind,
            bindingKey = gpuPreparedNativeBindingKey("prepared-image-test:$suffix"),
        )
        val exactScopeKeys = listOf(
            GPUPreparedNativeScopeKey(
                sourceStepIndex = sourceScopeIndices.first(),
                operationKind = GPUEncoderOperationKind.Upload,
                operandKeys = listOf(
                    testOperand(
                        GPUPreparedNativeOperandRole.UploadSource,
                        GPUPreparedNativeOperandKind.Buffer,
                        "upload-source",
                    ),
                    testOperand(
                        GPUPreparedNativeOperandRole.UploadDestination,
                        GPUPreparedNativeOperandKind.Texture,
                        "upload-destination",
                    ),
                ),
            ),
            GPUPreparedNativeScopeKey(
                sourceStepIndex = sourceScopeIndices.last(),
                operationKind = GPUEncoderOperationKind.Render,
                operandKeys = listOf(
                    testOperand(
                        GPUPreparedNativeOperandRole.RenderColorTarget,
                        GPUPreparedNativeOperandKind.TextureView,
                        "render-target",
                    ),
                ) + semantics.flatMapIndexed { index, _ ->
                    listOf(
                        testOperand(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "pipeline-$index",
                        ),
                        testOperand(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "bind-group-$index",
                        ),
                    )
                },
            ),
        )
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            taskList.capabilitySeal.deviceGeneration,
        )
        val handleFactory = RecordingPreparedImageHandleFactory()
        val runPlan = GPUPreparedImageRenderRunPlan(
            sourceScopeIndices = sourceScopeIndices,
            packets = semantics,
            resources = listOf(resource),
            uniformAllocations = resource.bindingRequests.map { it.uniformAllocation },
            exactScopeKeys = exactScopeKeys,
        )
        assertEquals(runPlan, runPlan.copy())
        assertEquals(runPlan.hashCode(), runPlan.copy().hashCode())
        val materialized = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedImageRenderRunMaterializer(
                cache,
                handleFactory,
            ).materializeAcceptedRun(
                runPlan,
                taskList.capabilitySeal.deviceGeneration,
            ),
        )

        assertEquals(
            exactScopeKeys.map { it.operandKeys },
            materialized.scopeOperands.map(GPUPreparedNativeScopeOperand::exactOperandKeys),
        )
        assertTrue(
            materialized.scopeOperands.first().exactOperandKeys.last().bindingKey ==
                exactScopeKeys.first().operandKeys.last().bindingKey,
            "texture upload must retain the generation-bearing destination key",
        )
        assertEquals(
            exactScopeKeys.last().operandKeys.map { it.bindingKey },
            materialized.scopeOperands.last().exactOperandKeys.map { it.bindingKey },
            "render target and command-bridge labels must come from preflight verbatim",
        )
        val renderRun = assertIs<GPUPreparedNativeScopeOperand.PreparedImageRenderRun>(
            materialized.scopeOperands.last(),
        )
        assertEquals(5, renderRun.exactOperandKeys.size)
        assertEquals(listOf(0L, 256L), renderRun.drawEntries.map { it.dynamicUniformOffset })
        assertSame(
            renderRun.drawEntries[0].pipeline.pipeline,
            renderRun.drawEntries[1].pipeline.pipeline,
        )
        assertEquals(1, nativeDevice.pipelineCreates)
        assertEquals(listOf("nearest", "linear"), handleFactory.samplerFilters)
        assertEquals(
            listOf(sourceScopeIndices.last()),
            materialized.uniformUploads.single().consumerSourceStepIndices,
        )
        assertTrue(renderRun.operands.none { it is org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeTextureViewOperand })

        materialized.ownedResources.single().close()
        cache.close()
    }

    @Test
    fun `sampled observer is not called when another packet materialization is invalid`() {
        val recorded = recording(imageCommand(0, 0), imageCommand(1, 1)).taskList
        val sharedPassId = packet(recorded, 0).passId
        val base = recorded.transformPackets { packet -> packet.rebuilt(passId = sharedPassId) }
        val nearest = imageSemantic(
            base,
            commandId = 0,
            sampling = GPUPreparedImageSampling.Nearest,
        )
        val imageSemantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to nearest,
            1 to imageSemantic(
                base,
                commandId = 1,
                artifactOverride = nearest,
                sampling = GPUPreparedImageSampling.Linear,
            ),
        )
        val target = GPUFrameTargetRef("target.prepared-image-invalid-observer")
        val capabilities = capabilities()
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = target,
                    targetBounds = bounds,
                    semanticsByCommandId = imageSemantics,
                    readbackRequestId = null,
                ),
            ),
        ).taskList
        val delegate = GPUConcreteResourceProvider()
        val invalidProvider = object : GPUFrameResourcePreflightProvider by delegate {
            override fun materializeCommandOperands(
                request: GPUCommandOperandMaterializationRequest,
                context: GPUTargetPreparationContext,
            ): GPUResourceMaterializationDecision =
                when (val decision = delegate.materializeCommandOperands(request, context)) {
                    is GPUResourceMaterializationDecision.Materialized ->
                        decision.copy(taskIds = emptyList())
                    else -> decision
                }
        }
        var observerCalled = false

        val result = preflightPreparedTaskList(
            taskList = taskList,
            target = target,
            capabilities = capabilities,
            resourceProvider = invalidProvider,
            observer = { observerCalled = true },
        )

        assertEquals(
            "unsupported.preflight.sampled_image_unmaterialized",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertFalse(observerCalled)
    }

    @Test
    fun `sampled early refusal does not invoke a throwing observer`() {
        class NominalObserverFailure : RuntimeException("nominal observer failure")

        val base = recording(imageCommand(0, 0)).taskList
        val target = GPUFrameTargetRef("target.prepared-image-throwing-observer")
        val capabilities = capabilities()
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = target,
                    targetBounds = bounds,
                    semanticsByCommandId = semantics(base),
                    readbackRequestId = null,
                ),
            ),
        ).taskList

        val result = preflightPreparedTaskList(
            taskList = taskList,
            target = target,
            capabilities = capabilities,
            resourceProvider = GPUConcreteResourceProvider(),
            observer = { throw NominalObserverFailure() },
        )
        assertEquals(
            "unsupported.preflight.sampled_image_unmaterialized",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `prepared image sampler uniform descriptor and payload participate in frame identity`() {
        val base = recording(imageCommand(0, 0)).taskList
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics(base))),
        ).taskList
        val framePlan = GPUFramePlanner.plan(taskList)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val upload = framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>().single()
        val packetId = render.drawPackets.single().packetId
        val binding = render.preparedImageBindingsByPacketId.getValue(packetId)
        val resourcePlan = requireNotNull(upload.imageResourcePlan)
        val uploadBytes = resourcePlan.uploadLayout.bytesForUpload()
        val changedUploadBytes = uploadBytes.copyOf().also { bytes ->
            bytes[0] = (bytes[0].toInt() xor 0x7f).toByte()
        }
        val variants = linkedMapOf(
            "sampler" to framePlan.replacingStep(
                render,
                render.rebuilt(
                    mapOf(
                        packetId to binding.copy(
                            sampler = binding.sampler.copy(
                                magFilter = if (binding.sampler.magFilter == "nearest") "linear" else "nearest",
                            ),
                        ),
                    ),
                ),
            ),
            "uniform" to framePlan.replacingStep(
                render,
                render.rebuilt(
                    mapOf(
                        packetId to binding.copy(
                            uniformAllocation = binding.uniformAllocation.copy(
                                offset = binding.uniformAllocation.offset + 256L,
                            ),
                        ),
                    ),
                ),
            ),
            "descriptor" to framePlan.replacingStep(
                upload,
                upload.rebuilt(
                    resourcePlan.rebuilt(
                        textureDescriptor = resourcePlan.textureDescriptor.copy(
                            sampleCount = resourcePlan.textureDescriptor.sampleCount + 1,
                        ),
                    ),
                ),
            ),
            "payload" to framePlan.replacingStep(
                upload,
                upload.rebuilt(
                    resourcePlan.rebuilt(
                        uploadLayout = GPUPreparedImageUploadLayout(
                            logicalBytesPerRow = resourcePlan.uploadLayout.logicalBytesPerRow,
                            bytesPerRow = resourcePlan.uploadLayout.bytesPerRow,
                            rowsPerImage = resourcePlan.uploadLayout.rowsPerImage,
                            width = resourcePlan.uploadLayout.width,
                            height = resourcePlan.uploadLayout.height,
                            paddedUploadBytes = changedUploadBytes,
                        ),
                    ),
                ),
            ),
        )
        val baseDump = framePlan.dumpLines().joinToString("\n")
        val identityCollisions = variants.flatMap { (authority, variant) ->
            buildList {
                if (framePlan.stableHash() == variant.stableHash()) add("$authority:hash")
                if (baseDump == variant.dumpLines().joinToString("\n")) add("$authority:dump")
            }
        }

        assertEquals(emptyList(), identityCollisions)
        assertTrue(baseDump.contains("payloadSha256="), baseDump)
        assertFalse(baseDump.contains(uploadBytes.contentToString()), baseDump)
    }

    @Test
    fun `prepared image binding insertion order does not change canonical frame identity`() {
        val base = recording(imageCommand(0, 0)).taskList
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics(base))),
        ).taskList
        val framePlan = GPUFramePlanner.plan(taskList)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val firstPacket = render.drawPackets.single()
        val secondPacket = firstPacket.rebuilt(
            packetId = GPUDrawPacketID("packet.image.synthetic-second"),
        )
        val firstBinding = render.preparedImageBindingsByPacketId.getValue(firstPacket.packetId)
        val secondBinding = firstBinding.copy(packetId = secondPacket.packetId.value)
        val orderedEntries = listOf(
            firstPacket.packetId to firstBinding,
            secondPacket.packetId to secondBinding,
        )
        val forward = framePlan.replacingStep(
            render,
            render.rebuilt(
                drawPackets = listOf(firstPacket, secondPacket),
                preparedImageBindingsByPacketId = linkedMapOf(*orderedEntries.toTypedArray()),
            ),
        )
        val reversed = framePlan.replacingStep(
            render,
            render.rebuilt(
                drawPackets = listOf(firstPacket, secondPacket),
                preparedImageBindingsByPacketId = linkedMapOf(*orderedEntries.reversed().toTypedArray()),
            ),
        )

        assertEquals(forward.stableHash(), reversed.stableHash())
        assertEquals(forward.dumpLines(), reversed.dumpLines())
    }

    @Test
    fun `render requires exact prepared image binding coverage`() {
        val base = recording(imageCommand(0, 0)).taskList
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request(base, semantics(base))),
        ).taskList
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()

        assertFailsWith<IllegalArgumentException> {
            render.rebuilt(preparedImageBindingsByPacketId = emptyMap())
        }
        val binding = render.preparedImageBindingsByPacketId.values.single()
        assertFailsWith<IllegalArgumentException> {
            render.rebuilt(
                preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId +
                    (GPUDrawPacketID("packet.extra") to binding.copy(packetId = "packet.extra")),
            )
        }
    }

    private fun request(
        base: GPUTaskList,
        semantics: Map<Int, GPUDrawSemanticPayload>,
        targetFormat: GPUColorFormat = GPUColorFormat.RGBA8Unorm,
    ) = GPUPreparedSurfaceFrameRequest(
        baseTaskList = base,
        capabilities = capabilities(),
        target = GPUFrameTargetRef("target.prepared-surface"),
        targetBounds = bounds,
        semanticsByCommandId = semantics,
        readbackRequestId = GPUReadbackRequestID("readback.prepared-surface"),
        targetFormat = targetFormat,
    )

    private fun preflightPreparedTaskList(
        taskList: GPUTaskList,
        target: GPUFrameTargetRef,
        capabilities: GPUCapabilities,
        resourceProvider: GPUFrameResourcePreflightProvider,
        observer: (List<GPUCommandEncoderScopePlan>) -> Unit,
    ): GPUFramePreflightResult {
        val framePlan = GPUFramePlanner.plan(taskList)
        assertFalse(framePlan.atomicallyRefused, framePlan.diagnostics.toString())
        val targetGeneration = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .first()
            .resourceGeneration
        val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap { it.requests }
            .associate { request ->
                request.resource to if (request.resource == target) targetGeneration else 5L
            }
        val completion = object : GPUQueueCompletionProvider {
            override fun reserveTicket(
                request: GPUQueueCompletionTicketRequest,
            ): GPUQueueCompletionTicketReservation =
                GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(
                        GPUQueueCompletionTicketID("ticket.prepared-image-observer"),
                        request.frameId,
                        request.deviceGeneration,
                    ),
                )

            override fun abandonReservedTicket(
                ticket: GPUQueueCompletionTicket,
            ): GPUQueueCompletionTicketAbandonResult =
                GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
        }
        val surface = object : GPUSurfaceOutputProvider {
            override fun acquire(
                request: GPUSurfaceAcquisitionRequest,
            ): GPUSurfaceAcquisitionResult =
                error("Prepared image observer preflight must not acquire a surface")

            override fun release(
                output: GPUAcquiredSurfaceOutput,
            ): GPUSurfaceReleaseResult =
                error("Prepared image observer preflight must not release a surface")
        }
        return GPUFramePreflighter(
            context = GPUFramePreflightContext(
                targetId = target.value,
                deviceGeneration = taskList.capabilitySeal.deviceGeneration,
                targetGeneration = targetGeneration,
                resourceGenerations = resourceGenerations,
            ),
            capabilities = capabilities,
            resourceProvider = resourceProvider,
            completionProvider = completion,
            surfaceProvider = surface,
            nominalEncoderScopeObserver = observer,
        ).preflight(framePlan)
    }

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

    private fun GPUTaskList.splitRenders(
        provisionalKeyFor: (GPUDrawPacket) -> GPUProvisionalRenderSegmentKey = {
            tasks.filterIsInstance<GPUTask.Render>().first().provisionalSegmentKey
        },
        depthStencilFor: (GPUDrawPacket) -> GPUDepthStencilLoadStorePlan? = {
            tasks.filterIsInstance<GPUTask.Render>().first().depthStencilLoadStore
        },
    ): GPUTaskList {
        val template = tasks.filterIsInstance<GPUTask.Render>().first()
        val packets = tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .sortedBy(GPUDrawPacket::originalPaintOrder)
        val eligibility = tasks.filterIsInstance<GPUTask.Render>()
            .flatMap { render -> render.batchEligibilityByPacketId.entries }
            .associate { it.toPair() }
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals,
            expectedReplayKeyHash = expectedReplayKeyHash,
            tasks = packets.map { packet ->
                GPUTask.Render(
                    taskId = GPUTaskID("task.split.${packet.commandIdValue}"),
                    recordingId = template.recordingId,
                    phase = template.phase,
                    target = template.target,
                    loadStore = template.loadStore,
                    samplePlan = template.samplePlan,
                    resourceUses = template.resourceUses,
                    provisionalSegmentKey = provisionalKeyFor(packet),
                    drawPackets = listOf(packet),
                    batchEligibilityByPacketId = mapOf(
                        packet.packetId to eligibility.getValue(packet.packetId),
                    ),
                    sampleContinuationKey = template.sampleContinuationKey,
                    depthStencilLoadStore = depthStencilFor(packet),
                )
            },
            dependencies = emptyList(),
            phaseOrder = phaseOrder,
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
        )
    }

    private fun GPUTask.Render.rebuilt(
        preparedImageBindingsByPacketId:
            Map<GPUDrawPacketID, org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest>,
    ) = GPUTask.Render(
        taskId = taskId,
        recordingId = recordingId,
        phase = phase,
        target = target,
        loadStore = loadStore,
        samplePlan = samplePlan,
        resourceUses = resourceUses,
        provisionalSegmentKey = provisionalSegmentKey,
        drawPackets = drawPackets,
        batchEligibilityByPacketId = batchEligibilityByPacketId,
        sampleContinuationKey = sampleContinuationKey,
        compositeMembership = compositeMembership,
        depthStencilLoadStore = depthStencilLoadStore,
        preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
    )

    private fun GPUFrameStep.RenderPassStep.rebuilt(
        preparedImageBindingsByPacketId: Map<GPUDrawPacketID, GPUImageBindingRequest>,
        drawPackets: List<GPUDrawPacket> = this.drawPackets,
    ) = GPUFrameStep.RenderPassStep(
        target = target,
        loadStore = loadStore,
        samplePlan = samplePlan,
        resourceUses = resourceUses,
        drawPackets = drawPackets,
        sourceTaskIds = sourceTaskIds,
        sampleContinuation = sampleContinuation,
        depthStencilLoadStore = depthStencilLoadStore,
        preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
    )

    private fun GPUFrameStep.UploadResourceStep.rebuilt(
        imageResourcePlan: GPUImageFrameResourcePlan,
    ) = GPUFrameStep.UploadResourceStep(
        staging = staging,
        destination = destination,
        layout = layout,
        sourceTaskIds = sourceTaskIds,
        imageResourcePlan = imageResourcePlan,
    )

    private fun GPUImageFrameResourcePlan.rebuilt(
        textureDescriptor:
            org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor = this.textureDescriptor,
        uploadLayout: GPUPreparedImageUploadLayout = this.uploadLayout,
    ) = copy(
        textureDescriptor = textureDescriptor,
        uploadLayout = uploadLayout,
    )

    private fun GPUFramePlan.replacingStep(
        original: GPUFrameStep,
        replacement: GPUFrameStep,
    ) = GPUFramePlan(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        steps = steps.map { step -> if (step === original) replacement else step },
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        elidedNoOpDraws = elidedNoOpDraws,
        atomicallyRefused = atomicallyRefused,
    )

    private fun GPUDrawPacket.rebuilt(
        packetId: GPUDrawPacketID = this.packetId,
        passId: String = this.passId,
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
