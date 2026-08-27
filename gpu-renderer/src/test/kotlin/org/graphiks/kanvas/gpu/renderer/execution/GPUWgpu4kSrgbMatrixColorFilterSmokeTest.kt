package org.graphiks.kanvas.gpu.renderer.execution

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.filters.SrgbMatrixColorFilter
import org.graphiks.kanvas.gpu.renderer.filters.SrgbMatrixColorFilterDescriptor
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectResolvedDraw
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUWgpu4kSrgbMatrixColorFilterSmokeTest {
    @Test
    fun `native ColorMatrix applies matrix in linear sRGB and matches independent CPU bytes`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val readbackRequestId = GPUReadbackRequestID("readback.srgb-colorfilter.matrix")
        val descriptor = SrgbMatrixColorFilterDescriptor(halfRedMatrix())
        val recorded = assertIs<GPURegisteredUniformRectFrameRecordingResult.Recorded>(
            GPURegisteredUniformRectFrameRecorder().record(
                GPURegisteredUniformRectFrameRecordingRequest(
                    frameId = GPUFrameID(10_900),
                    recordingId = GPURecordingID("recording.srgb-colorfilter.matrix"),
                    capabilities = capabilities,
                    deviceGeneration = backend.deviceGeneration,
                    target = GPUFrameTargetRef("target.srgb-colorfilter.matrix"),
                    targetBounds = GPUPixelBounds(0, 0, 4, 4),
                    draws = listOf(
                        GPURegisteredUniformRectResolvedDraw(
                            commandIdValue = 1,
                            bounds = GPUPixelBounds(0, 0, 4, 4),
                            program = GPURegisteredUniformProgram.ColorMatrix,
                            uniformBytes = descriptor.packNativeUniform(0.5f, 0.25f, 0.75f, 0.5f),
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

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            val actual = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
            val cpuPixel = SrgbMatrixColorFilter(descriptor)
                .applyEncodedStraightRgba(0.5f, 0.25f, 0.75f, 0.5f)
            val expected = ByteArray(4 * 4 * 4) { channel ->
                (cpuPixel[channel % 4].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            }
            val stats = byteDifferenceStats(expected, actual)
            println(
                "task9.srgb-colorfilter channels=${actual.size} differentChannels=${stats.differentChannels} " +
                    "maxDelta=${stats.maxDelta} meanDelta=${stats.meanDelta} " +
                    "cpuSha256=${sha256(expected)} gpuSha256=${sha256(actual)}",
            )
            assertEquals(expected.size, actual.size)
            assertTrue(stats.maxDelta <= 1, "sRGB matrix maxDelta=${stats.maxDelta} stats=$stats")
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

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun halfRedMatrix(): FloatArray = floatArrayOf(
    0.5f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun byteDifferenceStats(expected: ByteArray, actual: ByteArray): ByteDifferenceStats {
    require(expected.size == actual.size)
    val deltas = expected.indices.map { index ->
        kotlin.math.abs((expected[index].toInt() and 0xFF) - (actual[index].toInt() and 0xFF))
    }
    return ByteDifferenceStats(
        differentChannels = deltas.count { it != 0 },
        maxDelta = deltas.maxOrNull() ?: 0,
        meanDelta = deltas.average(),
    )
}

private data class ByteDifferenceStats(
    val differentChannels: Int,
    val maxDelta: Int,
    val meanDelta: Double,
)
