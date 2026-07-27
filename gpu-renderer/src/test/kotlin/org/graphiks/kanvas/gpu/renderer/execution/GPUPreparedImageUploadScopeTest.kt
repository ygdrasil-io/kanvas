package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.buildPreparedImageFrameResourcePlan

class GPUPreparedImageUploadScopeTest {
    @Test
    fun `prepared image upload keeps texture destination kind through frame linearization`() {
        val capabilities = capabilities()
        val plan = imageResourcePlan(capabilities)
        val upload = GPUTask.Upload(
            taskId = GPUTaskID("task.upload.image"),
            recordingId = GPURecordingID("recording.prepared-image"),
            phase = GPUTaskPhase.Upload,
            staging = plan.stagingRef,
            destination = plan.frameTextureRef,
            layout = plan.uploadTaskLayout,
            imageResourcePlan = plan,
        )

        assertEquals(GPUUploadDestinationKind.Texture, upload.destinationKind)

        val framePlan = GPUFramePlanner.plan(taskList(capabilities, upload, plan))
        val uploadStep = framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>().single()
        assertEquals(GPUUploadDestinationKind.Texture, uploadStep.destinationKind)
        assertEquals(plan.stagingRef, uploadStep.staging)
        assertEquals(plan.frameTextureRef, uploadStep.destination)
        assertEquals(plan.uploadTaskLayout, uploadStep.layout)
        assertEquals(plan, uploadStep.imageResourcePlan)
    }

    @Test
    fun `pure preflight seals texture upload data and destination without native allocation`() {
        val capabilities = capabilities()
        val plan = imageResourcePlan(capabilities)
        val upload = GPUTask.Upload(
            taskId = GPUTaskID("task.upload.image"),
            recordingId = GPURecordingID("recording.prepared-image"),
            phase = GPUTaskPhase.Upload,
            staging = plan.stagingRef,
            destination = plan.frameTextureRef,
            layout = plan.uploadTaskLayout,
            imageResourcePlan = plan,
        )
        val framePlan = GPUFramePlanner.plan(taskList(capabilities, upload, plan))
        val provider = GPUConcreteResourceProvider()
        val completion = object : GPUQueueCompletionProvider {
            override fun reserveTicket(
                request: GPUQueueCompletionTicketRequest,
            ): GPUQueueCompletionTicketReservation =
                GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(
                        GPUQueueCompletionTicketID("ticket.prepared-image"),
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
            override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
                error("Prepared-image upload-only preflight must not acquire a surface")

            override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult =
                error("Prepared-image upload-only preflight must not release a surface")
        }

        val preflightResult = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = "target.prepared-image",
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = plan.preparationRequests
                        .map { request -> request.resource }
                        .associateWith { 1L },
                ),
                capabilities = capabilities,
                resourceProvider = provider,
                completionProvider = completion,
                surfaceProvider = surface,
            ).preflight(framePlan)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflightResult,
            (preflightResult as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).frame
        val scope = prepared.encoderPlan.scopes.single()

        assertEquals(listOf("writeTexture"), scope.facadeOperationClasses)
        assertEquals(
            listOf(GPUPreparedNativeOperandRole.UploadSource, GPUPreparedNativeOperandRole.UploadDestination),
            scope.nativeOperandKeys.map(GPUPreparedNativeOperandKey::role),
        )
        assertEquals(
            listOf(GPUPreparedNativeOperandKind.Buffer, GPUPreparedNativeOperandKind.Texture),
            scope.nativeOperandKeys.map(GPUPreparedNativeOperandKey::kind),
        )
        assertEquals(
            gpuPreparedNativeBindingKey("prepared-image-upload-data:${plan.stagingRef.value}"),
            scope.nativeOperandKeys.first().bindingKey,
        )
        assertEquals(
            gpuPreparedNativeBindingKey(scope.resourceGenerationLabels[1]),
            scope.nativeOperandKeys.last().bindingKey,
        )
        assertEquals(false, prepared.hasNativePayload)
        prepared.rollback.execute()
    }

    private fun imageResourcePlan(capabilities: GPUCapabilities) =
        buildPreparedImageFrameResourcePlan(
            artifact = (GPUPreparedImageArtifactFactory.prepare(
                GPUPreparedImageSourceInput(
                    sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                    sourceId = "upload-scope",
                    width = 1,
                    height = 1,
                    sourceFormat = GPUPreparedImageSourceFormat.Rgba8,
                    alphaType = AlphaType.PREMUL,
                    sourceRowBytes = 4,
                    profile = GPUPreparedImageProfile.Srgb,
                    orientation = GPUPreparedImageOrientation.AppliedIdentity,
                    provenance = GPUPreparedImageProvenance.CallerPixels,
                    sourceGeneration = 3,
                    pixelBytes = byteArrayOf(1, 2, 3, 4),
                ),
            ) as GPUPreparedImageArtifactResult.Ready).artifact,
            packetIds = listOf("packet.image"),
            bindingLayoutHash = "layout.image",
            capabilities = capabilities,
            frameIdentity = "frame.upload-scope",
        )

    private fun taskList(
        capabilities: GPUCapabilities,
        task: GPUTask,
        plan: org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan,
    ): GPUTaskList {
        val frameId = GPUFrameID(41)
        val generation = GPUDeviceGenerationID(7)
        val seal = GPUFrameCapabilitySeal.capture(frameId, generation, capabilities)
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(
                    recordingId = task.recordingId,
                    insertionOrder = 0,
                    compatibilityKeyHash = "compat",
                    replayKeyHash = "replay",
                    capabilitySealHash = seal.sealHash,
                ),
            ),
            expectedReplayKeyHash = "replay",
            tasks = listOf(
                GPUTask.PrepareResources(
                    taskId = GPUTaskID("task.prepare.image"),
                    recordingId = task.recordingId,
                    phase = GPUTaskPhase.Prepare,
                    requests = plan.preparationRequests,
                ),
                task,
            ),
            dependencies = emptyList(),
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
                GPUFrameMemoryBudgetRequest(
                    allocations = plan.memoryAllocations,
                    configuredAggregateBudgetBytes = 1L shl 30,
                    deviceLimits = requireNotNull(capabilities.limits),
                ),
            ),
        )
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "prepared-image-upload-scope",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )
}
