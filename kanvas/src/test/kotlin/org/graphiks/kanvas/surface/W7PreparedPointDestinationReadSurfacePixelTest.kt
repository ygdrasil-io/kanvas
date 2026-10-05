@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class W7PreparedPointDestinationReadSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun preparedSquarePointDarkenBlendsBlueDestinationAndReplays() {
        val surface = Surface(32, 32)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 32f, 32f),
                Paint(color = ColorARGB.Blue, antiAlias = false),
            )
            drawPoint(
                16f,
                16f,
                Paint(
                    color = ColorARGB.Red,
                    antiAlias = false,
                    style = PaintStyle.FILL,
                    strokeWidth = 32f,
                    strokeCap = StrokeCap.SQUARE,
                    blendMode = BlendMode.DARKEN,
                ),
            )
        }

        val first = surface.render()
        printRenderEvidence("first", first)
        val replay = surface.render()
        printRenderEvidence("replay", replay)
        val results = listOf("first" to first, "replay" to replay)

        for ((label, result) in results) {
            val diagnosticText = result.diagnostics.entries.joinToString {
                "${it.code}:${it.operation}:${it.reason}"
            }
            assertEquals(32, result.width, label)
            assertEquals(32, result.height, label)
            assertEquals(PixelFormat.RGBA8, result.format, label)
            assertEquals(0, result.diagnostics.fatalCount, "$label $diagnosticText")
            assertEquals(0, result.stats.opsRefused, "$label ${result.stats}")
            assertTrue(result.diagnostics.entries.isNotEmpty(), "$label $diagnosticText")
            assertTrue(result.stats.opsDispatched > 0, "$label ${result.stats}")
            assertTrue(result.stats.drawCallCount > 0, "$label ${result.stats}")
            assertTrue(result.stats.pipelineCount > 0, "$label ${result.stats}")
            assertTrue(result.diagnostics.entries.any { diagnostic ->
                diagnostic.code.startsWith("route:destination-read:DrawPoint") &&
                    diagnostic.operation.startsWith("DrawPoint") &&
                    diagnostic.reason == "gpu-copy-then-formula"
            }, "$label $diagnosticText")

            for (offset in result.pixels.indices step 4) {
                assertTrue(abs((result.pixels[offset].toInt() and 0xff) - 0) <= 2,
                    "$label red at ${offset / 4}=${result.pixels[offset]}")
                assertTrue(abs((result.pixels[offset + 1].toInt() and 0xff) - 0) <= 2,
                    "$label green at ${offset / 4}=${result.pixels[offset + 1]}")
                assertTrue(abs((result.pixels[offset + 2].toInt() and 0xff) - 0) <= 2,
                    "$label blue at ${offset / 4}=${result.pixels[offset + 2]}")
                assertEquals(255, result.pixels[offset + 3].toInt() and 0xff,
                    "$label alpha at ${offset / 4}=${result.pixels[offset + 3]}")
            }
        }

        assertContentEquals(results[0].second.pixels, results[1].second.pixels, "first frame/replay RGBA")
    }

    @Test
    fun preparedSquarePointHalfAlphaDarkenBlendsOnceAndRefreshesDestination() {
        val surface = Surface(32, 32)
        val pointPaint = Paint(
            color = ColorARGB.of(128, 255, 0, 0),
            antiAlias = false,
            style = PaintStyle.FILL,
            strokeWidth = 32f,
            strokeCap = StrokeCap.SQUARE,
            blendMode = BlendMode.DARKEN,
        )

        fun recordFrame(background: ColorARGB) {
            surface.discardRecordedOperations()
            surface.canvas {
                drawRect(
                    RectF32.ofLTRB(0f, 0f, 32f, 32f),
                    Paint(color = background, antiAlias = false),
                )
                drawPoint(16f, 16f, pointPaint)
            }
        }

        fun assertFrameAndReplay(label: String, expected: List<Int>) {
            val first = surface.render()
            printRenderEvidence("$label-first", first)
            val replay = surface.render()
            printRenderEvidence("$label-replay", replay)
            assertHalfAlphaDarkenPixels("$label-first", first, expected)
            assertHalfAlphaDarkenPixels("$label-replay", replay, expected)
            assertContentEquals(first.pixels, replay.pixels, "$label full RGBA replay")
        }

        recordFrame(ColorARGB.Blue)
        assertFrameAndReplay("blue-destination", listOf(0, 0, 187, 255))

        recordFrame(ColorARGB.Red)
        assertFrameAndReplay("red-destination-refresh", listOf(255, 0, 0, 255))

        recordFrame(ColorARGB.Blue)
        assertFrameAndReplay("blue-destination-refresh", listOf(0, 0, 187, 255))
    }

    @Test
    fun publicBlueClearAndPreparedSquarePointRefuseWithoutConsumingOperations() {
        val surface = Surface(32, 32)
        val pointPaint = Paint(
            color = ColorARGB.Red,
            antiAlias = false,
            style = PaintStyle.FILL,
            strokeWidth = 8f,
            strokeCap = StrokeCap.SQUARE,
            blendMode = BlendMode.DARKEN,
        )
        surface.canvas {
            clear(ColorARGB.Blue)
            drawPoint(16f, 16f, pointPaint)
        }

        val originalOperations = surface.snapshotOps()
        assertEquals(2, originalOperations.size)
        assertEquals(ColorARGB.Blue, assertIs<DisplayOp.Clear>(originalOperations[0]).color)
        val point = assertIs<DisplayOp.DrawPoint>(originalOperations[1])
        assertEquals(16f, point.x)
        assertEquals(16f, point.y)
        assertEquals(pointPaint, point.paint)

        repeat(2) { attempt ->
            val failure = assertFailsWith<org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTerminalException> {
                surface.render()
            }
            assertEquals("invalid.w5b.prepared-points", failure.diagnostic.code.value)
            println(
                "W7_PUBLIC_BLUE_CLEAR_POINT_REFUSAL attempt=${attempt + 1} " +
                    "code=${failure.diagnostic.code.value} facts=${failure.diagnostic.facts}",
            )
            assertEquals(originalOperations, surface.snapshotOps(), "terminal refusal must retain the public operations")
        }
    }

    private fun assertHalfAlphaDarkenPixels(label: String, result: RenderResult, expected: List<Int>) {
        val diagnosticText = result.diagnostics.entries.joinToString {
            "${it.code}:${it.operation}:${it.reason}"
        }
        assertEquals(32, result.width, label)
        assertEquals(32, result.height, label)
        assertEquals(PixelFormat.RGBA8, result.format, label)
        assertEquals(0, result.diagnostics.fatalCount, "$label $diagnosticText")
        assertEquals(0, result.stats.opsRefused, "$label ${result.stats}")
        assertTrue(result.diagnostics.entries.any { diagnostic ->
            diagnostic.code.startsWith("route:destination-read:DrawPoint") &&
                diagnostic.operation.startsWith("DrawPoint") &&
                diagnostic.reason == "gpu-copy-then-formula"
        }, "$label $diagnosticText")
        assertTrue(result.stats.opsDispatched > 0, "$label ${result.stats}")
        assertTrue(result.stats.drawCallCount > 0, "$label ${result.stats}")
        assertTrue(result.stats.pipelineCount > 0, "$label ${result.stats}")

        for (offset in result.pixels.indices step 4) {
            for (channel in 0..2) {
                val actual = result.pixels[offset + channel].toInt() and 0xff
                assertTrue(abs(actual - expected[channel]) <= 2,
                    "$label channel=$channel pixel=${offset / 4}: expected=${expected[channel]} actual=$actual")
            }
            assertEquals(expected[3], result.pixels[offset + 3].toInt() and 0xff,
                "$label alpha at ${offset / 4}")
        }
    }

    private fun printRenderEvidence(label: String, result: RenderResult) {
        val diagnosticText = result.diagnostics.entries.joinToString {
            "${it.code}:${it.operation}:${it.reason}"
        }
        val rgbaSha256 = MessageDigest.getInstance("SHA-256")
            .digest(result.pixels.map { it.toByte() }.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        println(
            "W7_PREPARED_POINT_DESTINATION_READ label=$label stats=${result.stats} " +
                "diagnostics=$diagnosticText scopes=${result.nativeEvidenceScopeKinds} " +
                "counters=${result.nativeEvidenceCounters} rgbaSha256=$rgbaSha256",
        )
    }
}
