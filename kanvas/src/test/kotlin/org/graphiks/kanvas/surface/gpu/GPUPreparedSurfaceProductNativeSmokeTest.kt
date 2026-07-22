package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceProductNativeSmokeTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `semi transparent sRGB solid preserves encoded premultiplied readback`() {
        val color = Color.fromArgb(a = 160, r = 40, g = 120, b = 208)
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(rect(Rect.fromLTRB(0f, 0f, 4f, 4f), color)),
            ),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertEquals(
            GPUPreparedSurfaceRouteDecision.Legacy(
                "unsupported.surface.prepared.encoded-premul-srgb.translucent-solid",
            ),
            decisions.single(),
        )
        assertPixel(result.pixels.toByteArray(), 4, 2, 2, listOf(31, 96, 169, 160))
    }

    @Test
    fun `mixed direct affine direct frame uses the prepared product route with exact native evidence`() {
        val operations = listOf(
            rect(Rect.fromLTRB(1f, 1f, 7f, 7f), Color.RED),
            DisplayOp.DrawRect(
                Rect.fromLTRB(10f, 2f, 19f, 11f),
                Paint.fill(Color.GREEN).copy(antiAlias = false),
                Matrix33.skew(0.25f, 0f),
                ClipStack.WideOpen,
            ),
            rect(Rect.fromLTRB(22f, 18f, 30f, 26f), Color.BLUE),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(operations),
            width = 32,
            height = 32,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertEquals(1, decisions.size)
        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        assertPixel(result.pixels.toByteArray(), 32, 3, 3, listOf(255, 0, 0, 255))
        assertPixel(result.pixels.toByteArray(), 32, 14, 4, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 32, 25, 21, listOf(0, 0, 255, 255))
        assertPixel(result.pixels.toByteArray(), 32, 31, 31, listOf(0, 0, 0, 0))

        assertEquals(1L, evidence.targetCreations)
        assertEquals(1L, evidence.targetCloses)
        assertEquals(1L, evidence.frameCoordinatorCreations)
        assertEquals(1L, evidence.encoders)
        assertEquals(1L, evidence.commandBuffers)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0L, evidence.destinationSnapshotCreations)
        assertEquals(0L, evidence.destinationReadbackSnapshots)
        assertEquals(1L, evidence.renderPasses)
        assertEquals(3L, evidence.draws)
        assertEquals(3L, evidence.drawIndexed)
        assertEquals(1L, evidence.pipelineBinds)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, evidence.outputOwnedNativePayloads)
        assertEquals(0, evidence.quarantinedNativePayloads)
        assertEquals(evidence.retentionRegistrations, evidence.retentionCompletions)
        assertEquals(0L, evidence.retentionQuarantines)
        assertEquals(1, evidence.distinctRetentionTickets)

        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(Math.toIntExact(evidence.draws + evidence.drawIndexed), result.stats.drawCallCount)
        assertEquals(Math.toIntExact(evidence.pipelineBinds), result.stats.pipelineCount)
        assertEquals(false, result.stats.coverageMeasured)
    }

    private fun rect(bounds: Rect, color: Color) = DisplayOp.DrawRect(
        bounds,
        Paint.fill(color).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun assertPixel(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * width + x) * 4
        assertEquals(expected, (0..3).map { bytes[offset + it].toInt() and 0xff }, "pixel ($x,$y)")
    }

    private class StaticDisplayListBuffer(
        private val operations: List<DisplayOp>,
    ) : DisplayListBuffer {
        override fun append(op: DisplayOp) = error("static buffer")
        override fun ops(): List<DisplayOp> = operations
    }
}
