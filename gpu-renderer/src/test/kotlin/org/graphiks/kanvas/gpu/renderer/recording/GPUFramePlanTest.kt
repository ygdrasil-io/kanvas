package org.graphiks.kanvas.gpu.renderer.recording

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.buildPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.buildR8FrameResourcePlan

class GPUFramePlanTest {
    @Test
    fun `RGBA and R8 plans satisfy one generic texture upload step without duplicate typed state`() {
        val capabilities = capabilities()
        val image = imagePlan(capabilities)
        val r8 = r8Plan(capabilities)
        val plans: List<GPUTextureFrameResourcePlan> = listOf(image, r8)

        val steps = plans.mapIndexed { index, plan ->
            GPUFrameStep.UploadResourceStep(
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                sourceTaskIds = listOf(GPUTaskID("task.upload.$index")),
                textureResourcePlan = plan,
            )
        }

        assertSame(image, steps[0].textureResourcePlan)
        assertSame(image, steps[0].imageResourcePlan)
        assertNull(steps[0].r8ResourcePlan)
        assertSame(r8, steps[1].textureResourcePlan)
        assertNull(steps[1].imageResourcePlan)
        assertSame(r8, steps[1].r8ResourcePlan)
        assertContentEquals(image.uploadLayout.bytesForUpload(), image.bytesForUpload())
        assertContentEquals(r8.bytesForUpload(), plans[1].bytesForUpload())
    }

    @Test
    fun `frame planner transmits the exact generic R8 texture plan`() {
        val capabilities = capabilities()
        val plan = r8Plan(capabilities)
        val upload = GPUTask.Upload(
            taskId = GPUTaskID("task.upload.r8"),
            recordingId = GPURecordingID("recording.r8"),
            phase = GPUTaskPhase.Upload,
            staging = plan.stagingRef,
            destination = plan.frameTextureRef,
            layout = plan.uploadTaskLayout,
            textureResourcePlan = plan,
        )

        val framePlan = GPUFramePlanner.plan(taskList(capabilities, upload, plan))
        val step = framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>().single()

        assertEquals(GPUUploadDestinationKind.Texture, upload.destinationKind)
        assertEquals(GPUUploadDestinationKind.Texture, step.destinationKind)
        assertSame(plan, upload.textureResourcePlan)
        assertSame(plan, upload.r8ResourcePlan)
        assertSame(plan, step.textureResourcePlan)
        assertSame(plan, step.r8ResourcePlan)
    }

    @Test
    fun `generic texture upload constructor rejects a mismatched exact plan`() {
        val plan = r8Plan(capabilities())

        assertFailsWith<IllegalArgumentException> {
            GPUFrameStep.UploadResourceStep(
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout.copy(bytesPerRow = 512),
                sourceTaskIds = listOf(GPUTaskID("task.upload.invalid")),
                textureResourcePlan = plan,
            )
        }
    }

    @Test
    fun `generic texture plan is a closed contract`() {
        assertTrue(GPUTextureFrameResourcePlan::class.java.isSealed)
    }

    @Test
    fun `upload task and step store exactly one generic texture plan backing field`() {
        val plan = r8Plan(capabilities())
        val task = GPUTask.Upload(
            taskId = GPUTaskID("task.upload.backing-field"),
            recordingId = GPURecordingID("recording.backing-field"),
            phase = GPUTaskPhase.Upload,
            staging = plan.stagingRef,
            destination = plan.frameTextureRef,
            layout = plan.uploadTaskLayout,
            textureResourcePlan = plan,
        )
        val step = GPUFrameStep.UploadResourceStep(
            staging = plan.stagingRef,
            destination = plan.frameTextureRef,
            layout = plan.uploadTaskLayout,
            sourceTaskIds = listOf(task.taskId),
            textureResourcePlan = plan,
        )

        listOf(task, step).forEach { uploadContract ->
            val fields = uploadContract.javaClass.declaredFields.toList()
            assertEquals(
                listOf("textureResourcePlan"),
                fields
                    .filter { field ->
                        GPUTextureFrameResourcePlan::class.java.isAssignableFrom(field.type)
                    }
                    .map { field -> field.name },
            )
            assertNull(fields.singleOrNull { field -> field.name == "imageResourcePlan" })
            assertNull(fields.singleOrNull { field -> field.name == "r8ResourcePlan" })
        }
    }

    @Test
    fun `R8 upload bytes requests and allocations participate in frame hash and dump`() {
        val capabilities = capabilities()
        val firstPlan = r8Plan(
            capabilities = capabilities,
            bytes = byteArrayOf(1, 2, 3, 4),
        )
        val secondPlan = r8Plan(
            capabilities = capabilities,
            bytes = byteArrayOf(4, 3, 2, 1),
        )
        val first = plannedR8Frame(capabilities, firstPlan)
        val second = plannedR8Frame(capabilities, secondPlan)

        assertNotEquals(first.stableHash(), second.stableHash())
        assertNotEquals(first.dumpLines(), second.dumpLines())
        val firstDump = first.dumpLines().joinToString("\n")
        assertTrue(firstDump.contains(firstPlan.artifactContentHash))
        assertTrue(firstDump.contains(firstPlan.preparationRequests[0].diagnosticLabel))
        assertTrue(firstDump.contains(firstPlan.memoryAllocations[0].label))
    }

    private fun imagePlan(capabilities: GPUCapabilities): GPUImageFrameResourcePlan =
        buildPreparedImageFrameResourcePlan(
            artifact = (GPUPreparedImageArtifactFactory.prepare(
                GPUPreparedImageSourceInput(
                    sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                    sourceId = "generic-plan",
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
            frameIdentity = "frame.generic-image",
        )

    private fun r8Plan(
        capabilities: GPUCapabilities,
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
    ): GPUR8FrameResourcePlan {
        return buildR8FrameResourcePlan(
            artifact = GPUPreparedR8UploadArtifact(
                key = "page-r8",
                width = 2,
                height = 2,
                rowBytes = 2,
                generation = 5,
                contentHash = sha256(bytes),
                bytes = bytes,
            ),
            capabilities = capabilities,
            frameIdentity = "frame.generic-r8",
        )
    }

    private fun plannedR8Frame(
        capabilities: GPUCapabilities,
        plan: GPUR8FrameResourcePlan,
    ): GPUFramePlan {
        val task = GPUTask.Upload(
            taskId = GPUTaskID("task.upload.r8-hash"),
            recordingId = GPURecordingID("recording.r8-hash"),
            phase = GPUTaskPhase.Upload,
            staging = plan.stagingRef,
            destination = plan.frameTextureRef,
            layout = plan.uploadTaskLayout,
            textureResourcePlan = plan,
        )
        return GPUFramePlanner.plan(taskList(capabilities, task, plan))
    }

    private fun taskList(
        capabilities: GPUCapabilities,
        task: GPUTask,
        plan: GPUTextureFrameResourcePlan,
    ): GPUTaskList {
        val frameId = GPUFrameID(42)
        val seal = GPUFrameCapabilitySeal.capture(
            frameId,
            GPUDeviceGenerationID(7),
            capabilities,
        )
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
                    taskId = GPUTaskID("task.prepare.r8"),
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
        snapshotId = "generic-texture-frame-plan",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
