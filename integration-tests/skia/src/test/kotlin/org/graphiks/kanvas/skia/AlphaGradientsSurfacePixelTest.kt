@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.diagnostic.DiagnosticRunner
import org.graphiks.kanvas.diagnostic.RunnerInput
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.DebugLevel
import org.graphiks.kanvas.skia.gm.gradient.AlphaGradientsGm
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.roundToInt

class AlphaGradientsSurfacePixelTest {
    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun alphagradientsMatchesIndependentTwoColumnScene() {
        val expected = independentAlphaGradientsRgba()
        assertPixel(expected, 640, 160, 25, 191, 191, 191, 255)
        assertPixel(expected, 640, 470, 25, 255, 255, 255, 255)
        assertPixel(expected, 640, 160, 10, 0, 0, 0, 255)
        assertPixel(expected, 640, 160, 9, 255, 255, 255, 255)

        GpuAvailability.requireWebGpu()
        val actual = SkiaGmRenderer.render(AlphaGradientsGm())
        assertPixel(actual.rgba, actual.width, 160, 25, 191, 191, 191, 255)
        assertPixel(actual.rgba, actual.width, 470, 25, 255, 255, 255, 255)
        assertPixel(actual.rgba, actual.width, 160, 10, 0, 0, 0, 255)
        assertPixel(actual.rgba, actual.width, 160, 9, 255, 255, 255, 255)
        assertAlphaGradientsPixels(expected, actual.rgba)
        assertEquals(0, actual.refusedCount, actual.diagnostics.toString())
        assertTrue(actual.diagnostics.isEmpty(), actual.diagnostics.toString())
        assertTrue(actual.dispatchedCount > 0, "expected native dispatch")
        val repeated = SkiaGmRenderer.render(AlphaGradientsGm())
        assertTrue(actual.rgba.contentEquals(repeated.rgba), "repeated alphagradients bytes differ")
    }

    @Test
    fun gmDomainControlsPixelsWithoutDiscardingCallerBudget() {
        GpuAvailability.requireWebGpu()
        val linear = CompositionProbeGm("linear-domain", antiAlias = false)
        val encoded = EncodedCompositionProbeGm("encoded-domain", antiAlias = false)

        val linearFirst = SkiaGmRenderer.render(linear, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        val linearSecond = SkiaGmRenderer.render(linear, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        assertPixelNear(linearFirst.rgba, linearFirst.width, 0, 0, 187, 2)
        assertEquals(255, linearFirst.rgba[3].toInt() and 255)
        assertTrue(linearFirst.rgba.contentEquals(linearSecond.rgba), "linear GM repeat differs")

        val encodedFirst = SkiaGmRenderer.render(encoded, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR))
        val encodedSecond = SkiaGmRenderer.render(encoded, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR))
        assertPixelNear(encodedFirst.rgba, encodedFirst.width, 0, 0, 127, 2)
        assertEquals(255, encodedFirst.rgba[3].toInt() and 255)
        assertTrue(encodedFirst.rgba.contentEquals(encodedSecond.rgba), "encoded GM repeat differs")

        val budgetFailure = assertThrows<IllegalStateException> {
            SkiaGmRenderer.render(encoded, config = RenderConfig(frameLocalBudgetBytes = 1L))
        }
        assertEquals("w3.budget.frame_local_exceeded", budgetFailure.message.orEmpty().substringBefore(':'))
        val recovered = SkiaGmRenderer.render(encoded)
        assertTrue(recovered.rgba.contentEquals(encodedFirst.rgba), "budget recovery differs from healthy encoded bytes")
        assertEquals(0, recovered.refusedCount, recovered.diagnostics.toString())
        assertTrue(recovered.diagnostics.isEmpty(), recovered.diagnostics.toString())
    }

    @Test
    fun encodedUnsupportedDrawRefusesAcrossHarnessEntriesAndRecovers() {
        GpuAvailability.requireWebGpu()
        val healthyGm = EncodedCompositionProbeGm("encoded-healthy", antiAlias = false)
        val healthyBeforeUnsupported = SkiaGmRenderer.render(healthyGm)
        assertPixelNear(healthyBeforeUnsupported.rgba, healthyBeforeUnsupported.width, 0, 0, 127, 2)
        val unsupported = EncodedCompositionProbeGm("encoded-aa-unsupported", antiAlias = true)
        val renderFailure = assertThrows<IllegalStateException> { SkiaGmRenderer.render(unsupported) }
        assertEquals("unsupported.surface.composition.geometry", renderFailure.message.orEmpty().substringBefore(':'))

        val terminal = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(unsupported))
        assertEquals("unsupported.surface.composition.geometry", terminal.diagnostic.substringBefore(':'))
        assertTrue(terminal.operationCount > 0)

