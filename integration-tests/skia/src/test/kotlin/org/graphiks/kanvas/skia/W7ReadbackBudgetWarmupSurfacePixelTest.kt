@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Native pixel witnesses for encoded-domain readback-warmup order and a tight readback budget. */
class W7ReadbackBudgetWarmupSurfacePixelTest {
    companion object {
        private val RED = ColorARGB.of(255, 255, 0, 0)
        private val BLUE = ColorARGB.of(255, 0, 0, 255)
        private val GREEN = ColorARGB.of(255, 0, 255, 0)
        private val RED_RGBA = byteArrayOf(255.toByte(), 0, 0, 255.toByte())
        private val BLUE_RGBA = byteArrayOf(0, 0, 255.toByte(), 255.toByte())
        private val GREEN_RGBA = byteArrayOf(0, 255.toByte(), 0, 255.toByte())

        @AfterAll
        @JvmStatic
        fun disposeW7ReadbackBudgetWarmupSurfacePixelTestGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun largerThenSmallerWarmupPreservesTightSolidReadbackBudget() {
        warmup("larger-red", 3, RED, RED_RGBA)
        warmup("smaller-blue", 2, BLUE, BLUE_RGBA)

        val surface = encodedTarget()
        val operations = surface.snapshotOps().size
        val first = captureFirstOrLogFailure("larger-then-smaller-first", surface, operations)
        val replay = capture("larger-then-smaller-replay", surface, operations)

        assertNotNull(first, "first target render must succeed; replay cannot hide its failure")
        assertSuccessfulRender("larger-then-smaller-first", surface, requireNotNull(first), operations, 3, GREEN_RGBA)
        assertSuccessfulRender("larger-then-smaller-replay", surface, replay, operations, 3, GREEN_RGBA)
        assertArrayEquals(fullPixels(GREEN_RGBA, 3, 3), rgba(first), "first target full RGBA pixels")
        assertArrayEquals(fullPixels(GREEN_RGBA, 3, 3), rgba(replay), "same-surface replay full RGBA pixels")
    }

    @Test
    fun smallerThenLargerWarmupKeepsSameTightSolidPixels() {
        warmup("smaller-blue", 2, BLUE, BLUE_RGBA)
        warmup("larger-red", 3, RED, RED_RGBA)

        val surface = encodedTarget()
        val operations = surface.snapshotOps().size
        val first = captureFirstOrLogFailure("smaller-then-larger-first", surface, operations)
        val replay = capture("smaller-then-larger-replay", surface, operations)

        assertNotNull(first, "first target render must succeed; replay cannot hide its failure")
        assertSuccessfulRender("smaller-then-larger-first", surface, requireNotNull(first), operations, 3, GREEN_RGBA)
        assertSuccessfulRender("smaller-then-larger-replay", surface, replay, operations, 3, GREEN_RGBA)
        assertArrayEquals(fullPixels(GREEN_RGBA, 3, 3), rgba(first), "first target full RGBA pixels")
        assertArrayEquals(fullPixels(GREEN_RGBA, 3, 3), rgba(replay), "same-surface replay full RGBA pixels")
    }

    private fun warmup(label: String, side: Int, color: ColorARGB, expectedPixel: ByteArray) {
        val surface = Surface(
            side,
            side,
            config = RenderConfig.DEFAULT.copy(compositionDomain = CompositionDomain.SRGB_ENCODED),
        )
        surface.canvas().drawRect(RectF32(0f, 0f, side.toFloat(), side.toFloat()), Paint(color = color, antiAlias = false))
        val operations = surface.snapshotOps().size
        val result = capture(label, surface, operations)
        assertSuccessfulRender(label, surface, result, operations, side, expectedPixel)
        assertArrayEquals(fullPixels(expectedPixel, side, side), rgba(result), "$label full RGBA pixels")
    }

    private fun encodedTarget(): Surface {
        val surface = Surface(
            3,
            3,
            config = RenderConfig.DEFAULT.copy(
                compositionDomain = CompositionDomain.SRGB_ENCODED,
                frameLocalBudgetBytes = 888L,
            ),
        )
        surface.canvas().drawRect(RectF32(0f, 0f, 3f, 3f), Paint(color = GREEN, antiAlias = false))
        return surface
    }

    private fun captureFirstOrLogFailure(label: String, surface: Surface, operations: Int): RenderResult? = try {
        capture(label, surface, operations)
    } catch (failure: IllegalStateException) {
        println("W7_READBACK_BUDGET_WARMUP_NATIVE label=$label operations=$operations IllegalStateException=${failure.message}")
        failure.printStackTrace(System.out)
        null
    }

    private fun capture(label: String, surface: Surface, operations: Int): RenderResult {
        val result = surface.render()
        val pixels = rgba(result)
        println(
            "W7_READBACK_BUDGET_WARMUP_NATIVE label=$label operations=$operations " +
                "stats=${result.stats} diagnostics=${result.diagnostics.summary()} sha256=${sha256(pixels)}",
        )
        return result
    }

    private fun assertSuccessfulRender(
        label: String,
        surface: Surface,
        result: RenderResult,
        operations: Int,
        side: Int,
        expectedPixel: ByteArray,
    ) {
        assertEquals(1, operations, "$label one recorded rectangle")
        assertEquals(1, result.stats.opsDispatched, "$label dispatch count")
        assertEquals(0, result.stats.opsRefused, "$label refusal count")
        assertEquals(0, result.diagnostics.fatalCount, "$label fatal diagnostic count")
        assertTrue(result.diagnostics.isEmpty, "$label ${result.diagnostics.summary()}")
        assertTrue(result.stats.drawCallCount > 0, "$label issues a draw call")
        assertTrue(result.stats.pipelineCount > 0, "$label binds a pipeline")
        assertArrayEquals(fullPixels(expectedPixel, side, side), rgba(result), "$label full RGBA pixels")
        assertEquals(surface.snapshotOps().size, operations, "$label keeps the same recorded operation count")
    }

    private fun rgba(result: RenderResult): ByteArray = result.pixels.map { it.toByte() }.toByteArray()

    private fun fullPixels(pixel: ByteArray, width: Int, height: Int): ByteArray =
        ByteArray(width * height * pixel.size) { index -> pixel[index % pixel.size] }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

}
