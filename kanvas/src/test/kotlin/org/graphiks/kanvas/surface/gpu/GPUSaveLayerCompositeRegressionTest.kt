package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.DiagnosticLevel
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

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
                    color = translucentRed.toColor(),
                    antiAlias = false,
                    blendMode = BlendMode.SRC,
                ),
            )
            restore()
        }

        val pixels = surface.render().pixels

        assertPixelNear(pixels, x = 0, y = 0, expected = white, tolerance = 0)
        assertPixelNear(pixels, x = 2, y = 6, expected = checkerGray, tolerance = 0)
        assertPixelNear(pixels, x = 2, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(pixels, x = 5, y = 2, expected = sourceOverSrgb(translucentRed, checkerGray), tolerance = 2)
    }

    @Test
    fun `nested ordinary saveLayers preserve parent isolation before final composition`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(
                Rect(0f, 0f, 8f, 8f),
                Paint(color = green.toColor(), antiAlias = false),
            )
            saveLayer()
            drawRect(
                Rect(0f, 0f, 8f, 8f),
                Paint(
                    color = translucentRed.toColor(),
                    antiAlias = false,
                ),
            )
            saveLayer()
            drawRect(
                Rect(2f, 2f, 6f, 6f),
                Paint(
                    color = translucentBlue.toColor(),
                    antiAlias = false,
                    blendMode = BlendMode.DST_OUT,
                ),
            )
            restore()
            restore()
        }

        val pixels = surface.render().pixels
        val expectedOuterLayer = sourceOverSrgb(translucentRed, green)

        assertPixelNear(pixels, x = 1, y = 1, expected = expectedOuterLayer, tolerance = 2)
        assertPixelNear(pixels, x = 3, y = 3, expected = expectedOuterLayer, tolerance = 2)
    }

    @Test
    fun `ordinary saveLayer composites clipped DrawColor SRC over its parent`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = checkerGray.toColor(), antiAlias = false))
            saveLayer()
            save()
            clipRect(Rect(1f, 1f, 7f, 7f))
            drawColor(translucentBackground.toColor(), BlendMode.SRC)
            restore()
            restore()
        }

        val pixels = surface.render().pixels

        assertPixelNear(
            pixels,
            x = 2,
            y = 2,
            expected = sourceOverSrgb(translucentBackground, checkerGray),
            tolerance = 2,
        )
    }

    @Test
    fun `advanced blend leaves a translucent layer background unchanged outside its source`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = checkerGray.toColor(), antiAlias = false))
            saveLayer()
            save()
            clipRect(Rect(1f, 1f, 7f, 7f))
            drawColor(translucentBackground.toColor(), BlendMode.SRC)
            restore()
            drawRect(
                Rect(5f, 5f, 7f, 7f),
                Paint(color = translucentRed.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            restore()
        }

        val pixels = surface.render().pixels

        assertPixelNear(
            pixels,
            x = 2,
            y = 2,
            expected = sourceOverSrgb(translucentBackground, checkerGray),
            tolerance = 2,
        )
    }

    @Test
    fun `advanced blend snapshot does not retain root content behind an active layer`() {
        requireWebGpu()

        val baseline = Surface(width = 8, height = 8)
        baseline.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            drawRect(
                Rect(6f, 6f, 7f, 7f),
                Paint(color = translucentBlue.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            saveLayer()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentBlue.toColor(), antiAlias = false))
            restore()
        }

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            drawRect(
                Rect(6f, 6f, 7f, 7f),
                Paint(color = translucentBlue.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            saveLayer()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentBlue.toColor(), antiAlias = false))
            drawRect(
                Rect(6f, 6f, 7f, 7f),
                Paint(color = translucentRed.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            restore()
        }

        assertPixelNearPixels(surface.render().pixels, baseline.render().pixels, x = 2, y = 2, tolerance = 2)
    }

    @Test
    fun `DrawPicture containing saveLayer is refused before any child reaches its parent`() {
        requireWebGpu()

        val recorder = PictureRecorder()
        val pictureCanvas = recorder.beginRecording(Rect(0f, 0f, 8f, 8f))
        pictureCanvas.drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
        pictureCanvas.saveLayer()
        pictureCanvas.drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentBlue.toColor(), antiAlias = false))
        pictureCanvas.restore()
        val picture = recorder.finishRecordingAsPicture()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            drawPicture(picture)
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 2, y = 2, expected = white, tolerance = 0)
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(
            "unsupported.picture.save_layer",
            result.diagnostics.entries.single { it.level == DiagnosticLevel.FATAL }.reason,
        )
    }

    @Test
    fun `empty ordinary saveLayer leaves its parent untouched`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            saveLayer()
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 0, y = 0, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 2, y = 6, expected = checkerGray, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `bounded saveLayer clips child and composite to device bounds`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            saveLayer(Rect(2f, 2f, 6f, 6f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 1, y = 1, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 3, y = 3, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `empty bounded saveLayer leaves parent untouched`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            saveLayer(Rect(20f, 20f, 21f, 21f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertCheckerboard(result.pixels)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `translated bounded saveLayer maps local bounds to device space`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            translate(2f, 1f)
            saveLayer(Rect(0f, 0f, 4f, 4f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 1, y = 2, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 4, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 6, y = 2, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `scaled bounded saveLayer maps local bounds to device space`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            scale(2f, 2f)
            saveLayer(Rect(1f, 1f, 3f, 3f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 1, y = 3, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 3, y = 3, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 6, y = 3, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `partially offscreen bounded saveLayer clips at the device edge`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            translate(-2f, 1f)
            saveLayer(Rect(0f, 0f, 6f, 4f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 3, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 4, y = 2, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `non finite mapped saveLayer bounds leave parent and report exact refusal`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            concat(Matrix33.makeAll(1f, 0f, Float.POSITIVE_INFINITY, 0f, 1f, 0f))
            saveLayer(Rect(0f, 0f, 4f, 4f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertCheckerboard(result.pixels)
        assertFatalReason(result, "unsupported.layer.bounds.non_finite")
    }

    @Test
    fun `nested bounded saveLayers intersect their transformed device bounds`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            translate(1f, 0f)
            saveLayer(Rect(0f, 0f, 5f, 6f))
            translate(2f, 0f)
            saveLayer(Rect(0f, 0f, 5f, 6f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 2, y = 2, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 5, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 6, y = 2, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawCheckerboardRoot() {
        drawRect(Rect(0f, 0f, 4f, 4f), Paint(color = white.toColor(), antiAlias = false))
        drawRect(
            Rect(4f, 0f, 8f, 4f),
            Paint(color = checkerGray.toColor(), antiAlias = false),
        )
        drawRect(
            Rect(0f, 4f, 4f, 8f),
            Paint(color = checkerGray.toColor(), antiAlias = false),
        )
        drawRect(Rect(4f, 4f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
    }

    private fun assertCheckerboard(pixels: UByteArray) {
        assertPixelNear(pixels, x = 0, y = 0, expected = white, tolerance = 0)
        assertPixelNear(pixels, x = 6, y = 0, expected = checkerGray, tolerance = 0)
        assertPixelNear(pixels, x = 0, y = 6, expected = checkerGray, tolerance = 0)
        assertPixelNear(pixels, x = 6, y = 6, expected = white, tolerance = 0)
    }

    private fun assertFatalReason(result: org.graphiks.kanvas.surface.RenderResult, reason: String) {
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(reason, result.diagnostics.entries.single { it.level == DiagnosticLevel.FATAL }.reason)
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
        expected: Rgba,
        tolerance: Int,
    ) {
        val offset = (y * 8 + x) * 4
        val actual = IntArray(4) { channel -> pixels[offset + channel].toInt() and 0xff }
        actual.zip(expected.toIntArray()).forEachIndexed { channel, (actualByte, expectedByte) ->
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= tolerance,
                "channel=$channel at ($x,$y): expected=$expectedByte +/- $tolerance, actual=$actualByte",
            )
        }
    }

    private fun assertPixelNearPixels(
        actual: UByteArray,
        expected: UByteArray,
        x: Int,
        y: Int,
        tolerance: Int,
    ) {
        val offset = (y * 8 + x) * 4
        (0 until 4).forEach { channel ->
            val actualByte = actual[offset + channel].toInt() and 0xff
            val expectedByte = expected[offset + channel].toInt() and 0xff
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= tolerance,
                "channel=$channel at ($x,$y): expected=$expectedByte +/- $tolerance, actual=$actualByte",
            )
        }
    }

    /** Models one RGBA8_UNORM_SRGB source-over pass, including texture quantisation. */
    private fun sourceOverSrgb(source: Rgba, destination: Rgba): Rgba {
        val sourceAlpha = source.alpha / 255f
        val destinationAlpha = destination.alpha / 255f
        val outputAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)

        fun composite(sourceChannel: Int, destinationChannel: Int): Int {
            if (outputAlpha == 0f) return 0
            val outputLinearPremul = srgbToLinear(sourceChannel) * sourceAlpha +
                srgbToLinear(destinationChannel) * destinationAlpha * (1f - sourceAlpha)
            return linearToSrgb(outputLinearPremul / outputAlpha)
        }

        return Rgba(
            red = composite(source.red, destination.red),
            green = composite(source.green, destination.green),
            blue = composite(source.blue, destination.blue),
            alpha = (outputAlpha * 255f + 0.5f).toInt(),
        )
    }

    private fun srgbToLinear(channel: Int): Float {
        val srgb = channel / 255f
        return if (srgb <= 0.04045f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(linear: Float): Int {
        val srgb = if (linear <= 0.0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        return (srgb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    private data class Rgba(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    ) {
        fun toColor(): Color = Color.fromRGBA(red / 255f, green / 255f, blue / 255f, alpha / 255f)

        fun toIntArray(): IntArray = intArrayOf(red, green, blue, alpha)
    }

    private companion object {
        val white = Rgba(red = 255, green = 255, blue = 255, alpha = 255)
        val checkerGray = Rgba(red = 191, green = 191, blue = 191, alpha = 255)
        val green = Rgba(red = 0, green = 255, blue = 0, alpha = 255)
        val translucentRed = Rgba(red = 255, green = 0, blue = 0, alpha = 128)
        val translucentBlue = Rgba(red = 0, green = 0, blue = 255, alpha = 128)
        val translucentBackground = Rgba(red = 210, green = 184, blue = 135, alpha = 200)
    }
}
