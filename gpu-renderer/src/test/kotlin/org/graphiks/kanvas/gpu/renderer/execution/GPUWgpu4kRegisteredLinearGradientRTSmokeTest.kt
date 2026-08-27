package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectResolvedDraw
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectMaterialEvaluationInput
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectMaterialEvaluationResult
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.LinearGradientRTCPUOracle
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUWgpu4kRegisteredLinearGradientRTSmokeTest {
    @Test
    fun `registered linear gradient parser CPU oracle and native WebGPU readback agree`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val ready = assertIs<GPUPreparedRuntimeEffectResolution.Ready>(
            KanvasPreparedRuntimeEffectResolver().resolve("runtime.linear_gradient_rt", 1),
        )
        assertEquals(64, ready.program.uniformBlockSizeBytes)
        val uniformBytes = linearGradientUniformBytes()
        val readbackRequestId = GPUReadbackRequestID("readback.runtime.linear-gradient-rt")
        val recorded = assertIs<GPURegisteredUniformRectFrameRecordingResult.Recorded>(
            GPURegisteredUniformRectFrameRecorder().record(
                GPURegisteredUniformRectFrameRecordingRequest(
                    frameId = GPUFrameID(11_001),
                    recordingId = GPURecordingID("recording.runtime.linear-gradient-rt"),
                    capabilities = requireNotNull(backend.capabilities),
                    deviceGeneration = backend.deviceGeneration,
                    target = GPUFrameTargetRef("target.runtime.linear-gradient-rt"),
                    targetBounds = GPUPixelBounds(0, 0, 4, 4),
                    draws = listOf(
                        GPURegisteredUniformRectResolvedDraw(
                            commandIdValue = 1,
                            bounds = GPUPixelBounds(0, 0, 4, 4),
                            program = GPURegisteredUniformProgram.LinearGradient,
                            uniformBytes = uniformBytes,
                        ),
                    ),
                    readbackRequestId = readbackRequestId,
                ),
            ),
        )
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(4, 4))
        try {
            val terminal = session.renderFrame(
                recorded.taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackRequestId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(GPUFrameStructuralOutcome.Succeeded, terminal.outcome)
            val actual = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
            val expected = expectedPixels(uniformBytes)
            assertEquals(expected.size, actual.size)
            assertEquals(expected.asList(), actual.asList())
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().readbackCopies)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }
}

private fun linearGradientUniformBytes(): ByteArray =
    ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
        listOf(
            0f, 0f, 0f, 0f,
            0f, 4f, 0f, 0f,
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ).forEach(::putFloat)
    }.array()

private fun expectedPixels(uniformBytes: ByteArray): ByteArray =
    ByteArray(4 * 4 * 4).also { bytes ->
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val color = assertIs<GPURuntimeEffectMaterialEvaluationResult.Color>(
                    LinearGradientRTCPUOracle.evaluateMaterial(
                        GPURuntimeEffectMaterialEvaluationInput(
                            uniformBytes,
                            localPositionX = x + 0.5f,
                            localPositionY = y + 0.5f,
                        ),
                    ),
                )
                listOf(color.r, color.g, color.b, color.a).forEachIndexed { channel, value ->
                    bytes[(y * 4 + x) * 4 + channel] =
                        (value.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                }
            }
        }
    }
