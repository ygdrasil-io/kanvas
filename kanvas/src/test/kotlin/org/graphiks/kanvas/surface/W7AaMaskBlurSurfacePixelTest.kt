@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public W7 contract for a direct AA coverage source consumed by NORMAL mask blur. */
class W7AaMaskBlurSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `tiny normal blur preserves fractional aa coverage`() {
        val alphaSamples = mutableListOf<Int>()
        listOf(0.125f, 0.375f, 0.625f).forEach { phase ->
            val surface = triangleSurface(phase, Paint(ColorARGB.Black,
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.1f), antiAlias = true))
            val result = renderRepeated(surface)
            assertNative(result)
            assertPixel(result.pixels, 96, 24, 24, 0, 0, 0, 255)
            assertPixel(result.pixels, 96, 90, 90, 0, 0, 0, 0)
            alphaSamples += diagonalBandAlphas(result.pixels)
        }
        assertTrue(alphaSamples.any { it in 1..254 }, alphaSamples.toString())

        val hard = triangleSurface(0.125f, Paint(ColorARGB.Black,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.1f), antiAlias = false)).render()
        assertNative(hard)
        assertTrue(diagonalBandAlphas(hard.pixels).none { it in 1..254 })
    }

    @Test
    fun `normal aa blur preserves halo and integer translation`() {
        val baseSurface = triangleSurface(0f, Paint(ColorARGB.Black,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f), antiAlias = true))
        val base = renderRepeated(baseSurface)
        assertNative(base)
        assertPixel(base.pixels, 96, 24, 24, 0, 0, 0, 255)
        assertAlphaIn(base.pixels, 96, 15, 32, 1..254)
        assertPixel(base.pixels, 96, 9, 32, 0, 0, 0, 0)

        val translatedSurface = Surface(96, 96).also { surface ->
            surface.canvas {
                translate(3f, 2f)
                drawPath(triangle(), Paint(ColorARGB.Black,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f), antiAlias = true))
            }
        }
        val translated = renderRepeated(translatedSurface)
        assertNative(translated)
        assertTranslatedPixels(base.pixels, translated.pixels)
        assertTranslationExcludedRegions(base.pixels, translated.pixels)
    }

    @Test
    fun `normal aa blur applies paint alpha once`() {
        listOf(0, 128, 255).forEach { alpha ->
            val result = renderRepeated(triangleSurface(0f, Paint(ColorARGB.of(alpha, 0, 0, 0),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f), antiAlias = true)))
            assertNative(result)
            assertPixelNear(result.pixels, 96, 24, 24, 0, 0, 0, alpha, 1)
        }

        val black = RectF32.ofLTRB(0f, 0f, 96f, 96f)
        val halfWhite = renderRepeated(Surface(96, 96).also { surface ->
            surface.canvas {
                drawRect(black, Paint(ColorARGB.Black, antiAlias = false))
                drawPath(triangle(), Paint(ColorARGB.of(128, 255, 255, 255),
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f), antiAlias = true))
            }
        })
        assertNative(halfWhite)
        assertPixelNear(halfWhite.pixels, 96, 24, 24, 188, 188, 188, 255, 1)

        val coloredBackground = ColorARGB.of(255, 23, 89, 173)
        val alphaZero = renderRepeated(Surface(96, 96).also { surface ->
            surface.canvas {
                drawRect(black, Paint(coloredBackground, antiAlias = false))
                drawPath(triangle(), Paint(ColorARGB.of(0, 255, 255, 255),
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f), antiAlias = true))
            }
        })
        assertNative(alphaZero)
        for (y in 0 until 96) for (x in 0 until 96) {
            assertPixel(alphaZero.pixels, 96, x, y, 23, 89, 173, 255)
        }
    }

    @Test
    fun `direct aa mask scope remains closed`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 96f, 96f)
        val base = Paint(ColorARGB.Black, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f), antiAlias = true)
        val fixtures = listOf(
            "w6a.layer.unsupported_child" to { base.copy(imageFilter = ImageFilter.Blur(1f, 1f)) },
            "w6a.layer.unsupported_child" to { base.copy(blendMode = BlendMode.PLUS) },
            "w6a.layer.unsupported_child" to { base.copy(style = PaintStyle.STROKE) },
            "w6a.layer.unsupported_child" to { Paint(ColorARGB.Black, maskFilter = MaskFilter.Blur(BlurStyle.OUTER, 1.5f), antiAlias = true) },
        )
        fixtures.forEach { (expectedCode, paint) ->
            val surface = Surface(96, 96).also { target -> target.canvas { drawPath(triangle(), paint()) } }
            assertTerminalAndRecovers(surface, bounds, expectedCode)
        }
        val stencil = Path().apply {
            moveTo(16f, 16f); lineTo(80f, 16f); lineTo(80f, 80f); lineTo(48f, 48f); lineTo(16f, 80f); close()
        }
        assertTerminalAndRecovers(Surface(96, 96).also { target -> target.canvas { drawPath(stencil, base) } }, bounds,
            "w6a.layer.unsupported_child")
        assertTerminalAndRecovers(Surface(96, 96).also { target -> target.canvas {
            saveLayer()
            drawPath(triangle(), base)
            restore()
        } }, bounds, "w6a.layer.unsupported_spatial_filter")
    }

    private fun triangleSurface(phase: Float, paint: Paint): Surface = Surface(96, 96).also { surface ->
        surface.canvas {
            translate(phase, phase)
            drawPath(triangle(), paint)
        }
    }

    private fun triangle(): Path = Path().apply {
        moveTo(16f, 16f); lineTo(80f, 16f); lineTo(16f, 80f); close()
    }

    private fun diagonalBandAlphas(pixels: UByteArray): List<Int> = buildList {
        for (y in 20..76) for (x in 20..76) if (x + y in 93..99) {
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u), pixels.copyOfRange((y * 96 + x) * 4, (y * 96 + x) * 4 + 3))
            add(pixels[(y * 96 + x) * 4 + 3].toInt())
        }
    }

    private fun assertTerminalAndRecovers(surface: Surface, bounds: RectF32, expectedCode: String) {
        val sentinel = UByteArray(96 * 96 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.substringBefore(':') == expectedCode, failure.message)
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Black, antiAlias = false)) }
        surface.render().also { result ->
            assertNative(result)
            assertPixel(result.pixels, 96, 48, 48, 0, 0, 0, 255)
        }
    }

    /** The common domain is frozen before rendering: base [0,92]×[0,93] maps by (+3,+2). */
    private fun assertTranslatedPixels(base: UByteArray, translated: UByteArray) {
        for (y in 0..93) for (x in 0..92) {
            assertContentEquals(base.copyOfRange((y * 96 + x) * 4, (y * 96 + x + 1) * 4),
                translated.copyOfRange(((y + 2) * 96 + x + 3) * 4, ((y + 2) * 96 + x + 4) * 4), "($x,$y)")
        }
    }

    private fun assertTranslationExcludedRegions(base: UByteArray, translated: UByteArray) {
        for (y in 0 until 96) for (x in 93 until 96) assertPixel(base, 96, x, y, 0, 0, 0, 0)
        for (y in 94 until 96) for (x in 0..92) assertPixel(base, 96, x, y, 0, 0, 0, 0)
        for (y in 0 until 96) for (x in 0..2) assertPixel(translated, 96, x, y, 0, 0, 0, 0)
        for (y in 0..1) for (x in 3 until 96) assertPixel(translated, 96, x, y, 0, 0, 0, 0)
    }

    private fun assertNative(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
    }

    private fun renderRepeated(surface: Surface): RenderResult {
        val first = surface.render()
        val second = surface.render()
        assertNative(first)
        assertNative(second)
        assertContentEquals(first.pixels, second.pixels)
        return first
    }

    private fun assertAlphaIn(pixels: UByteArray, width: Int, x: Int, y: Int, expected: IntRange) {
        assertTrue(pixels[(y * width + x) * 4 + 3].toInt() in expected)
    }

    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
        assertContentEquals(ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte()),
            pixels.copyOfRange((y * width + x) * 4, (y * width + x + 1) * 4))
    }

    private fun assertPixelNear(pixels: UByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int, tolerance: Int) {
        val values = pixels.copyOfRange((y * width + x) * 4, (y * width + x + 1) * 4).map(UByte::toInt)
        assertTrue(listOf(red, green, blue, alpha).zip(values).all { (expected, actual) -> kotlin.math.abs(expected - actual) <= tolerance }, values.toString())
    }
}
