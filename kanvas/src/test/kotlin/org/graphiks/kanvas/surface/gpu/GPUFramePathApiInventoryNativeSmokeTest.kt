package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUFramePathApiInventoryNativeSmokeTest {
    @Test
    fun `display ops traverse inventory into one canonical native frame`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val targetBounds = org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, 32, 32)
        val readbackId = GPUReadbackRequestID("readback.inventory-core-primitive.rect-affine")
        val halfRed = Color.fromRGBA(1f, 0f, 0f, 0.5f)
        val halfGreen = Color.fromRGBA(0f, 1f, 0f, 0.5f)
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    Rect.fromLTRB(2f, 2f, 20f, 20f),
                    Paint.fill(halfRed).copy(antiAlias = false),
                    Matrix33.identity(),
                    ClipStack.DeviceRect(Rect.fromLTRB(4f, 5f, 18f, 19f), antiAlias = false),
                ),
                DisplayOp.DrawRect(
                    Rect.fromLTRB(6f, 6f, 12f, 12f),
                    Paint.fill(halfGreen).copy(antiAlias = false),
                    Matrix33.skew(0.5f, 0f),
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(32, 32, "rgba8unorm"),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
            deviceGeneration = generation,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUFramePathApiInventory.prepareNativeTaskList(
                inventory = inventory,
                capabilities = capabilities,
                targetBounds = targetBounds,
                readbackRequestId = readbackId,
            ),
        ).taskList
        val framePlan = GPUFramePlanner.plan(prepared)
        val renderPass = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        assertEquals(2, renderPass.drawPackets.size)
        val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
                width = 32,
                height = 32,
                colorFormat = colorMapping.physicalFormat,
                colorInterpretation = colorMapping.interpretation,
            ),
        )
        try {
            val completed = session.renderFrame(
                prepared,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                completed.outcome,
                "${completed.diagnostic?.code?.value}: ${completed.diagnostic?.message}",
            )
            val bytes = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes

            // This proves DisplayOp -> inventory -> canonical native frame. It is not yet the active Surface route.
            // Color.fromRGBA quantizes 0.5 to 128/255 before SrcOver, hence the exact blended alpha 192.
            assertPixelEquals(bytes, 6, 8, listOf(128, 0, 0, 128))
            // The later affine half-green draw blends over red, proving draw order and Y orientation.
            assertPixelEquals(bytes, 13, 8, listOf(64, 128, 0, 192))
            // Outside the rect scissor and where a vertically mirrored affine draw would appear.
            assertPixelEquals(bytes, 3, 8, listOf(0, 0, 0, 0))
            assertPixelEquals(bytes, 13, 23, listOf(0, 0, 0, 0))

            val counters = session.nativeCounters()
            assertEquals(1L, counters.encoders)
            assertEquals(1L, counters.commandBuffers)
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
            assertEquals(1L, counters.corePrimitiveInvariantCreations)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun assertPixelEquals(
        bytes: ByteArray,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * 32 + x) * 4
        val actual = (0..3).map { bytes[offset + it].toInt() and 0xff }
        assertEquals(expected, actual, "pixel ($x,$y)")
    }
}
