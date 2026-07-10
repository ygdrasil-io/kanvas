package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUSaveLayerCompositeRegressionTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `ordinary saveLayer composites SRC content over its opaque checkerboard parent`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            saveLayer()
            drawRect(
                Rect(1f, 1f, 7f, 3f),
                Paint(
                    color = Color.fromRGBA(1f, 0f, 0f, 0.5f),
                    antiAlias = false,
                    blendMode = BlendMode.SRC,
                ),
            )
            restore()
        }

        val pixels = surface.render().pixels

        assertPixelNear(pixels, x = 0, y = 0, expected = intArrayOf(255, 255, 255, 255), tolerance = 0)
        assertPixelNear(pixels, x = 2, y = 6, expected = intArrayOf(191, 191, 191, 255), tolerance = 0)
        assertPixelNear(pixels, x = 2, y = 2, expected = intArrayOf(255, 128, 128, 255), tolerance = 2)
        assertPixelNear(pixels, x = 5, y = 2, expected = intArrayOf(224, 96, 96, 255), tolerance = 2)
    }

    @Test
    fun `nested ordinary saveLayers preserve parent isolation before final composition`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(
                Rect(0f, 0f, 8f, 8f),
                Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f), antiAlias = false),
            )
            saveLayer()
            drawRect(
                Rect(0f, 0f, 8f, 8f),
                Paint(
                    color = Color.fromRGBA(1f, 0f, 0f, 0.5f),
                    antiAlias = false,
                    blendMode = BlendMode.SRC,
                ),
            )
            saveLayer()
            drawRect(
                Rect(2f, 2f, 6f, 6f),
                Paint(
                    color = Color.fromRGBA(0f, 0f, 1f, 0.5f),
                    antiAlias = false,
                    blendMode = BlendMode.SRC,
                ),
            )
            restore()
            restore()
        }

        val pixels = surface.render().pixels

        assertPixelNear(pixels, x = 1, y = 1, expected = intArrayOf(128, 128, 0, 255), tolerance = 2)
        assertPixelNear(pixels, x = 3, y = 3, expected = intArrayOf(64, 64, 128, 255), tolerance = 2)
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawCheckerboardRoot() {
        drawRect(Rect(0f, 0f, 4f, 4f), Paint(color = Color.WHITE, antiAlias = false))
        drawRect(
            Rect(4f, 0f, 8f, 4f),
            Paint(color = Color.fromRGBA(0.75f, 0.75f, 0.75f, 1f), antiAlias = false),
        )
        drawRect(
            Rect(0f, 4f, 4f, 8f),
            Paint(color = Color.fromRGBA(0.75f, 0.75f, 0.75f, 1f), antiAlias = false),
        )
        drawRect(Rect(4f, 4f, 8f, 8f), Paint(color = Color.WHITE, antiAlias = false))
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPixelNear(
        pixels: UByteArray,
        x: Int,
        y: Int,
        expected: IntArray,
        tolerance: Int,
    ) {
        val offset = (y * 8 + x) * 4
        val actual = IntArray(4) { channel -> pixels[offset + channel].toInt() and 0xff }
        actual.zip(expected).forEachIndexed { channel, (actualByte, expectedByte) ->
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= tolerance,
                "channel=$channel at ($x,$y): expected=$expectedByte +/- $tolerance, actual=$actualByte",
            )
        }
    }
}
