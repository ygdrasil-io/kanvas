package org.graphiks.kanvas.surface.gpu

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.a
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUPreparedColorGlyphSourceNativeOracleTest {
    @Test
    fun `source COLRv0 CPAL currentColor and paint alpha match independent CPU pixels`() {
        val source = sourceFixtureWithForegroundLayer()
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        if (backend == null) {
            println("$EVIDENCE_MARKER available=false executed=0 skipped=1")
            assumeTrue(false, "GPU backend unavailable in current environment")
        }
        backend!!
        try {
            val typeface = FontTypeface(source.fontBytes, "Task 11 source COLRv0 currentColor")
            val foreground = Color.fromRGBA(
                CURRENT_COLOR_REQUESTED_R,
                CURRENT_COLOR_REQUESTED_G,
                CURRENT_COLOR_REQUESTED_B,
                PAINT_ALPHA_REQUESTED,
            )
            val operations = listOf(
                textOperation(typeface, A8_GLYPH_ID, A8_ORIGIN_X, BASELINE_Y, Color.WHITE),
                textOperation(typeface, BASE_GLYPH_ID, COLOR_ORIGIN_X_0, BASELINE_Y, foreground),
                textOperation(typeface, BASE_GLYPH_ID, COLOR_ORIGIN_X_1, BASELINE_Y, foreground),
            )
            val capabilities = requireNotNull(backend.capabilities)
            val colorMapping = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
                RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
            )
            val targetBounds = GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT)
            val requestId = GPUReadbackRequestID("readback.task11.source-colrv0-current-color")
            val inventory = GPUFramePathApiInventory.plan(
                operations = operations,
                target = GPUTargetFacts(
                    TARGET_WIDTH,
                    TARGET_HEIGHT,
                    colorMapping.physicalFormat.value,
                ),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
                deviceGeneration = backend.deviceGeneration,
            )
            val preparation = GPUFramePathApiInventory.preparePreparedNativeTaskList(
                inventory = inventory,
                capabilities = capabilities,
                targetBounds = targetBounds,
                readbackRequestId = requestId,
            )
            val prepared = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
                preparation,
                preparation.toString(),
            ).taskList
            val framePlan = GPUFramePlanner.plan(prepared)
            val semantics = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { render -> render.drawPackets }
            .mapNotNull { packet -> packet.semanticPayload }
            assertEquals(3, semantics.size)
            assertIs<GPUDrawSemanticPayload.TextA8>(semantics[0])
            assertIs<GPUDrawSemanticPayload.ColorGlyph>(semantics[1])
            assertIs<GPUDrawSemanticPayload.ColorGlyph>(semantics[2])
            assertEquals(1, semantics.filterIsInstance<GPUDrawSemanticPayload.TextA8>().size)
            val colorSemantics = semantics.filterIsInstance<GPUDrawSemanticPayload.ColorGlyph>()
            assertEquals(2, colorSemantics.size)
            assertEquals(
                listOf(false, true),
                colorSemantics.first().layers.map { layer -> layer.useForeground },
            )
            assertTrue(colorSemantics.all { semantic ->
                semantic.layers.map { layer -> layer.paletteIndex } ==
                    listOf(source.layers[0].paletteIndex, FOREGROUND_PALETTE_INDEX)
            })
            colorSemantics.forEach { semantic ->
                semantic.layers.forEach { layer ->
                    assertEquals(
                        EXPECTED_PAINT_ALPHA,
                        layer.premultipliedRgba[3],
                        0.000001f,
                        "paint alpha must modulate CPAL and currentColor exactly once",
                    )
                }
            }
            assertEquals(
                1,
                colorSemantics.map { semantic -> semantic.planArtifactKey }.distinct().size,
            )

            val expected = independentCpuOracle(source)
            val session = backend.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    width = TARGET_WIDTH,
                    height = TARGET_HEIGHT,
                    colorFormat = colorMapping.physicalFormat,
                    colorInterpretation = colorMapping.interpretation,
                ),
            )
            try {
                val completed = session.renderFrame(
                    prepared,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    completed.outcome,
                    completed.diagnostic.toString(),
                )
                val actual = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output).bytes
                val maximumDelta = assertRgbaWithinOneByte(expected, actual)
                val counters = session.nativeCounters()
                assertEquals(1L, counters.encoders)
                assertEquals(1L, counters.commandBuffers)
                assertEquals(1L, counters.submits)
                assertEquals(1L, counters.readbackCopies)

                val evidence = NativeEvidence(available = true, executed = 1, skipped = 0)
                assertEquals(NativeEvidence(true, 1, 0), evidence)
                println(
                    "$EVIDENCE_MARKER available=${evidence.available} " +
                        "executed=${evidence.executed} skipped=${evidence.skipped} " +
                        "maxChannelDelta=$maximumDelta",
                )
            } finally {
                session.close()
            }
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun independentCpuOracle(source: SourceFixture): ByteArray {
        val draws = buildList {
            add(
                CpuLayer(
                    bounds = GLYPH_7_RECTANGLE.toDeviceBounds(A8_ORIGIN_X, BASELINE_Y),
                    linearPremul = floatArrayOf(1f, 1f, 1f, 1f),
                ),
            )
            listOf(COLOR_ORIGIN_X_0, COLOR_ORIGIN_X_1).forEach { originX ->
                source.layers.forEach { layer ->
                    val rectangle = when (layer.glyphId) {
                        7 -> GLYPH_7_RECTANGLE
                        8 -> GLYPH_8_RECTANGLE
                        else -> error("Unexpected source layer glyph ${layer.glyphId}")
                    }
                    val sourceColor = if (layer.paletteIndex == FOREGROUND_PALETTE_INDEX) {
                        floatArrayOf(
                            testSrgbToLinear(EXPECTED_CURRENT_COLOR_R),
                            testSrgbToLinear(EXPECTED_CURRENT_COLOR_G),
                            testSrgbToLinear(EXPECTED_CURRENT_COLOR_B),
                            1f,
                        )
                    } else {
                        requireNotNull(layer.colorArgb).toLinearPremultipliedRgba()
                    }
                    val paintModulated = FloatArray(4) { index ->
                        sourceColor[index] * EXPECTED_PAINT_ALPHA
                    }
                    add(
                        CpuLayer(
                            bounds = rectangle.toDeviceBounds(originX, BASELINE_Y),
                            linearPremul = paintModulated,
                        ),
                    )
                }
            }
        }
        return composeSourceOver(draws)
    }

    private fun composeSourceOver(layers: List<CpuLayer>): ByteArray {
        val rgba = FloatArray(TARGET_WIDTH * TARGET_HEIGHT * 4)
        layers.forEach { layer ->
            for (y in layer.bounds.top until layer.bounds.bottom) {
                for (x in layer.bounds.left until layer.bounds.right) {
                    require(x in 0 until TARGET_WIDTH && y in 0 until TARGET_HEIGHT) {
                        "source rectangle outside target: x=$x y=$y bounds=${layer.bounds}"
                    }
                    val pixel = (y * TARGET_WIDTH + x) * 4
                    val sourceAlpha = layer.linearPremul[3]
                    val inverseSourceAlpha = 1f - sourceAlpha
                    rgba[pixel] =
                        layer.linearPremul[0] + rgba[pixel] * inverseSourceAlpha
                    rgba[pixel + 1] =
                        layer.linearPremul[1] + rgba[pixel + 1] * inverseSourceAlpha
                    rgba[pixel + 2] =
                        layer.linearPremul[2] + rgba[pixel + 2] * inverseSourceAlpha
                    rgba[pixel + 3] = sourceAlpha + rgba[pixel + 3] * inverseSourceAlpha
                }
            }
        }
        return ByteArray(rgba.size) { index ->
            val encoded = if (index % 4 == 3) rgba[index] else rgba[index].linearToSrgb()
            (encoded.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }
    }

    private fun sourceFixtureWithForegroundLayer(): SourceFixture {
        val bytes = GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer()
        assertEquals(PATCHED_SOURCE_FONT_SHA256, bytes.sha256Hex())
        val colr = sfntTableOffset(bytes, "COLR")
        assertEquals(0, readU16(bytes, colr))
        val baseRecordCount = readU16(bytes, colr + 2)
        val baseRecords = colr + readU32(bytes, colr + 4)
        val layerRecords = colr + readU32(bytes, colr + 8)
        val baseRecord = (0 until baseRecordCount)
            .map { index -> baseRecords + index * 6 }
            .single { offset -> readU16(bytes, offset) == BASE_GLYPH_ID }
        val firstLayerIndex = readU16(bytes, baseRecord + 2)
        val layerCount = readU16(bytes, baseRecord + 4)
        assertEquals(2, layerCount)
        val parsedLayers = (0 until layerCount).map { layerIndex ->
            val offset = layerRecords + (firstLayerIndex + layerIndex) * 4
            val paletteIndex = readU16(bytes, offset + 2)
            SourceLayer(
                glyphId = readU16(bytes, offset),
                paletteIndex = paletteIndex,
                colorArgb = if (paletteIndex == FOREGROUND_PALETTE_INDEX) {
                    null
                } else {
                    readCpalColor(bytes, paletteIndex)
                },
            )
        }
        assertEquals(listOf(7, 8), parsedLayers.map(SourceLayer::glyphId))
        assertEquals(listOf(0, FOREGROUND_PALETTE_INDEX), parsedLayers.map {
            it.paletteIndex
        })
        assertEquals(0xFFFF2A2A.toInt(), parsedLayers.first().colorArgb)
        return SourceFixture(bytes, parsedLayers)
    }

    private fun readCpalColor(bytes: ByteArray, paletteIndex: Int): Int {
        val cpal = sfntTableOffset(bytes, "CPAL")
        val paletteEntryCount = readU16(bytes, cpal + 2)
        val paletteCount = readU16(bytes, cpal + 4)
        val colorRecordCount = readU16(bytes, cpal + 6)
        assertTrue(paletteCount > 0 && paletteIndex in 0 until paletteEntryCount)
        val firstRecord = readU16(bytes, cpal + 12)
        val recordIndex = firstRecord + paletteIndex
        assertTrue(recordIndex in 0 until colorRecordCount)
        val offset = cpal + readU32(bytes, cpal + 8) + recordIndex * 4
        val blue = bytes[offset].toInt() and 0xff
        val green = bytes[offset + 1].toInt() and 0xff
        val red = bytes[offset + 2].toInt() and 0xff
        val alpha = bytes[offset + 3].toInt() and 0xff
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun sfntTableOffset(bytes: ByteArray, wantedTag: String): Int {
        val tableCount = readU16(bytes, 4)
        repeat(tableCount) { index ->
            val entry = 12 + index * 16
            val tag = String(bytes, entry, 4, Charsets.ISO_8859_1)
            if (tag == wantedTag) return readU32(bytes, entry + 8)
        }
        error("Missing $wantedTag table")
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun textOperation(
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
                    fontSize = FONT_SIZE,
                ),
            ),
            typeface = typeface,
            fontSize = FONT_SIZE,
        ),
        x = x.toFloat(),
        y = baselineY.toFloat(),
        paint = Paint.fill(color),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun Int.toLinearPremultipliedRgba(): FloatArray {
        val alpha = (ushr(24) and 0xff) / 255f
        return floatArrayOf(
            ((ushr(16) and 0xff) / 255f).srgbToLinear() * alpha,
            ((ushr(8) and 0xff) / 255f).srgbToLinear() * alpha,
            ((this and 0xff) / 255f).srgbToLinear() * alpha,
            alpha,
        )
    }

    private fun Float.srgbToLinear(): Float =
        if (this <= 0.04045f) this / 12.92f
        else (((this + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()

    private fun testSrgbToLinear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f
        else (((value + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()

    private fun Float.linearToSrgb(): Float =
        if (this <= 0.0031308f) this * 12.92f
        else (1.055 * toDouble().pow(1.0 / 2.4) - 0.055).toFloat()

    private fun assertRgbaWithinOneByte(expected: ByteArray, actual: ByteArray): Int {
        assertEquals(expected.size, actual.size)
        var maximumDelta = 0
        var maximumDeltaIndex = -1
        expected.indices.forEach { index ->
            val delta = abs(
                (expected[index].toInt() and 0xff) - (actual[index].toInt() and 0xff),
            )
            if (delta > maximumDelta) {
                maximumDelta = delta
                maximumDeltaIndex = index
            }
        }
        assertTrue(
            maximumDelta <= 1,
            "maxChannelDelta=$maximumDelta at rgba[$maximumDeltaIndex], " +
                "expected=${expected.getOrNull(maximumDeltaIndex)?.toInt()?.and(0xff)}, " +
                "actual=${actual.getOrNull(maximumDeltaIndex)?.toInt()?.and(0xff)}",
        )
        return maximumDelta
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class SourceFixture(
        val fontBytes: ByteArray,
        val layers: List<SourceLayer>,
    )

    private data class SourceLayer(
        val glyphId: Int,
        val paletteIndex: Int,
        val colorArgb: Int?,
    )

    private data class CpuLayer(
        val bounds: DeviceRectangle,
        val linearPremul: FloatArray,
    )

    private data class FontUnitRectangle(
        val xMin: Int,
        val yMin: Int,
        val xMax: Int,
        val yMax: Int,
    ) {
        fun toDeviceBounds(originX: Int, baselineY: Int): DeviceRectangle {
            fun scaled(value: Int): Int {
                val numerator = value * FONT_SIZE_PX
                require(numerator % FONT_UNITS_PER_EM == 0)
                return numerator / FONT_UNITS_PER_EM
            }
            return DeviceRectangle(
                left = originX + scaled(xMin),
                top = baselineY - scaled(yMax),
                right = originX + scaled(xMax),
                bottom = baselineY - scaled(yMin),
            )
        }
    }

    private data class DeviceRectangle(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private data class NativeEvidence(
        val available: Boolean,
        val executed: Int,
        val skipped: Int,
    )

    private companion object {
        const val EVIDENCE_MARKER = "task11.native-source-colrv0"
        const val FOREGROUND_PALETTE_INDEX = 0xffff
        const val BASE_GLYPH_ID = 2
        const val A8_GLYPH_ID = 7
        const val FONT_SIZE = 48f
        const val FONT_SIZE_PX = 48
        const val FONT_UNITS_PER_EM = 1_000
        const val PAINT_ALPHA_REQUESTED = 0.625f
        const val CURRENT_COLOR_REQUESTED_R = 0.5f
        const val CURRENT_COLOR_REQUESTED_G = 0.25f
        const val CURRENT_COLOR_REQUESTED_B = 0.75f
        const val EXPECTED_PAINT_ALPHA = 159f / 255f
        const val EXPECTED_CURRENT_COLOR_R = 128f / 255f
        const val EXPECTED_CURRENT_COLOR_G = 64f / 255f
        const val EXPECTED_CURRENT_COLOR_B = 191f / 255f
        const val TARGET_WIDTH = 176
        const val TARGET_HEIGHT = 104
        const val A8_ORIGIN_X = 4
        const val COLOR_ORIGIN_X_0 = 58
        const val COLOR_ORIGIN_X_1 = 118
        const val BASELINE_Y = 58
        const val PATCHED_SOURCE_FONT_SHA256 =
            "7fe253c74758df56226679d9e43965e78bbdb2437d2b7d4788d918805323874d"
        val GLYPH_7_RECTANGLE = FontUnitRectangle(0, -375, 500, 375)
        val GLYPH_8_RECTANGLE = FontUnitRectangle(125, -250, 375, 250)
    }
}
