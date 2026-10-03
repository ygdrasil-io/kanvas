@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.skia.gm.image.DrawBitmapRect3Gm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Native pixel witnesses for the source-pinned 3x3 bitmap-rect GM. */
class W7BitmapRectSourceSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun disposeW7BitmapRectSourceSurfacePixelTestGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun registeredBitmapRectKeepsPinnedNativeGrid() {
        val gm = DrawBitmapRect3Gm()
        val first = SkiaGmRenderer.render(gm)
        logRegisteredRender("registered-bitmaprect-first", first)
        val second = SkiaGmRenderer.render(DrawBitmapRect3Gm())
        logRegisteredRender("registered-bitmaprect-second", second)

        assertEquals(640, first.width)
        assertEquals(480, first.height)
        assertEquals(2, first.ops.size, "GM background plus bitmap-rect operation")
        assertEquals(2, second.ops.size, "second GM background plus bitmap-rect operation")
        assertEquals(2, first.dispatchedCount, "GM background plus bitmap-rect dispatch")
        assertEquals(2, second.dispatchedCount, "second GM background plus bitmap-rect dispatch")
        assertEquals(0, first.refusedCount)
        assertEquals(0, second.refusedCount)
        assertTrue(first.diagnostics.isEmpty(), first.diagnostics.toString())
        assertTrue(second.diagnostics.isEmpty(), second.diagnostics.toString())
        assertArrayEquals(first.rgba, second.rgba, "independent fresh-surface RGBA repeatability")
        assertPinnedNativeGrid(first.rgba, first.width, first.height, "registered bitmaprect")
    }

    @Test
    fun sameSurfaceBitmapRectReplayKeepsPinnedGrayAndGrid() {
        val gm = DrawBitmapRect3Gm()
        val surface = Surface(width = 640, height = 480, config = gm.compositionConfig())
        val canvas = surface.canvas()
        canvas.drawRect(
            RectF32.ofLTRB(0f, 0f, 640f, 480f),
            Paint(color = ColorARGB.Black, antiAlias = false),
        )
        val gmCanvas = GmCanvas(canvas, 640, 480)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, 640, 480)

        val operationCount = surface.snapshotOps().size
        val first = captureSurfaceRender("same-surface-bitmaprect-frame", surface, operationCount)
        val replay = captureSurfaceRender("same-surface-bitmaprect-replay", surface, operationCount)

        assertEquals(2, operationCount, "literal black background plus bitmap-rect operation")
        assertEquals(2, first.result.stats.opsDispatched, "first native render dispatches")
        assertEquals(2, replay.result.stats.opsDispatched, "replay native render dispatches")
        assertEquals(0, first.result.stats.opsRefused)
        assertEquals(0, replay.result.stats.opsRefused)
        assertEquals(0, first.result.diagnostics.fatalCount, first.result.diagnostics.summary())
        assertEquals(0, replay.result.diagnostics.fatalCount, replay.result.diagnostics.summary())
        assertTrue(first.result.diagnostics.isEmpty, first.result.diagnostics.summary())
        assertTrue(replay.result.diagnostics.isEmpty, replay.result.diagnostics.summary())
        assertTrue(first.result.stats.drawCallCount > 0, "first native render issues a draw call")
        assertTrue(replay.result.stats.drawCallCount > 0, "replay native render issues a draw call")
        assertTrue(first.result.stats.pipelineCount > 0, "first native render binds a pipeline")
        assertTrue(replay.result.stats.pipelineCount > 0, "replay native render binds a pipeline")
        assertArrayEquals(first.rgba, replay.rgba, "same-surface full-buffer replay")
        assertPinnedNativeGrid(first.rgba, first.result.width, first.result.height, "same-surface bitmaprect")
        assertPinnedNativeGrid(replay.rgba, replay.result.width, replay.result.height, "same-surface bitmaprect replay")
    }

    @Test
    fun defaultGmBackgroundRemainsOpaqueWhite() {
        val result = SkiaGmRenderer.render(EmptyWhiteBackgroundGm())
        logRegisteredRender("default-gm-background-white", result)

        assertEquals(8, result.width)
        assertEquals(8, result.height)
        assertEquals(1, result.ops.size, "the renderer background is the sole operation")
        assertEquals(1, result.dispatchedCount, "the renderer background is the sole dispatch")
        assertEquals(0, result.refusedCount)
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.toString())
        assertEquals(8 * 8 * 4, result.rgba.size, "full RGBA buffer size")
        for (pixel in 0 until 8 * 8) {
            val offset = pixel * 4
            assertEquals(255.toByte(), result.rgba[offset], "white red at pixel $pixel")
            assertEquals(255.toByte(), result.rgba[offset + 1], "white green at pixel $pixel")
            assertEquals(255.toByte(), result.rgba[offset + 2], "white blue at pixel $pixel")
            assertEquals(255.toByte(), result.rgba[offset + 3], "opaque alpha at pixel $pixel")
        }
    }

    private fun captureSurfaceRender(label: String, surface: Surface, operationCount: Int): SurfaceCapture {
        val result = surface.render()
        val rgba = result.pixels.map { it.toByte() }.toByteArray()
        println(
            "W7_BITMAPRECT_SOURCE_NATIVE label=$label operations=$operationCount " +
                "stats=${result.stats} diagnostics=${result.diagnostics.summary()} sha256=${sha256(rgba)}",
        )
        return SurfaceCapture(result, rgba)
    }

    private fun logRegisteredRender(label: String, result: SkiaRenderResult) {
        println(
            "W7_BITMAPRECT_SOURCE_NATIVE label=$label operations=${result.ops.size} " +
                "dispatched=${result.dispatchedCount} refused=${result.refusedCount} " +
                "diagnostics=${result.diagnostics} sha256=${sha256(result.rgba)}",
        )
    }

    private fun assertPinnedNativeGrid(rgba: ByteArray, width: Int, height: Int, label: String) {
        assertEquals(640, width, "$label width")
        assertEquals(480, height, "$label height")
        assertEquals(width * height * 4, rgba.size, "$label full RGBA buffer size")

        for (y in 0 until height) {
            for (x in 0 until width) {
                val expected = when {
                    x in 100 until 150 && y in 100 until 125 -> RED
                    x in 100 until 150 && y in 125 until 175 -> WHITE
                    x in 100 until 150 && y in 175 until 200 -> BLUE
                    x in 150 until 250 && y in 100 until 125 -> GREEN
                    x in 150 until 250 && y in 125 until 175 -> BLACK
                    x in 150 until 250 && y in 175 until 200 -> CYAN
                    x in 250 until 300 && y in 100 until 125 -> YELLOW
                    x in 250 until 300 && y in 125 until 175 -> GRAY
                    x in 250 until 300 && y in 175 until 200 -> MAGENTA
                    else -> BLACK
                }
                val offset = (y * width + x) * 4
                for (channel in 0..3) {
                    assertEquals(
                        expected[channel].toByte(),
                        rgba[offset + channel],
                        "$label pixel ($x,$y) channel $channel",
                    )
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class SurfaceCapture(val result: RenderResult, val rgba: ByteArray)

    private class EmptyWhiteBackgroundGm : SkiaGm {
        override val name: String = "w7_empty_white_background"
        override val renderFamily: RenderFamily = RenderFamily.COLOR
        override val renderCost: RenderCost = RenderCost.FAST
        override val minSimilarity: Double = 0.0
        override val width: Int = 8
        override val height: Int = 8

        override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
    }

}

private val RED = intArrayOf(255, 0, 0, 255)
private val GREEN = intArrayOf(0, 255, 0, 255)
private val YELLOW = intArrayOf(255, 255, 0, 255)
private val WHITE = intArrayOf(255, 255, 255, 255)
private val BLACK = intArrayOf(0, 0, 0, 255)
private val GRAY = intArrayOf(136, 136, 136, 255)
private val BLUE = intArrayOf(0, 0, 255, 255)
private val CYAN = intArrayOf(0, 255, 255, 255)
private val MAGENTA = intArrayOf(255, 0, 255, 255)
