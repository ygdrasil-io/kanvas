@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.canvas.drawLine
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.skia.gm.image.ChildSamplingRTGm
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

/** Native source and candidate witnesses for the pinned child_sampling_rt scene. */
class W7ChildSamplingPortSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun registeredImageChildKeepsScaleLinearFilteringAndOpacity() {
        RuntimeEffectWgsl4kWiring.install()

        val image = Image.fromPixels(
            width = 2,
            height = 1,
            pixels = byteArrayOf(255.toByte(), 0, 0, 255.toByte(), 0, 0, 255.toByte(), 255.toByte()),
            sourceId = "w7-child-sampling-red-blue-control",
            alphaType = AlphaType.PREMUL,
        )
        val captures = mutableListOf<ControlCapture>()

        for ((alpha, alphaLabel) in listOf(1f to "opaque", 0.5f to "half")) {
            for ((sampling, samplingLabel) in listOf(
                SamplingOptions.NEAREST to "nearest",
                SamplingOptions.LINEAR to "linear",
            )) {
                val label = "CHILD_CONTROL_${alphaLabel}_${samplingLabel.uppercase()}"
                val surface = Surface(width = 20, height = 10)
                val shader = runtimeImageChild(image, alpha, sampling)
                surface.canvas().drawRect(
                    RectF32(0f, 0f, 20f, 10f),
                    Paint(shader = shader, antiAlias = false),
                )
                val first = capture(label, surface, "frame")
                val replay = capture(label, surface, "replay")
                assertArrayEquals(rgba(first), rgba(replay), "$label full-buffer replay")
                captures += ControlCapture(alpha, sampling, alphaLabel, samplingLabel, first)
            }
        }

        // Preserve raw first-frame control pixels before any literal oracle can fail.
        saveFirstFrames(captures.map { control ->
            "w7-child-sampling-control-${control.alphaLabel}-${control.samplingLabel}.png" to rgba(control.first)
        }, 20, 10)

        for (control in captures) {
            val alpha = control.alpha
            val result = control.first
            val endpoint = if (alpha == 1f) {
                listOf(255, 0, 0, 255) to listOf(0, 0, 255, 255)
            } else {
                listOf(188, 0, 0, 128) to listOf(0, 0, 188, 128)
            }
            assertPixelNear(result, 0, 4, endpoint.first, alpha)
            assertPixelNear(result, 19, 4, endpoint.second, alpha)

            val middle = when {
                alpha == 1f && control.sampling == SamplingOptions.NEAREST -> listOf(255, 0, 0, 255)
                alpha == 1f -> listOf(196, 0, 179, 255)
                control.sampling == SamplingOptions.NEAREST -> listOf(188, 0, 0, 128)
                else -> listOf(143, 0, 130, 128)
            }
            assertPixelNear(result, 9, 4, middle, alpha)
        }
    }

    @Test
    fun encodedNativeDiagonalSourceKeepsTransparentBackgroundAndRedPremultiplication() {
        val surface = Surface(
            width = 100,
            height = 100,
            config = RenderConfig.DEFAULT.copy(compositionDomain = CompositionDomain.SRGB_ENCODED),
        )
        surface.canvas().drawLine(
            0f,
            0f,
            100f,
            100f,
            Paint(
                color = ColorARGB.Red,
                antiAlias = true,
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            ),
        )

        val first = capture("ENCODED_DIAGONAL_SOURCE", surface, "frame")
        val replay = capture("ENCODED_DIAGONAL_SOURCE", surface, "replay")
        assertArrayEquals(rgba(first), rgba(replay), "encoded source full-buffer replay")
        saveFirstFrames(listOf("w7-child-sampling-encoded-source.png" to rgba(first)), 100, 100)

        val pixels = rgba(first)
        for (pixel in 0 until 100 * 100) {
            val offset = pixel * 4
            val red = pixels[offset].toInt() and 0xff
            val green = pixels[offset + 1].toInt() and 0xff
            val blue = pixels[offset + 2].toInt() and 0xff
            val alpha = pixels[offset + 3].toInt() and 0xff
            assertEquals(0, green, "source green at (${pixel % 100},${pixel / 100})")
            assertEquals(0, blue, "source blue at (${pixel % 100},${pixel / 100})")
            assertEquals(alpha, red, "premultiplied red at (${pixel % 100},${pixel / 100})")
        }
        for ((x, y) in listOf(75 to 5, 5 to 75, 90 to 10)) {
            assertPixelEquals(first, x, y, listOf(0, 0, 0, 0))
        }
        for ((x, y) in listOf(20 to 20, 50 to 50, 80 to 80)) {
            val offset = (y * first.width + x) * 4
            assertTrue((pixels[offset + 3].toInt() and 0xff) > 0, "diagonal source alpha at ($x,$y)")
        }
    }

    @Test
    fun realChildSamplingGmMatchesSourceScenario() {
        RuntimeEffectWgsl4kWiring.install()

        val gm = ChildSamplingRTGm()
        val surface = Surface(width = 256, height = 256, config = gm.compositionConfig())
        val canvas = surface.canvas()
        canvas.drawRect(
            RectF32(0f, 0f, 256f, 256f),
            Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false),
        )
        val gmCanvas = GmCanvas(canvas, 256, 256)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, 256, 256)

        val first = capture("REAL_CHILD_SAMPLING_RT_GM", surface, "frame")
        val replay = capture("REAL_CHILD_SAMPLING_RT_GM", surface, "replay")
        assertArrayEquals(rgba(first), rgba(replay), "real GM full-buffer replay")
        saveFirstFrames(listOf("w7-child-sampling-real-gm.png" to rgba(first)), 256, 256)

        val pixels = rgba(first)
        for (offset in 3 until pixels.size step 4) {
            assertEquals(255.toByte(), pixels[offset], "real GM alpha at pixel ${offset / 4}")
        }
        for ((x, y) in listOf(200 to 20, 20 to 200, 250 to 20)) {
            assertPixelEquals(first, x, y, listOf(255, 255, 255, 255))
        }
        for ((x, y) in listOf(150 to 150, 200 to 200)) {
            val offset = (y * first.width + x) * 4
            val red = pixels[offset].toInt() and 0xff
            val green = pixels[offset + 1].toInt() and 0xff
            val blue = pixels[offset + 2].toInt() and 0xff
            assertTrue(red > green && red > blue && green < 254 && blue < 254,
                "visible enlarged red diagonal at ($x,$y): [$red,$green,$blue]")
        }
    }
}

