package org.graphiks.kanvas.surface.gpu

import java.util.stream.Stream
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GPUPreparedSurfaceTextNativeSmokeTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @TestFactory
    fun `prepared text partial paint alpha preserves independent source coverage encodings`(): Stream<DynamicTest> =
        listOf(BlendMode.CLEAR, BlendMode.DST_IN, BlendMode.MODULATE).flatMap { blendMode ->
            PartialAlphaClipContext.entries.map { context ->
                DynamicTest.dynamicTest("${blendMode.name}/${context.name}") {
                    val typeface = FontTypeface(
                        GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
                        "Task 14 partial-alpha ${blendMode.name}/${context.name}",
                    )
                    val shaderColor = Color.fromRGBA(1f, 0f, 0f, 0.5f)
                    val shader = Shader.LinearGradient(
                        start = Point(0f, 0f),
                        end = Point(40f, 0f),
                        stops = listOf(
                            GradientStop(0f, shaderColor),
                            GradientStop(1f, shaderColor),
                        ),
                    )
                    val paint = Paint.fill(Color.fromRGBA(1f, 1f, 1f, 0.6f)).copy(
                        shader = shader,
                        blendMode = blendMode,
                    )
                    val glyph = text(
                        typeface,
                        GPUPreparedTextTestFixtures.A8_GLYPH_ID,
                        4,
                        58,
                        Color.WHITE,
                        paint,
                    ).copy(
                        x = 4.5f,
                        clip = when (context) {
                            PartialAlphaClipContext.UNCLIPPED -> ClipStack.WideOpen
                            PartialAlphaClipContext.SCISSOR -> ClipStack.DeviceRect(
                                rect = Rect.fromLTRB(0f, 0f, 20f, 80f),
                                antiAlias = false,
                            )
                        },
                    )
                    val destination = DisplayOp.DrawRect(
                        rect = Rect.fromLTRB(0f, 0f, 40f, 80f),
                        paint = Paint.fill(Color.WHITE).copy(antiAlias = false),
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    )

                    val result = execute(
                        operations = listOf(destination, glyph),
                        width = 40,
                        height = 80,
                        output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
                    )

                    val expected = expectedPartialAlphaBlend(
                        blendMode = blendMode,
                        glyphCoverage = 128f / 255f,
                    )
                    val actual = pixelAt(result.rgba, width = 40, x = 4, y = 58)
                    val delta = GPUPreparedTextPixelOracle.maxChannelDelta(actual, expected)
                    assertTrue(
                        delta <= 1,
                        "${blendMode.name}/${context.name} must apply partial paint alpha to S, " +
                            "not fractional glyph coverage F; expected=${expected.toUnsignedList()} " +
                            "actual=${actual.toUnsignedList()} delta=$delta",
                    )
                    println(
                        "task14.partial-alpha ${blendMode.name}/${context.name} F=128/255 " +
                            "expected=${expected.toUnsignedList()} actual=${actual.toUnsignedList()} delta=$delta",
                    )
                    if (context == PartialAlphaClipContext.SCISSOR) {
                        assertTrue(
                            deltaAt(
                                result.rgba,
                                width = 40,
                                x = 24,
                                y = 58,
                                expected = byteArrayOf(
                                    255.toByte(),
                                    255.toByte(),
                                    255.toByte(),
                                    255.toByte(),
                                ),
                            ) <= 1,
                            "SCISSOR must preserve the destination outside its bounds",
                        )
                    }
                }
            }
        }.stream()

    @Test
    fun `prepared text executes fractional analytic coverage before native blending`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 14 coverage-mask RED",
        )
        val clipped = text(
            typeface,
            GPUPreparedTextTestFixtures.A8_GLYPH_ID,
            4,
            58,
            Color.WHITE,
        ).copy(
            clip = ClipStack.DeviceRect(
                rect = Rect.fromLTRB(16.5f, 0f, 40f, 80f),
                antiAlias = true,
            ),
        )

        val result = execute(
            operations = listOf(clipped),
            width = 40,
            height = 80,
            output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
        )

        assertTrue(
            deltaAt(
                result.rgba,
                width = 40,
                x = 16,
                y = 58,
                expected = byteArrayOf(
                    188.toByte(),
                    188.toByte(),
                    188.toByte(),
                    128.toByte(),
                ),
            ) <= 1,
            "fractional clip edge must multiply glyph A8 coverage exactly once",
        )
        assertTrue(
            deltaAt(
                result.rgba,
                width = 40,
                x = 15,
                y = 58,
                expected = byteArrayOf(0, 0, 0, 0),
            ) <= 1,
            "coverage mask exterior must remain untouched",
        )
    }

    @Test
    fun `prepared text samples ordered complex coverage mask before native blending`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 14 ordered coverage-mask RED",
        )
        val clipped = text(
            typeface,
            GPUPreparedTextTestFixtures.A8_GLYPH_ID,
            4,
            58,
            Color.WHITE,
        ).copy(
            clip = ClipStack.Complex(
                listOf(
                    ClipStackOp.RectOp(
                        rect = Rect.fromLTRB(8f, 0f, 32f, 80f),
                        op = ClipOp.INTERSECT,
                        antiAlias = false,
                    ),
                    ClipStackOp.RectOp(
                        rect = Rect.fromLTRB(14f, 0f, 18f, 80f),
                        op = ClipOp.DIFFERENCE,
                        antiAlias = false,
                    ),
                ),
            ),
        )

        val result = execute(
            operations = listOf(clipped),
            width = 40,
            height = 80,
            output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
        )

        assertTrue(
            deltaAt(
                result.rgba,
                width = 40,
                x = 10,
                y = 58,
                expected = byteArrayOf(
                    255.toByte(),
                    255.toByte(),
                    255.toByte(),
                    255.toByte(),
                ),
            ) <= 1,
            "coverage-mask intersection interior must retain the glyph",
        )
        listOf(6, 15).forEach { x ->
            assertTrue(
                deltaAt(
                    result.rgba,
                    width = 40,
                    x = x,
                    y = 58,
                    expected = byteArrayOf(0, 0, 0, 0),
                ) <= 1,
                "coverage-mask exterior/difference hole must remain untouched at x=$x",
            )
        }
    }

    @Test
    fun `completion only coverage mask text closes then a recreated runtime reads back`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 13 completion-only",
        )
        val operations = listOf(
            text(
                typeface,
                GPUPreparedTextTestFixtures.A8_GLYPH_ID,
                4,
                58,
                Color.WHITE,
            ).copy(
                clip = ClipStack.Complex(
                    listOf(
                        ClipStackOp.RectOp(
                            rect = Rect.fromLTRB(0f, 0f, 40f, 80f),
                            op = ClipOp.INTERSECT,
                            antiAlias = false,
                        ),
                        ClipStackOp.RectOp(
                            rect = Rect.fromLTRB(35f, 0f, 38f, 80f),
                            op = ClipOp.DIFFERENCE,
                            antiAlias = false,
                        ),
                    ),
                ),
            ),
        )

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
        assertEquals(0L, completionOnly.evidence.targetCloses)
        assertEquals(0, completionOnly.evidence.activeNativePayloads)
        assertEquals(0, completionOnly.evidence.outputOwnedNativePayloads)
        assertEquals(0, completionOnly.evidence.quarantinedNativePayloads)
        assertEquals(
            completionOnly.evidence.retentionRegistrations,
            completionOnly.evidence.retentionCompletions,
        )
        assertEquals(0L, completionOnly.evidence.retentionQuarantines)
        assertEquals(1, completionOnly.evidence.distinctRetentionTickets)

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
        assertEquals(0L, recreated.evidence.targetCloses)
        assertEquals(0, recreated.evidence.activeNativePayloads)
        assertEquals(0, recreated.evidence.outputOwnedNativePayloads)
        assertEquals(0, recreated.evidence.quarantinedNativePayloads)
        assertEquals(
            recreated.evidence.retentionRegistrations,
            recreated.evidence.retentionCompletions,
        )
        assertEquals(0L, recreated.evidence.retentionQuarantines)
        assertEquals(1, recreated.evidence.distinctRetentionTickets)
        assertTrue(recreated.rgba.any { byte -> byte.toInt() != 0 })
        val expected = GPUPreparedTextPixelOracle.renderLayers(
            width = 40,
            height = 80,
            layers = listOf(
                GPUPreparedTextPixelOracle.Layer(
                    bounds = GPUPreparedTextPixelOracle.IntRect(4, 40, 28, 76),
                    color = GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
                    paintAlpha = 1f,
                ),
            ),
        )
        assertTrue(
            GPUPreparedTextPixelOracle.maxChannelDelta(recreated.rgba, expected) <= 1,
            "recreated readback must match the sole pixel oracle",
        )
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
                Matrix3x3F32.Identity,
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
        assertEquals(7, result.evidence.textCounters.bindGroups)
        assertEquals(1, result.evidence.textCounters.submits)
        assertEquals(5L, result.evidence.draws + result.evidence.drawIndexed)

        println(
            "task13.native prepared=true skipped=0 encoders=${result.evidence.encoders} " +
                "submits=${result.evidence.submits} readbacks=${result.evidence.readbackCopies} " +
                "maxChannelDelta=${deltas.max()}",
        )
    }

    @Test
    fun `native stroke dash text counts encoded path work independently from nontext work`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 13 stroke counters",
        )
        val stroke = Paint.stroke(Color.WHITE, 3f).copy(
            antiAlias = false,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.BEVEL,
            pathEffect = PathEffect.Dash(floatArrayOf(100f, 20f), phase = 1f),
        )
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(0f, 0f, 4f, 4f),
                Paint.fill(Color.RED).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            text(
                typeface,
                GPUPreparedTextTestFixtures.A8_GLYPH_ID,
                12,
                58,
                Color.WHITE,
                stroke,
            ),
        )

        val result = execute(
            operations,
            width = 80,
            height = 96,
            output = GPUPreparedSurfaceRequestedOutput.CompletionOnly,
        )

        assertEquals(1, result.evidence.textCounters.pathStrokeDraws)
        assertEquals(0, result.evidence.textCounters.atlasInstances)
        assertEquals(1, result.evidence.textCounters.subRuns)
        assertEquals(2, result.evidence.textCounters.draws)
        assertEquals(2, result.evidence.textCounters.bindGroups)
        assertEquals(1, result.evidence.textCounters.submits)
        assertTrue(
            result.evidence.draws + result.evidence.drawIndexed >
                result.evidence.textCounters.draws,
            "the nontext rectangle must remain outside exact text counters",
        )
    }

    @Test
    fun `native frame without text publishes zero exact text command counters`() {
        val result = execute(
            listOf(
                DisplayOp.DrawRect(
                    Rect.fromLTRB(0f, 0f, 4f, 4f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            width = 16,
            height = 16,
            output = GPUPreparedSurfaceRequestedOutput.CompletionOnly,
        )

        assertEquals(0, result.evidence.textCounters.draws)
        assertEquals(0, result.evidence.textCounters.bindGroups)
        assertEquals(0, result.evidence.textCounters.submits)
        assertEquals(0, result.evidence.textCounters.pathStrokeDraws)
        assertEquals(0, result.evidence.textCounters.atlasInstances)
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
        paint: Paint = Paint.fill(color),
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
        paint = paint,
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun drawImage(image: Image, dst: Rect): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = image,
        src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = Paint.fill(Color.WHITE).copy(
            shader = Shader.Image(image, sampling = SamplingOptions.NEAREST),
        ),
        transform = Matrix3x3F32.Identity,
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

    private fun pixelAt(rgba: ByteArray, width: Int, x: Int, y: Int): ByteArray {
        val offset = (y * width + x) * 4
        return rgba.copyOfRange(offset, offset + 4)
    }

    private fun expectedPartialAlphaBlend(
        blendMode: BlendMode,
        glyphCoverage: Float,
    ): ByteArray {
        val paintAlpha = 0.6f
        val materialPremul = floatArrayOf(0.5f, 0f, 0f, 0.5f)
        val source = FloatArray(4) { channel -> materialPremul[channel] * paintAlpha }
        val destination = floatArrayOf(1f, 1f, 1f, 1f)
        val output = when (blendMode) {
            BlendMode.CLEAR ->
                FloatArray(4) { channel -> destination[channel] * (1f - glyphCoverage) }
            BlendMode.DST_IN ->
                FloatArray(4) { channel ->
                    destination[channel] * (1f - glyphCoverage * (1f - source[3]))
                }
            BlendMode.MODULATE ->
                FloatArray(4) { channel ->
                    destination[channel] * (1f - glyphCoverage * (1f - source[channel]))
                }
            else -> error("Unexpected partial-alpha blend $blendMode")
        }
        return ByteArray(4) { channel ->
            val value = if (channel == 3) output[channel] else linearToSrgb(output[channel])
            (value * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    private fun linearToSrgb(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= .0031308f) {
            clamped * 12.92f
        } else {
            1.055f * clamped.pow(1f / 2.4f) - .055f
        }
    }

    private fun ByteArray.toUnsignedList(): List<Int> = map { byte -> byte.toInt() and 0xff }

    private enum class PartialAlphaClipContext { UNCLIPPED, SCISSOR }
}
