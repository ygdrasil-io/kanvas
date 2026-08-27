package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.skia.gm.clip.RRectClipAaGm
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalUnsignedTypes::class)
class ThinRectsGpuCoverageTest {
    private data class ExpectedTerminalRefusal(
        val diagnostic: String,
        val operationCount: Int,
    )

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
    fun `full thin geometry GMs expose exact terminal route refusals`() {
        GpuAvailability.requireWebGpu()

        val expected = mapOf(
            "thinrects" to ExpectedTerminalRefusal(
                diagnostic = "unsupported.core_primitive.geometry.invalid",
                operationCount = 338,
            ),
            "thinroundrects" to ExpectedTerminalRefusal(
                diagnostic = "unsupported.core_primitive.geometry.invalid",
                operationCount = 338,
            ),
            "rrect_clip_aa" to ExpectedTerminalRefusal(
                diagnostic = "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
                operationCount = 45,
            ),
        )
        listOf(ThinRectsGm(), ThinRoundRectsGm(), RRectClipAaGm()).forEach { gm ->
            val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm))
            val refusal = requireNotNull(expected[gm.name])
            val diagnostic = attempt.diagnostic.substringBefore(":")
            assertEquals(refusal.diagnostic, diagnostic, gm.name)
            assertEquals(refusal.operationCount, attempt.operationCount, gm.name)
            println(
                "task5.gm-refusal gm=${gm.name} operations=${attempt.operationCount} " +
                    "diagnostic=$diagnostic",
            )
        }
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
