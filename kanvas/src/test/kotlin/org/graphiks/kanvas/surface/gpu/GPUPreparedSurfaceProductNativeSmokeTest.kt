package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
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
    fun `hardware sRGB store preserves semi transparent premultiplied readback`() {
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

        assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        )
        assertPixel(result.pixels.toByteArray(), 4, 2, 2, listOf(31, 96, 169, 160))
    }

    @Test
    fun `mixed direct path direct frame uses the prepared product route with exact native evidence`() {
        val operations = listOf(
            rect(Rect.fromLTRB(1f, 1f, 7f, 7f), Color.RED),
            DisplayOp.DrawPath(
                triangle(),
                Paint.fill(Color.GREEN).copy(antiAlias = false),
                Matrix33.identity(),
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
        assertPixel(result.pixels.toByteArray(), 32, 14, 5, listOf(0, 255, 0, 255))
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
        assertEquals(0L, evidence.draws)
        assertEquals(4L, evidence.drawIndexed)
        assertEquals(4L, evidence.pipelineBinds)
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

    @Test
    fun `direct prepared image frame preserves native pixels ordering and ownership`() {
        val rgba = image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "native-rgba",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        )
        val bgra = image(
            width = GPUPreparedImageTestFixtures.bgraOpaque2x2Width,
            height = GPUPreparedImageTestFixtures.bgraOpaque2x2Height,
            colorType = GPUPreparedImageTestFixtures.bgraOpaque2x2ColorType,
            sourceId = "native-bgra",
            pixels = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes,
        )
        val alpha = image(
            width = GPUPreparedImageTestFixtures.a8_3x1Width,
            height = GPUPreparedImageTestFixtures.a8_3x1Height,
            colorType = GPUPreparedImageTestFixtures.a8_3x1ColorType,
            sourceId = "native-a8",
            pixels = GPUPreparedImageTestFixtures.a8_3x1Bytes,
        )
        val linear = image(
            width = 2,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "native-linear",
            pixels = byteArrayOf(
                0, 0, 0, 255.toByte(),
                255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            ),
        )
        val operations = listOf(
            rect(Rect.fromLTRB(0f, 0f, 2f, 2f), Color.RED),
            drawImage(rgba, Rect.fromLTRB(3f, 0f, 5f, 2f), SamplingOptions.NEAREST),
            drawImage(bgra, Rect.fromLTRB(6f, 0f, 8f, 2f), SamplingOptions.NEAREST),
            drawImage(
                alpha,
                Rect.fromLTRB(9f, 0f, 12f, 1f),
                SamplingOptions.NEAREST,
                Paint.fill(Color.RED),
            ),
            drawImage(linear, Rect.fromLTRB(13f, 0f, 14f, 1f), SamplingOptions.LINEAR),
            rect(Rect.fromLTRB(15f, 0f, 17f, 2f), Color.BLUE),
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 18,
                height = 4,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(execution, execution.toString())

        assertPixel(result.rgba, 18, 0, 0, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 18, 3, 0, listOf(188, 0, 0, 128))
        assertPixel(result.rgba, 18, 6, 0, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 18, 10, 0, listOf(188, 0, 0, 128))
        assertTrue(
            GPUPreparedImagePixelOracle.matchesWithinOneLsb(
                result.rgba.copyOfRange((13 * 4), (13 * 4) + 4),
                byteArrayOf(188.toByte(), 188.toByte(), 188.toByte(), 255.toByte()),
            ),
        )
        assertPixel(result.rgba, 18, 15, 0, listOf(0, 0, 255, 255))

        val evidence = result.evidence
        assertEquals(1L, evidence.targetCreations)
        assertEquals(1L, evidence.targetCloses)
        assertEquals(1L, evidence.frameCoordinatorCreations)
        assertEquals(1L, evidence.encoders)
        assertEquals(1L, evidence.commandBuffers)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0L, evidence.destinationSnapshotCreations)
        assertEquals(0L, evidence.destinationReadbackSnapshots)
        assertEquals(3L, evidence.renderPasses)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, evidence.outputOwnedNativePayloads)
        assertEquals(0, evidence.quarantinedNativePayloads)
        assertEquals(evidence.retentionRegistrations, evidence.retentionCompletions)
        assertEquals(0L, evidence.retentionQuarantines)
        assertEquals(1, evidence.distinctRetentionTickets)
        assertEquals(operations.size, result.visualOperationCount)
    }

    private fun rect(bounds: Rect, color: Color) = DisplayOp.DrawRect(
        bounds,
        Paint.fill(color).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun drawImage(
        image: Image,
        dst: Rect,
        sampling: SamplingOptions,
        paint: Paint = Paint.fill(Color.WHITE),
    ) = DisplayOp.DrawImage(
        image = image,
        src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = paint.copy(shader = Shader.Image(image, sampling = sampling)),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun image(
        width: Int,
        height: Int,
        colorType: ColorType,
        sourceId: String,
        pixels: ByteArray,
    ) = Image(
        width = width,
        height = height,
        colorType = colorType,
        sourceId = sourceId,
        pixels = pixels,
        alphaType = AlphaType.PREMUL,
    )

    private fun triangle(): Path = Path().apply {
        moveTo(10f, 2f)
        lineTo(19f, 2f)
        lineTo(14f, 11f)
        close()
    }

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
