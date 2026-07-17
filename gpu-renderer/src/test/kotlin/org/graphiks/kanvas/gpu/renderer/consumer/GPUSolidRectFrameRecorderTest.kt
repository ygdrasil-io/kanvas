package org.graphiks.kanvas.gpu.renderer.consumer

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameResolvedDraw
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

class GPUSolidRectFrameRecorderTest {
    @Test
    fun `consumer records an opaque batch with one target and optional readback`() {
        val result = assertIs<GPUSolidRectFrameRecordingResult.Recorded>(
            GPUSolidRectFrameRecorder().record(
                GPUSolidRectFrameRecordingRequest(
                    frameId = GPUFrameID(1),
                    recordingId = GPURecordingID("recording.consumer.solid"),
                    capabilities = capabilities(),
                    deviceGeneration = GPUDeviceGenerationID(9),
                    target = GPUFrameTargetRef("target.consumer.solid"),
                    targetBounds = GPUPixelBounds(0, 0, 64, 64),
                    draws = listOf(
                        GPUSolidRectFrameResolvedDraw(1, GPUPixelBounds(0, 0, 64, 64), listOf(0f, 0f, 0f, 1f)),
                        GPUSolidRectFrameResolvedDraw(2, GPUPixelBounds(8, 9, 32, 40), listOf(1f, 0f, 0f, 1f)),
                    ),
                    readbackRequestId = GPUReadbackRequestID("readback.consumer.solid"),
                ),
            ),
        )

        assertEquals(2, result.semantics.size)
        val render = result.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(2, render.drawPackets.size)
        assertEquals(1, result.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().size)
        assertEquals(1, result.taskList.tasks.filterIsInstance<GPUTask.Readback>().size)
        assertTrue(render.drawPackets.all { it.semanticPayload != null })
    }

    @Test
    fun `consumer refuses invalid color before a task list is exposed`() {
        val result = assertIs<GPUSolidRectFrameRecordingResult.Refused>(
            GPUSolidRectFrameRecorder().record(
                GPUSolidRectFrameRecordingRequest(
                    frameId = GPUFrameID(2),
                    recordingId = GPURecordingID("recording.consumer.solid.translucent"),
                    capabilities = capabilities(),
                    deviceGeneration = GPUDeviceGenerationID(9),
                    target = GPUFrameTargetRef("target.consumer.solid"),
                    targetBounds = GPUPixelBounds(0, 0, 8, 8),
                    draws = listOf(
                        GPUSolidRectFrameResolvedDraw(1, GPUPixelBounds(0, 0, 8, 8), listOf(1f, 0f, 0f, 1.5f)),
                    ),
                ),
            ),
        )

        assertEquals("invalid.recording.solid_rect_color", result.diagnostic.code.value)
    }

    @Test
    fun `consumer refuses target byte size overflow before allocating frame resources`() {
        val hugeBounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, Int.MAX_VALUE)

        val result = assertIs<GPUSolidRectFrameRecordingResult.Refused>(
            GPUSolidRectFrameRecorder().record(
                GPUSolidRectFrameRecordingRequest(
                    frameId = GPUFrameID(3),
                    recordingId = GPURecordingID("recording.consumer.solid.overflow"),
                    capabilities = capabilities(),
                    deviceGeneration = GPUDeviceGenerationID(9),
                    target = GPUFrameTargetRef("target.consumer.solid.overflow"),
                    targetBounds = hugeBounds,
                    draws = listOf(
                        GPUSolidRectFrameResolvedDraw(1, hugeBounds, listOf(1f, 1f, 1f, 1f)),
                    ),
                ),
            ),
        )

        assertEquals("unsupported.recording.solid_rect_target_size", result.diagnostic.code.value)
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "consumer-solid")),
        snapshotId = "capabilities-consumer-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
