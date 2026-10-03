@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll

/**
 * Native W7 diagonal witnesses. For the unit-square cells crossed at each edge,
 * the independent analytic area fractions are about 0.1433982 (outer) and
 * 0.8921356 (inner); these are diagnostic geometry values, not byte oracles.
 */
class W7DiagonalStrokeSurfacePixelTest {
    companion object {
        private const val WIDTH = 192
        private const val HEIGHT = 128
        private val sampleXs = 104..116
        private val domains = listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)

        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun tinyReciprocalAndLiteralOutlinePreserveDiagonalRamp() {
        for (domain in domains) {
            val scenes = listOf(
                "reciprocal-source" to { sourceStroke(domain) },
                "device-line" to { deviceStroke(domain) },
                "literal-fill-outline" to { literalOutline(domain) },
            )
            val samplesByScene = mutableMapOf<String, List<List<Int>>>()
            for ((name, makeSurface) in scenes) {
                val surface = makeSurface()
                val first = surface.render()
                assertNative(first, domain, name)
                assertPinnedPixels(first, "$domain/$name")
                printEvidence(domain, name, first)
                val second = surface.render()
                assertNative(second, domain, "$name replay")
                assertContentEquals(first.pixels, second.pixels, "$domain/$name replay")
                samplesByScene[name] = sampleRow(first)
            }
            val source = samplesByScene.getValue("reciprocal-source")
            assertEquals(source, samplesByScene.getValue("device-line"), "$domain device control ramp")
            assertEquals(source, samplesByScene.getValue("literal-fill-outline"), "$domain literal-outline ramp")
        }
    }