        val evidence = SkiaGmRenderer.inventoryEvidence(unsupported)
        assertTrue(evidence.attempted)
        assertTrue(evidence.terminalFailure)
        assertTrue(!evidence.renderSucceeded)
        assertEquals("render-failure", evidence.route)
        assertEquals("unsupported.surface.composition.geometry", evidence.diagnostics.single().substringBefore(':'))

        val healthy = SkiaGmRenderer.render(healthyGm)
        assertTrue(healthy.rgba.contentEquals(healthyBeforeUnsupported.rgba), "unsupported-draw recovery differs from healthy encoded bytes")
        assertEquals(0, healthy.refusedCount, healthy.diagnostics.toString())
        assertTrue(healthy.diagnostics.isEmpty(), healthy.diagnostics.toString())
    }

    @Test
    fun encodedDiagnosticReplayKeepsPixelsInBothReplayModes() {
        GpuAvailability.requireWebGpu()
        val reference = ByteArray(2 * 2 * 4) { 255.toByte() }

        listOf(
            "sequential" to 0,
            "checkpoint" to 60,
        ).forEach { (replayMode, whitePrefixCount) ->
            val gm = EncodedDiagnosticReplayProbeGm("encoded-diagnostic-$replayMode", whitePrefixCount)
            val result = SkiaGmRenderer.render(gm)
            assertWholeImageRgbNear(result.rgba, 127, 2)

            val outputDir = File(tempDir, replayMode).also { it.mkdirs() }
            val manifest = DiagnosticRunner.run(RunnerInput(
                gmName = gm.name,
                minSimilarity = gm.minSimilarity,
                actualRgba = result.rgba,
                referenceRgba = reference,
                width = result.width,
                height = result.height,
                tolerance = gm.tolerance,
                ops = result.ops,
                dispatchedCount = result.dispatchedCount,
                refusedCount = result.refusedCount,
                diagnostics = result.diagnostics,
                debugLevel = DebugLevel.OP,
                outputDir = outputDir,
                renderConfig = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED),
            ))

            val suspect = requireNotNull(requireNotNull(manifest.opTrace).ops.lastOrNull { it.afterUrl != null })
            val before = ComparisonUtils.loadPngAsSrgbRgba(File(outputDir, requireNotNull(suspect.beforeUrl)))
            val after = ComparisonUtils.loadPngAsSrgbRgba(File(outputDir, requireNotNull(suspect.afterUrl)))
            assertTrue(before.all { it.toInt() and 255 == 255 }, "$replayMode before PNG was not opaque white")
            assertWholeImageRgbNear(after, 127, 2)
        }
    }
}

private open class CompositionProbeGm(
    override val name: String,
    private val antiAlias: Boolean,
) : SkiaGm {
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2
    override val height = 2

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(ColorARGB.of(128, 0, 0, 0), antiAlias = antiAlias),
        )
    }
}

private class EncodedCompositionProbeGm(name: String, antiAlias: Boolean) :
    CompositionProbeGm(name, antiAlias) {
    override val compositionDomain = CompositionDomain.SRGB_ENCODED
}

private class EncodedDiagnosticReplayProbeGm(
    override val name: String,
    private val whitePrefixCount: Int,
) : SkiaGm {
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2
    override val height = 2
    override val compositionDomain = CompositionDomain.SRGB_ENCODED

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bounds = RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())
        repeat(whitePrefixCount) {
            canvas.drawRect(bounds, Paint(ColorARGB.of(255, 255, 255, 255), antiAlias = false))
        }
        canvas.drawRect(bounds, Paint(ColorARGB.of(128, 0, 0, 0), antiAlias = false))
    }
}

private fun independentAlphaGradientsRgba(): ByteArray {
    val width = 640
    val height = 480
    val expected = ByteArray(width * height * 4)
    for (pixel in 0 until width * height) {
        expected[pixel * 4] = 255.toByte()
        expected[pixel * 4 + 1] = 255.toByte()
        expected[pixel * 4 + 2] = 255.toByte()
        expected[pixel * 4 + 3] = 255.toByte()
    }
    val pairs = listOf(
        Rgb(1.0, 1.0, 1.0) to Rgb(0.0, 0.0, 0.0),
        Rgb(1.0, 1.0, 1.0) to Rgb(1.0, 0.0, 0.0),
        Rgb(1.0, 1.0, 1.0) to Rgb(1.0, 1.0, 0.0),
        Rgb(1.0, 1.0, 1.0) to Rgb(1.0, 1.0, 1.0),
        Rgb(1.0, 0.0, 0.0) to Rgb(0.0, 0.0, 0.0),
        Rgb(1.0, 0.0, 0.0) to Rgb(1.0, 0.0, 0.0),
        Rgb(1.0, 0.0, 0.0) to Rgb(1.0, 1.0, 0.0),
        Rgb(1.0, 0.0, 0.0) to Rgb(1.0, 1.0, 1.0),
        Rgb(0.0, 0.0, 1.0) to Rgb(0.0, 0.0, 0.0),
        Rgb(0.0, 0.0, 1.0) to Rgb(1.0, 0.0, 0.0),
        Rgb(0.0, 0.0, 1.0) to Rgb(1.0, 1.0, 0.0),
        Rgb(0.0, 0.0, 1.0) to Rgb(1.0, 1.0, 1.0),
    )
    for (column in 0..1) for ((row, pair) in pairs.withIndex()) {
        val left = 10 + column * 310
        val top = 10 + row * 38
        for (y in top until top + 30) for (x in left until left + 300) {
            val t = (((x - left) + 0.5) * 300.0 + ((y - top) + 0.5) * 30.0) / 90_900.0
            val alpha = 1.0 - t
            val rgb = if (column == 0) {
                pair.first.interpolate(pair.second, t).scale(alpha).plus(t)
            } else {
                pair.first.scale(alpha).plus(t)
            }
            putPixel(expected, width, x, y, rgb)
        }
        for (x in left..left + 300) {
            putPixel(expected, width, x, top, Rgb.BLACK)
            putPixel(expected, width, x, top + 30, Rgb.BLACK)
        }
        for (y in top..top + 30) {
            putPixel(expected, width, left, y, Rgb.BLACK)
            putPixel(expected, width, left + 300, y, Rgb.BLACK)
        }
    }
    return expected
}

