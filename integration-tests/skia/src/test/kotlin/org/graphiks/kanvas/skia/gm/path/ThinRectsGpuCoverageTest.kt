package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalUnsignedTypes::class)
class ThinRectsGpuCoverageTest {
    @Test
    fun `native thinrect fixture preserves exact one eighth pixel coverage`() {
        GpuAvailability.requireWebGpu()

        val surface = Surface(width = 4, height = 4)
        surface.canvas().drawRect(
            RectF32(1.125f, 1f, 1.25f, 3f),
            Paint(color = ColorARGB.Green, antiAlias = true),
        )
        val rendered = surface.render()
        val pixelOffset = (1 * 4 + 1) * 4

        // The CPU oracle is rectangle/pixel area: (1.25 - 1.125) * 1.0 = 1/8.
        val expectedCoverage = 0.125f
        val actualAlpha = rendered.pixels[pixelOffset + 3].toInt()

        assertEquals(
            (expectedCoverage * 255f).roundToInt(),
            actualAlpha,
            "the native analytic route must preserve the CPU rectangle/pixel overlap",
        )

        val cpuOracle = ByteArray(4 * 4 * 4)
        listOf(1, 2).forEach { y ->
            val offset = (y * 4 + 1) * 4
            cpuOracle[offset + 1] = srgbByteFromLinear(expectedCoverage).toByte()
            cpuOracle[offset + 3] = (expectedCoverage * 255f).roundToInt().toByte()
        }
        assertArrayEquals(
            cpuOracle,
            rendered.pixels.toByteArray(),
            "the GPU readback must equal the deterministic CPU overlap oracle",
        )
    }

    @Test
    fun `full thinrects GM remains refused for its degenerate rect variants`() {
        GpuAvailability.requireWebGpu()

        val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(ThinRectsGm()))

        assertTrue(attempt.diagnostic.contains("unsupported.core_primitive.geometry.invalid"))
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}

private fun srgbByteFromLinear(linear: Float): Int =
    ((1.055f * linear.coerceIn(0f, 1f).pow(1f / 2.4f) - 0.055f) * 255f)
        .roundToInt()
