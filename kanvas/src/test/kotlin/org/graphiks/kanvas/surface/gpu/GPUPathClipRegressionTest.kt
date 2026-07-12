package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPathClipRegressionTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `device rect clip preserves translated path color and exterior`() {
        requireWebGpu()

        val surface = Surface(width = 32, height = 32)
        surface.canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            save()
            clipRect(Rect(8f, 8f, 24f, 24f))
            drawPath(
                Path {
                    moveTo(8f, 8f)
                    lineTo(24f, 8f)
                    lineTo(16f, 24f)
                    close()
                },
                Paint.fill(Color.RED).copy(antiAlias = false),
            )
            restore()
        }

        val pixels = surface.render().pixels.toByteArray()
        assertPixelAtLeast(pixels, 16, 12, red = 200, green = 0, blue = 0, alpha = 200)
        assertPixelAtLeast(pixels, 2, 2, red = 200, green = 200, blue = 200, alpha = 200)
    }

    @Test
    fun `dst in path preserves destination outside the source geometry`() {
        requireWebGpu()

        val surface = Surface(width = 32, height = 32)
        surface.canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawPath(
                Path {
                    moveTo(8f, 8f)
                    lineTo(24f, 8f)
                    lineTo(16f, 24f)
                    close()
                },
                Paint.fill(Color.BLACK).copy(
                    antiAlias = false,
                    blendMode = BlendMode.DST_IN,
                ),
            )
        }

        val pixels = surface.render().pixels.toByteArray()
        assertPixelAtLeast(pixels, 2, 2, red = 200, green = 200, blue = 200, alpha = 200)
    }

    @Test
    fun `darken advanced blend preserves destination outside source geometry`() {
        requireWebGpu()

        val surface = Surface(width = 32, height = 32)
        surface.canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawRect(
                Rect(8f, 8f, 24f, 24f),
                Paint.fill(Color.BLACK).copy(blendMode = BlendMode.DARKEN),
            )
        }

        val pixels = surface.render().pixels.toByteArray()
        assertPixelAtLeast(pixels, 2, 2, red = 200, green = 200, blue = 200, alpha = 200)
    }

    @Test
    fun `advanced path blend renders through the destination snapshot formula`() {
        requireWebGpu()

        val surface = Surface(width = 32, height = 32)
        surface.canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawPath(
                Path {
                    moveTo(8f, 8f)
                    lineTo(24f, 8f)
                    lineTo(16f, 24f)
                    close()
                },
                Paint.fill(Color.RED).copy(antiAlias = false, blendMode = BlendMode.DIFFERENCE),
            )
        }

        val result = surface.render()
        val pixels = result.pixels.toByteArray()
        assertPixelAtLeast(pixels, 16, 12, red = 0, green = 200, blue = 200, alpha = 200)
        assertTrue(result.diagnostics.entries.any { it.reason == "gpu-copy-then-formula" })
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPixelAtLeast(
        pixels: ByteArray,
        x: Int,
        y: Int,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        val offset = (y * 32 + x) * 4
        assertTrue((pixels[offset].toInt() and 0xff) >= red, "red at ($x,$y)")
        assertTrue((pixels[offset + 1].toInt() and 0xff) >= green, "green at ($x,$y)")
        assertTrue((pixels[offset + 2].toInt() and 0xff) >= blue, "blue at ($x,$y)")
        assertTrue((pixels[offset + 3].toInt() and 0xff) >= alpha, "alpha at ($x,$y)")
    }
}
