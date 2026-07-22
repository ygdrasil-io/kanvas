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
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

class GPUSeparableBlurRectFrameRecorderTest {
    @Test
    fun `consumer records source horizontal and vertical blur passes with explicit intermediates`() {
        val recorded = assertIs<GPUSeparableBlurRectFrameRecordingResult.Recorded>(
            GPUSeparableBlurRectFrameRecorder().record(
                request(),
            ),
        )

        assertTrue(recorded.semantic.hasCanonicalHashIntegrity())
        assertEquals(5, recorded.semantic.tapCount)
        assertEquals(25, recorded.semantic.weights.size)

        val prepare = recorded.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().single()
        assertEquals(2, prepare.requests.count { it.role == GPUFrameResourceRole.FilterTarget })
        val renders = recorded.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(
            listOf("target.blur.source.31", "target.blur.scratch.31", "target.consumer.blur"),
            renders.map { it.target.value },
        )
        assertTrue(
            renders[1].resourceUses.any {
                it.resource.value == "target.blur.source.31" &&
                    it.usage == GPUFrameResourceUsage.TextureBinding && !it.write
            },
        )
        assertTrue(
            renders[2].resourceUses.any {
                it.resource.value == "target.blur.scratch.31" &&
                    it.usage == GPUFrameResourceUsage.TextureBinding && !it.write
            },
        )
        assertEquals(1, recorded.taskList.tasks.filterIsInstance<GPUTask.Readback>().size)
    }

    @Test
    fun `consumer refuses blur outside the bounded first slice`() {
        val refused = assertIs<GPUSeparableBlurRectFrameRecordingResult.Refused>(
            GPUSeparableBlurRectFrameRecorder().record(request(sigma = 12.1f)),
        )

        assertEquals("unsupported.recording.separable_blur.sigma", refused.diagnostic.code.value)
    }

    @Test
    fun `consumer records the supported lower sigma bound with three taps`() {
        val recorded = assertIs<GPUSeparableBlurRectFrameRecordingResult.Recorded>(
            GPUSeparableBlurRectFrameRecorder().record(request(sigma = 0.5f)),
        )

        assertEquals(3, recorded.semantic.tapCount)
        assertTrue(recorded.semantic.hasCanonicalHashIntegrity())
    }

    private fun request(sigma: Float = 2f) = GPUSeparableBlurRectFrameRecordingRequest(
        frameId = GPUFrameID(31),
        recordingId = GPURecordingID("recording.consumer.blur"),
        capabilities = capabilities(),
        deviceGeneration = GPUDeviceGenerationID(9),
        target = GPUFrameTargetRef("target.consumer.blur"),
        targetBounds = GPUPixelBounds(0, 0, 64, 48),
        sourceBounds = GPUPixelBounds(12, 10, 52, 38),
        sourcePremultipliedRgba = floatArrayOf(0.15f, 0.30f, 0.45f, 0.75f),
        clearPremultipliedRgba = floatArrayOf(0.02f, 0.03f, 0.04f, 1f),
        sigma = sigma,
        readbackRequestId = GPUReadbackRequestID("readback.consumer.blur"),
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "consumer-separable-blur")),
        snapshotId = "capabilities-consumer-separable-blur-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
