package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class AAXfermodesRegressionTest {
    @Test
    fun `clipped AA blend mode grid retains visible pixels`() {
        GpuAvailability.requireWebGpu()

        val gm = AAXfermodesGm()
        val actual = SkiaGmRenderer.render(gm).rgba
        val reference = ReferenceManager.loadReference("/reference/${gm.name}.png")
        val matchingPixels = actual.asList()
            .zip(reference.asList())
            .chunked(4)
            .count { channels -> channels.all { (actualByte, referenceByte) -> actualByte == referenceByte } }

        assertTrue(
            matchingPixels > 10_000,
            "expected clipped blend grid to retain visible reference pixels, matched=$matchingPixels",
        )
    }

    @Test
    fun `first translucent background cell retains checkerboard through saveLayer`() {
        GpuAvailability.requireWebGpu()

        val checkerboard = Color.fromRGBA(198f / 255f, 195f / 255f, 198f / 255f, 1f)
        val surface = Surface(width = 32, height = 32)
        surface.canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint(color = checkerboard, antiAlias = false))
            saveLayer()
            save()
            clipRect(Rect(1f, 1f, 31f, 31f))
            drawColor(AAXfermodesGm.kBGColor, BlendMode.SRC)
            restore()
            restore()
        }

        val pixels = surface.render().pixels
        val expected = intArrayOf(208, 186, 149, 255)
        val offset = (8 * 32 + 8) * 4
        expected.forEachIndexed { channel, expectedByte ->
            val actualByte = pixels[offset + channel].toInt() and 0xff
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= 4,
                "channel=$channel: expected=$expectedByte +/- 4, actual=$actualByte",
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