private data class ControlCapture(
    val alpha: Float,
    val sampling: SamplingOptions,
    val alphaLabel: String,
    val samplingLabel: String,
    val first: RenderResult,
)

private fun runtimeImageChild(image: Image, alpha: Float, sampling: SamplingOptions): Shader {
    val effect = requireNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1))
    val child = Shader.WithLocalMatrix(
        Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP, sampling),
        Matrix3x3F32.scaling(10f, 10f),
    )
    return effect.makeShader(
        UniformBlock { float1("alpha", alpha) },
        mapOf("child" to child),
    )
}

private fun capture(label: String, surface: Surface, frame: String): RenderResult {
    val result = surface.render()
    val diagnostics = result.diagnostics.entries.map { "${it.code}: ${it.reason}" }
    val rgba = rgba(result)
    println(
        "W7_CHILD_SAMPLING_PORT_NATIVE label=$label frame=$frame " +
            "operationCount=${surface.snapshotOps().size} stats=${result.stats} " +
            "scopes=${result.nativeEvidenceScopeKinds} counters=${result.nativeEvidenceCounters} " +
            "diagnostics=$diagnostics sha256=${sha256(rgba)}",
    )

    assertTrue(result.isClean, "$label $frame diagnostics=${result.diagnostics.summary()}")
    assertTrue(result.diagnostics.isEmpty, "$label $frame diagnostics=$diagnostics")
    assertEquals(0, result.stats.opsRefused, "$label $frame refused operations")
    assertTrue(result.stats.opsDispatched > 0, "$label $frame dispatched operations: ${result.stats}")
    assertTrue(result.stats.drawCallCount > 0, "$label $frame native draw count: ${result.stats}")
    assertTrue(result.stats.pipelineCount > 0, "$label $frame native pipeline count: ${result.stats}")
    assertEquals(PixelFormat.RGBA8, result.format, "$label $frame format")
    assertEquals(surface.width, result.width, "$label $frame width")
    assertEquals(surface.height, result.height, "$label $frame height")
    return result
}

private fun assertPixelNear(result: RenderResult, x: Int, y: Int, expected: List<Int>, alpha: Float) {
    val offset = (y * result.width + x) * 4
    for (channel in 0..2) {
        val actual = result.pixels[offset + channel].toInt() and 0xff
        assertTrue(kotlin.math.abs(actual - expected[channel]) <= 2,
            "pixel ($x,$y) channel $channel was $actual; expected ${expected[channel]} ±2")
    }
    val actualAlpha = result.pixels[offset + 3].toInt() and 0xff
    if (alpha == 1f) assertEquals(255, actualAlpha, "pixel ($x,$y) alpha")
    else assertTrue(actualAlpha in 127..128, "pixel ($x,$y) alpha was $actualAlpha; expected 127..128")
}

private fun assertPixelEquals(result: RenderResult, x: Int, y: Int, expected: List<Int>) {
    val offset = (y * result.width + x) * 4
    val actual = (0..3).map { result.pixels[offset + it].toInt() and 0xff }
    assertEquals(expected, actual, "pixel ($x,$y)")
}

private fun saveFirstFrames(images: List<Pair<String, ByteArray>>, width: Int, height: Int) {
    val evidenceRoot = System.getProperty("w7.ordinaryAaEvidenceDir") ?: return
    val outputs = images.map { (name, pixels) -> File(evidenceRoot, name) to pixels }
    outputs.forEach { (file, _) -> check(!file.exists()) { "Refusing to overwrite evidence image: $file" } }
    outputs.forEach { (file, pixels) -> ComparisonUtils.saveRgbaAsPng(pixels, width, height, file) }
}

private fun rgba(result: RenderResult): ByteArray = result.pixels.map { it.toByte() }.toByteArray()

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
