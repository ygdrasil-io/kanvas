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
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectResolvedDraw
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

class GPURegisteredUniformRectFrameRecorderTest {
    @Test
    fun `consumer records different registered programs in one immutable prepared batch`() {
        val mutableLinearUniform = ByteArray(GPURegisteredUniformProgram.LinearGradient.uniformByteSize) {
            (it + 1).toByte()
        }
        val result = assertIs<GPURegisteredUniformRectFrameRecordingResult.Recorded>(
            GPURegisteredUniformRectFrameRecorder().record(
                GPURegisteredUniformRectFrameRecordingRequest(
                    frameId = GPUFrameID(20),
                    recordingId = GPURecordingID("recording.consumer.registered-uniform"),
                    capabilities = capabilities(),
                    deviceGeneration = GPUDeviceGenerationID(9),
                    target = GPUFrameTargetRef("target.consumer.registered-uniform"),
                    targetBounds = GPUPixelBounds(0, 0, 64, 64),
                    draws = listOf(
                        GPURegisteredUniformRectResolvedDraw(
                            commandIdValue = 1,
                            bounds = GPUPixelBounds(0, 0, 64, 64),
                            program = GPURegisteredUniformProgram.SolidColor,
                            uniformBytes = ByteArray(16) { 0 },
                            paintOrder = 0,
                        ),
                        GPURegisteredUniformRectResolvedDraw(
                            commandIdValue = 2,
                            bounds = GPUPixelBounds(8, 9, 32, 40),
                            program = GPURegisteredUniformProgram.LinearGradient,
                            uniformBytes = mutableLinearUniform,
                            paintOrder = 1,
                        ),
                    ),
                    readbackRequestId = GPUReadbackRequestID("readback.consumer.registered-uniform"),
                ),
            ),
        )

        mutableLinearUniform.fill(0)

        assertEquals(
            listOf(GPURegisteredUniformProgram.SolidColor, GPURegisteredUniformProgram.LinearGradient),
            result.semantics.map { it.program },
        )
        assertEquals(1, result.semantics[1].uniformBytes.first())
        assertTrue(result.semantics.all { it.hasCanonicalHashIntegrity() })
        val render = result.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(2, render.drawPackets.size)
        assertTrue(render.drawPackets.all { it.resourceGeneration == 0L })
        assertEquals(1, result.taskList.tasks.filterIsInstance<GPUTask.Readback>().size)
    }

    @Test
    fun `consumer refuses a uniform block that does not match its registered ABI`() {
        val result = assertIs<GPURegisteredUniformRectFrameRecordingResult.Refused>(
            GPURegisteredUniformRectFrameRecorder().record(
                GPURegisteredUniformRectFrameRecordingRequest(
                    frameId = GPUFrameID(21),
                    recordingId = GPURecordingID("recording.consumer.registered-uniform.invalid"),
                    capabilities = capabilities(),
                    deviceGeneration = GPUDeviceGenerationID(9),
                    target = GPUFrameTargetRef("target.consumer.registered-uniform.invalid"),
                    targetBounds = GPUPixelBounds(0, 0, 8, 8),
                    draws = listOf(
                        GPURegisteredUniformRectResolvedDraw(
                            commandIdValue = 1,
                            bounds = GPUPixelBounds(0, 0, 8, 8),
                            program = GPURegisteredUniformProgram.LinearGradient,
                            uniformBytes = ByteArray(16),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("invalid.recording.registered_uniform_abi", result.diagnostic.code.value)
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "consumer-registered-uniform")),
        snapshotId = "capabilities-consumer-registered-uniform-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