private data class Rgb(val red: Double, val green: Double, val blue: Double) {
    fun interpolate(other: Rgb, t: Double) = Rgb(
        red + (other.red - red) * t,
        green + (other.green - green) * t,
        blue + (other.blue - blue) * t,
    )

    fun scale(value: Double) = Rgb(red * value, green * value, blue * value)
    fun plus(value: Double) = Rgb(red + value, green + value, blue + value)

    companion object {
        val BLACK = Rgb(0.0, 0.0, 0.0)
    }
}

private fun putPixel(pixels: ByteArray, width: Int, x: Int, y: Int, rgb: Rgb) {
    val offset = (y * width + x) * 4
    pixels[offset] = (rgb.red.coerceIn(0.0, 1.0) * 255.0).roundToInt().toByte()
    pixels[offset + 1] = (rgb.green.coerceIn(0.0, 1.0) * 255.0).roundToInt().toByte()
    pixels[offset + 2] = (rgb.blue.coerceIn(0.0, 1.0) * 255.0).roundToInt().toByte()
    pixels[offset + 3] = 255.toByte()
}

private fun assertAlphaGradientsPixels(expected: ByteArray, actual: ByteArray) {
    assertEquals(expected.size, actual.size)
    for (offset in expected.indices step 4) {
        val x = (offset / 4) % 640
        val y = (offset / 4) / 640
        val interior = (0..1).any { column -> (0 until 12).any { row ->
            x in (11 + column * 310)..(309 + column * 310) && y in (11 + row * 38)..(39 + row * 38)
        } }
        for (channel in 0..3) {
            val expectedChannel = expected[offset + channel].toInt() and 255
            val actualChannel = actual[offset + channel].toInt() and 255
            val delta = kotlin.math.abs(expectedChannel - actualChannel)
            assertTrue(if (interior && channel < 3) delta <= 2 else delta == 0,
                "pixel ($x,$y) channel=$channel expected=$expectedChannel actual=$actualChannel")
        }
    }
}

private fun assertPixel(pixels: ByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
    val offset = (y * width + x) * 4
    assertEquals(red, pixels[offset].toInt() and 255, "red ($x,$y)")
    assertEquals(green, pixels[offset + 1].toInt() and 255, "green ($x,$y)")
    assertEquals(blue, pixels[offset + 2].toInt() and 255, "blue ($x,$y)")
    assertEquals(alpha, pixels[offset + 3].toInt() and 255, "alpha ($x,$y)")
}

private fun assertPixelNear(pixels: ByteArray, width: Int, x: Int, y: Int, expectedRgb: Int, tolerance: Int) {
    val offset = (y * width + x) * 4
    for (channel in 0..2) {
        val actual = pixels[offset + channel].toInt() and 255
        assertTrue(kotlin.math.abs(actual - expectedRgb) <= tolerance,
            "pixel ($x,$y) channel=$channel expected=$expectedRgb±$tolerance actual=$actual")
    }
}

private fun assertWholeImageRgbNear(pixels: ByteArray, expectedRgb: Int, tolerance: Int) {
    assertEquals(16, pixels.size, "expected complete 2x2 RGBA buffer")
    for (offset in pixels.indices step 4) {
        for (channel in 0..2) {
            val actual = pixels[offset + channel].toInt() and 255
            assertTrue(kotlin.math.abs(actual - expectedRgb) <= tolerance,
                "pixel offset=$offset channel=$channel expected=$expectedRgb±$tolerance actual=$actual")
        }
        assertEquals(255, pixels[offset + 3].toInt() and 255, "alpha offset=$offset")
    }
}
