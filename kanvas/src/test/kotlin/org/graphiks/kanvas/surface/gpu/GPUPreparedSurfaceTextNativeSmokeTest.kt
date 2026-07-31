package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceTextNativeSmokeTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `completion only text frame closes then a recreated runtime reads back`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 13 completion-only",
        )
        val operations = listOf(text(typeface, GPUPreparedTextTestFixtures.A8_GLYPH_ID, 4, 58, Color.WHITE))

        val completionOnly = execute(
            operations,
            width = 40,
            height = 80,
            output = GPUPreparedSurfaceRequestedOutput.CompletionOnly,
        )
        assertEquals(GPUPreparedSurfaceOutputKind.CurrentFrameCompletionOnly, completionOnly.outputKind)
        assertEquals(0, completionOnly.rgba.size)
        assertEquals(1L, completionOnly.evidence.encoders)
        assertEquals(1L, completionOnly.evidence.submits)
        assertEquals(0L, completionOnly.evidence.readbackCopies)
        assertEquals(1L, completionOnly.evidence.targetCloses)
        assertEquals(0, completionOnly.evidence.activeNativePayloads)

        GPUBackendRuntimeFactory.dispose()

        val recreated = execute(
            operations,
            width = 40,
            height = 80,
            output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
        )
        assertEquals(GPUPreparedSurfaceOutputKind.ReadbackRgba, recreated.outputKind)
        assertEquals(40 * 80 * 4, recreated.rgba.size)
        assertEquals(1L, recreated.evidence.encoders)
        assertEquals(1L, recreated.evidence.submits)
        assertEquals(1L, recreated.evidence.readbackCopies)
        assertEquals(1L, recreated.evidence.targetCloses)
        assertEquals(0, recreated.evidence.activeNativePayloads)
    }

    @Test
    fun `mixed Core TextA8 Image TextA8 ColorGlyph uses one prepared submission and one readback`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 13 mixed COLRv0",
        )
        val image = Image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = ColorType.RGBA_8888,
            sourceId = "task13-mixed-image",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
            alphaType = AlphaType.PREMUL,
        )
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(0f, 0f, 4f, 4f),
                Paint.fill(Color.RED).copy(antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
            text(typeface, GPUPreparedTextTestFixtures.A8_GLYPH_ID, 12, 58, Color.WHITE),
            drawImage(image, Rect.fromLTRB(48f, 0f, 50f, 2f)),
            text(typeface, GPUPreparedTextTestFixtures.A8_GLYPH_ID, 60, 58, Color.GREEN),
            text(typeface, GPUPreparedTextTestFixtures.COLOR_BASE_GLYPH_ID, 108, 58, Color.BLUE),
        )
        var captured: GPUPreparedSurfaceFrameBuildResult.Ready? = null
        val executor = GPUPreparedSurfaceFrameExecutor(
            backendFactory = GPUPreparedSurfaceNativeBackendPortFactory,
            frameBuilder = { request ->
                GPUPreparedSurfaceFrameBuilder.build(request).also { result ->
                    if (result is GPUPreparedSurfaceFrameBuildResult.Ready) captured = result
                }
            },
        )

        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(request(operations, 160, 96, GPUPreparedSurfaceRequestedOutput.ReadbackRgba)),
        )
        val semanticOrder = checkNotNull(captured).taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull(GPUDrawPacket::semanticPayload)
            .map(GPUDrawSemanticPayload::canonicalType)
        assertEquals(
            listOf("CorePrimitive", "TextA8", "SampledImage", "TextA8", "ColorGlyph"),
            semanticOrder,
        )

        val expectedCore = byteArrayOf(255.toByte(), 0, 0, 255.toByte())
        val expectedFirstText = GPUPreparedTextPixelOracle.a8SourceOver(
            GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
            paintAlpha = 1f,
            coverage = 255,
        ).bytes()
        val expectedImage = byteArrayOf(188.toByte(), 0, 0, 128.toByte())
        val expectedSecondText = GPUPreparedTextPixelOracle.a8SourceOver(
            GPUPreparedTextPixelOracle.StraightSrgb(0, 255, 0),
            paintAlpha = 1f,
            coverage = 255,
        ).bytes()
        val expectedColorOuter = GPUPreparedTextPixelOracle.colorGlyphSourceOver(
            GPUPreparedTextPixelOracle.StraightSrgb(255, 42, 42),
            paintAlpha = 1f,
            coverage = 255,
        ).bytes()
        val expectedColorInner = GPUPreparedTextPixelOracle.colorGlyphSourceOver(
            GPUPreparedTextPixelOracle.StraightSrgb(0, 0, 255),
            paintAlpha = 1f,
            coverage = 255,
        ).bytes()
        val deltas = listOf(
            deltaAt(result.rgba, 160, 1, 1, expectedCore),
            deltaAt(result.rgba, 160, 16, 58, expectedFirstText),
            deltaAt(result.rgba, 160, 48, 0, expectedImage),
            deltaAt(result.rgba, 160, 64, 58, expectedSecondText),
            deltaAt(result.rgba, 160, 110, 58, expectedColorOuter),
            deltaAt(result.rgba, 160, 116, 58, expectedColorInner),
        )
        assertTrue(deltas.all { it <= 1 }, "maxChannelDeltas=$deltas")
        assertTrue(result.rgba.any { it.toInt() != 0 }, "native output must be nonblank")

        assertEquals(5, result.visualOperationCount)
        assertEquals(1L, result.evidence.encoders)
        assertEquals(1L, result.evidence.commandBuffers)
        assertEquals(1L, result.evidence.submits)
        assertEquals(1L, result.evidence.readbackCopies)
        assertEquals(0, result.evidence.activeNativePayloads)
        assertEquals(0, result.evidence.outputOwnedNativePayloads)
        assertEquals(0, result.evidence.quarantinedNativePayloads)
        assertEquals(result.evidence.retentionRegistrations, result.evidence.retentionCompletions)
        assertEquals(0L, result.evidence.retentionQuarantines)
        assertEquals(1, result.evidence.distinctRetentionTickets)
        assertEquals(2, result.evidence.textCounters.a8Instances)
        assertEquals(2, result.evidence.textCounters.colorGlyphInstances)
        assertEquals(0, result.evidence.textCounters.pathStrokeDraws)
        assertEquals(1, result.evidence.textCounters.pageCount)
        assertEquals(512 * 512, result.evidence.textCounters.pageBytes)
        assertEquals(3, result.evidence.textCounters.subRuns)
        assertEquals(3, result.evidence.textCounters.draws)
        assertEquals(3, result.evidence.textCounters.bindGroups)
        assertEquals(1, result.evidence.textCounters.submits)

        println(
            "task13.native prepared=true skipped=0 encoders=${result.evidence.encoders} " +
                "submits=${result.evidence.submits} readbacks=${result.evidence.readbackCopies} " +
                "maxChannelDelta=${deltas.max()}",
        )
    }

    private fun execute(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
        output: GPUPreparedSurfaceRequestedOutput,
    ): GPUPreparedSurfaceExecutionResult.Succeeded = assertIs(
        GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            .execute(request(operations, width, height, output)).also { result ->
                if (result !is GPUPreparedSurfaceExecutionResult.Succeeded) {
                    println("task13.native.failure=$result")
                }
            },
    )

    private fun request(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
        output: GPUPreparedSurfaceRequestedOutput,
    ): GPUPreparedSurfaceExecutionRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceExecutionRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            width = width,
            height = height,
            output = output,
        )
    }

    private fun text(
        typeface: FontTypeface,
        glyphId: Int,
        x: Int,
        baselineY: Int,
        color: Color,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(glyphId.toUShort()),
                    positions = listOf(Point(0f, 0f)),
                    fontSize = 48f,
                ),
            ),
            typeface = typeface,
            fontSize = 48f,
        ),
        x = x.toFloat(),
        y = baselineY.toFloat(),
        paint = Paint.fill(color),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun drawImage(image: Image, dst: Rect): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = image,
        src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = Paint.fill(Color.WHITE).copy(
            shader = Shader.Image(image, sampling = SamplingOptions.NEAREST),
        ),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun deltaAt(
        rgba: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: ByteArray,
    ): Int {
        val offset = (y * width + x) * 4
        return GPUPreparedTextPixelOracle.maxChannelDelta(
            rgba.copyOfRange(offset, offset + 4),
            expected,
        )
    }
}