    @Test
    fun transparentAlphaSeparatesDiagonalCoverageFromComposition() {
        val transparent = mutableMapOf<CompositionDomain, RenderResult>()
        val whiteBackground = mutableMapOf<CompositionDomain, RenderResult>()

        for (domain in domains) {
            val clearSurface = deviceStroke(domain)
            val clearFirst = clearSurface.render()
            assertNative(clearFirst, domain, "transparent-stroke")
            assertIndependentStrokePixels(clearFirst, "$domain transparent")
            printEvidence(domain, "transparent-stroke", clearFirst)
            val clearReplay = clearSurface.render()
            assertNative(clearReplay, domain, "transparent-stroke replay")
            assertContentEquals(clearFirst.pixels, clearReplay.pixels, "$domain transparent replay")
            transparent[domain] = clearFirst

            val whiteSurface = Surface(WIDTH, HEIGHT, config = RenderConfig(compositionDomain = domain))
            whiteSurface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()),
                    Paint(ColorARGB.White, antiAlias = false))
                drawPath(devicePath(), blackStroke())
            }
            val whiteFirst = whiteSurface.render()
            assertNative(whiteFirst, domain, "hard-white-background-stroke")
            assertTrue(sampleXs.all { x -> rgba(whiteFirst, x, 60)[3] == 255 },
                "$domain hard white background must stay opaque across the sampled row")
            assertEquals(listOf(0, 0, 0, 255), rgba(whiteFirst, 110, 60),
                "$domain black stroke interior on white")
            for (x in listOf(104, 116)) {
                val pixel = rgba(whiteFirst, x, 60)
                assertTrue(pixel.take(3).all { it in 254..256 }, "$domain white exterior at x=$x: $pixel")
                assertEquals(255, pixel[3], "$domain white exterior alpha at x=$x")
            }
            printEvidence(domain, "hard-white-background-stroke", whiteFirst)
            val whiteReplay = whiteSurface.render()
            assertNative(whiteReplay, domain, "hard-white-background-stroke replay")
            assertContentEquals(whiteFirst.pixels, whiteReplay.pixels, "$domain white-background replay")
            whiteBackground[domain] = whiteFirst
        }

        val linearAlpha = sampleXs.map { rgba(transparent.getValue(CompositionDomain.LINEAR), it, 60)[3] }
        val encodedAlpha = sampleXs.map { rgba(transparent.getValue(CompositionDomain.SRGB_ENCODED), it, 60)[3] }
        assertEquals(linearAlpha, encodedAlpha, "coverage alpha must not depend on the blend domain")
        assertTrue(linearAlpha.any { it in 1..254 }, "sample row must include partial-coverage cells: $linearAlpha")

        val encodedClear = transparent.getValue(CompositionDomain.SRGB_ENCODED)
        val encodedWhite = whiteBackground.getValue(CompositionDomain.SRGB_ENCODED)
        for (x in sampleXs) {
            val transparentAlpha = rgba(encodedClear, x, 60)[3]
            val whitePixel = rgba(encodedWhite, x, 60)
            for (channel in 0..2) {
                assertTrue(
                    whitePixel[channel] + transparentAlpha in 254..256,
                    "encoded white RGB + transparent alpha at x=$x channel=$channel: " +
                        "${whitePixel[channel]} + $transparentAlpha",
                )
            }
        }

        val linearWhiteGray = sampleXs.map { rgba(whiteBackground.getValue(CompositionDomain.LINEAR), it, 60)[0] }
        val encodedWhiteGray = sampleXs.map { rgba(whiteBackground.getValue(CompositionDomain.SRGB_ENCODED), it, 60)[0] }
        for (index in linearAlpha.indices) {
            if (linearAlpha[index] in 1..254) {
                assertTrue(
                    linearWhiteGray[index] > encodedWhiteGray[index],
                    "partial cell x=${sampleXs.elementAt(index)}: linear gray=${linearWhiteGray[index]}, encoded gray=${encodedWhiteGray[index]}",
                )
            }
        }
    }

    private fun sourceStroke(domain: CompositionDomain): Surface {
        val scale = .00005f
        return Surface(WIDTH, HEIGHT, config = RenderConfig(compositionDomain = domain)).also { surface ->
            surface.canvas {
                setMatrix(Matrix3x3F32(sx = 1f / scale, sy = 1f / scale, tx = 50f, ty = 0f))
                drawPath(Path().apply { moveTo(20f * scale, 20f * scale); lineTo(100f * scale, 100f * scale) },
                    blackStroke(5f * scale))
            }
        }
    }

    private fun deviceStroke(domain: CompositionDomain): Surface =
        Surface(WIDTH, HEIGHT, config = RenderConfig(compositionDomain = domain)).also { surface ->
            surface.canvas { drawPath(devicePath(), blackStroke()) }
        }

    private fun literalOutline(domain: CompositionDomain): Surface =
        Surface(WIDTH, HEIGHT, config = RenderConfig(compositionDomain = domain)).also { surface ->
            surface.canvas { drawPath(literalPath(), Paint(ColorARGB.Black, antiAlias = true)) }
        }

    private fun devicePath() = Path().apply { moveTo(70f, 20f); lineTo(150f, 100f) }

    private fun literalPath() = Path().apply {
        moveTo(71.76777f, 18.23223f)
        lineTo(151.76777f, 98.23223f)
        lineTo(148.23223f, 101.76777f)
        lineTo(68.23223f, 21.76777f)
        close()
    }

    private fun blackStroke(width: Float = 5f) = Paint(
        ColorARGB.Black,
        style = PaintStyle.STROKE,
        strokeWidth = width,
        antiAlias = true,
    )

    private fun assertPinnedPixels(result: RenderResult, label: String) {
        assertEquals(listOf(0, 0, 0, 255), rgba(result, 110, 60), "$label black interior")
        assertEquals(listOf(0, 0, 0, 0), rgba(result, 104, 60), "$label clear exterior left")
        assertEquals(listOf(0, 0, 0, 0), rgba(result, 116, 60), "$label clear exterior right")
    }

    private fun assertIndependentStrokePixels(result: RenderResult, label: String) {
        assertEquals(listOf(0, 0, 0, 255), rgba(result, 110, 60), "$label interior")
        assertEquals(listOf(0, 0, 0, 0), rgba(result, 104, 60), "$label exterior left")
        assertEquals(listOf(0, 0, 0, 0), rgba(result, 116, 60), "$label exterior right")
    }

    private fun assertNative(result: RenderResult, domain: CompositionDomain, scene: String) {
        val evidence = "domain=$domain scene=$scene dispatch=${result.stats.opsDispatched} " +
            "refusal=${result.stats.opsRefused} scopes=${result.nativeEvidenceScopeKinds} " +
            "scopeCounts=${result.nativeEvidenceScopeKinds.groupingBy { it }.eachCount()} " +
            "counters=${result.nativeEvidenceCounters}"
        assertTrue(result.isClean, "$evidence diagnostics=${result.diagnostics.summary()}")
        assertTrue(result.stats.opsDispatched > 0, evidence)
        assertEquals(0, result.stats.opsRefused, evidence)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), evidence)
    }

    private fun printEvidence(domain: CompositionDomain, scene: String, result: RenderResult) {
        println(
            "W7_DIAGONAL_AA_EVIDENCE domain=$domain scene=$scene rowY=60 " +
                "rgba=${sampleRow(result)} dispatch=${result.stats.opsDispatched} " +
                "refusal=${result.stats.opsRefused} scopes=${result.nativeEvidenceScopeKinds} " +
                "scopeCounts=${result.nativeEvidenceScopeKinds.groupingBy { it }.eachCount()} " +
                "counters=${result.nativeEvidenceCounters}",
        )
    }

    private fun sampleRow(result: RenderResult): List<List<Int>> = sampleXs.map { x -> rgba(result, x, 60) }

    private fun rgba(result: RenderResult, x: Int, y: Int): List<Int> {
        val offset = (y * result.width + x) * 4
        return (0 until 4).map { result.pixels[offset + it].toInt() }
    }
}
