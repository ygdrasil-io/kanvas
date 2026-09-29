@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public Render/Readback witnesses that AA root circles retain device-space curve precision. */
class W7AaCurvePrecisionSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // GLFW/AppKit teardown must run on the test's first thread, not a JVM shutdown hook.
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `one device pixel aa circle retains its hand-derived alpha area`() {
        // pi for r=1 is approximately 3.142; these bounds are independent of path preparation.
        val result = renderCircle(width = 4, height = 4, centerX = 2f, centerY = 2f, radius = 1f)

        assertNativeRenderReadback(result)
        assertTransparent(result, width = 4, x = 0, y = 0)
        assertTransparent(result, width = 4, x = 3, y = 3)
        val alphaArea = alphaArea(result)
        assertTrue(alphaArea in 2.5..3.75, "expected r=1 alpha area in [2.5, 3.75], got $alphaArea")
    }

    @Test
    fun `eight device pixel aa circle retains its hand-derived alpha area`() {
        // pi * 8^2 is approximately 201.062; this interval permits only sampling error.
        val result = renderCircle(width = 20, height = 20, centerX = 10f, centerY = 10f, radius = 8f)

        assertNativeRenderReadback(result)
        assertTransparent(result, width = 20, x = 0, y = 0)
        assertTransparent(result, width = 20, x = 19, y = 19)
        val alphaArea = alphaArea(result)
        assertTrue(alphaArea in 198.0..204.0, "expected r=8 alpha area in [198, 204], got $alphaArea")
    }

    @Test
    fun `scaled aa circle retains device-space alpha area`() {
        // A local r=4 at scale 2 is the same device-space r=8 circle, whose area is pi * 64.
        val surface = Surface(20, 20)
        surface.canvas {
            scale(2f, 2f)
            drawPath(Path().apply { addCircle(5f, 5f, 4f) }, Paint(ColorARGB.White, antiAlias = true))
        }
        val result = surface.render()

        assertNativeRenderReadback(result)
        assertTransparent(result, width = 20, x = 0, y = 0)
        assertTransparent(result, width = 20, x = 19, y = 19)
        val alphaArea = alphaArea(result)
        assertTrue(alphaArea in 198.0..204.0, "expected scaled r=8 alpha area in [198, 204], got $alphaArea")
    }

    private fun renderCircle(
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ): RenderResult {
        val surface = Surface(width, height)
        surface.canvas {
            drawPath(Path().apply { addCircle(centerX, centerY, radius) }, Paint(ColorARGB.White, antiAlias = true))
        }
        return surface.render()
    }

    private fun alphaArea(result: RenderResult): Double =
        result.pixels.indices.filter { it % 4 == 3 }.sumOf { result.pixels[it].toInt() } / 255.0

    private fun assertTransparent(result: RenderResult, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        assertEquals(0.toUByte(), result.pixels[offset], "red at ($x, $y)")
        assertEquals(0.toUByte(), result.pixels[offset + 1], "green at ($x, $y)")
        assertEquals(0.toUByte(), result.pixels[offset + 2], "blue at ($x, $y)")
        assertEquals(0.toUByte(), result.pixels[offset + 3], "alpha at ($x, $y)")
    }

    private fun assertNativeRenderReadback(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }
}
